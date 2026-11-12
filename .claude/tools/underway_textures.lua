-- Draws the shared textures of the Underway's props. Run with Aseprite: --script-param "out=<folder>" --script this file.
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

local STEEL = { 64, 72, 84 }
local RUST = { 112, 74, 52 }

local img = base({ 134, 136, 131 }, 5, 1)
speckle(img, { 112, 114, 110 }, 0.05, 2)
for x = 0, S - 1 do if hash(x, 7, 3) < 0.7 then put(img, x, 7, { 122, 124, 120 }) end end
save(img, "concrete")

img = base({ 104, 106, 101 }, 5, 4)
speckle(img, { 86, 88, 84 }, 0.07, 5)
for y = 0, S - 1 do
  if hash(5, y, 6) < 0.8 then put(img, 5, y, { 90, 92, 86 }) end
  if hash(11, y, 7) < 0.5 then put(img, 11, y, { 94, 96, 90 }) end
end
save(img, "concrete_stained")

img = base(STEEL, 3, 8)
frame(img, shade(STEEL, 16), shade(STEEL, -18))
for _, p in ipairs({ { 2, 2 }, { 13, 2 }, { 2, 13 }, { 13, 13 } }) do
  put(img, p[1], p[2], shade(STEEL, 30))
  put(img, p[1], p[2] + 1, shade(STEEL, -22))
end
save(img, "steel_plate")

img = base({ 72, 80, 92 }, 3, 9)
for y = 0, S - 1 do for x = 0, S - 1 do if hash(x, 0, 10) < 0.25 then put(img, x, y, shade({ 72, 80, 92 }, 6 + (hash(x, y, 11) - 0.5) * 4)) end end end
save(img, "steel")

img = base({ 42, 46, 54 }, 3, 12)
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
painted({ 90, 98, 72 }, 20, "paint_olive")
painted({ 126, 132, 134 }, 24, "paint_grey")
painted({ 128, 60, 50 }, 28, "paint_red")

img = base({ 40, 40, 44 }, 3, 32)
for y = 0, S - 1 do
  for x = 0, S - 1 do
    if ((x + y) // 4) % 2 == 0 then put(img, x, y, shade({ 172, 146, 62 }, (hash(x, y, 33) - 0.5) * 14)) end
    if hash(x // 2, y // 2, 34) < 0.06 then put(img, x, y, STEEL) end
  end
end
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
for _, p in ipairs({ { 5, 6 }, { 6, 6 }, { 7, 6 }, { 9, 6 }, { 10, 6 }, { 5, 8 }, { 7, 8 }, { 8, 8 }, { 10, 8 }, { 5, 10 }, { 6, 10 }, { 8, 10 }, { 9, 10 }, { 10, 10 } }) do
  put(img, p[1], p[2], { 44, 40, 38 })
end
save(img, "crate")

img = base({ 140, 128, 98 }, 3, 52)
for y = 0, S - 1 do for x = 0, S - 1 do if (x + y) % 2 == 0 then put(img, x, y, shade({ 140, 128, 98 }, -7 + (hash(x, y, 53) - 0.5) * 4)) end end end
save(img, "canvas")

img = base({ 86, 96, 108 }, 4, 56)
for x = 0, S - 1 do put(img, x, 3, { 122, 130, 136 }) put(img, x, 4, { 122, 130, 136 }) put(img, x, 12, { 70, 78, 90 }) end
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
lens({ 92, 96, 92 }, { 116, 120, 114 }, 100, "lens_off")

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

img = base({ 72, 80, 92 }, 3, 116)
for y = 0, S - 1 do for x = 0, S - 1 do
  if y % 3 == 1 then put(img, x, y, { 24, 26, 30 }) elseif y % 3 == 2 then put(img, x, y, { 92, 100, 112 }) end
end end
frame(img, { 92, 100, 112 }, { 46, 52, 62 })
save(img, "grille")

-- Four dials over a row of switches. Faces crop a single dial or the switch row with their own UV.
img = base({ 74, 80, 84 }, 3, 120)
frame(img, { 96, 102, 106 }, { 50, 54, 58 })
for _, o in ipairs({ { 1, 1 }, { 9, 1 }, { 1, 8 }, { 9, 8 } }) do
  rect(img, o[1] + 1, o[2], o[1] + 4, o[2] + 5, { 198, 194, 176 })
  rect(img, o[1], o[2] + 1, o[1] + 5, o[2] + 4, { 198, 194, 176 })
  local tip = hash(o[1], o[2], 121) < 0.5 and 1 or 4
  put(img, o[1] + 2, o[2] + 3, { 40, 40, 44 }) put(img, o[1] + 3, o[2] + 2, { 40, 40, 44 }) put(img, o[1] + tip, o[2] + 1 + (tip == 1 and 3 or 0), { 150, 50, 44 })
end
save(img, "dials")

-- A panel of jacks and switches with a few dead pilot lamps.
img = base({ 60, 66, 72 }, 3, 124)
frame(img, { 84, 90, 96 }, { 40, 44, 50 })
for y = 2, 13, 3 do for x = 2, 13, 3 do
  local roll = hash(x, y, 125)
  local c = roll < 0.12 and { 150, 50, 44 } or (roll < 0.2 and { 96, 130, 90 } or { 150, 124, 70 })
  put(img, x, y, c) put(img, x, y + 1, { 30, 32, 36 })
end end
save(img, "switches")

-- An enamel plate with a stencilled mark, for signs.
img = base({ 204, 202, 190 }, 3, 128)
frame(img, { 52, 54, 58 }, { 52, 54, 58 })
rect(img, 3, 4, 4, 11, { 52, 54, 58 }) rect(img, 3, 4, 7, 5, { 52, 54, 58 }) rect(img, 3, 7, 6, 8, { 52, 54, 58 })
rect(img, 9, 4, 12, 5, { 52, 54, 58 }) rect(img, 11, 4, 12, 11, { 52, 54, 58 }) rect(img, 9, 10, 12, 11, { 52, 54, 58 }) rect(img, 9, 7, 12, 8, { 52, 54, 58 })
speckle(img, RUST, 0.03, 129)
save(img, "sign")

img = base({ 204, 202, 190 }, 3, 132)
frame(img, { 52, 54, 58 }, { 52, 54, 58 })
rect(img, 3, 7, 11, 8, { 128, 60, 50 })
for i = 0, 3 do put(img, 9 + i, 4 + i, { 128, 60, 50 }) put(img, 9 + i, 11 - i, { 128, 60, 50 }) put(img, 10 + i, 4 + i, { 128, 60, 50 }) put(img, 10 + i, 11 - i, { 128, 60, 50 }) end
speckle(img, RUST, 0.03, 133)
save(img, "sign_arrow")

-- Rows of tins on a shelf, seen from the front.
img = base({ 30, 32, 36 }, 2, 136)
for row = 0, 2 do
  local y0 = row * 5 + 1
  for x = 0, S - 1 do
    local can = x // 3
    local roll = hash(can, row, 137)
    if x % 3 ~= 2 and roll > 0.15 then
      local label = roll < 0.45 and { 124, 70, 56 } or (roll < 0.75 and { 90, 98, 72 } or { 180, 172, 140 })
      put(img, x, y0, { 156, 158, 150 }) put(img, x, y0 + 1, label) put(img, x, y0 + 2, label) put(img, x, y0 + 3, { 120, 122, 116 })
    end
  end
  rect(img, 0, y0 + 4, S - 1, y0 + 4, { 72, 80, 92 })
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
  rect(img, 0, y0 + 7, S - 1, y0 + 7, { 72, 80, 92 })
end
save(img, "binders")

-- The block family. Grating and tracks leave their gaps fully clear, never half clear, so they draw as cutouts.
local CONCRETE = { 134, 136, 131 }

img = base(CONCRETE, 5, 150)
speckle(img, { 112, 114, 110 }, 0.04, 151)
for y = 0, S - 1 do
  for x = 0, S - 1 do
    if y % 4 == 3 then put(img, x, y, shade({ 122, 124, 120 }, (hash(x, y, 152) - 0.5) * 6))
    elseif y % 4 == 0 then put(img, x, y, shade({ 138, 140, 135 }, (hash(x, y, 153) - 0.5) * 6)) end
    if y % 4 ~= 3 and (y // 4) % 2 == 0 and (x + (y // 4) * 5) % 16 == 0 then put(img, x, y, { 126, 128, 124 }) end
  end
end
save(img, "concrete_formed")

img = base(CONCRETE, 5, 154)
speckle(img, { 112, 114, 110 }, 0.05, 155)
for _, p in ipairs({ { 3, 0 }, { 3, 1 }, { 4, 2 }, { 4, 3 }, { 5, 4 }, { 6, 4 }, { 7, 5 }, { 7, 6 }, { 8, 7 }, { 8, 8 }, { 9, 9 }, { 10, 9 }, { 11, 10 }, { 11, 11 }, { 12, 12 }, { 12, 13 }, { 13, 14 }, { 13, 15 }, { 6, 5 }, { 5, 6 }, { 4, 7 }, { 4, 8 }, { 10, 10 }, { 9, 11 } }) do
  put(img, p[1], p[2], { 70, 72, 70 })
end
save(img, "concrete_cracked")

img = base(STEEL, 3, 156)
frame(img, shade(STEEL, 16), shade(STEEL, -18))
for y = 5, 10 do
  for x = 1, S - 2 do
    if ((x + y) // 3) % 2 == 0 then put(img, x, y, shade({ 172, 146, 62 }, (hash(x, y, 157) - 0.5) * 14)) else put(img, x, y, { 40, 40, 44 }) end
    if hash(x // 2, y // 2, 158) < 0.08 then put(img, x, y, shade(STEEL, -6)) end
  end
end
for _, p in ipairs({ { 2, 2 }, { 13, 2 }, { 2, 13 }, { 13, 13 } }) do put(img, p[1], p[2], shade(STEEL, 30)) put(img, p[1], p[2] + 1, shade(STEEL, -22)) end
save(img, "armour_hazard")

local GATE = { 54, 60, 70 }
img = base(GATE, 3, 160)
frame(img, shade(GATE, 22), shade(GATE, -20))
rect(img, 1, 7, 14, 8, shade(GATE, -18))
rect(img, 1, 6, 14, 6, shade(GATE, 12))
for _, p in ipairs({ { 3, 3 }, { 11, 3 }, { 3, 11 }, { 11, 11 } }) do
  rect(img, p[1], p[2], p[1] + 1, p[2] + 1, shade(GATE, 34))
  put(img, p[1] + 1, p[2] + 1, shade(GATE, -24)) put(img, p[1], p[2] + 2, shade(GATE, -24)) put(img, p[1] + 1, p[2] + 2, shade(GATE, -24))
end
save(img, "blast_gate")

img = Image(S, S, ColorMode.RGB)
for y = 0, S - 1 do
  for x = 0, S - 1 do
    local edge = x == 0 or y == 0 or x == S - 1 or y == S - 1
    if edge then put(img, x, y, shade({ 72, 80, 92 }, -10))
    elseif x % 4 == 2 then put(img, x, y, shade({ 72, 80, 92 }, 10 + (hash(x, y, 162) - 0.5) * 6))
    elseif y % 4 == 2 then put(img, x, y, shade({ 72, 80, 92 }, -4 + (hash(x, y, 163) - 0.5) * 6)) end
  end
end
save(img, "grating")

img = base({ 72, 80, 92 }, 3, 164)
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

img = base({ 70, 72, 70 }, 5, 174)
for chunk = 0, 17 do
  local w = 2 + math.floor(hash(chunk, 1, 175) * 3.99)
  local h = 2 + math.floor(hash(chunk, 2, 175) * 2.99)
  local x0 = math.floor(hash(chunk, 3, 175) * (S - w + 0.99))
  local y0 = math.floor(hash(chunk, 4, 175) * (S - h + 0.99))
  local tone = shade({ 124, 126, 121 }, (hash(chunk, 5, 175) - 0.5) * 36)
  for y = y0, y0 + h - 1 do for x = x0, x0 + w - 1 do
    put(img, x, y, shade(tone, (hash(x, y, 176) - 0.5) * 8 + ((x == x0 or y == y0) and 10 or 0) - ((x == x0 + w - 1 or y == y0 + h - 1) and 12 or 0)))
  end end
end
put(img, 9, 3, RUST) put(img, 10, 3, RUST) put(img, 11, 4, RUST)
save(img, "rubble")

local DOOR = { 86, 94, 102 }
local function door(name, top)
  local image = base(DOOR, 3, top and 178 or 180)
  frame(image, shade(DOOR, 18), shade(DOOR, -22))
  rect(image, 1, 0, 1, S - 1, shade(DOOR, -14))
  for _, y in ipairs(top and { 3, 12 } or { 4, 12 }) do rect(image, 0, y, 2, y + 1, { 44, 48, 56 }) end
  for _, p in ipairs({ { 4, 1 }, { 13, 1 }, { 4, 14 }, { 13, 14 } }) do put(image, p[1], p[2], shade(DOOR, 28)) end
  if top then
    rect(image, 6, 5, 11, 10, { 44, 48, 56 })
    rect(image, 7, 6, 10, 9, { 28, 36, 44 })
    put(image, 7, 6, { 90, 110, 124 })
  else
    rect(image, 5, 3, 12, 3, shade(DOOR, -18))
    rect(image, 11, 5, 13, 6, { 150, 124, 70 }) put(image, 13, 7, { 116, 94, 52 })
    for x = 4, 13 do if ((x + 11) // 2) % 2 == 0 then put(image, x, 12, { 172, 146, 62 }) else put(image, x, 12, { 40, 40, 44 }) end end
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
img:saveAs(out .. "/underway_door_item.png")
