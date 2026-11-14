-- Draws the textures of the props a cult of the void left in the Underway's temples: bone and teeth, the wine flesh of what it worshipped, wax and soot, its banners and runner, the pale brood, and the bed its mosaic was set in.
-- Run with Aseprite: --script-param "out=<folder>" --script-param "sign=<path of underway/sign.png>" --script this file. Every texture is opaque, since 26.3 picks a block's chunk layer from its texture.
local out = app.params["out"]
local pc = app.pixelColor
local S = 16

local function hash(x, y, seed)
  local n = (x * 73856093) ~ (y * 19349663) ~ (seed * 83492791)
  n = (n ~ (n >> 13)) * 1274126177
  return ((n ~ (n >> 16)) & 0xFFFF) / 0xFFFF
end

local function clamp(v) return math.max(0, math.min(255, math.floor(v + 0.5))) end
local function shade(c, d) return { clamp(c[1] + d), clamp(c[2] + d), clamp(c[3] + d) } end
local function put(img, x, y, c) if x >= 0 and x < S and y >= 0 and y < S then img:drawPixel(x, y, pc.rgba(c[1], c[2], c[3], 255)) end end

local function base(c, amp, seed)
  local img = Image(S, S, ColorMode.RGB)
  for y = 0, S - 1 do
    for x = 0, S - 1 do
      put(img, x, y, shade(c, (hash(x, y, seed) - 0.5) * 2 * amp))
    end
  end
  return img
end

local function speckle(img, c, chance, seed)
  for y = 0, S - 1 do
    for x = 0, S - 1 do
      if hash(x, y, seed) < chance then put(img, x, y, c) end
    end
  end
end

local function save(img, name) img:saveAs(out .. "/" .. name .. ".png") end

local GOLD = { 196, 158, 74 }
local GOLD_LIGHT = { 232, 200, 116 }
local GOLD_DARK = { 140, 104, 44 }
local WINE = { 92, 30, 40 }
local PAINT = { 74, 24, 26 }

-- Bone, for teeth, skulls and the pale skin of idols: ivory gone yellow, cracked here and there.
local BONE = { 186, 176, 152 }
local img = base(BONE, 6, 1)
speckle(img, shade(BONE, -26), 0.06, 2)
for x = 0, S - 1 do put(img, x, 0, shade(BONE, 12)) end
save(img, "bone")

-- The flesh of what the cult worshipped, in the wine of its ravine on the research map, with darker folds and a few wet glints.
local FLESH = { { 94, 42, 51 }, { 107, 48, 56 }, { 84, 36, 45 }, { 114, 54, 63 } }
img = Image(S, S, ColorMode.RGB)
for y = 0, S - 1 do
  for x = 0, S - 1 do
    local c = FLESH[1 + math.floor(hash(x // 2, y // 2, 3) * 4) % 4]
    if (x + y * 2 + math.floor(hash(y, 0, 4) * 3)) % 9 == 0 then c = { 42, 18, 24 } end
    if hash(x, y, 5) < 0.04 then c = { 154, 90, 98 } end
    put(img, x, y, c)
  end
end
save(img, "flesh")

-- Wax, dirty ivory with runs down it.
local WAX = { 204, 192, 164 }
img = base(WAX, 5, 6)
for x = 0, S - 1 do
  if hash(x, 0, 7) < 0.4 then
    for y = math.floor(hash(x, 1, 8) * 6), S - 1 do put(img, x, y, shade(WAX, -22)) end
  end
end
save(img, "wax")

-- Soot and cold ash, for wicks and spent coals.
img = base({ 52, 48, 50 }, 4, 9)
speckle(img, { 96, 90, 86 }, 0.12, 10)
save(img, "ash")

-- Coals still burning under a sealed temple's flames.
img = base({ 70, 22, 12 }, 6, 11)
speckle(img, { 214, 104, 36 }, 0.3, 12)
speckle(img, { 255, 196, 96 }, 0.08, 13)
save(img, "embers")

-- Gold leaf, flaking.
img = base(GOLD, 8, 14)
speckle(img, GOLD_LIGHT, 0.1, 15)
speckle(img, GOLD_DARK, 0.08, 16)
save(img, "gilt")

-- Dark stone for altars and plinths, with what was spilled on it dried in runs from the top.
local ALTAR = { 58, 52, 62 }
img = base(ALTAR, 4, 17)
speckle(img, shade(ALTAR, -12), 0.07, 18)
for x = 0, S - 1 do
  if hash(x, 2, 19) < 0.3 then
    local run = 3 + math.floor(hash(x, 3, 20) * 10)
    for y = 0, run do put(img, x, y, (y == run) and { 96, 34, 38 } or { 78, 28, 34 }) end
  end
end
save(img, "altar")

-- The cult's sign, embroidered in gold on wine cloth: an almond mouth with teeth inside its lips, ringed by six eggs. Centred where both the banner and the altar's cloth show it.
local function sigil(image, cx, cy, thread, dark)
  for y = -4, 4 do
    local half = math.floor(2.6 * (1 - (y / 4.6) ^ 2) + 0.5)
    put(image, cx - half, cy + y, thread) put(image, cx + half, cy + y, thread)
    for x = -half + 1, half - 1 do put(image, cx + x, cy + y, dark) end
  end
  put(image, cx, cy - 1, thread) put(image, cx, cy + 1, thread)
  for _, d in ipairs({ { 0, -6 }, { 5, -3 }, { 5, 3 }, { 0, 6 }, { -5, 3 }, { -5, -3 } }) do put(image, cx + d[1], cy + d[2], thread) end
end
local function cloth(seed)
  local image = Image(S, S, ColorMode.RGB)
  for y = 0, S - 1 do
    for x = 0, S - 1 do put(image, x, y, shade(WINE, ((x + y) % 2) * 5 - 2 + (hash(x, y, seed) - 0.5) * 6)) end
  end
  for y = 0, S - 1 do put(image, 2, y, GOLD_DARK) put(image, 13, y, GOLD_DARK) end
  return image
end
img = cloth(21)
sigil(img, 8, 7, GOLD, { 34, 10, 16 })
save(img, "banner")
img = cloth(22)
for _, y in ipairs({ 3, 9 }) do for x = 6, 9 do put(img, x, y, GOLD_DARK) end end
save(img, "banner_tail")

-- The runner down a temple's aisle: wine with ochre borders and a lozenge every four blocks of its length, so lengths of it join.
img = Image(S, S, ColorMode.RGB)
for y = 0, S - 1 do
  for x = 0, S - 1 do
    local c = shade({ 82, 32, 30 }, (hash(x, y, 23) - 0.5) * 8)
    if x == 3 or x == 12 then c = { 150, 110, 60 } elseif x == 4 or x == 11 then c = { 60, 22, 22 } end
    local dy = math.abs((y % 8) - 4)
    if math.abs(x - 7.5) + dy <= 2.5 and math.abs(x - 7.5) + dy >= 1.5 then c = { 150, 110, 60 } end
    put(img, x, y, c)
  end
end
save(img, "rug")

-- Eggs: pale green-ivory, veined.
local EGG = { 208, 206, 178 }
img = base(EGG, 4, 24)
for i = 0, 10 do put(img, math.floor(hash(i, 0, 25) * 16), math.floor(hash(i, 1, 25) * 16), { 170, 150, 140 }) end
for x = 0, S - 1 do put(img, x, 0, shade(EGG, 10)) put(img, x, S - 1, shade(EGG, -20)) end
save(img, "egg")

-- The brood: small pale bodies curled tight against each other, each with a dark eye.
img = base({ 150, 122, 112 }, 4, 26)
for cy = 1, S - 1, 4 do
  for cx = 1 + (cy // 4) % 2 * 2, S - 1, 4 do
    for _, d in ipairs({ { 0, 0 }, { 1, 0 }, { 0, 1 }, { 1, 1 }, { 2, 1 } }) do put(img, cx + d[1], cy + d[2], { 210, 190, 168 }) end
    put(img, cx + 1, cy, { 232, 216, 196 })
    put(img, cx + 2, cy + 1, { 62, 40, 44 })
  end
end
save(img, "brood")

-- Murky green under glass, with pale things floating in it.
img = base({ 52, 74, 56 }, 4, 27)
for i = 0, 5 do
  local x = 2 + math.floor(hash(i, 0, 28) * 11)
  local y = 2 + math.floor(hash(i, 1, 28) * 11)
  put(img, x, y, { 176, 170, 140 }) put(img, x + 1, y, { 176, 170, 140 }) put(img, x + 1, y + 1, { 150, 144, 116 })
end
for y = 1, 6 do put(img, 2, y, { 104, 136, 106 }) end
save(img, "spawn_glass")

-- Fallen tesserae, in the mosaic's colours.
local TILES = { { 30, 22, 36 }, { 150, 114, 52 }, { 92, 40, 50 }, { 39, 125, 99 }, { 176, 158, 140 }, { 91, 34, 168 }, { 15, 10, 19 }, { 44, 30, 60 } }
img = Image(S, S, ColorMode.RGB)
for y = 0, S - 1 do
  for x = 0, S - 1 do
    local c = TILES[1 + math.floor(hash(x // 2, y // 2, 29) * #TILES) % #TILES]
    if x % 2 == 1 or y % 2 == 1 then c = shade(c, -24) end
    put(img, x, y, c)
  end
end
save(img, "tesserae")

-- Dried paint, dark red going brown, for what the cult daubed on walls.
img = base(PAINT, 6, 30)
speckle(img, shade(PAINT, -14), 0.1, 31)
save(img, "paint_dark")

-- An eye of the idol of teeth: yellow-green iris with a slit.
img = base({ 200, 208, 64 }, 5, 32)
for y = 0, S - 1 do put(img, 8, y, { 10, 10, 4 }) put(img, 7, y, { 138, 148, 32 }) end
save(img, "idol_eye")

-- A station sign the cult painted over: the sign as it was, with its almond mouth daubed across it and run.
local sign = app.open(app.params["sign"])
img = Image(S, S, ColorMode.RGB)
img:drawSprite(sign, 1)
sign:close()
-- Brighter than the dried paint on stone, or it would sink into the dark plate
local function daub(x, y) put(img, x, y, shade({ 128, 40, 36 }, (hash(x, y, 33) - 0.5) * 14)) end
for y = -4, 4 do
  local half = math.floor(3.4 * (1 - (y / 5) ^ 2) + 0.5)
  daub(8 - half, 8 + y) daub(8 + half, 8 + y) daub(7 - half, 8 + y)
end
for y = 6, 10 do daub(8, y) end
for _, d in ipairs({ { 5, 13 }, { 6, 14 }, { 11, 13 }, { 11, 14 }, { 11, 15 }, { 4, 12 } }) do daub(d[1], d[2]) end
save(img, "sign_defaced")

-- The bed a mosaic was set in, where its tiles fell: lime mortar showing the grooves the tesserae left.
local BED = { 120, 110, 98 }
img = base(BED, 5, 34)
for y = 0, S - 1 do
  for x = 0, S - 1 do
    local gx = (x + math.floor(hash(y // 3, 0, 35) * 2)) % 3
    if gx == 2 or y % 3 == 2 then put(img, x, y, shade(BED, -18 + (hash(x, y, 36) - 0.5) * 6)) end
  end
end
speckle(img, { 150, 114, 52 }, 0.015, 37)
speckle(img, { 30, 22, 36 }, 0.03, 38)
speckle(img, { 91, 34, 168 }, 0.01, 39)
save(img, "mosaic_bed")
