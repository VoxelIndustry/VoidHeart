-- Draws the shroud gate's textures: dark voidstone frame (side and end), seal, altar (top with the Wraith Ink's outline, side), and the animated void.
-- Params: out=<dir>, ink=<path to wraith_ink.png>
local out = app.params["out"]
local inkPath = app.params["ink"]

local seed = 1234567
local function rand()
  seed = (seed * 1103515245 + 12345) % 2147483648
  return seed / 2147483648
end
local function reseed(s) seed = s end

local function hex(h)
  return Color{ r = tonumber(h:sub(2, 3), 16), g = tonumber(h:sub(4, 5), 16), b = tonumber(h:sub(6, 7), 16), a = 255 }
end

-- Dark voidstone: voidstone's greys pushed darker and a touch toward violet
local D = { hex("#17121B"), hex("#1C1721"), hex("#221C27"), hex("#28212D"), hex("#2E2734"), hex("#35303C"), hex("#3E3946") }
local V = { hex("#2B1A3A"), hex("#3D2456"), hex("#58337E"), hex("#7A49AE") }

local function newImage(w, h) return Image(w, h, ColorMode.RGB) end
local function put(img, x, y, c) img:drawPixel(x, y, c) end

local function save(img, name)
  local sprite = Sprite(img.width, img.height, ColorMode.RGB)
  sprite.cels[1].image = img
  sprite:saveAs(out .. "/" .. name .. ".png")
  sprite:close()
end

-- A mottled dark stone field between two shades
local function mottle(img, x0, y0, x1, y1, lo, hi)
  for y = y0, y1 do
    for x = x0, x1 do
      put(img, x, y, D[lo + math.floor(rand() * (hi - lo + 1))])
    end
  end
end

-- Raised one-pixel border: lit top and left, shaded bottom and right
local function bevel(img, x0, y0, x1, y1, lit, shade)
  for x = x0, x1 do put(img, x, y0, D[lit]); put(img, x, y1, D[shade]) end
  for y = y0, y1 do put(img, x0, y, D[lit]); put(img, x1, y, D[shade]) end
  put(img, x1, y0, D[lit - 1]); put(img, x0, y1, D[shade + 1])
end

-- Frame side: the grain runs top to bottom along the block's axis. Raised margins, a sunken channel down the middle with a hairline of ink.
local function frameSide()
  reseed(11)
  local img = newImage(16, 16)
  mottle(img, 0, 0, 15, 15, 3, 5)
  for y = 0, 15 do
    put(img, 0, y, D[6]); put(img, 1, y, D[5]); put(img, 14, y, D[3]); put(img, 15, y, D[2])
    put(img, 6, y, D[2]); put(img, 7, y, D[1]); put(img, 8, y, D[1]); put(img, 9, y, D[4])
  end
  -- The ink in the channel shows only now and then
  for _, y in ipairs({ 2, 3, 9, 13 }) do put(img, 7, y, V[1]) end
  put(img, 8, 10, V[2])
  return img
end

-- Frame end: a recessed square with a small diamond holding a faint violet glint
local function frameEnd()
  reseed(23)
  local img = newImage(16, 16)
  mottle(img, 0, 0, 15, 15, 3, 5)
  bevel(img, 0, 0, 15, 15, 6, 2)
  bevel(img, 3, 3, 12, 12, 2, 5)
  mottle(img, 4, 4, 11, 11, 2, 3)
  local diamond = { { 7, 5 }, { 8, 5 }, { 6, 6 }, { 9, 6 }, { 5, 7 }, { 10, 7 }, { 5, 8 }, { 10, 8 }, { 6, 9 }, { 9, 9 }, { 7, 10 }, { 8, 10 } }
  for _, p in ipairs(diamond) do put(img, p[1], p[2], D[5]) end
  for _, p in ipairs({ { 7, 6 }, { 8, 6 }, { 6, 7 }, { 9, 7 }, { 6, 8 }, { 9, 8 }, { 7, 9 }, { 8, 9 } }) do put(img, p[1], p[2], D[1]) end
  put(img, 7, 7, V[2]); put(img, 8, 7, V[3]); put(img, 7, 8, V[1]); put(img, 8, 8, V[2])
  return img
end

-- Seal: smoother and darker than the frame, with a few dark violet veins that join across block edges
local function seal()
  reseed(37)
  local img = newImage(16, 16)
  mottle(img, 0, 0, 15, 15, 2, 3)
  for i = 1, 22 do put(img, math.floor(rand() * 16), math.floor(rand() * 16), D[4]) end
  for i = 1, 14 do put(img, math.floor(rand() * 16), math.floor(rand() * 16), D[1]) end
  -- One vein wanders down and wraps at the edges, broken in places, so a wall of seal reads as one slab rather than stripes
  local x = 5
  for y = 0, 15 do
    if y < 4 or y > 6 then put(img, x % 16, y, V[1]) end
    if rand() < 0.6 then
      x = x + (rand() < 0.65 and 1 or -1)
      if y < 4 or y > 6 then put(img, x % 16, y, V[1]) end
    end
  end
  -- A short branch, and a glint where it forks
  local bx, by = 9, 9
  for step = 0, 3 do put(img, (bx + step) % 16, by + math.floor(step / 2), V[1]) end
  put(img, 9, 9, V[2])
  return img
end

-- Altar top: framed like the frame's end, with the outline of a Wraith Ink cut into it where an offered ink lies
local function altarTop()
  reseed(41)
  local img = newImage(16, 16)
  mottle(img, 0, 0, 15, 15, 3, 5)
  bevel(img, 0, 0, 15, 15, 6, 2)
  local ink = app.open(inkPath)
  local inkImage = Image(16, 16, ColorMode.RGB)
  inkImage:drawSprite(ink, 1)
  ink:close()
  local function inside(x, y)
    if x < 0 or y < 0 or x > 15 or y > 15 then return false end
    return app.pixelColor.rgbaA(inkImage:getPixel(x, y)) > 0
  end
  for y = 0, 15 do
    for x = 0, 15 do
      if inside(x, y) then
        local edge = not (inside(x - 1, y) and inside(x + 1, y) and inside(x, y - 1) and inside(x, y + 1))
        if edge then
          put(img, x, y, V[2])
        else
          -- A shallow recess, its lower right catching the light as a cut does
          put(img, x, y, (not inside(x + 1, y + 1) or not inside(x, y + 2)) and D[3] or D[1])
        end
      end
    end
  end
  return img
end

-- Altar side, banded to the model: top slab rows 0-3, neck rows 4-12, base rows 13-15
local function altarSide()
  reseed(53)
  local img = newImage(16, 16)
  mottle(img, 0, 0, 15, 3, 3, 5)
  for x = 0, 15 do put(img, x, 0, D[6]); put(img, x, 3, D[2]) end
  for x = 1, 14 do put(img, x, 2, V[1]) end
  put(img, 7, 2, V[3]); put(img, 8, 2, V[2])
  mottle(img, 0, 4, 15, 12, 2, 4)
  for y = 4, 12 do
    for _, x in ipairs({ 4, 7, 10 }) do put(img, x, y, D[1]) end
    for _, x in ipairs({ 5, 8, 11 }) do put(img, x, y, D[5]) end
  end
  for x = 0, 15 do put(img, x, 4, D[1]) end
  mottle(img, 0, 13, 15, 15, 3, 5)
  for x = 0, 15 do put(img, x, 13, D[6]); put(img, x, 15, D[2]) end
  return img
end

-- The void: flat near black, with barely-there violet wisps rising slowly through it, one frame a pixel higher than the last so the strip loops
local VOID_FRAMES = 16
local function void()
  reseed(67)
  local base = { hex("#050308"), hex("#07040B"), hex("#09060E") }
  local wisp = { hex("#0B0711"), hex("#0D0815"), hex("#100A1A") }
  local field = {}
  for y = 0, 15 do
    field[y] = {}
    for x = 0, 15 do field[y][x] = math.floor(rand() * 3) + 1 end
  end
  local wisps = {}
  for y = 0, 15 do
    wisps[y] = {}
    for x = 0, 15 do wisps[y][x] = 0 end
  end
  -- A few soft wisps, each a short wavering stroke
  for i = 1, 4 do
    local x = math.floor(rand() * 16)
    local y = math.floor(rand() * 16)
    local strength = 1 + math.floor(rand() * 3)
    for step = 0, 4 do
      wisps[(y - step) % 16][x % 16] = math.max(wisps[(y - step) % 16][x % 16], strength - (step >= 3 and 1 or 0))
      if rand() < 0.4 then x = x + (rand() < 0.5 and -1 or 1) end
    end
  end
  local img = newImage(16, 16 * VOID_FRAMES)
  for f = 0, VOID_FRAMES - 1 do
    for y = 0, 15 do
      for x = 0, 15 do
        local w = wisps[(y + f) % 16][x]
        put(img, x, f * 16 + y, w > 0 and wisp[math.min(w, 3)] or base[field[y][x]])
      end
    end
  end
  return img
end

save(frameSide(), "shroud_frame")
save(frameEnd(), "shroud_frame_end")
save(seal(), "shroud_seal")
save(altarTop(), "shroud_altar_top")
save(altarSide(), "shroud_altar_side")
save(void(), "shroud_void")
print("done")
