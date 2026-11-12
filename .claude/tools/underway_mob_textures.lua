-- Draws the textures of the Underway's Mantlet, Ram cart, Sapper and Fitter, each with its three lights: the eye, the glyph bands and the rubrum.
-- Run with Aseprite: --script-param "out=<folder>" --script this file.
-- Each box of a model is painted where its texOffs unwraps it: top and bottom side by side, then right, front, left and back in a row.
-- Same copper, brass and lights as automaton_texture.lua.
local out = app.params["out"]
local pc = app.pixelColor

local function hash(x, y, seed)
  local n = (x * 73856093) ~ (y * 19349663) ~ (seed * 83492791)
  n = (n ~ (n >> 13)) * 1274126177
  return ((n ~ (n >> 16)) & 0xFFFF) / 0xFFFF
end

local function clamp(v) return math.max(0, math.min(255, math.floor(v + 0.5))) end
local function shade(c, d) return { clamp(c[1] + d), clamp(c[2] + d), clamp(c[3] + d) } end

local COPPER = { 150, 98, 68 }
local COPPER_DARK = { 106, 68, 50 }
local BRASS = { 178, 142, 72 }
local PATINA = { 88, 146, 122 }
local IRON = { 62, 62, 68 }
local GLASS = { 20, 22, 26 }
local LENS_OFF = { 70, 46, 18 }
local LENS = { 255, 184, 64 }
local SLIT = { 34, 16, 6 }
local GLYPH_OFF = { 54, 62, 58 }
local GLYPH = { 54, 197, 176 }
local RUBRUM_OFF = { 92, 30, 24 }
local RUBRUM = { 232, 70, 44 }

-- The four images of one machine: its skin and its three lights.
local function sheet(w, h)
  return { w = w, h = h, img = Image(w, h, ColorMode.RGB), eye = Image(w, h, ColorMode.RGB), glyphs = Image(w, h, ColorMode.RGB), rubrum = Image(w, h, ColorMode.RGB) }
end

local function put(s, x, y, c)
  if x >= 0 and x < s.w and y >= 0 and y < s.h then s.img:drawPixel(x, y, pc.rgba(c[1], c[2], c[3], 255)) end
end
local function light(layer, x, y, c) layer:drawPixel(x, y, pc.rgba(c[1], c[2], c[3], 255)) end

-- Metal worn by centuries: grain, and verdigris gathered in patches.
local function metal(s, x0, y0, w, h, c, seed, verdigris)
  for y = y0, y0 + h - 1 do
    for x = x0, x0 + w - 1 do
      if verdigris > 0 and hash(x // 2, y // 2, seed + 100) < verdigris then
        put(s, x, y, shade(PATINA, (hash(x, y, seed) - 0.5) * 18))
      else
        put(s, x, y, shade(c, (hash(x, y, seed) - 0.5) * 10))
      end
    end
  end
end

-- The six faces of a box of the model. Returns where its front face starts.
local function box(s, u, v, w, h, d, c, seed, verdigris)
  verdigris = verdigris or 0
  metal(s, u + d, v, w, d, shade(c, 12), seed, verdigris)
  metal(s, u + d + w, v, w, d, shade(c, -26), seed + 1, verdigris)
  metal(s, u, v + d, d, h, shade(c, -8), seed + 2, verdigris)
  metal(s, u + d, v + d, w, h, c, seed + 3, verdigris)
  metal(s, u + d + w, v + d, d, h, shade(c, -8), seed + 4, verdigris)
  metal(s, u + d + w + d, v + d, w, h, shade(c, -14), seed + 5, verdigris)
  return u + d, v + d
end

-- A band around the four sides of a box, rows counted from the top of its sides.
local function band(s, u, v, w, d, row0, row1, c, seed)
  metal(s, u, v + d + row0, 2 * (w + d), row1 - row0 + 1, c, seed, 0)
end

-- A row of glyphs: dark marks on the skin, lit on the glyph layer.
local function glyphRow(s, x0, x1, y, seed)
  for x = x0, x1 do
    if hash(x, y, seed) < 0.55 then
      put(s, x, y, GLYPH_OFF)
      light(s.glyphs, x, y, GLYPH)
    end
  end
end
local function glyphBand(s, u, v, w, d, row, seed) glyphRow(s, u, u + 2 * (w + d) - 1, v + d + row, seed) end

local function rivet(s, x, y) put(s, x, y, shade(BRASS, 26)) end

-- A port of dark glass in a brass frame, with the rubrum shard behind it.
local function port(s, x, y, w, h)
  for py = y, y + h - 1 do for px = x, x + w - 1 do put(s, px, py, BRASS) end end
  for py = y + 1, y + h - 2 do
    for px = x + 1, x + w - 2 do
      if hash(px, py, 7) < 0.7 then
        put(s, px, py, RUBRUM_OFF)
        light(s.rubrum, px, py, RUBRUM)
      else
        put(s, px, py, GLASS)
      end
    end
  end
end

-- A lens of amber glass, dark until the machine's eye opens, with a slit where it is wide enough for one.
local function lens(s, x, y, w, h)
  for py = y, y + h - 1 do
    for px = x, x + w - 1 do
      local slit = w >= 3 and px == x + w // 2
      put(s, px, py, slit and GLASS or LENS_OFF)
      light(s.eye, px, py, slit and SLIT or LENS)
    end
  end
end

local function save(s, name)
  s.img:saveAs(out .. "/" .. name .. ".png")
  s.eye:saveAs(out .. "/" .. name .. "_eye.png")
  s.glyphs:saveAs(out .. "/" .. name .. "_glyphs.png")
  s.rubrum:saveAs(out .. "/" .. name .. "_rubrum.png")
end

-- Mantlet: a squat machine and the slab it walks behind.
do
  local s = sheet(128, 64)
  -- Head: a low casing with a dark visor band, hidden from the front by the shield.
  box(s, 0, 0, 6, 5, 6, COPPER, 1, 0.12)
  band(s, 0, 0, 6, 6, 0, 0, BRASS, 2)
  band(s, 0, 0, 6, 6, 2, 2, GLASS, 3)
  -- Trunk, banded like the Automaton's.
  local fx, fy = box(s, 0, 12, 10, 10, 6, COPPER, 10, 0.14)
  band(s, 0, 12, 10, 6, 0, 0, BRASS, 11)
  band(s, 0, 12, 10, 6, 4, 4, COPPER_DARK, 12)
  glyphBand(s, 0, 12, 10, 6, 7, 13)
  rivet(s, fx, fy + 1) rivet(s, fx + 9, fy + 1) rivet(s, fx, fy + 9) rivet(s, fx + 9, fy + 9)
  -- Tank on the back, where the rubrum shows to whoever got behind it.
  box(s, 32, 12, 6, 8, 3, shade(COPPER, -14), 20, 0.2)
  band(s, 32, 12, 6, 3, 1, 1, BRASS, 21)
  band(s, 32, 12, 6, 3, 6, 6, BRASS, 22)
  port(s, 32 + 3 + 6 + 3 + 1, 12 + 3 + 2, 4, 4)
  -- Arm and leg.
  box(s, 52, 12, 4, 10, 4, shade(COPPER, -8), 30, 0.1)
  band(s, 52, 12, 4, 4, 4, 5, BRASS, 31)
  band(s, 52, 12, 4, 4, 8, 9, IRON, 32)
  box(s, 0, 30, 4, 10, 4, shade(COPPER, -6), 40, 0.14)
  band(s, 0, 30, 4, 4, 3, 4, BRASS, 41)
  glyphRow(s, 0, 15, 30 + 4 + 6, 42)
  band(s, 0, 30, 4, 4, 8, 9, IRON, 43)
  -- Shield: riveted plate in a brass rim, banded, with the slit, the port and a row of glyphs on its face.
  fx, fy = box(s, 24, 30, 22, 30, 2, COPPER, 50, 0.16)
  for x = 0, 21 do put(s, fx + x, fy, BRASS) put(s, fx + x, fy + 29, shade(BRASS, -20)) end
  for y = 0, 29 do put(s, fx, fy + y, BRASS) put(s, fx + 21, fy + y, shade(BRASS, -20)) end
  for _, row in ipairs({ 10, 20 }) do metal(s, fx + 1, fy + row, 20, 1, COPPER_DARK, 51 + row, 0) end
  for _, row in ipairs({ 2, 9, 11, 19, 21, 27 }) do rivet(s, fx + 2, fy + row) rivet(s, fx + 19, fy + row) end
  -- The slit behind the shutter, and the muzzles in it
  for y = 6, 7 do for x = 8, 13 do put(s, fx + x, fy + y, GLASS) end end
  put(s, fx + 10, fy + 6, BRASS) put(s, fx + 11, fy + 7, BRASS)
  port(s, fx + 9, fy + 12, 4, 5)
  glyphRow(s, fx + 3, fx + 18, fy + 23, 54)
  glyphRow(s, fx + 5, fx + 16, fy + 25, 55)
  -- The back of the shield: darker, with two braces
  metal(s, 24 + 2 + 22 + 2, 32, 22, 30, COPPER_DARK, 56, 0.06)
  for _, row in ipairs({ 8, 22 }) do metal(s, 24 + 2 + 22 + 2 + 2, 32 + row, 18, 2, IRON, 57 + row, 0) end
  -- The shutter over the slit, and the eye set in the shield.
  fx, fy = box(s, 76, 30, 8, 3, 1, BRASS, 60)
  rivet(s, fx, fy + 1) rivet(s, fx + 7, fy + 1)
  metal(s, fx + 2, fy + 1, 4, 1, shade(BRASS, -30), 61, 0)
  fx, fy = box(s, 76, 36, 4, 4, 1, BRASS, 62)
  lens(s, fx + 1, fy + 1, 2, 2)
  save(s, "mantlet")
end

-- Ram cart: a copper wagon with a ram plate and a lamp at each end.
do
  local s = sheet(128, 64)
  -- Chassis: riveted plate, a brass rail along the top, glyphs along both flanks.
  local fx, fy = box(s, 0, 0, 14, 8, 18, COPPER, 1, 0.14)
  band(s, 0, 0, 14, 18, 0, 0, BRASS, 2)
  band(s, 0, 0, 14, 18, 6, 7, COPPER_DARK, 3)
  glyphRow(s, 1, 16, 18 + 3, 4)
  glyphRow(s, 18 + 14 + 1, 18 + 14 + 16, 18 + 3, 5)
  for x = 0, 17, 4 do rivet(s, x + 1, 18 + 1) rivet(s, 18 + 14 + x + 1, 18 + 1) end
  -- Ram plate: dark armour edged in brass, studded.
  fx, fy = box(s, 0, 26, 16, 12, 2, COPPER_DARK, 10, 0.08)
  -- The plate at the front shows its front face and the one at the rear its back face, so both are dressed
  for _, px in ipairs({ fx, fx + 16 + 2 }) do
    for x = 0, 15 do put(s, px + x, fy, BRASS) put(s, px + x, fy + 11, shade(BRASS, -20)) end
    for y = 0, 11 do put(s, px, fy + y, BRASS) put(s, px + 15, fy + y, shade(BRASS, -20)) end
    for _, p in ipairs({ { 2, 2 }, { 13, 2 }, { 2, 9 }, { 13, 9 }, { 5, 9 }, { 10, 9 } }) do rivet(s, px + p[1], fy + p[2]) end
    metal(s, px + 2, fy + 8, 12, 1, IRON, 11, 0)
  end
  -- Boiler: brass banded, the rubrum behind a port on each flank.
  box(s, 36, 26, 8, 5, 10, shade(COPPER, -10), 20, 0.18)
  band(s, 36, 26, 8, 10, 0, 0, BRASS, 21)
  band(s, 36, 26, 8, 10, 4, 4, BRASS, 22)
  port(s, 36 + 3, 26 + 10 + 1, 4, 3)
  port(s, 36 + 10 + 8 + 3, 26 + 10 + 1, 4, 3)
  -- Stack.
  box(s, 72, 26, 2, 4, 2, BRASS, 30)
  metal(s, 72 + 2, 26, 2, 2, GLASS, 31, 0)
  -- Wheel: iron, with a brass hub on each face.
  box(s, 0, 40, 2, 6, 6, IRON, 40)
  for _, x in ipairs({ 0, 6 + 2 }) do
    for y = 2, 3 do for dx = 2, 3 do put(s, x + dx, 46 + y, BRASS) end end
  end
  -- Lamp: a brass ring around a slit eye, the same on the face each plate shows.
  box(s, 16, 40, 3, 3, 1, BRASS, 50)
  lens(s, 17, 41, 3, 3)
  lens(s, 16 + 1 + 3 + 1, 41, 3, 3)
  save(s, "ram_cart")
end

-- Sapper: a flat body, the drum of its charge, one eye and six thin legs.
do
  local s = sheet(64, 32)
  local fx, fy = box(s, 0, 0, 6, 3, 8, COPPER, 1, 0.12)
  band(s, 0, 0, 6, 8, 0, 0, BRASS, 2)
  glyphBand(s, 0, 0, 6, 8, 1, 3)
  -- The drum: brass, a hazard band, and the charge showing red through its lid
  box(s, 0, 12, 4, 3, 5, BRASS, 10)
  band(s, 0, 12, 4, 5, 1, 1, COPPER_DARK, 11)
  port(s, 5, 12, 4, 5)
  box(s, 20, 12, 2, 2, 1, BRASS, 20)
  lens(s, 21, 13, 2, 2)
  box(s, 28, 0, 1, 4, 1, IRON, 30)
  save(s, "sapper")
end

-- Fitter: a low hull on treads, a tank, one eye and a jointed arm ending in a torch.
do
  local s = sheet(64, 32)
  local fx, fy = box(s, 0, 0, 8, 4, 10, COPPER, 1, 0.16)
  band(s, 0, 0, 8, 10, 0, 0, BRASS, 2)
  glyphBand(s, 0, 0, 8, 10, 2, 3)
  rivet(s, fx, fy + 1) rivet(s, fx + 7, fy + 1)
  -- Tank: darker, banded, the rubrum behind a port on its rear
  box(s, 36, 0, 6, 3, 4, shade(COPPER, -14), 10, 0.2)
  band(s, 36, 0, 6, 4, 0, 0, BRASS, 11)
  port(s, 36 + 4 + 6 + 4 + 1, 4, 4, 3)
  box(s, 36, 8, 2, 2, 1, BRASS, 20)
  lens(s, 37, 9, 2, 2)
  -- Tread: iron links
  box(s, 0, 14, 2, 3, 12, IRON, 30)
  for x = 0, 27, 2 do metal(s, x, 14 + 12, 1, 3, shade(IRON, -18), 31, 0) end
  for x = 12, 15 do for y = 14, 25, 2 do put(s, x, y, shade(IRON, -18)) end end
  -- Arm, forearm and the torch at its tip, which lights with the glyphs
  box(s, 44, 8, 2, 6, 2, BRASS, 40)
  band(s, 44, 8, 2, 2, 0, 0, IRON, 41)
  box(s, 52, 8, 1, 6, 1, IRON, 42)
  box(s, 56, 8, 2, 1, 2, COPPER_DARK, 43)
  for y = 8, 9 do for x = 58, 59 do
    put(s, x, y, GLYPH_OFF)
    light(s.glyphs, x, y, GLYPH)
  end end
  save(s, "fitter")
end
