-- Draws the mosaic of a temple of the Underway and cuts it into the block tiles of underway_mosaic, whole and chipped.
-- Run with Aseprite: --script-param "out=<folder>" --script this file. Writes <out>/mosaic_full.png and <out>/mosaic_chipped.png to review, and <out>/mosaic/tile_<n>.png and tile_<n>_chipped.png, counted row by row from the top left.
--
-- The picture, on a ground of voidstone run through with writhing veins: across the top the flesh that hungers, eyes open all over it, its mouth a gullet of crooked teeth with an eye far down it, its tendrils hanging and its tongue coiled round a ripe world cracked with void light, its trees grown down. On the left the angel as the old texts saw it: wheels within wheels covered in eyes round one great eye, six wings feathered with small bodies and tipped with eyes, its brood dripping from it. On the right, in a niche of its own, a veiled figure without a face under a violet halo. Small faithful kneel along the foot, and ley glyphs run round the edge.
-- It is drawn as a scene of regions, then laid in tesserae: cells about three pixels across that never straddle two regions, each one colour, with a darker joint on their right and lower sides.
--
-- With --script-param "wake=<assets/voidheart folder>" it also writes what the mosaic needs to wake when stared at: textures/misc/temple_mosaic.png, the whole picture over the chipped one with a white patch for plain colours; mosaic/temple_mosaic_map.png, each pixel's tessera, region and figure; and mosaic/temple_mosaic.json, its eyes, tendrils and the rest it moves.
local out = app.params["out"]
local wake = app.params["wake"]
local pc = app.pixelColor
local W, H, TILE = 240, 128, 16
local PITCH = 3
local GROUT = 0.72

local function hash(x, y, seed)
  local n = (x * 73856093) ~ (y * 19349663) ~ (seed * 83492791)
  n = (n ~ (n >> 13)) * 1274126177
  return ((n ~ (n >> 16)) & 0xFFFF) / 0xFFFF
end
local function clamp(v) return math.max(0, math.min(255, math.floor(v + 0.5))) end
local function shade(c, d) return { clamp(c[1] + d), clamp(c[2] + d), clamp(c[3] + d) } end
local function mix(a, b, t) t = math.max(0, math.min(1, t)); return { a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t, a[3] + (b[3] - a[3]) * t } end
local function scale(c, f) return { clamp(c[1] * f), clamp(c[2] * f), clamp(c[3] * f) } end

-- Regions. Those in FINE are laid a pixel to a tessera with no joints, for lines and small detail that cells would blur.
local VOID, BORDER, FLESH, GULLET, TOOTH, EYE, LIP, RING, WING, OUTLINE, GOLD_LINE, BED, WORLD, FAITHFUL, ALTAR, VEIL, HALO, VEIN, TENDRIL, BROOD, GLYPH =
  1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21
local FINE = { [TOOTH] = true, [EYE] = true, [OUTLINE] = true, [GOLD_LINE] = true, [WING] = true, [FAITHFUL] = true, [BORDER] = true, [RING] = true,
  [HALO] = true, [VEIN] = true, [TENDRIL] = true, [BROOD] = true, [GLYPH] = true }

local GROUND_TOP = { 30, 21, 36 }
local GROUND_FOOT = { 15, 10, 19 }
local GOLD = { 150, 114, 52 }
local GOLD_LIGHT = { 184, 146, 72 }
local GOLD_DARK = { 104, 76, 36 }
local FLESH_TONES = { { 80, 34, 44 }, { 92, 40, 50 }, { 70, 28, 38 }, { 98, 46, 56 } }
local FLESH_LIT = { { 106, 50, 60 }, { 116, 58, 68 }, { 98, 46, 56 }, { 124, 64, 73 } }
local FOLD = { 50, 20, 28 }
local BONE = { 160, 148, 128 }
local BONE_SHADE = { 116, 106, 94 }
local SCLERA = { 204, 192, 150 }
local MAW_IRIS = { 190, 200, 60 }
local MAW_IRIS_DARK = { 128, 138, 30 }
local PUPIL = { 8, 6, 4 }
local VIOLET_DARK = { 91, 34, 168 }
local VIOLET = { 138, 61, 240 }
local VIOLET_PALE = { 191, 169, 232 }
local TEAL = { 54, 197, 176 }
local TEAL_DIM = { 39, 125, 99 }
local BED_TONE = { 120, 110, 98 }
local DARK_LINE = { 22, 8, 14 }

-- Figures, which the waking moves as one, and which part of one, such as which tendril. Whatever paints last over a pixel owns it.
local NOBODY, FLESH_BODY, TENDRIL_BODY, ANGEL_BODY, WHEEL_BODY, VEILED_BODY, WORLD_BODY, FAITHFUL_BODY, BORDER_BODY, ALTAR_BODY = 0, 1, 2, 3, 4, 5, 6, 7, 8, 9
local body, part = NOBODY, 0
-- What the waking needs to know besides the pixels, in the order drawn
local EYES, SHUT, TENDRILS, WHEELS, HEADS = {}, {}, {}, {}, {}

local R, G, B, REG, FIGURE, FIGURE_PART = {}, {}, {}, {}, {}, {}
local function at(x, y) return y * W + x + 1 end
local function paint(x, y, c, region)
  x, y = math.floor(x + 0.5), math.floor(y + 0.5)
  if x < 0 or y < 0 or x >= W or y >= H then return end
  local i = at(x, y)
  R[i], G[i], B[i], REG[i], FIGURE[i], FIGURE_PART[i] = c[1], c[2], c[3], region, body, part
end
local function regionAt(x, y)
  if x < 0 or y < 0 or x >= W or y >= H then return 0 end
  return REG[at(x, y)]
end
local function colourAt(x, y) local i = at(x, y); return { R[i], G[i], B[i] } end

-- An almond eye: dark rim, sclera, an iris and a slit
local function eye(cx, cy, rx, ry, iris, irisDark, rim)
  EYES[#EYES + 1] = { cx, cy, rx, ry, iris, irisDark, rim, body, part }
  for y = math.floor(cy - ry - 1), math.ceil(cy + ry + 1) do
    for x = math.floor(cx - rx - 1), math.ceil(cx + rx + 1) do
      local e = ((x - cx) / rx) ^ 2 + ((y - cy) / ry) ^ 2
      if e < 1 then
        local d = math.sqrt((x - cx) ^ 2 + ((y - cy) * 1.2) ^ 2)
        local c = SCLERA
        local ir = math.min(rx, ry) * 0.75
        if d < ir then c = d < ir * 0.65 and iris or irisDark end
        if math.abs(x - cx) < 0.6 and math.abs(y - cy) < ry * 0.7 then c = PUPIL end
        paint(x, y, c, EYE)
      elseif e < 1.5 then
        paint(x, y, rim, EYE)
      end
    end
  end
end

-- The ground: voidstone, darker towards the foot, run through with writhing veins
for y = 0, H - 1 do
  for x = 0, W - 1 do
    local wx = x + 9 * math.sin(y * 0.06 + x * 0.013)
    local wy = y + 7 * math.sin(x * 0.05)
    local v = math.sin(wx * 0.11 + wy * 0.05) * math.sin(wy * 0.09 - wx * 0.03)
    if math.abs(v) < 0.03 then paint(x, y, mix({ 62, 34, 80 }, { 40, 22, 52 }, y / H), VEIN)
    else paint(x, y, mix(GROUND_TOP, GROUND_FOOT, y / H), VOID) end
  end
end

-- Niches: an arch on two posts of tarnished gold, around the angel and around the veiled one
local NICHE_TOP, NICHE_R = 72, 24
local function inNiche(x, y, cx)
  if y <= NICHE_TOP then return (x - cx) ^ 2 + (y - NICHE_TOP) ^ 2 < NICHE_R ^ 2 end
  return math.abs(x - cx) < NICHE_R
end
local function niche(cx)
  for y = 40, H - 1 do
    for x = cx - NICHE_R - 3, cx + NICHE_R + 3 do
      if not inNiche(x, y, cx) and (inNiche(x - 2, y, cx) or inNiche(x + 2, y, cx) or inNiche(x, y + 2, cx)) then
        paint(x, y, (x + y) % 4 == 0 and GOLD_LIGHT or ((x * 3 + y) % 7 == 0 and GOLD_DARK or GOLD), GOLD_LINE)
      end
    end
  end
end

-- A ritual circle in ley glyphs behind the ripe world
local WX, WY, WR = 120, 84, 9
body, part = WORLD_BODY, 0
for a = 0, 359, 2 do
  local r = math.rad(a)
  paint(WX + 16 * math.cos(r), WY + 16 * math.sin(r), a % 30 < 4 and TEAL or TEAL_DIM, GLYPH)
  if a % 10 < 6 then paint(WX + 13 * math.cos(r), WY + 13 * math.sin(r), { 30, 84, 70 }, GLYPH) end
end

-- The flesh across the top, deepest in the middle, ragged at its edge, folded, lit on its upper reaches
local MOUTH_X, MOUTH_Y, MOUTH_W, MOUTH_H = 120, 23, 38, 11
body, part = FLESH_BODY, 0
local function fleshEdge(x)
  local dx = (x - 120) / 112
  if math.abs(dx) >= 1 then return 5 end
  return 5 + 40 * (1 - dx * dx) ^ 0.55 + (hash(x // 3, 0, 44) - 0.5) * 4
end
for x = 5, W - 6 do
  local edge = fleshEdge(x)
  local depth = edge - 5
  for y = 5, math.floor(edge) do
    local t = (y - 5) / math.max(depth, 1)
    local tones = t < 0.3 and FLESH_LIT or FLESH_TONES
    local c = tones[1 + math.floor(hash(x // 2, y // 2, 45) * 4) % 4]
    for k = 1, 3 do
      local fold = 5 + depth * (0.25 * k) + math.sin(x * 0.07 + k * 1.7) * 3
      if math.abs(y - fold) < 0.9 and math.abs(x - 120) > 48 then c = FOLD end
    end
    if y >= edge - 3 then c = scale(c, 0.65) end
    paint(x, y, c, FLESH)
  end
  paint(x, math.floor(edge) + 1, DARK_LINE, OUTLINE)
end

-- Tendrils hanging from it, swaying, thinning to hooks
local function tendril(x0, length, sway, phase, width)
  body, part = TENDRIL_BODY, #TENDRILS
  local path = {}
  TENDRILS[#TENDRILS + 1] = path
  local top = math.floor(fleshEdge(x0)) - 1
  local last = { x0, top }
  for s = 0, length do
    local y = top + s
    local x = x0 + sway * math.sin(s * 0.22 + phase) + (s > length - 4 and (s - length + 4) * (sway > 0 and 1 or -1) or 0)
    local w = math.max(1, math.floor(width * (1 - s / length) + 0.7))
    path[#path + 1] = { math.floor(x + 0.5), y, w }
    for dx = -w, w do
      local c = (dx == -w or dx == w) and DARK_LINE or (dx < 0 and FLESH_LIT[2] or FLESH_TONES[3])
      paint(x + dx, y, c, TENDRIL)
    end
    last = { x, y }
  end
  paint(last[1], last[2] + 1, BONE, TENDRIL)
end
tendril(24, 24, 3, 0.5, 2)
tendril(42, 12, -2, 1.3, 1)
tendril(158, 16, 3, 2.1, 2)
tendril(196, 30, -4, 0.2, 2)
tendril(216, 18, 3, 1.0, 1)
body, part = FLESH_BODY, 0

-- Eyes opened all over the flesh, and a few shut
for _, e in ipairs({ { 36, 13, 3.4, 2 }, { 66, 26, 2.4, 1.5 }, { 88, 10, 2, 1.2 }, { 160, 9, 2.2, 1.4 }, { 176, 27, 2.6, 1.6 }, { 205, 14, 3.4, 2 }, { 18, 9, 1.8, 1.1 } }) do
  eye(e[1], e[2], e[3], e[4], MAW_IRIS, MAW_IRIS_DARK, { 30, 8, 12 })
end
for _, e in ipairs({ { 52, 18 }, { 190, 20 }, { 146, 6 } }) do
  SHUT[#SHUT + 1] = e
  for dx = -3, 3 do paint(e[1] + dx, e[2] + math.floor(math.abs(dx) / 2), DARK_LINE, OUTLINE) end
  for dx = -2, 2, 2 do paint(e[1] + dx, e[2] + 2, BONE_SHADE, OUTLINE) end
end

-- The mouth: an almond of gullet going black towards its middle, wet lips, crooked teeth along both lips and a ring of smaller ones deeper in
local function mouthHalf(x) local dx = (x - MOUTH_X) / MOUTH_W; if math.abs(dx) >= 1 then return -1 end; return MOUTH_H * (1 - dx * dx) ^ 0.8 end
for y = 5, 45 do
  for x = MOUTH_X - 46, MOUTH_X + 46 do
    local half = mouthHalf(x)
    local dx = (x - MOUTH_X) / MOUTH_W
    local lip = math.abs(dx) < 1.18 and MOUTH_H * 1.25 * math.max(0, 1 - (dx / 1.18) ^ 2) ^ 0.8 or -1
    local dy = y - MOUTH_Y
    if half >= 0 and math.abs(dy) <= half then
      local r = math.sqrt(dx * dx + (dy / MOUTH_H) ^ 2)
      local c = r > 0.7 and mix({ 24, 8, 12 }, { 50, 18, 26 }, (r - 0.7) / 0.3) or mix({ 6, 2, 6 }, { 24, 8, 12 }, r / 0.7)
      paint(x, y, c, GULLET)
    elseif lip >= 0 and math.abs(dy) <= lip then
      paint(x, y, dy > 0 and { 140, 78, 88 } or { 112, 56, 66 }, LIP)
    end
  end
end
local function tooth(x, rootY, length, down, lean)
  for j = 0, length - 1 do
    local hw = math.floor(1.5 * (1 - j / length) + 0.5)
    local y = down and rootY + j or rootY - j
    local cx = x + math.floor(lean * j / length + 0.5)
    for dx = -hw, hw do paint(cx + dx, y, dx > 0 and BONE_SHADE or BONE, TOOTH) end
  end
end
for tx = -33, 33, 5 do
  local x = MOUTH_X + tx
  local roll = hash(tx, 1, 62)
  tooth(x, math.ceil(MOUTH_Y - mouthHalf(x)), (math.abs(tx) < 22 and 4 or 2) + math.floor(roll * 3), true, (roll - 0.5) * 4)
  local lower = x + 2
  if mouthHalf(lower) > 2 then tooth(lower, math.floor(MOUTH_Y + mouthHalf(lower)), (math.abs(tx) < 20 and 3 or 2) + math.floor(hash(tx, 2, 62) * 3), false, (hash(tx, 3, 62) - 0.5) * 4) end
end
for a = 0, 359, 18 do
  paint(MOUTH_X + MOUTH_W * 0.55 * math.cos(math.rad(a)), MOUTH_Y + MOUTH_H * 0.55 * math.sin(math.rad(a)), BONE_SHADE, TOOTH)
end
eye(MOUTH_X, MOUTH_Y, 6.5, 3.6, MAW_IRIS, MAW_IRIS_DARK, { 120, 30, 30 })

-- The ripe world: a dark sphere ringed in tarnished gold, cracked through with void light, its trees grown down from its underside
body, part = WORLD_BODY, 0
for y = WY - WR - 2, WY + WR + 2 do
  for x = WX - WR - 2, WX + WR + 2 do
    local d = math.sqrt((x - WX) ^ 2 + (y - WY) ^ 2)
    if d < WR then
      local c = hash(x // 2, y // 2, 46) < 0.4 and { 64, 112, 86 } or { 40, 86, 74 }
      c = scale(c, 1.1 - 0.5 * math.max(0, ((x - WX) + (y - WY)) / (2 * WR)))
      paint(x, y, c, WORLD)
    elseif d < WR + 1.1 then
      paint(x, y, DARK_LINE, OUTLINE)
    end
  end
end
local crackX = WX - 6
for y = WY - 7, WY + 7 do
  crackX = crackX + (hash(y, 0, 63) < 0.5 and 1 or 0)
  paint(crackX, y, VIOLET_PALE, OUTLINE)
  paint(crackX + 1, y, VIOLET, OUTLINE)
  if hash(y, 1, 63) < 0.5 then paint(crackX - 1, y, VIOLET_DARK, OUTLINE) end
end
for _, tree in ipairs({ { -7, 3 }, { -4, 5 }, { 0, 7 }, { 4, 4 }, { 7, 3 } }) do
  local x = WX + tree[1]
  local top = WY + math.floor(math.sqrt(WR * WR - tree[1] * tree[1])) + 1
  for y = top, top + tree[2] do paint(x, y, { 20, 30, 26 }, OUTLINE) end
  local tip = top + tree[2]
  for dx = -1, 1 do paint(x + dx, tip, { 30, 56, 42 }, OUTLINE) paint(x + dx, tip + 1, { 24, 46, 36 }, OUTLINE) end
end

-- Its tongue, down from the lower lip and coiled round the world
local tongueX = MOUTH_X
for y = MOUTH_Y + 12, WY - WR - 2 do
  tongueX = MOUTH_X + 3 * math.sin((y - MOUTH_Y) * 0.25)
  for dx = -1, 1 do paint(tongueX + dx, y, dx < 0 and { 150, 84, 94 } or { 112, 52, 62 }, TENDRIL) end
  paint(tongueX - 2, y, DARK_LINE, TENDRIL) paint(tongueX + 2, y, DARK_LINE, TENDRIL)
end
-- From the top of the world round its left side and under it, tightening as it goes
for a = -90, -320, -2 do
  local r = math.rad(a)
  local reach = WR + 2.2 - (-90 - a) / 230 * 1.2
  for k = -1, 1 do
    paint(WX + (reach + k * 0.9) * math.cos(r), WY + (reach + k * 0.9) * math.sin(r), k == 0 and { 140, 78, 88 } or (k > 0 and DARK_LINE or { 112, 52, 62 }), TENDRIL)
  end
end

-- The altar under it, as in the temple itself
body, part = ALTAR_BODY, 0
for y = 109, 122 do
  for x = 110, 130 do
    local c = { 44, 38, 48 }
    if y == 109 then c = GOLD elseif x >= 114 and x <= 126 and y >= 111 and y <= 119 then c = { 70, 22, 30 } end
    paint(x, y, c, (y == 109) and GOLD_LINE or ALTAR)
  end
end
for y = 112, 118 do local half = math.floor(2 * (1 - ((y - 115) / 3.6) ^ 2) + 0.5); paint(120 - half, y, GOLD, GOLD_LINE); paint(120 + half, y, GOLD, GOLD_LINE) end
for y = 120, 122 do paint(118, y, { 90, 20, 26 }, OUTLINE) paint(123, y + 1, { 90, 20, 26 }, OUTLINE) end

-- The angel as the old texts saw it, in its niche
local AX, AY = 62, 74
body, part = NOBODY, 0
niche(AX)
body, part = ANGEL_BODY, 0
-- Six wings: one pair up, one spread, one down over where its feet would be. Each wing is a fan of feathers, each feather rows of small bodies with an eye at its tip
local WINGS = { { 72, 30 }, { 18, 36 }, { -42, 28 } }
local FAN = { -13, 0, 13 }
local feathers = {}
for side = -1, 1, 2 do
  for _, wing in ipairs(WINGS) do
    for f, spread in ipairs(FAN) do
      local a = math.rad(wing[1] + spread * (f == 2 and 0 or 1))
      local length = wing[2] * (f == 2 and 1 or 0.82)
      local dx, dy = side * math.cos(a), -math.sin(a)
      local half = length / 2
      feathers[#feathers + 1] = { AX + side * 5 + dx * (half + 2), AY + dy * (half + 2), dx, dy, half, 4.2, side }
    end
  end
end
local function inFeather(x, y, feather)
  local u = (x - feather[1]) * feather[3] + (y - feather[2]) * feather[4]
  local v = -(x - feather[1]) * feather[4] + (y - feather[2]) * feather[3]
  return (u / feather[5]) ^ 2 + (v / feather[6]) ^ 2 < 1
end
local function inWing(x, y)
  for _, feather in ipairs(feathers) do if inFeather(x, y, feather) then return true end end
  return false
end
for y = 30, 104 do
  for x = 5, 120 do
    if inWing(x, y) then paint(x, y, { 92, 76, 74 }, WING) end
  end
end
for _, feather in ipairs(feathers) do
  local cx, cy, dx, dy, half = feather[1], feather[2], feather[3], feather[4], feather[5]
  for u = -half + 3, half - 6, 3.5 do
    for v = -1.6, 1.6, 3.2 do
      local x = math.floor(cx + dx * u - dy * v + 0.5)
      local y = math.floor(cy + dy * u + dx * v + 0.5)
      for _, d in ipairs({ { 0, 0 }, { 1, 0 }, { 0, 1 }, { 1, 1 } }) do paint(x + d[1], y + d[2], { 176, 158, 140 }, WING) end
      paint(x, y, { 206, 192, 172 }, WING)
      paint(x + (feather[7] > 0 and 1 or 0), y + 1, { 52, 32, 36 }, WING)
    end
  end
end
for y = 30, 104 do
  for x = 5, 120 do
    if inWing(x, y) and (not inWing(x - 1, y) or not inWing(x + 1, y) or not inWing(x, y - 1) or not inWing(x, y + 1)) then paint(x, y, { 40, 24, 30 }, OUTLINE) end
  end
end
for _, feather in ipairs(feathers) do
  eye(feather[1] + feather[3] * (feather[5] - 3), feather[2] + feather[4] * (feather[5] - 3), 2.2, 1.6, { 150, 30, 30 }, { 90, 16, 18 }, { 30, 14, 18 })
end
-- Its brood, dripping from the lower wings towards the faithful
body, part = ANGEL_BODY, 1
for i = 0, 26 do
  local x = AX - 26 + math.floor(hash(i, 0, 64) * 52)
  local y = 96 + math.floor(hash(i, 1, 64) ^ 0.6 * 14)
  paint(x, y, { 186, 166, 146 }, BROOD) paint(x + 1, y, { 160, 140, 122 }, BROOD) paint(x + (i % 2), y + 1, { 52, 32, 36 }, BROOD)
end
-- Wheels within wheels, gold gone dark, set with eyes all round
local function ring(rx, ry, tilt, eyes)
  body, part = WHEEL_BODY, #WHEELS
  WHEELS[#WHEELS + 1] = { rx, ry, tilt, eyes }
  local cosT, sinT = math.cos(math.rad(tilt)), math.sin(math.rad(tilt))
  for a = 0, 359, 1 do
    local r = math.rad(a)
    for k = 0, 1 do
      local ex, ey = (rx - k) * math.cos(r), (ry - k) * math.sin(r)
      paint(AX + ex * cosT - ey * sinT, AY + ex * sinT + ey * cosT, k == 0 and GOLD or GOLD_DARK, RING)
    end
  end
  for n = 0, eyes - 1 do
    local r = math.rad(n * 360 / eyes + tilt)
    local ex, ey = rx * math.cos(r), ry * math.sin(r)
    local x, y = AX + ex * cosT - ey * sinT, AY + ex * sinT + ey * cosT
    eye(x, y, 1.6, 1.3, { 30, 20, 14 }, { 30, 20, 14 }, { 110, 28, 28 })
  end
end
ring(21, 7, 24, 10)
ring(21, 7, -24, 10)
ring(7, 19, 0, 8)
-- The one great eye at the hub, pale gold, slit
body, part = WHEEL_BODY, #WHEELS
eye(AX, AY, 6, 4, { 210, 176, 84 }, { 150, 112, 48 }, { 30, 8, 12 })

-- The veiled one: tall, without a face, hands hidden, a violet halo behind its hood
local SX, HEAD_Y = 178, 64
body, part = NOBODY, 0
niche(SX)
for y = 44, 80 do
  for x = SX - 20, SX + 20 do
    local d = math.sqrt((x - SX) ^ 2 + (y - (HEAD_Y - 3)) ^ 2)
    if d > 9 and d < 18 and regionAt(x, y) == VOID then paint(x, y, mix({ 52, 26, 78 }, colourAt(x, y), (d - 9) / 9), VOID) end
  end
end
body, part = VEILED_BODY, 0
for a = 0, 359, 1 do
  local r = math.rad(a)
  local hx, hy = SX + 12.5 * math.cos(r), HEAD_Y - 3 + 12.5 * math.sin(r)
  paint(hx, hy, VIOLET, HALO)
  paint(SX + 13.5 * math.cos(r), HEAD_Y - 3 + 13.5 * math.sin(r), VIOLET_DARK, HALO)
  if a % 3 == 0 then paint(SX + 11.5 * math.cos(r), HEAD_Y - 3 + 11.5 * math.sin(r), VIOLET_PALE, HALO) end
end
-- The niche behind it glows faintly, so the veil stands out of the dark
body, part = NOBODY, 0
for y = 48, 112 do
  for x = SX - NICHE_R + 1, SX + NICHE_R - 1 do
    if inNiche(x, y, SX) and (regionAt(x, y) == VOID or regionAt(x, y) == VEIN) then
      paint(x, y, mix({ 46, 28, 62 }, colourAt(x, y), (y - 48) / 70), regionAt(x, y))
    end
  end
end
body, part = VEILED_BODY, 1
local ROBE = { 44, 30, 60 }
local ROBE_FOLD = { 26, 16, 38 }
local ROBE_LIT = { 80, 48, 116 }
-- A hood, shoulders falling away from it, then the veil hanging almost straight to a ragged hem; its folds fan out from the shoulders
local SHOULDERS = HEAD_Y + 5
local BODY = HEAD_Y + 12
local function veilHalf(y)
  if y < SHOULDERS then return 7 * math.sqrt(math.max(0, 1 - ((y - (HEAD_Y - 1)) / 9) ^ 2)) + (y > HEAD_Y - 1 and 1 or 0) end
  if y < BODY then local t = (y - SHOULDERS) / (BODY - SHOULDERS); return 7.5 + 5.5 * math.sin(t * math.pi / 2) end
  return 13 + (y - BODY) * 0.2
end
for y = HEAD_Y - 9, 112 do
  local half = veilHalf(y)
  for x = math.floor(SX - half), math.ceil(SX + half) do
    local ragged = y > 106 and hash(x, y, 67) < (y - 106) * 0.18
    if not ragged then
      local edge = math.abs(x - SX) >= half - 0.8
      local spread = (x - SX) / math.max(half, 1) * 3.5 + 0.5
      local fold = y > BODY and math.abs(spread - math.floor(spread + 0.5)) < 0.12
      local c = fold and ROBE_FOLD or ROBE
      -- The halo lights the hood and shoulders, less and less further down
      if math.abs(x - SX) >= half - 2.5 then c = mix(ROBE_LIT, ROBE, (y - HEAD_Y) / 30) end
      if edge then c = mix({ 128, 76, 190 }, { 50, 30, 70 }, (y - HEAD_Y) / 44) end
      paint(x, y, c, edge and OUTLINE or VEIL)
    end
  end
end
-- Its arms folded in its sleeves, which meet in front
for side = -1, 1, 2 do
  for s = 0, 14 do
    local x = SX + side * (12 - s * 0.75)
    local y = SHOULDERS + 4 + s * 0.9
    for w = -2, 2 do
      paint(x, y + w, w == -2 and { 70, 44, 100 } or (w == 2 and ROBE_FOLD or { 54, 36, 74 }), VEIL)
    end
  end
end
for y = SHOULDERS + 15, SHOULDERS + 19 do
  for x = SX - 2, SX + 2 do paint(x, y, (y == SHOULDERS + 15) and { 70, 44, 100 } or { 20, 12, 28 }, VEIL) end
end
-- Under the hood, nothing: black, with the hood's lip lit round it
body, part = VEILED_BODY, 2
for y = HEAD_Y - 6, HEAD_Y + 6 do
  for x = SX - 6, SX + 6 do
    local e = ((x - SX) / 4.2) ^ 2 + ((y - (HEAD_Y + 0.5)) / 5) ^ 2
    if e < 1 then paint(x, y, { 4, 2, 8 }, VEIL)
    elseif e < 1.6 then paint(x, y, { 96, 58, 140 }, OUTLINE) end
  end
end
-- Violet glyphs along its hem, and shadow spreading from under it
body, part = VEILED_BODY, 1
local GLYPH_ROW = 104
for x = math.ceil(SX - veilHalf(GLYPH_ROW) + 2), math.floor(SX + veilHalf(GLYPH_ROW) - 2) do
  if (x - SX) % 4 ~= 0 and hash(x, 0, 65) < 0.75 then paint(x, GLYPH_ROW + ((x - SX) % 4 == 2 and 1 or 0), (x % 3 == 0) and VIOLET or VIOLET_DARK, OUTLINE) end
end
for x = SX - 26, SX + 26 do
  if hash(x, 1, 66) < 0.5 then paint(x, 113 + math.floor(hash(x, 2, 66) * 2), { 34, 16, 48 }, OUTLINE) end
end

-- The faithful, kneeling along the foot with their hands raised towards the middle
local KNEELER = { "..##...", ".####..", ".####..", "..###..", ".#####h", "######.", "######.", "#######", "#######" }
local ROBES = { { 76, 44, 32 }, { 36, 54, 48 }, { 64, 26, 36 } }
local function kneel(fx, facing, robe)
  body, part = FAITHFUL_BODY, #HEADS
  -- The middle of the head, the second and third columns of its first two rows
  HEADS[#HEADS + 1] = { facing > 0 and fx + 2.5 or fx + #KNEELER[1] - 3.5, 112.5, facing }
  for row, line in ipairs(KNEELER) do
    for col = 1, #line do
      local ch = line:sub(col, col)
      local x = facing > 0 and fx + col - 1 or fx + #line - col
      local y = 111 + row
      if ch == "#" then paint(x, y, row <= 2 and scale(robe, 0.6) or robe, FAITHFUL)
      elseif ch == "h" then paint(x, y, { 170, 156, 134 }, FAITHFUL) end
    end
  end
end
local n = 0
for fx = 9, 96, 10 do kneel(fx, 1, ROBES[1 + n % 3]) n = n + 1 end
for fx = 137, 226, 10 do kneel(fx, -1, ROBES[1 + n % 3]) n = n + 1 end

-- The border: dark stone between a black edge and a line of tarnished gold, with ley glyphs cut in it, a few of them still bright
body, part = BORDER_BODY, 0
for y = 0, H - 1 do
  for x = 0, W - 1 do
    local ring = math.min(x, y, W - 1 - x, H - 1 - y)
    if ring < 5 then
      local along = (ring == x or ring == W - 1 - x) and y or x
      local c
      if ring == 0 then c = { 10, 7, 12 }
      elseif ring == 4 then c = GOLD_DARK
      else
        c = { 40, 32, 46 }
        local p = along % 8
        local glyph = (ring == 2 and (p == 2 or p == 5)) or ((ring == 1 or ring == 3) and (p == 3 or p == 4))
        if glyph then c = (along // 8) % 5 == 0 and TEAL or TEAL_DIM end
      end
      paint(x, y, c, BORDER)
    end
  end
end

-- Lay it in tesserae
local points = {}
local GW, GH = W // PITCH + 1, H // PITCH + 1
for gy = 0, GH - 1 do
  for gx = 0, GW - 1 do
    local px = math.min(W - 1, gx * PITCH + math.floor(hash(gx, gy, 50) * PITCH))
    local py = math.min(H - 1, gy * PITCH + math.floor(hash(gx, gy, 51) * PITCH))
    points[gy * GW + gx] = { px, py, regionAt(px, py) }
  end
end
local cell = {}
for y = 0, H - 1 do
  for x = 0, W - 1 do
    local i = at(x, y)
    local best, bestD = -i, 1e9
    if not FINE[REG[i]] then
      local gx, gy = x // PITCH, y // PITCH
      for oy = -2, 2 do
        for ox = -2, 2 do
          local p = points[(gy + oy) * GW + gx + ox]
          if gx + ox >= 0 and gx + ox < GW and p and p[3] == REG[i] then
            local d = (p[1] - x) ^ 2 + (p[2] - y) ^ 2
            if d < bestD then best, bestD = (gy + oy) * GW + gx + ox, d end
          end
        end
      end
    end
    cell[i] = best
  end
end

local function cellColour(id, i)
  if id < 0 then
    local x, y = (i - 1) % W, (i - 1) // W
    return shade({ R[i], G[i], B[i] }, (hash(x, y, 52) - 0.5) * 8)
  end
  local p = points[id]
  local j = at(p[1], p[2])
  local c = { R[j], G[j], B[j] }
  local roll = hash(p[1], p[2], 53)
  if p[3] == VOID then
    if roll < 0.008 then return shade(GOLD, (hash(p[1], p[2], 54) - 0.5) * 24) end
    if roll < 0.02 then return { 96, 52, 150 } end
    if roll < 0.07 then c = shade(c, 8) end
  end
  local amp = (p[3] == FLESH or p[3] == VOID or p[3] == VEIL) and 10 or 8
  return shade(c, (hash(p[1], p[2], 56) - 0.5) * amp)
end

-- Which pixels are grout between tesserae, and which a chipped tile shows as bare bed
local JOINT, BARE = {}, {}
local function render(chipped)
  local img = Image(W, H, ColorMode.RGB)
  for y = 0, H - 1 do
    for x = 0, W - 1 do
      local i = at(x, y)
      local id = cell[i]
      local c = cellColour(id, i)
      local joint = (x + 1 < W and cell[at(x + 1, y)] ~= id) or (y + 1 < H and cell[at(x, y + 1)] ~= id)
      local fine = FINE[REG[i]]
      if chipped then
        local cx, cy = x // TILE, y // TILE
        local bx = cx * TILE + 2 + hash(cx, cy, 57) * 12
        local by = cy * TILE + 2 + hash(cx, cy, 58) * 12
        local radius = 3.5 + hash(cx, cy, 59) * 3
        local px, py = x, y
        if id >= 0 then px, py = points[id][1], points[id][2] end
        if (px - bx) ^ 2 + (py - by) ^ 2 < radius * radius or (id >= 0 and hash(id, 0, 60) < 0.03) then
          c = shade(BED_TONE, (hash(x, y, 61) - 0.5) * 8)
          fine = false
          joint = joint or (x % 3 == 2) or (y % 3 == 2)
          BARE[i] = true
        end
      else
        JOINT[i] = joint and not fine
      end
      if joint and not fine then c = scale(c, GROUT) end
      img:drawPixel(x, y, pc.rgba(clamp(c[1]), clamp(c[2]), clamp(c[3]), 255))
    end
  end
  return img
end

local whole = render(false)
local chipped = render(true)
whole:saveAs(out .. "/mosaic_full.png")
chipped:saveAs(out .. "/mosaic_chipped.png")
app.fs.makeAllDirectories(out .. "/mosaic")
for row = 0, H // TILE - 1 do
  for column = 0, W // TILE - 1 do
    local n = row * (W // TILE) + column
    for _, version in ipairs({ { whole, "" }, { chipped, "_chipped" } }) do
      local tile = Image(TILE, TILE, ColorMode.RGB)
      tile:drawImage(version[1], Point(-column * TILE, -row * TILE))
      tile:saveAs(out .. "/mosaic/tile_" .. n .. version[2] .. ".png")
    end
  end
end

if wake then
  -- The whole picture over the chipped one, and a white patch right of them that plain colours are drawn with
  local texture = Image(256, 256, ColorMode.RGB)
  texture:drawImage(whole, Point(0, 0))
  texture:drawImage(chipped, Point(0, H))
  for y = 0, 15 do for x = W, W + 15 do texture:drawPixel(x, y, pc.rgba(255, 255, 255, 255)) end end
  app.fs.makeAllDirectories(wake .. "/textures/misc")
  texture:saveAs(wake .. "/textures/misc/temple_mosaic.png")

  -- Over the picture's height, each pixel's tessera in red and green, all ones for a pixel that is a tessera of its own, and its region in blue,
  -- plus 64 when it is grout and 128 when a chipped tile shows bare bed there. Under it, its figure in red and which part of it in green.
  local map = Image(W, H * 2, ColorMode.RGB)
  for y = 0, H - 1 do
    for x = 0, W - 1 do
      local i = at(x, y)
      local id = cell[i] >= 0 and cell[i] or 0xFFFF
      map:drawPixel(x, y, pc.rgba(id >> 8, id & 0xFF, REG[i] + (JOINT[i] and 64 or 0) + (BARE[i] and 128 or 0), 255))
      map:drawPixel(x, y + H, pc.rgba(FIGURE[i], FIGURE_PART[i], 0, 255))
    end
  end
  app.fs.makeAllDirectories(wake .. "/mosaic")
  map:saveAs(wake .. "/mosaic/temple_mosaic_map.png")

  local function num(v) local text = string.format("%.3f", v):gsub("0+$", ""):gsub("%.$", ""); return text end
  local function rgb(c) return "[" .. clamp(c[1]) .. ", " .. clamp(c[2]) .. ", " .. clamp(c[3]) .. "]" end
  local lines = {}
  local function list(name, items, last)
    lines[#lines + 1] = '  "' .. name .. '": [\n    ' .. table.concat(items, ",\n    ") .. "\n  ]" .. (last and "" or ",")
  end
  local eyes = {}
  for _, e in ipairs(EYES) do
    eyes[#eyes + 1] = string.format('{ "x": %s, "y": %s, "rx": %s, "ry": %s, "iris": %s, "irisDark": %s, "rim": %s, "body": %d, "part": %d }',
      num(e[1]), num(e[2]), num(e[3]), num(e[4]), rgb(e[5]), rgb(e[6]), rgb(e[7]), e[8], e[9])
  end
  local shut = {}
  for _, e in ipairs(SHUT) do shut[#shut + 1] = string.format('{ "x": %d, "y": %d }', e[1], e[2]) end
  local tendrils = {}
  for _, path in ipairs(TENDRILS) do
    local points = {}
    for _, p in ipairs(path) do points[#points + 1] = "[" .. p[1] .. ", " .. p[2] .. ", " .. p[3] .. "]" end
    tendrils[#tendrils + 1] = "[" .. table.concat(points, ", ") .. "]"
  end
  local wheels = {}
  for _, w in ipairs(WHEELS) do wheels[#wheels + 1] = string.format('{ "rx": %s, "ry": %s, "tilt": %s, "eyes": %d }', num(w[1]), num(w[2]), num(w[3]), w[4]) end
  local heads = {}
  for _, h in ipairs(HEADS) do heads[#heads + 1] = string.format('{ "x": %s, "y": %s, "facing": %d }', num(h[1]), num(h[2]), h[3]) end
  local edge = {}
  for x = 0, W - 1 do edge[#edge + 1] = num(fleshEdge(x)) end

  lines[#lines + 1] = "{"
  lines[#lines + 1] = string.format('  "width": %d, "height": %d,', W, H)
  lines[#lines + 1] = string.format('  "mouth": { "x": %d, "y": %d, "w": %d, "h": %d },', MOUTH_X, MOUTH_Y, MOUTH_W, MOUTH_H)
  lines[#lines + 1] = string.format('  "world": { "x": %d, "y": %d, "r": %d },', WX, WY, WR)
  lines[#lines + 1] = string.format('  "hub": { "x": %d, "y": %d },', AX, AY)
  lines[#lines + 1] = string.format('  "veiled": { "x": %d, "head": %d },', SX, HEAD_Y)
  lines[#lines + 1] = '  "fleshEdge": [' .. table.concat(edge, ", ") .. "],"
  list("eyes", eyes)
  list("shut", shut)
  list("tendrils", tendrils)
  list("wheels", wheels)
  list("heads", heads, true)
  lines[#lines + 1] = "}"
  local file = io.open(wake .. "/mosaic/temple_mosaic.json", "w")
  file:write(table.concat(lines, "\n") .. "\n")
  file:close()
end
