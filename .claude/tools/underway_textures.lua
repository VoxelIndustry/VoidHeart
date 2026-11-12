-- Draws the shared textures of the Underway's props: dark iron, copper gone green, brass, lead, rubrum and the teal of warding glyphs. Run with Aseprite: --script-param "out=<folder>" --script this file.
-- Every texture is opaque, since 26.3 picks a block's chunk layer from its texture.
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

-- A flat colour with a little grain, so large faces don't read as plastic.
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

local function rect(img, x0, y0, x1, y1, c)
  for y = y0, y1 do for x = x0, x1 do put(img, x, y, c) end end
end

local function frame(img, light, dark)
  for i = 0, S - 1 do
    put(img, i, 0, light) put(img, 0, i, light)
    put(img, i, S - 1, dark) put(img, S - 1, i, dark)
  end
end

local function save(img, name) img:saveAs(out .. "/" .. name .. ".png") end

local STEEL = { 62, 62, 72 }
local BRASS = { 150, 124, 70 }
local BRASS_LIGHT = { 184, 160, 100 }
local BRASS_DARK = { 116, 94, 52 }
local RIVET = { 196, 162, 92 }
local TEAL = { 54, 197, 176 }
local TEAL_DIM = { 39, 125, 99 }
local RUST = { 112, 74, 52 }

-- The shell: dressed slate, a grey with the violet of voidstone in it. The files keep the names of the concrete they replaced.
-- A flag fills the block, lit along its top and left and shaded along its foot, so a floor reads as paving.
local STONE = { 100, 96, 110 }
local function flag(c, seed)
  local image = base(c, 4, seed)
  speckle(image, shade(c, -16), 0.05, seed + 1)
  for i = 0, S - 1 do
    put(image, i, 0, shade(c, 9 + (hash(i, 0, seed + 2) - 0.5) * 6)) put(image, 0, i, shade(c, 7 + (hash(0, i, seed + 2) - 0.5) * 6))
    put(image, i, S - 1, shade(c, -20)) put(image, S - 1, i, shade(c, -16))
  end
  return image
end
local img = flag(STONE, 1)
save(img, "concrete")

-- Where the copper above has wept: the same flag, darker, with green runs down it.
img = flag(shade(STONE, -14), 4)
for y = 1, S - 2 do
  if hash(5, y, 6) < 0.8 then put(img, 5, y, { 70, 110, 98 }) end
  if hash(11, y, 7) < 0.5 then put(img, 11, y, { 62, 96, 88 }) end
  if y > 6 and hash(6, y, 8) < 0.4 then put(img, 6, y, { 62, 96, 88 }) end
end
save(img, "concrete_stained")

img = base(STEEL, 3, 8)
frame(img, shade(STEEL, 16), shade(STEEL, -18))
for _, p in ipairs({ { 2, 2 }, { 13, 2 }, { 2, 13 }, { 13, 13 } }) do
  put(img, p[1], p[2], RIVET)
  put(img, p[1], p[2] + 1, shade(STEEL, -22))
end
save(img, "steel_plate")

img = base({ 76, 76, 88 }, 3, 9)
for y = 0, S - 1 do for x = 0, S - 1 do if hash(x, 0, 10) < 0.25 then put(img, x, y, shade({ 76, 76, 88 }, 6 + (hash(x, y, 11) - 0.5) * 4)) end end end
save(img, "steel")

img = base({ 40, 38, 46 }, 3, 12)
save(img, "steel_dark")

local function painted(c, seed, name)
  local image = base(c, 4, seed)
  for y = 0, S - 1 do
    for x = 0, S - 1 do
      local chip = hash(x // 2, y // 2, seed + 1)
      if chip < 0.05 then put(image, x, y, shade(STEEL, (hash(x, y, seed + 2) - 0.5) * 8))
      elseif chip < 0.075 then put(image, x, y, RUST) end
    end
  end
  save(image, name)
end
painted({ 122, 44, 46 }, 28, "paint_red")

-- Lead sheet: dull, blue in the grey, tarnished in spots.
img = base({ 88, 92, 108 }, 4, 24)
speckle(img, { 70, 72, 86 }, 0.08, 25)
frame(img, { 108, 112, 128 }, { 62, 64, 78 })
save(img, "lead")

-- A warding band: glyphs cut into a dark ground between brass rails, a few of them still holding their light. It keeps the name of the stripes it replaced.
local WARD_GROUND = { 34, 28, 40 }
local function ward(image, y0, seed)
  for x = 0, S - 1 do
    put(image, x, y0, shade(BRASS_LIGHT, (hash(x, y0, seed) - 0.5) * 10))
    put(image, x, y0 + 3, shade(BRASS_DARK, (hash(x, y0 + 3, seed) - 0.5) * 10))
    for y = y0 + 1, y0 + 2 do
      local mark = hash(x, y, seed + 1) < 0.55 and x % 4 ~= 3
      local lit = hash(x // 4, y0, seed + 2) < 0.3
      put(image, x, y, mark and (lit and TEAL or TEAL_DIM) or WARD_GROUND)
    end
  end
end
img = base(WARD_GROUND, 2, 32)
for row = 0, 3 do ward(img, row * 4, 33 + row * 3) end
save(img, "hazard")

local function planks(c, seed, name)
  local image = base(c, 4, seed)
  for y = 0, S - 1 do
    for x = 0, S - 1 do
      if y % 4 == 3 then put(image, x, y, shade(c, -22))
      elseif hash(x // 3, y // 4, seed + 1) < 0.3 then put(image, x, y, shade(c, -8)) end
      if y % 4 ~= 3 and (x + (y // 4) * 7) % 16 == 0 then put(image, x, y, shade(c, -22)) end
    end
  end
  save(image, name)
end
planks({ 100, 80, 60 }, 40, "wood")
planks({ 72, 58, 46 }, 44, "wood_dark")

img = base({ 100, 80, 60 }, 4, 48)
for y = 0, S - 1 do for x = 0, S - 1 do if y % 4 == 3 then put(img, x, y, { 78, 60, 44 }) end end end
frame(img, { 116, 94, 70 }, { 70, 54, 40 })
rect(img, 1, 1, 14, 2, { 112, 90, 66 }) rect(img, 1, 13, 14, 14, { 86, 68, 50 })
rect(img, 1, 1, 2, 14, { 110, 88, 66 }) rect(img, 13, 1, 14, 14, { 86, 68, 50 })
for _, p in ipairs({ { 0, 0 }, { 14, 0 }, { 0, 14 }, { 14, 14 } }) do
  rect(img, p[1], p[2], p[1] + 1, p[2] + 1, BRASS)
  put(img, p[1], p[2], BRASS_LIGHT) put(img, p[1] + 1, p[2] + 1, BRASS_DARK)
end
rect(img, 6, 6, 9, 9, { 44, 36, 46 })
put(img, 6, 6, { 78, 60, 44 }) put(img, 9, 6, { 78, 60, 44 }) put(img, 6, 9, { 78, 60, 44 }) put(img, 9, 9, { 78, 60, 44 })
put(img, 7, 7, TEAL_DIM) put(img, 8, 8, TEAL_DIM) put(img, 8, 7, { 24, 20, 28 }) put(img, 7, 8, { 24, 20, 28 })
save(img, "crate")

img = base({ 140, 128, 98 }, 3, 52)
for y = 0, S - 1 do for x = 0, S - 1 do if (x + y) % 2 == 0 then put(img, x, y, shade({ 140, 128, 98 }, -7 + (hash(x, y, 53) - 0.5) * 4)) end end end
save(img, "canvas")

img = base({ 84, 70, 98 }, 4, 56)
for x = 0, S - 1 do put(img, x, 3, { 150, 124, 70 }) put(img, x, 4, { 126, 102, 60 }) put(img, x, 12, { 66, 54, 80 }) end
save(img, "blanket")

img = base({ 178, 172, 152 }, 3, 60)
for y = 0, S - 1 do for x = 0, S - 1 do if x % 4 == 1 then put(img, x, y, { 110, 122, 136 }) end end end
save(img, "mattress")

img = base({ 204, 202, 190 }, 3, 64)
speckle(img, { 60, 62, 66 }, 0.02, 65)
frame(img, { 222, 220, 210 }, { 168, 168, 160 })
save(img, "enamel")

img = base({ 150, 124, 70 }, 4, 68)
for y = 0, S - 1 do for x = 0, S - 1 do
  if x % 8 == 2 then put(img, x, y, { 184, 160, 100 }) elseif x % 8 == 6 then put(img, x, y, { 116, 94, 52 }) end
end end
save(img, "brass")

save(base({ 34, 34, 38 }, 3, 72), "rubber")
save(base({ 64, 70, 66 }, 4, 76), "cloth_dark")

img = base({ 84, 60, 44 }, 4, 80)
for x = 0, S - 1 do put(img, x, 5, { 62, 44, 32 }) put(img, x, 11, { 62, 44, 32 }) end
save(img, "leather")

img = base({ 28, 36, 44 }, 2, 84)
put(img, 3, 3, { 90, 110, 124 }) put(img, 4, 3, { 60, 76, 88 }) put(img, 3, 4, { 60, 76, 88 })
put(img, 11, 11, { 60, 76, 88 })
save(img, "glass_dark")

img = base({ 44, 70, 56 }, 3, 88)
for y = 0, S - 1 do put(img, 2, y, { 86, 120, 100 }) put(img, 10, y, { 86, 120, 100 }) end
save(img, "glass_green")

local function lens(c, core, seed, name)
  local image = base(c, 3, seed)
  for y = 0, S - 1 do for x = 0, S - 1 do
    local dx, dy = (x % 8) - 3.5, (y % 8) - 3.5
    if dx * dx + dy * dy < 5 then put(image, x, y, core) end
  end end
  save(image, name)
end
lens({ 150, 42, 38 }, { 222, 96, 76 }, 92, "lens_red")
lens({ 226, 198, 124 }, { 250, 238, 186 }, 96, "lens_warm")
lens({ 70, 56, 42 }, { 104, 86, 58 }, 100, "lens_off")

-- The copper of the civilisation's machines, as centuries left it: riveted plate with verdigris gathered in patches, and a darker cut for what sits in recesses.
local COPPER = { 150, 98, 68 }
local PATINA = { 88, 146, 122 }
local function copper(c, verdigris, seed, name, plated)
  local image = base(c, 5, seed)
  for y = 0, S - 1 do for x = 0, S - 1 do
    if hash(x // 2, y // 2, seed + 1) < verdigris then put(image, x, y, shade(PATINA, (hash(x, y, seed + 2) - 0.5) * 18)) end
  end end
  if plated then
    frame(image, shade(c, 18), shade(c, -24))
    for _, p in ipairs({ { 2, 2 }, { 13, 2 }, { 2, 13 }, { 13, 13 } }) do
      put(image, p[1], p[2], { 196, 162, 92 })
      put(image, p[1], p[2] + 1, shade(c, -30))
    end
  end
  save(image, name)
end
copper(COPPER, 0.16, 180, "copper", true)
copper({ 104, 66, 50 }, 0.1, 184, "copper_dark", false)

-- What the fort drew on and what it kept out: voidstone, the same cut with warding glyphs, a ley crystal, void held under glass, and a spent rubrum cell.
local VOIDSTONE = { 55, 48, 58 }
img = base(VOIDSTONE, 4, 200)
speckle(img, { 45, 38, 47 }, 0.08, 201)
save(img, "voidstone")
img = base(VOIDSTONE, 4, 200)
for y = 0, S - 1 do for x = 0, S - 1 do
  local band = y % 8
  if band >= 2 and band <= 5 and x % 4 ~= 3 and hash(x, y, 203) < 0.5 then
    put(img, x, y, hash(x // 4, y // 8, 204) < 0.6 and TEAL or TEAL_DIM)
  end
end end
save(img, "glyph")
img = base(TEAL_DIM, 8, 206)
speckle(img, TEAL, 0.2, 207)
speckle(img, { 20, 80, 55 }, 0.15, 208)
save(img, "ley")
img = base({ 34, 20, 52 }, 4, 210)
speckle(img, { 91, 34, 168 }, 0.18, 211)
speckle(img, { 138, 61, 240 }, 0.05, 212)
for y = 0, S - 1 do if y % 8 < 5 then put(img, 6, y, { 120, 100, 160 }) end end
save(img, "void_glass")
img = base({ 118, 30, 38 }, 6, 214)
speckle(img, { 170, 54, 58 }, 0.15, 215)
speckle(img, { 70, 16, 26 }, 0.15, 216)
for y = 0, S - 1 do for x = 2, S - 1, 8 do put(img, x, y, { 206, 96, 90 }) end end
save(img, "rubrum")

-- The lamp of an alarm: two frames, lit and dark, which the game plays in turn (see lens_strobe.png.mcmeta).
do
  local strobe = Image(S, 2 * S, ColorMode.RGB)
  for frame = 0, 1 do
    local c = frame == 0 and { 200, 50, 42 } or { 64, 28, 26 }
    local core = frame == 0 and { 255, 136, 108 } or { 90, 40, 36 }
    for y = 0, S - 1 do for x = 0, S - 1 do
      local dx, dy = (x % 8) - 3.5, (y % 8) - 3.5
      local colour = dx * dx + dy * dy < 5 and core or shade(c, (hash(x, y, 104 + frame) - 0.5) * 6)
      strobe:drawPixel(x, y + frame * S, pc.rgba(colour[1], colour[2], colour[3], 255))
    end end
  end
  strobe:saveAs(out .. "/lens_strobe.png")
end

-- The copper of the civilisation's machines, as centuries left it: riveted plate with verdigris gathered in patches, and a darker cut for what sits in recesses.
local COPPER = { 150, 98, 68 }
local PATINA = { 88, 146, 122 }
local function copper(c, verdigris, seed, name, plated)
  local image = base(c, 5, seed)
  for y = 0, S - 1 do for x = 0, S - 1 do
    if hash(x // 2, y // 2, seed + 1) < verdigris then put(image, x, y, shade(PATINA, (hash(x, y, seed + 2) - 0.5) * 18)) end
  end end
  if plated then
    frame(image, shade(c, 18), shade(c, -24))
    for _, p in ipairs({ { 2, 2 }, { 13, 2 }, { 2, 13 }, { 13, 13 } }) do
      put(image, p[1], p[2], { 196, 162, 92 })
      put(image, p[1], p[2] + 1, shade(c, -30))
    end
  end
  save(image, name)
end
copper(COPPER, 0.16, 180, "copper", true)
copper({ 104, 66, 50 }, 0.1, 184, "copper_dark", false)

-- The eyes of the machines, lit: amber glass with a slit down the middle. A face three wide shows one eye from the corner; the iris fills a face six wide, and its slit is drawn apart so that it can wander.
local AMBER = { 255, 184, 64 }
img = base(AMBER, 6, 190)
for y = 0, S - 1 do for x = 0, S - 1 do if x % 3 == 1 then put(img, x, y, { 34, 16, 6 }) end end end
save(img, "eye_small")
img = base(AMBER, 8, 192)
for i = 0, 5 do
  put(img, i, 0, { 176, 104, 24 }) put(img, i, 5, { 176, 104, 24 }) put(img, 0, i, { 176, 104, 24 }) put(img, 5, i, { 176, 104, 24 })
end
save(img, "eye_iris")
img = base({ 30, 14, 6 }, 2, 194)
save(img, "eye_slit")

img = base({ 196, 186, 150 }, 4, 104)
for y = 2, 13, 2 do for x = 2, 13 do if hash(x, y, 105) < 0.8 and x < 3 + hash(0, y, 106) * 11 then put(img, x, y, { 142, 132, 104 }) end end end
save(img, "paper")

img = base({ 196, 186, 150 }, 4, 108)
frame(img, { 150, 140, 110 }, { 150, 140, 110 })
for i = 2, 13 do put(img, i, 8, { 70, 66, 60 }) end
for i = 3, 8 do put(img, 5, i, { 70, 66, 60 }) end
for i = 8, 12 do put(img, 10, i, { 70, 66, 60 }) end
for i = 0, 4 do put(img, 10 + i, 12 - i, { 70, 66, 60 }) end
for _, p in ipairs({ { 2, 8 }, { 5, 8 }, { 10, 8 }, { 13, 8 }, { 5, 3 }, { 10, 12 } }) do put(img, p[1], p[2], { 150, 50, 44 }) end
save(img, "map")

img = base(RUST, 6, 112)
speckle(img, { 78, 52, 40 }, 0.2, 113)
speckle(img, shade(STEEL, 4), 0.1, 114)
save(img, "rust")

img = base({ 76, 76, 88 }, 3, 116)
for y = 0, S - 1 do for x = 0, S - 1 do
  if y % 3 == 1 then put(img, x, y, { 24, 26, 30 }) elseif y % 3 == 2 then put(img, x, y, { 92, 100, 112 }) end
end end
frame(img, { 92, 100, 112 }, { 46, 52, 62 })
save(img, "grille")

-- Four gauges on brass. Faces crop a single gauge or a row with their own UV.
img = base(BRASS_DARK, 4, 120)
frame(img, BRASS, { 84, 66, 38 })
for _, o in ipairs({ { 1, 1 }, { 9, 1 }, { 1, 8 }, { 9, 8 } }) do
  rect(img, o[1] + 1, o[2], o[1] + 4, o[2] + 5, { 198, 194, 176 })
  rect(img, o[1], o[2] + 1, o[1] + 5, o[2] + 4, { 198, 194, 176 })
  local tip = hash(o[1], o[2], 121) < 0.5 and 1 or 4
  put(img, o[1] + 2, o[2] + 3, { 40, 40, 44 }) put(img, o[1] + 3, o[2] + 2, { 40, 40, 44 }) put(img, o[1] + tip, o[2] + 1 + (tip == 1 and 3 or 0), { 150, 50, 44 })
end
save(img, "dials")

-- A panel of jacks and switches with a few dead pilot lamps.
img = base({ 74, 52, 44 }, 3, 124)
frame(img, { 104, 74, 58 }, { 48, 34, 30 })
for y = 2, 13, 3 do for x = 2, 13, 3 do
  local roll = hash(x, y, 125)
  local c = roll < 0.12 and { 170, 54, 58 } or (roll < 0.22 and TEAL_DIM or BRASS_LIGHT)
  put(img, x, y, c) put(img, x, y + 1, { 30, 32, 36 })
end end
save(img, "switches")

-- An enamel plate with a stencilled mark, for signs.
local PLATE = { 46, 42, 56 }
img = base(PLATE, 3, 128)
frame(img, BRASS_LIGHT, BRASS_DARK)
rect(img, 3, 4, 3, 11, BRASS_LIGHT) rect(img, 3, 4, 6, 4, BRASS_LIGHT) rect(img, 5, 7, 6, 8, BRASS_LIGHT) rect(img, 3, 11, 5, 11, BRASS_LIGHT)
rect(img, 9, 4, 12, 4, BRASS_LIGHT) rect(img, 12, 4, 12, 8, BRASS_LIGHT) rect(img, 9, 8, 9, 11, BRASS_LIGHT) rect(img, 9, 11, 12, 11, BRASS_LIGHT)
put(img, 10, 6, TEAL_DIM) put(img, 5, 9, TEAL_DIM)
speckle(img, { 88, 146, 122 }, 0.03, 129)
save(img, "sign")

img = base(PLATE, 3, 132)
frame(img, BRASS_LIGHT, BRASS_DARK)
rect(img, 3, 7, 11, 8, BRASS_LIGHT)
for i = 0, 3 do put(img, 9 + i, 4 + i, BRASS_LIGHT) put(img, 9 + i, 11 - i, BRASS_LIGHT) put(img, 10 + i, 4 + i, BRASS_LIGHT) put(img, 10 + i, 11 - i, BRASS_LIGHT) end
put(img, 13, 7, TEAL) put(img, 13, 8, TEAL)
speckle(img, { 88, 146, 122 }, 0.03, 133)
save(img, "sign_arrow")

-- Rows of tins on a shelf, seen from the front.
img = base({ 30, 32, 36 }, 2, 136)
for row = 0, 2 do
  local y0 = row * 5 + 1
  for x = 0, S - 1 do
    local can = x // 3
    local roll = hash(can, row, 137)
    if x % 3 ~= 2 and roll > 0.15 then
      local label = roll < 0.45 and { 124, 70, 56 } or (roll < 0.75 and { 70, 110, 100 } or { 180, 172, 140 })
      put(img, x, y0, { 156, 158, 150 }) put(img, x, y0 + 1, label) put(img, x, y0 + 2, label) put(img, x, y0 + 3, { 120, 122, 116 })
    end
  end
  rect(img, 0, y0 + 4, S - 1, y0 + 4, { 76, 76, 88 })
end
save(img, "tins")

-- Spines of binders and boxes on a shelf.
img = base({ 30, 32, 36 }, 2, 140)
for row = 0, 1 do
  local y0 = row * 8
  local x = 0
  while x < S do
    local w = 1 + math.floor(hash(x, row, 141) * 2.99)
    local roll = hash(x, row, 142)
    local c = roll < 0.3 and { 92, 82, 62 } or (roll < 0.55 and { 70, 84, 80 } or (roll < 0.8 and { 110, 96, 70 } or { 104, 62, 54 }))
    local top = roll < 0.12 and 3 or 1
    for i = 0, w - 1 do
      if x + i < S then
        rect(img, x + i, y0 + top, x + i, y0 + 6, shade(c, i == 0 and 8 or 0))
        put(img, x + i, y0 + 3, { 190, 182, 150 })
      end
    end
    x = x + w + (hash(x, row, 143) < 0.2 and 1 or 0)
  end
  rect(img, 0, y0 + 7, S - 1, y0 + 7, { 76, 76, 88 })
end
save(img, "binders")

-- The block family. Grating and tracks leave their gaps fully clear, never half clear, so they draw as cutouts.
-- Walls: two courses of long ashlar to the block, their joints staggered.
img = base(STONE, 4, 150)
speckle(img, shade(STONE, -16), 0.04, 151)
for y = 0, S - 1 do
  for x = 0, S - 1 do
    local course = y // 8
    if y % 8 == 7 then put(img, x, y, shade(STONE, -24 + (hash(x, y, 152) - 0.5) * 6))
    elseif y % 8 == 0 then put(img, x, y, shade(STONE, 9 + (hash(x, y, 153) - 0.5) * 6))
    elseif x == (course == 0 and 15 or 7) then put(img, x, y, shade(STONE, -22)) end
  end
end
save(img, "concrete_formed")

img = flag(STONE, 154)
for _, p in ipairs({ { 3, 0 }, { 3, 1 }, { 4, 2 }, { 4, 3 }, { 5, 4 }, { 6, 4 }, { 7, 5 }, { 7, 6 }, { 8, 7 }, { 8, 8 }, { 9, 9 }, { 10, 9 }, { 11, 10 }, { 11, 11 }, { 12, 12 }, { 12, 13 }, { 13, 14 }, { 13, 15 }, { 6, 5 }, { 5, 6 }, { 4, 7 }, { 4, 8 }, { 10, 10 }, { 9, 11 } }) do
  put(img, p[1], p[2], { 44, 40, 52 })
end
save(img, "concrete_cracked")

img = base(STEEL, 3, 156)
frame(img, shade(STEEL, 16), shade(STEEL, -18))
ward(img, 6, 157)
for _, p in ipairs({ { 2, 2 }, { 13, 2 }, { 2, 13 }, { 13, 13 } }) do put(img, p[1], p[2], RIVET) put(img, p[1], p[2] + 1, shade(STEEL, -22)) end
save(img, "armour_hazard")

local GATE = { 52, 52, 62 }
img = base(GATE, 3, 160)
frame(img, shade(GATE, 22), shade(GATE, -20))
rect(img, 0, 7, 15, 8, BRASS_DARK)
rect(img, 0, 6, 15, 6, BRASS_LIGHT)
for x = 2, S - 1, 4 do put(img, x, 7, RIVET) end
for _, p in ipairs({ { 3, 3 }, { 11, 3 }, { 3, 11 }, { 11, 11 } }) do
  rect(img, p[1], p[2], p[1] + 1, p[2] + 1, shade(GATE, 34))
  put(img, p[1] + 1, p[2] + 1, shade(GATE, -24)) put(img, p[1], p[2] + 2, shade(GATE, -24)) put(img, p[1] + 1, p[2] + 2, shade(GATE, -24))
end
save(img, "blast_gate")

-- A roller shutter: steel slats, each lit along its top and shaded along its foot, with no frame so a doorway of them reads as one curtain.
local SLAT = { 82, 88, 96 }
img = base(SLAT, 3, 170)
for y = 0, S - 1 do
  for x = 0, S - 1 do
    if y % 4 == 0 then put(img, x, y, shade(SLAT, 20 + (hash(x, y, 171) - 0.5) * 6)) end
    if y % 4 == 3 then put(img, x, y, shade(SLAT, -30)) end
  end
end
speckle(img, shade(RUST, -36), 0.012, 172)
save(img, "shutter")

-- The hatch in a district's floor: heavy tread plate, and the same with the lip where its two leaves meet along the top edge.
local HATCH = { 56, 56, 64 }
local function tread(seed)
  local image = base(HATCH, 3, seed)
  for y = 0, S - 1 do
    for x = 0, S - 1 do
      if (x + y) % 8 == 1 and (x - y) % 4 == 1 then put(image, x, y, shade(HATCH, 16)) put(image, x + 1, y + 1, shade(HATCH, -14)) end
    end
  end
  return image
end
img = tread(170)
save(img, "hatch_plate")
img = tread(170)
rect(img, 0, 0, S - 1, 0, { 18, 19, 22 })
rect(img, 0, 1, S - 1, 1, shade(HATCH, -16))
rect(img, 0, 2, S - 1, 3, shade(HATCH, 10))
rect(img, 0, 4, S - 1, 4, shade(HATCH, -20))
for x = 2, S - 1, 4 do put(img, x, 2, RIVET) put(img, x, 3, shade(HATCH, -10)) end
save(img, "hatch_edge")

img = Image(S, S, ColorMode.RGB)
for y = 0, S - 1 do
  for x = 0, S - 1 do
    local edge = x == 0 or y == 0 or x == S - 1 or y == S - 1
    if edge then put(img, x, y, shade({ 76, 76, 88 }, -10))
    elseif x % 4 == 2 then put(img, x, y, shade({ 76, 76, 88 }, 10 + (hash(x, y, 162) - 0.5) * 6))
    elseif y % 4 == 2 then put(img, x, y, shade({ 76, 76, 88 }, -4 + (hash(x, y, 163) - 0.5) * 6)) end
  end
end
save(img, "grating")

img = base({ 76, 76, 88 }, 3, 164)
for y = 0, S - 1 do for x = 0, S - 1 do
  if (x + y) % 4 == 0 and (x - y) % 8 < 4 then put(img, x, y, { 98, 106, 118 }) end
  if (x - y) % 4 == 0 and (x + y) % 8 >= 4 then put(img, x, y, { 54, 60, 70 }) end
end end
frame(img, { 92, 100, 112 }, { 50, 56, 66 })
save(img, "tread")

local TIE = { 72, 58, 46 }
local function ties(image, rows, x0, x1, seed)
  for _, row in ipairs(rows) do
    for y = row, row + 2 do for x = x0, x1 do put(image, x, y, shade(TIE, (hash(x, y, seed) - 0.5) * 10 - (y == row + 2 and 14 or 0))) end end
  end
end
img = Image(S, S, ColorMode.RGB)
ties(img, { 1, 9 }, 0, S - 1, 166)
for y = 0, S - 1 do put(img, 7, y, { 118, 126, 138 }) put(img, 8, y, { 70, 78, 90 }) put(img, 6, y, { 46, 50, 58 }) put(img, 9, y, { 46, 50, 58 }) end
save(img, "trunk_rail")

img = Image(S, S, ColorMode.RGB)
ties(img, { 1, 9 }, 0, S - 1, 166)
save(img, "track_tie")

img = Image(S, S, ColorMode.RGB)
ties(img, { 2, 10 }, 3, 12, 168)
for y = 0, S - 1 do put(img, 5, y, { 112, 100, 92 }) put(img, 10, y, { 112, 100, 92 }) end
save(img, "feeder_rail")

img = base({ 86, 82, 78 }, 8, 170)
speckle(img, { 58, 56, 54 }, 0.22, 171)
speckle(img, { 112, 108, 102 }, 0.16, 172)
save(img, "ballast")

img = base({ 56, 52, 62 }, 5, 174)
for chunk = 0, 17 do
  local w = 2 + math.floor(hash(chunk, 1, 175) * 3.99)
  local h = 2 + math.floor(hash(chunk, 2, 175) * 2.99)
  local x0 = math.floor(hash(chunk, 3, 175) * (S - w + 0.99))
  local y0 = math.floor(hash(chunk, 4, 175) * (S - h + 0.99))
  local tone = shade(STONE, (hash(chunk, 5, 175) - 0.5) * 36)
  for y = y0, y0 + h - 1 do for x = x0, x0 + w - 1 do
    put(img, x, y, shade(tone, (hash(x, y, 176) - 0.5) * 8 + ((x == x0 or y == y0) and 10 or 0) - ((x == x0 + w - 1 or y == y0 + h - 1) and 12 or 0)))
  end end
end
put(img, 9, 3, RUST) put(img, 10, 3, RUST) put(img, 11, 4, RUST)
save(img, "rubble")

local DOOR = { 84, 84, 96 }
local function door(name, top)
  local image = base(DOOR, 3, top and 178 or 180)
  frame(image, shade(DOOR, 18), shade(DOOR, -22))
  rect(image, 1, 0, 1, S - 1, shade(DOOR, -14))
  for _, y in ipairs(top and { 3, 12 } or { 4, 12 }) do rect(image, 0, y, 2, y + 1, { 44, 48, 56 }) end
  for _, p in ipairs({ { 4, 1 }, { 13, 1 }, { 4, 14 }, { 13, 14 } }) do put(image, p[1], p[2], RIVET) end
  if top then
    rect(image, 6, 5, 11, 10, { 44, 48, 56 })
    rect(image, 7, 6, 10, 9, { 28, 36, 44 })
    put(image, 7, 6, { 90, 110, 124 })
  else
    rect(image, 5, 3, 12, 3, shade(DOOR, -18))
    rect(image, 11, 5, 13, 6, { 150, 124, 70 }) put(image, 13, 7, { 116, 94, 52 })
    for x = 4, 13 do if ((x + 11) // 2) % 2 == 0 then put(image, x, 12, BRASS_LIGHT) else put(image, x, 12, TEAL_DIM) end end
  end
  image:saveAs(out .. "/" .. name .. ".png")
end
door("underway_door_top", true)
door("underway_door_bottom", false)

img = Image(S, S, ColorMode.RGB)
for y = 1, 14 do for x = 4, 11 do
  local c = (x == 4 or y == 1) and shade(DOOR, 18) or ((x == 11 or y == 14) and shade(DOOR, -22) or shade(DOOR, (hash(x, y, 182) - 0.5) * 6))
  put(img, x, y, c)
end end
rect(img, 6, 3, 9, 6, { 28, 36, 44 }) put(img, 6, 3, { 90, 110, 124 })
put(img, 10, 9, { 150, 124, 70 }) put(img, 10, 10, { 116, 94, 52 })
-- The door's item, named as its model asks for it: it goes in textures/item, the two halves above in textures/block.
img:saveAs(out .. "/underway_door.png")
