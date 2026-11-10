-- Wraith ink timing masks for textures/entity/wraith_ink, see WraithInk and wraith_ink.fsh. Run from the repo root:
-- Aseprite.exe -b --script-param w=32 --script-param h=64 --script-param seed=1 --script-param out=<png> --script-param view=<preview png> --script .claude/tools/wraith_ink_mask.lua
-- tall_N uses 32x64 and seed N+1, square_N 32x32 and seed N+11.
-- Wraith ink timing masks. Alpha: when the ink reaches each pixel, 0 first, 1 never.
-- Red: when the hole opens there, from the middle out, 1 never. Green and blue unused.
local pc = app.pixelColor
local W = tonumber(app.params["w"])
local H = tonumber(app.params["h"])
local seed = tonumber(app.params["seed"])
-- The tunnel mouth, as a fraction of the mark's half size: the hole must stay inside it
local COVER = 0.6

local state = seed * 7919 + 13
local function rand()
  state = (state * 1103515245 + 12345) % 2147483648
  return state / 2147483648
end

local function hash(x, y, s)
  local h = (x * 374761393 + y * 668265263 + s * 982451653) & 0xFFFFFFFF
  h = ((h ~ (h >> 13)) * 1274126177) & 0xFFFFFFFF
  return ((h ~ (h >> 16)) & 0xFFFF) / 65535
end
local function smooth(t) return t * t * (3 - 2 * t) end
local function noise(x, y, s)
  local x0, y0 = math.floor(x), math.floor(y)
  local fx, fy = smooth(x - x0), smooth(y - y0)
  local a = hash(x0, y0, s) + (hash(x0 + 1, y0, s) - hash(x0, y0, s)) * fx
  local b = hash(x0, y0 + 1, s) + (hash(x0 + 1, y0 + 1, s) - hash(x0, y0 + 1, s)) * fx
  return a + (b - a) * fy
end
local function fbm(x, y, s, octaves)
  local total, amp, norm, f = 0, 1, 0, 1
  for o = 0, octaves - 1 do
    total = total + amp * noise(x * f, y * f, s + o * 31)
    norm = norm + amp
    amp = amp * 0.5
    f = f * 2
  end
  return total / norm
end
local function clamp(t, a, b) if t < a then return a elseif t > b then return b end return t end
local function smoothstep(a, b, t) return smooth(clamp((t - a) / (b - a), 0, 1)) end

-- Pixel centre in mark units, -1..1 across each axis; `aspect` scales y so distances are true lengths
local aspect = H / W
local function markPos(px, py)
  return ((px + 0.5) / W) * 2 - 1, (((py + 0.5) / H) * 2 - 1)
end

-- How far out a point is, 1 on the mouth's edge, boxy so a tall mark fills a tall mouth
local function boxRadius(x, y)
  local qx, qy = math.abs(x) / COVER, math.abs(y) / COVER
  return (qx ^ 3 + qy ^ 3) ^ (1 / 3)
end

-- The outline wobbles around the mouth's edge, by angle so it wraps without a seam
local function outline(x, y)
  local angle = math.atan(y * aspect, x)
  local c, s = math.cos(angle) * 2.6, math.sin(angle) * 2.6
  local wobble = fbm(c + 9, s + 9, seed * 3 + 1, 5)
  local jag = noise(c * 3.5 + 40, s * 3.5 + 40, seed * 3 + 7)
  return 1.2 + (wobble - 0.5) * 0.7 + (jag - 0.5) * 0.22
end

local seeds = {}
local count = 3 + math.floor(rand() * 2)
for i = 1, count do
  local angle = rand() * math.pi * 2
  -- Spread over the mouth, so separate blots show before they meet
  local reach = 0.35 + rand() * 0.5
  seeds[i] = { x = math.cos(angle) * reach * COVER, y = math.sin(angle) * reach * COVER, delay = (i == 1) and 0 or 0.08 + rand() * 0.3 }
end

local ink, hole = {}, {}
local maxSeed = 0
for py = 0, H - 1 do
  for px = 0, W - 1 do
    local x, y = markPos(px, py)
    local r = boxRadius(x, y) / outline(x, y)
    local i = py * W + px
    if r < 1 then
      -- Fronts from each seed, slowed and sped by noise so they arrive ragged and meet unevenly
      local warp = fbm(x * 2.6 + 4, y * 2.6 * aspect + 4, seed * 3 + 2, 3)
      local best = 1e9
      for _, s in ipairs(seeds) do
        local dx, dy = x - s.x, (y - s.y) * aspect
        local d = math.sqrt(dx * dx + dy * dy) * (0.45 + warp * 1.3) + s.delay
        if d < best then best = d end
      end
      ink[i] = { seedTime = best, r = r }
      local seen = best
      if seen > maxSeed then maxSeed = seen end
      -- The hole grows from the middle out, its front as ragged as the outline, and never past the mouth
      -- Rounder than the mouth, and ragged, so it reads as torn rather than cut to the mouth's frame
      local holeWarp = fbm(x * 5 + 21, y * 5 * aspect + 21, seed * 3 + 3, 3)
      local qx, qy = math.abs(x) / COVER, math.abs(y) / COVER
      local round = (qx ^ 2.2 + qy ^ 2.2) ^ (1 / 2.2)
      -- Fully open at 0.8; past that the hole's edge keeps counting up, so its glow has ink to burn into
      local h = math.min(0.99, 0.8 * (round / 0.97) * (0.72 + holeWarp * 0.56))
      if boxRadius(x, y) > 0.97 then h = math.max(h, 0.81) end
      hole[i] = h
    end
  end
end

local arrival = {}
for py = 0, H - 1 do
  for px = 0, W - 1 do
    local i = py * W + px
    local cell = ink[i]
    if cell then
      -- The seeds' fronts lead almost to the outline, which only takes over at the very edge
      local t = math.max(math.min(1, cell.seedTime / (maxSeed * 0.85)), smoothstep(0.78, 1.0, cell.r) * 0.98)
      arrival[i] = 0.03 + 0.85 * t
    else
      arrival[i] = 1
    end
  end
end

-- Tendrils creep past the outline last, thinning toward their tips
local tendrils = 5 + math.floor(rand() * 3)
for _ = 1, tendrils do
  local angle = rand() * math.pi * 2
  local x, y = 0, 0
  -- Walk out from the middle to the outline along this angle
  for step = 0, 200 do
    local t = step / 200
    local tx, ty = math.cos(angle) * t, math.sin(angle) * t / aspect
    if boxRadius(tx, ty) / outline(tx, ty) >= 0.95 then x, y = tx, ty break end
  end
  local length = 0.18 + rand() * 0.22
  local steps = 40
  local heading = angle
  for step = 0, steps do
    local t = step / steps
    heading = heading + (noise(step * 0.35, 0, seed * 11 + angle * 100) - 0.5) * 0.5
    local px = math.floor((x + 1) / 2 * W)
    local py = math.floor((y + 1) / 2 * H)
    local value = 0.86 + 0.09 * t
    local width = (t < 0.45) and 1 or 0
    for ox = -width, width do
      for oy = -width, width do
        local qx, qy = px + ox, py + oy
        if qx >= 0 and qx < W and qy >= 0 and qy < H then
          local i = qy * W + qx
          if arrival[i] > value then arrival[i] = value end
        end
      end
    end
    x = x + math.cos(heading) * length / steps * 2
    y = y + math.sin(heading) * length / steps * 2 / aspect
  end
end

local img = Image(W, H, ColorMode.RGB)
for py = 0, H - 1 do
  for px = 0, W - 1 do
    local i = py * W + px
    local h = hole[i] or 1
    img:drawPixel(px, py, pc.rgba(math.floor(h * 255 + 0.5), 255, 255, math.floor(arrival[i] * 255 + 0.5)))
  end
end
img:saveAs(app.params["out"])

-- A readable preview: ink arrival as grey, never as magenta, hole region tinted
local view = Image(W, H, ColorMode.RGB)
for py = 0, H - 1 do
  for px = 0, W - 1 do
    local i = py * W + px
    local a = arrival[i]
    if a >= 1 then
      view:drawPixel(px, py, pc.rgba(255, 0, 255, 255))
    else
      local g = math.floor(a * 255)
      local h = hole[i] or 1
      if h < 0.8 then
        view:drawPixel(px, py, pc.rgba(g, math.floor(g * 0.6), 255, 255))
      else
        view:drawPixel(px, py, pc.rgba(g, g, g, 255))
      end
    end
  end
end
view:saveAs(app.params["view"])
