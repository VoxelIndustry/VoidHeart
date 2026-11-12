-- Draws the Automaton's texture and its three lights: the eye, the glyph bands and the rubrum in its boiler.
-- Run with Aseprite: --script-param "out=<folder>" --script this file.
-- Each box of AutomatonModel is painted where its texOffs unwraps it: top and bottom side by side, then right, front, left and back in a row.
local out = app.params["out"]
local pc = app.pixelColor
local W, H = 128, 64

local function hash(x, y, seed)
  local n = (x * 73856093) ~ (y * 19349663) ~ (seed * 83492791)
  n = (n ~ (n >> 13)) * 1274126177
  return ((n ~ (n >> 16)) & 0xFFFF) / 0xFFFF
end

local function clamp(v) return math.max(0, math.min(255, math.floor(v + 0.5))) end
local function shade(c, d) return { clamp(c[1] + d), clamp(c[2] + d), clamp(c[3] + d) } end

local img = Image(W, H, ColorMode.RGB)
local eye = Image(W, H, ColorMode.RGB)
local glyphs = Image(W, H, ColorMode.RGB)
local rubrum = Image(W, H, ColorMode.RGB)

local function put(x, y, c) if x >= 0 and x < W and y >= 0 and y < H then img:drawPixel(x, y, pc.rgba(c[1], c[2], c[3], 255)) end end
local function light(layer, x, y, c) layer:drawPixel(x, y, pc.rgba(c[1], c[2], c[3], 255)) end

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

-- Copper worn by centuries: grain, and verdigris gathered in patches.
local function metal(x0, y0, w, h, c, seed, verdigris)
  for y = y0, y0 + h - 1 do
    for x = x0, x0 + w - 1 do
      if verdigris > 0 and hash(x // 2, y // 2, seed + 100) < verdigris then
        put(x, y, shade(PATINA, (hash(x, y, seed) - 0.5) * 18))
      else
        put(x, y, shade(c, (hash(x, y, seed) - 0.5) * 10))
      end
    end
  end
end

-- The six faces of a box of the model. Returns where its front face starts.
local function box(u, v, w, h, d, c, seed, verdigris)
  verdigris = verdigris or 0
  metal(u + d, v, w, d, shade(c, 12), seed, verdigris)
  metal(u + d + w, v, w, d, shade(c, -26), seed + 1, verdigris)
  metal(u, v + d, d, h, shade(c, -8), seed + 2, verdigris)
  metal(u + d, v + d, w, h, c, seed + 3, verdigris)
  metal(u + d + w, v + d, d, h, shade(c, -8), seed + 4, verdigris)
  metal(u + d + w + d, v + d, w, h, shade(c, -14), seed + 5, verdigris)
  return u + d, v + d
end

-- A band around the four sides of a box, rows counted from the top of its sides.
local function band(u, v, w, d, row0, row1, c, seed)
  metal(u, v + d + row0, 2 * (w + d), row1 - row0 + 1, c, seed, 0)
end

-- A band of glyphs around the four sides of a box: dark marks on the body, lit on the glyph layer.
local function glyphBand(u, v, w, d, row, seed)
  for x = u, u + 2 * (w + d) - 1 do
    if hash(x, row, seed) < 0.55 then
      put(x, v + d + row, GLYPH_OFF)
      light(glyphs, x, v + d + row, GLYPH)
    end
  end
end

local function rivet(x, y) put(x, y, shade(BRASS, 26)) end

-- Head: a copper casing banded in brass, its face taken up by the eye's ring.
local fx, fy = box(0, 0, 8, 6, 8, COPPER, 1, 0.12)
band(0, 0, 8, 8, 0, 0, BRASS, 7)
band(0, 0, 8, 8, 5, 5, COPPER_DARK, 8)
rivet(fx, fy + 1) rivet(fx + 7, fy + 1) rivet(fx, fy + 4) rivet(fx + 7, fy + 4)
-- The eye: a brass ring around amber glass with a slit down its middle. Dark until it opens.
fx, fy = box(32, 0, 5, 5, 1, BRASS, 10)
for y = 1, 3 do
  for x = 1, 3 do
    local slit = x == 2
    put(fx + x, fy + y, slit and GLASS or LENS_OFF)
    light(eye, fx + x, fy + y, slit and SLIT or LENS)
  end
end

-- Trunk: plates banded like a Legionary's, a glass port on the boiler holding the rubrum, and a gauge beside it.
fx, fy = box(0, 16, 10, 11, 6, COPPER, 20, 0.14)
for _, row in ipairs({ 3, 7 }) do band(0, 16, 10, 6, row, row, COPPER_DARK, 26 + row) end
band(0, 16, 10, 6, 0, 0, BRASS, 30)
glyphBand(0, 16, 10, 6, 9, 31)
-- The port: a brass frame, dark glass, and the shard behind it
for y = 2, 6 do for x = 1, 5 do put(fx + x, fy + y, BRASS) end end
for y = 3, 5 do for x = 2, 4 do put(fx + x, fy + y, GLASS) end end
for _, p in ipairs({ { 3, 3 }, { 3, 4 }, { 2, 4 }, { 4, 5 }, { 3, 5 } }) do
  put(fx + p[1], fy + p[2], RUBRUM_OFF)
  light(rubrum, fx + p[1], fy + p[2], RUBRUM)
end
-- The gauge: a pale dial under the needle
for y = 4, 6 do for x = 7, 9 do put(fx + x, fy + y, { 214, 204, 176 }) end end
put(fx + 7, fy + 4, BRASS) put(fx + 9, fy + 4, BRASS) put(fx + 7, fy + 6, BRASS) put(fx + 9, fy + 6, BRASS)
rivet(fx, fy + 1) rivet(fx + 9, fy + 1) rivet(fx, fy + 10) rivet(fx + 9, fy + 10)
-- The needle itself
put(62, 16, { 150, 30, 24 }) put(62, 17, IRON)

-- Waist: a darker collar the legs hang from.
box(32, 36, 8, 3, 5, COPPER_DARK, 34, 0.08)
band(32, 36, 8, 5, 1, 1, BRASS, 39)

-- Tank and vents on the back.
fx, fy = box(32, 16, 8, 9, 3, shade(COPPER, -14), 40, 0.2)
band(32, 16, 8, 3, 1, 1, BRASS, 46)
band(32, 16, 8, 3, 7, 7, BRASS, 47)
box(54, 16, 2, 5, 2, BRASS, 48)
metal(56, 16, 2, 2, GLASS, 49, 0)

-- Shoulder plate and arm: a brass elbow, glyphs at the wrist, an iron hand.
box(64, 0, 5, 4, 5, COPPER, 50, 0.1)
band(64, 0, 5, 5, 3, 3, BRASS, 56)
box(64, 10, 4, 12, 4, shade(COPPER, -8), 58, 0.1)
band(64, 10, 4, 4, 5, 6, BRASS, 64)
glyphBand(64, 10, 4, 4, 8, 65)
band(64, 10, 4, 4, 10, 11, IRON, 66)

-- Leg: a brass knee, glyphs under it, an iron foot.
box(0, 36, 4, 12, 4, shade(COPPER, -6), 70, 0.14)
band(0, 36, 4, 4, 4, 5, BRASS, 76)
glyphBand(0, 36, 4, 4, 7, 77)
band(0, 36, 4, 4, 10, 11, IRON, 78)

-- Lance: a brass cylinder banded in iron, and the iron point it drives.
box(16, 36, 3, 12, 3, BRASS, 80)
band(16, 36, 3, 3, 0, 1, IRON, 86)
band(16, 36, 3, 3, 6, 6, COPPER_DARK, 87)
band(16, 36, 3, 3, 11, 11, IRON, 88)
box(28, 36, 1, 9, 1, { 150, 154, 160 }, 90)

img:saveAs(out .. "/automaton.png")
eye:saveAs(out .. "/automaton_eye.png")
glyphs:saveAs(out .. "/automaton_glyphs.png")
rubrum:saveAs(out .. "/automaton_rubrum.png")
