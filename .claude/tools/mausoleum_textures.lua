-- Draws the voidstone mausoleum's textures: funerary green fire, the fireball of a floating orb (block and item), the maleficent wisp,
-- the glyph wall panels, the starred ceiling and the sarcophagus.
-- Params: out=<dir>
local out = app.params["out"]
local pc = app.pixelColor
local TAU = math.pi * 2

local seed = 1
local function rand()
  seed = (seed * 1103515245 + 12345) % 2147483648
  return seed / 2147483648
end
local function reseed(s) seed = s; rand(); rand() end

local function hex(h)
  return pc.rgba(tonumber(h:sub(2, 3), 16), tonumber(h:sub(4, 5), 16), tonumber(h:sub(6, 7), 16), 255)
end
local CLEAR = pc.rgba(0, 0, 0, 0)

-- Voidstone, dark to light, and the deep recess of its carvings
local V = { hex("#261B29"), hex("#2D262F"), hex("#312C38"), hex("#37303A"), hex("#3B353E"), hex("#3E3C45"), hex("#45464B") }
local CUT = hex("#1B1017")
-- Bronze inlay, dark to light
local B = { hex("#4A3220"), hex("#6A4A2E"), hex("#8A6238"), hex("#B48A58") }
-- The orb's stone: voidstone pushed darker
local O = { hex("#0E0B12"), hex("#141017"), hex("#19141D"), hex("#1E1823"), hex("#231D29"), hex("#29222F"), hex("#2F2936"), hex("#363040") }
-- The night sky of the ceiling: voidstone pushed darker and toward blue violet
local S = { hex("#120F19"), hex("#17131F"), hex("#1B1726"), hex("#201A2C"), hex("#251F33") }
-- Funerary fire: near-black core, lime body, pale tips
local F = {
  core0 = hex("#0C1408"), core1 = hex("#14240C"),
  b1 = hex("#2E7A12"), b2 = hex("#4FB81E"), b3 = hex("#7EE83A"),
  t1 = hex("#C8FF70"), t2 = hex("#EDFFC0"),
}
local HOLLOW = hex("#0E0B12")

local function newImage(w, h)
  local img = Image(w, h, ColorMode.RGB)
  img:clear(CLEAR)
  return img
end
local function put(img, x, y, c) img:drawPixel(x, y, c) end
local function get(img, x, y) return img:getPixel(x, y) end

local function save(img, name)
  local sprite = Sprite(img.width, img.height, ColorMode.RGB)
  sprite.cels[1].image = img
  sprite:saveAs(out .. "/" .. name .. ".png")
  sprite:close()
end

local function saveMeta(name, frametime)
  local f = io.open(out .. "/" .. name .. ".png.mcmeta", "w")
  f:write('{\n  "animation": {\n    "frametime": ' .. frametime .. '\n  }\n}\n')
  f:close()
end

-- Picks from a palette by weights, e.g. { [4] = 3, [5] = 3, [3] = 2 }
local function weighted(pal, weights)
  local total = 0
  for _, w in pairs(weights) do total = total + w end
  local r = rand() * total
  local keys = {}
  for k in pairs(weights) do table.insert(keys, k) end
  table.sort(keys)
  for _, k in ipairs(keys) do
    r = r - weights[k]
    if r <= 0 then return pal[k] end
  end
  return pal[keys[#keys]]
end

local function fill(img, x0, y0, x1, y1, pal, weights)
  for y = y0, y1 do
    for x = x0, x1 do put(img, x, y, weighted(pal, weights)) end
  end
end

-- Smooth value noise on a lattice of nx by ny cells, wrapping, so anything drawn from it tiles
local function periodicNoise(cw, ch, nx, ny)
  local lat = {}
  for j = 0, ny - 1 do
    lat[j] = {}
    for i = 0, nx - 1 do lat[j][i] = rand() end
  end
  local function at(i, j) return lat[j % ny][i % nx] end
  return function(x, y)
    local fx, fy = x / cw, y / ch
    local i0, j0 = math.floor(fx), math.floor(fy)
    local tx, ty = fx - i0, fy - j0
    tx = tx * tx * (3 - 2 * tx)
    ty = ty * ty * (3 - 2 * ty)
    local a = at(i0, j0) + (at(i0 + 1, j0) - at(i0, j0)) * tx
    local b = at(i0, j0 + 1) + (at(i0 + 1, j0 + 1) - at(i0, j0 + 1)) * tx
    return a + (b - a) * ty
  end
end

-- How wide the dark core is, in pixels either side of the axis, at height c through it (0 to 1): a teardrop,
-- rounded at its foot, widest a third of the way up, drawn to a point
local function coreHalfWidth(c, width)
  if c <= 0 or c >= 1 then return 0 end
  return width * (1 - c) ^ 0.9 * math.min(1, 0.45 + c / 0.3 * 0.55)
end

-- Colour of a flame pixel from how far it sits from its tongue's axis (d, 0 to 1), how high up the tongue (v, 0 to 1),
-- and how deep in its dark core (0 outside, 1 at its rim, 2 in its heart)
local function flameColor(d, v, core)
  if core == 2 then return F.core0 end
  if core == 1 then return F.core1 end
  if v > 0.8 then return d < 0.55 and F.t2 or F.t1 end
  if v > 0.62 then
    if d < 0.4 then return F.t1 end
    return d < 0.8 and F.b3 or F.b2
  end
  if d > 0.76 then return F.b1 end
  if d > 0.48 then return F.b2 end
  return F.b3
end

-- One frame of tongues rising from row `foot`. Every motion has a whole number of cycles per loop, so the strip loops.
local function drawTongues(img, oy, cfg, f, frames)
  local t = f / frames
  for y = 0, 15 do
    for x = 0, 15 do
      local u = cfg.foot - y
      if u >= 0 then
        local n = cfg.noise(x, y + cfg.scroll * f) - 0.5
        local bestD, bestV, bestCore = 2, 0, 0
        for _, tg in ipairs(cfg.tongues) do
          local h = tg.h + tg.a1 * math.sin(TAU * (t * tg.k1 + tg.p1)) + tg.a2 * math.sin(TAU * (t * tg.k2 + tg.p2))
          local v = (u + 0.5) / h + n * cfg.vJitter
          if v < 1 then
            local rise = math.min(1, u / h * 1.5)
            local cx = tg.x + (tg.lean or 0) * u + tg.sway * math.sin(TAU * (t * tg.ks + tg.ps) - u * 0.5) * rise
            local hw = tg.w * (1 - math.max(v, 0)) ^ 0.7 * (1 + 0.3 * math.sin(math.pi * math.min(math.max(v, 0) * 1.8, 1)))
            local ax = math.abs(x + 0.5 - cx)
            local d = ax / hw + n * cfg.dJitter
            if d < 1 and d < bestD then
              bestD, bestV, bestCore = d, math.max(v, 0), 0
              if cfg.core and tg.core ~= false then
                -- The core runs from the foot to `top` of the tongue's height, its width scaled to the tongue's
                local foot = cfg.coreFoot or 0.5
                local c = (u + 0.5 - foot) / (h * cfg.core.top - foot) + n * 0.25
                local half = coreHalfWidth(c, cfg.core.width * tg.w / 3.5)
                local ac = ax + n * 0.6
                if half > 0 and ac < half then bestCore = (ac < half - 0.9 and c < 0.7) and 2 or 1 end
              end
            end
          end
        end
        if bestD < 1 then
          local c = flameColor(bestD, bestV, bestCore)
          -- The foot of the fire glows, a little dimmer than the tongues above it
          if u == 0 and bestCore == 0 and c == F.b3 then c = F.b2 end
          put(img, x, oy + y, c)
        elseif cfg.bed and u < cfg.bed then
          put(img, x, oy + y, n > 0.05 and F.b2 or F.b1)
        end
      end
    end
  end
  -- Sparks break off the tips and rise, each on its own turn of the loop
  for _, s in ipairs(cfg.sparks or {}) do
    local age = (f - s.at) % frames
    if age < s.life then
      local x = math.floor(s.x + s.drift * age + 0.5)
      local y = s.y - age * s.speed
      if x >= 0 and x < 16 and y >= 0 and y < 16 and pc.rgbaA(get(img, x, oy + y)) == 0 then
        put(img, x, oy + y, age < s.life * 0.4 and F.t2 or (age < s.life * 0.75 and F.t1 or F.b3))
      end
    end
  end
end

local function fireStrip(cfg, frames)
  local img = newImage(16, 16 * frames)
  for f = 0, frames - 1 do drawTongues(img, f * 16, cfg, f, frames) end
  return img
end

local function tongue(x, w, h, a1, k1, p1, a2, k2, p2, sway, ks, ps)
  return { x = x, w = w, h = h, a1 = a1, k1 = k1, p1 = p1, a2 = a2, k2 = k2, p2 = p2, sway = sway, ks = ks, ps = ps }
end

local FIRE_FRAMES = 16

-- Three tongues, the middle one reaching the top
local function funeraryFire0()
  reseed(101)
  return fireStrip({
    foot = 15, scroll = 2, vJitter = 0.16, dJitter = 0.55, bed = 2, coreFoot = 1,
    noise = periodicNoise(3, 4, 6, 8),
    core = { top = 0.42, width = 2.4 },
    tongues = {
      tongue(3.4, 3.2, 10.5, 1.4, 1, 0.00, 0.8, 3, 0.30, 0.8, 2, 0.10),
      tongue(8.2, 3.8, 15.5, 1.0, 1, 0.50, 0.9, 2, 0.70, 1.0, 1, 0.40),
      tongue(12.9, 3.1, 12.0, 1.5, 2, 0.25, 0.6, 3, 0.90, 0.8, 2, 0.75),
    },
    sparks = {
      { x = 8, y = 2, at = 3, life = 4, speed = 1, drift = 0.3 },
      { x = 3, y = 6, at = 9, life = 5, speed = 1, drift = -0.2 },
      { x = 13, y = 4, at = 13, life = 4, speed = 1, drift = 0.25 },
    },
  }, FIRE_FRAMES)
end

-- Four tongues of uneven height, twin peaks off centre
local function funeraryFire1()
  reseed(202)
  return fireStrip({
    foot = 15, scroll = 2, vJitter = 0.16, dJitter = 0.55, bed = 2, coreFoot = 1,
    noise = periodicNoise(3, 4, 6, 8),
    core = { top = 0.42, width = 2.4 },
    tongues = {
      tongue(1.8, 2.6, 8.5, 1.2, 2, 0.60, 0.7, 3, 0.10, 0.6, 2, 0.90),
      tongue(5.7, 3.3, 14.5, 1.3, 1, 0.20, 0.8, 3, 0.55, 1.1, 1, 0.20),
      tongue(10.6, 3.3, 13.0, 1.2, 1, 0.70, 0.9, 2, 0.35, 1.0, 2, 0.60),
      tongue(14.4, 2.5, 9.5, 1.3, 2, 0.05, 0.6, 3, 0.80, 0.6, 1, 0.30),
    },
    sparks = {
      { x = 6, y = 2, at = 1, life = 4, speed = 1, drift = -0.3 },
      { x = 11, y = 3, at = 7, life = 4, speed = 1, drift = 0.3 },
      { x = 2, y = 7, at = 12, life = 5, speed = 1, drift = 0.2 },
    },
  }, FIRE_FRAMES)
end

-- A ball of the fire with a dark core and tongues licking up and back. The floating orbs are these; the maleficent wisp
-- adds two angry eyes.
local WISP_FRAMES = 8
local function fireBall(seedValue, eyes, coreR)
  reseed(seedValue)
  local cfg = {
    foot = 11, scroll = 2, vJitter = 0.14, dJitter = 0.5,
    noise = periodicNoise(3, 4, 6, 4),
    tongues = {
      tongue(4.4, 3.0, 10.0, 1.0, 1, 0.10, 0.6, 2, 0.40, 0.7, 1, 0.20),
      tongue(8.2, 3.6, 11.8, 0.9, 1, 0.60, 0.7, 3, 0.00, 0.9, 1, 0.70),
      tongue(11.8, 2.8, 9.0, 1.1, 2, 0.30, 0.5, 1, 0.80, 0.7, 2, 0.50),
    },
    sparks = {
      { x = 10, y = 1, at = 2, life = 3, speed = 1, drift = 0.4 },
      { x = 5, y = 2, at = 6, life = 3, speed = 1, drift = 0.3 },
    },
  }
  for _, tg in ipairs(cfg.tongues) do tg.core = false; tg.lean = 0.1 end
  local ballNoise = periodicNoise(2, 2, 8, 8)
  local cx, cy, R = 8.0, 10.3, 5.4
  local img = newImage(16, 16 * WISP_FRAMES)
  for f = 0, WISP_FRAMES - 1 do
    local oy = f * 16
    drawTongues(img, oy, cfg, f, WISP_FRAMES)
    local t = f / WISP_FRAMES
    for y = 0, 15 do
      for x = 0, 15 do
        local dx, dy = x + 0.5 - cx, y + 0.5 - cy
        local r = math.sqrt(dx * dx + dy * dy)
        -- The rim flickers as the noise turns around the ball
        local a = math.atan(dy, dx)
        local n = ballNoise(8 + 6 * math.cos(a) + 3 * math.cos(TAU * t), 8 + 6 * math.sin(a) + 3 * math.sin(TAU * t)) - 0.5
        local rim = R + n * 1.8
        if r < rim then
          local c
          if r < coreR - 1.0 then c = F.core0
          elseif r < coreR then c = F.core1
          elseif dy < -1.5 then c = (r < coreR + 1.2) and F.b3 or F.b2
          elseif r < coreR + 1.0 then c = F.b3
          elseif r < rim - 0.9 then c = F.b2
          else c = F.b1 end
          put(img, x, oy + y, c)
        end
      end
    end
    if eyes then
      -- Narrow slanted eyes, their inner ends low: brows drawn down in fury
      for _, e in ipairs({ { 5, 9 }, { 5, 10 }, { 6, 10 }, { 10, 9 }, { 10, 10 }, { 9, 10 } }) do put(img, e[1], oy + e[2], F.t2) end
      put(img, 4, oy + 9, F.t1); put(img, 11, oy + 9, F.t1)
    end
  end
  return img
end

-- The wisp's dark core is wide enough to hold its face; an orb's is a small dark heart in a ball of fire
local function maleficentWisp() return fireBall(303, true, 3.9) end
local function funeraryFireball() return fireBall(606, false, 2.3) end

-- The orb's item: the first frame of its fireball
local function fireballItem()
  local strip = funeraryFireball()
  local img = newImage(16, 16)
  for y = 0, 15 do
    for x = 0, 15 do put(img, x, y, get(strip, x, y)) end
  end
  return img
end

-- Cuts glyph masks into the stone: '#' is a recess, 'b' a bronze pixel set in one. The recess is shadowed where the
-- lip above or left of it hangs over, and the stone just below or right of it catches the light.
local function carve(img, glyphs, shades)
  local cut, bronze = {}, {}
  for y = 0, 15 do cut[y] = {}; bronze[y] = {} end
  for _, g in ipairs(glyphs) do
    for row, line in ipairs(g.rows) do
      for col = 1, #line do
        local ch = line:sub(col, col)
        local x, y = g.x + col - 1, g.y + row - 1
        if ch == "#" or ch == "b" then cut[y][x] = true end
        if ch == "b" then bronze[y][x] = true end
      end
    end
  end
  local function isCut(x, y) return x >= 0 and y >= 0 and x < 16 and y < 16 and cut[y][x] end
  for y = 0, 15 do
    for x = 0, 15 do
      if cut[y][x] then
        if bronze[y][x] then
          put(img, x, y, (isCut(x - 1, y) or isCut(x, y - 1)) and B[3] or B[4])
        elseif not isCut(x, y - 1) or not isCut(x - 1, y) then
          put(img, x, y, shades.shadow)
        else
          put(img, x, y, shades.floor)
        end
      elseif isCut(x, y - 1) or isCut(x - 1, y) then
        put(img, x, y, shades.lit)
      elseif isCut(x - 1, y - 1) then
        put(img, x, y, shades.litSoft)
      end
    end
  end
end

local POLISHED = { [3] = 2, [4] = 3, [5] = 3, [6] = 2, [7] = 1 }

-- Glyph panels: each block is one column of two glyphs between plain rows, with a carved rule down its right edge
-- whose lit lip is the next block's left edge. All three share the rows and the rule, so any arrangement lines up.

-- Each glyph fits a cell 6 rows tall; the column's axis runs between x = 7 and x = 8
local GLYPH = {
  -- An eye under its brow, a tear and a curl beneath
  eye = {
    "..######..",
    "..........",
    "...####...",
    ".##.##.##.",
    "...####...",
    "....#..#..",
  },
  -- A bird standing, facing left
  bird = {
    ".##......",
    "###......",
    "..#.....#",
    "..#######",
    "...#####.",
    "....#.#..",
  },
  -- A sun disc on spread wings, two serpents hanging from it
  sun = {
    ".....##.....",
    "###.#..#.###",
    ".####.b####.",
    "..##.##.##..",
    "....#..#....",
    "............",
  },
  -- An open hand, fingers up
  hand = {
    "#.#.#.",
    "#.#.#.",
    "#####.",
    "######",
    ".####.",
    ".###..",
  },
  -- A staff with a hooked head and a forked foot
  staff = {
    "##.",
    ".#.",
    ".#.",
    ".#.",
    ".#.",
    "#.#",
  },
  -- Two lines of water
  waves = {
    "..#...#...#.",
    ".#.#.#.#.#.#",
    "#...#...#...",
    "..#...#...#.",
    ".#.#.#.#.#.#",
    "#...#...#...",
  },
  -- A figure with both arms raised
  figure = {
    "#..##..#",
    ".#.##.#.",
    "..####..",
    "...##...",
    "..#..#..",
    ".#....#.",
  },
}

local GLYPH_SHADES = { shadow = CUT, floor = V[1], lit = V[7], litSoft = V[6] }
local DRESSED = { [3] = 2, [4] = 4, [5] = 4, [6] = 2 }

local function glyphPanel(s, glyphs)
  reseed(s)
  local img = newImage(16, 16)
  fill(img, 0, 0, 15, 15, V, DRESSED)
  -- The column rule: a shallow cut down x = 15, lit on the next block's x = 0
  for y = 0, 15 do
    put(img, 15, y, rand() < 0.75 and V[2] or V[1])
    put(img, 0, y, rand() < 0.6 and V[6] or V[7])
  end
  carve(img, glyphs, GLYPH_SHADES)
  return img
end

local function glyphs1()
  return glyphPanel(601, {
    { x = 3, y = 1, rows = GLYPH.eye },
    { x = 3, y = 9, rows = GLYPH.bird },
  })
end

local function glyphs2()
  return glyphPanel(602, {
    { x = 2, y = 1, rows = GLYPH.sun },
    { x = 3, y = 9, rows = GLYPH.hand },
    { x = 10, y = 9, rows = GLYPH.staff },
  })
end

local function glyphs3()
  return glyphPanel(603, {
    { x = 2, y = 1, rows = GLYPH.waves },
    { x = 4, y = 9, rows = GLYPH.figure },
  })
end

-- The starred ceiling
local STAR5 = {
  "..o..",
  "doood",
  ".oco.",
  ".o.o.",
  "d...d",
}
local STAR3 = {
  ".o.",
  "oco",
  "o.o",
}

local function stamp(img, x0, y0, rows)
  for row, line in ipairs(rows) do
    for col = 1, #line do
      local ch = line:sub(col, col)
      if ch == "o" then put(img, x0 + col - 1, y0 + row - 1, B[3])
      elseif ch == "c" then put(img, x0 + col - 1, y0 + row - 1, B[4])
      elseif ch == "d" then put(img, x0 + col - 1, y0 + row - 1, B[2]) end
    end
  end
end

local function starred()
  reseed(707)
  local img = newImage(16, 16)
  local noise = periodicNoise(4, 4, 4, 4)
  for y = 0, 15 do
    for x = 0, 15 do
      local n = noise(x, y) * 0.55 + rand() * 0.45
      put(img, x, y, S[1 + math.min(4, math.floor(n * 5))])
    end
  end
  stamp(img, 2, 2, STAR5)
  stamp(img, 10, 6, STAR3)
  stamp(img, 5, 10, STAR5)
  return img
end

-- The sarcophagus, of polished voidstone. The box is 14 wide and 11 tall, so the sides show rows 5 to 15.

-- The bronze band at the top of the box, the groove under it, and the thin line near its foot
local function sarcophagusBands(img)
  for x = 0, 15 do
    put(img, x, 5, rand() < 0.2 and B[4] or B[3])
    put(img, x, 6, rand() < 0.3 and B[3] or B[2])
    put(img, x, 7, V[1])
    put(img, x, 14, rand() < 0.3 and B[2] or B[3])
    put(img, x, 15, rand() < 0.6 and V[3] or V[2])
  end
  -- The box's corners: lit on the left, shaded on the right
  for y = 8, 13 do put(img, 1, y, V[6]); put(img, 14, y, V[3]) end
end

local function sarcophagusSide()
  reseed(801)
  local img = newImage(16, 16)
  fill(img, 0, 0, 15, 15, V, POLISHED)
  sarcophagusBands(img)
  -- Ankh, eye, ankh
  carve(img, {
    { x = 2, y = 9, rows = { ".#.", "#.#", "###", ".#." } },
    { x = 6, y = 10, rows = { ".##.", "#..#", ".##." } },
    { x = 11, y = 9, rows = { ".#.", "#.#", "###", ".#." } },
  }, GLYPH_SHADES)
  return img
end

local function sarcophagusEnd()
  reseed(802)
  local img = newImage(16, 16)
  fill(img, 0, 0, 15, 15, V, POLISHED)
  sarcophagusBands(img)
  -- An eye drawn out at its corners, a drop hanging from it, a bronze glint in the pupil
  carve(img, {
    { x = 3, y = 9, rows = {
      "..######..",
      "##..#b..##",
      "..######..",
      "....##....",
    } },
  }, GLYPH_SHADES)
  return img
end

-- From above: a polished rim round the dark hollow, the faintest green down in it
local function sarcophagusTop()
  reseed(803)
  local img = newImage(16, 16)
  fill(img, 0, 0, 15, 15, V, POLISHED)
  for i = 0, 15 do
    put(img, i, 0, V[7]); put(img, 0, i, V[7])
    put(img, i, 15, V[3]); put(img, 15, i, V[3])
  end
  for y = 3, 12 do
    for x = 3, 12 do put(img, x, y, HOLLOW) end
  end
  -- The rim's inner lip: in shadow on the upper and left walls, catching light on the lower and right ones
  for i = 3, 12 do
    put(img, i, 2, V[2]); put(img, 2, i, V[2])
    put(img, i, 13, V[6]); put(img, 13, i, V[6])
    put(img, i, 12, O[3]); put(img, 12, i, O[3])
  end
  put(img, 2, 2, V[1]); put(img, 13, 13, V[7]); put(img, 2, 13, V[4]); put(img, 13, 2, V[4])
  put(img, 12, 12, O[4])
  for _, p in ipairs({ { 6, 8 }, { 9, 6 }, { 8, 10 }, { 5, 5 } }) do put(img, p[1], p[2], F.core0) end
  for _, p in ipairs({ { 7, 8 }, { 9, 9 } }) do put(img, p[1], p[2], F.core1) end
  return img
end

save(funeraryFire0(), "funerary_fire_0"); saveMeta("funerary_fire_0", 2)
save(funeraryFire1(), "funerary_fire_1"); saveMeta("funerary_fire_1", 2)
save(maleficentWisp(), "maleficent_wisp"); saveMeta("maleficent_wisp", 2)
save(funeraryFireball(), "funerary_fireball"); saveMeta("funerary_fireball", 2)
save(fireballItem(), "funerary_orb_item")
save(glyphs1(), "voidstone_glyphs")
save(glyphs2(), "voidstone_glyphs_2")
save(glyphs3(), "voidstone_glyphs_3")
save(starred(), "voidstone_starred")
save(sarcophagusSide(), "sarcophagus_side")
save(sarcophagusEnd(), "sarcophagus_end")
save(sarcophagusTop(), "sarcophagus_top")
print("done")
