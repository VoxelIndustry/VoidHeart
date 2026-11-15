-- Draws the sanctum's flesh: the blocks of flesh that floors and walls turn to, the flesh the mound props are made of, and the tentacle the
-- tendril props are made of. Run with --script-param "out=<textures dir>".
--
-- Flesh is drawn as folds, lobes of one tileable Voronoi pattern parted by soft creases, in a muted palette darker and greyer than the research
-- map's wine. Every drawing of a block shares those folds, so any two blocks meet without a seam, and the drawings differ only by quiet details
-- kept off their edges. The bruised kind is the same folds a little purpler, so where a patch of it ends the folds run on and only the tint shifts.
local out = app.params["out"] or "src/main/resources/assets/voidheart/textures"
local pc = app.pixelColor

local function hex(h)
  return pc.rgba(tonumber(h:sub(1, 2), 16), tonumber(h:sub(3, 4), 16), tonumber(h:sub(5, 6), 16), 255)
end

-- Dark to light: crease, shadow, dark, base, mid, lit, wet sheen
local FLESH = { hex("24151A"), hex("33202A"), hex("442A30"), hex("54343A"), hex("623E43"), hex("72494C"), hex("8E6262") }
local BRUISED = { hex("22141B"), hex("311E29"), hex("412834"), hex("50303C"), hex("5D3946"), hex("6C4452"), hex("885E6A") }
local SINEW = { hex("5E4A47"), hex("8C766E") }
-- The growths' raw flesh, crease to wet sheen
local RAW = { hex("2E1A24"), hex("42262F"), hex("55303B"), hex("683C48"), hex("7A4855"), hex("8E5664"), hex("B07884") }

local SIZE = 16
local BAYER = { { 0, 2 }, { 3, 1 } }

local function hash(x, y, salt)
  local h = (x * 374761393 + y * 668265263 + salt * 2246822519) % 4294967296
  h = ((h ~ (h >> 13)) * 1274126177) % 4294967296
  return (h ~ (h >> 16)) % 1000
end

-- The folds every drawing shares: one tileable Voronoi pattern of uneven lobes, so whatever drawings meet, their folds run on across the join
local POINTS = {}
for i = 1, 8 do
  POINTS[i] = { hash(i, 1, 31) % 160 / 10, hash(i, 2, 31) % 160 / 10, hash(i, 3, 31) }
end

local function nearest(x, y)
  local f1, f2, cell = 1e9, 1e9, nil
  for _, p in ipairs(POINTS) do
    local dx = math.abs(x + 0.5 - p[1])
    local dy = math.abs(y + 0.5 - p[2])
    dx = math.min(dx, SIZE - dx)
    dy = math.min(dy, SIZE - dy)
    local d = math.sqrt(dx * dx * 0.7 + dy * dy * 1.4)
    if d < f1 then
      f2 = f1
      f1, cell = d, p
    elseif d < f2 then
      f2 = d
    end
  end
  return f1, f2, cell
end

-- The shade of the shared folds at a pixel, 1 crease to 6 lit: creases mostly a shadow, only their narrowest a true crease
local BASE = {}
for y = 0, SIZE - 1 do
  BASE[y] = {}
  for x = 0, SIZE - 1 do
    local f1, f2, cell = nearest(x, y)
    local gap = f2 - f1
    local l
    if gap < 0.35 then l = 1
    elseif gap < 1.1 then l = 2
    else
      local swell = 1 - math.min(1, f1 / (2.8 + (cell[3] % 3) * 0.5))
      l = 3 + math.max(0, math.min(3, math.floor(swell * 3 + BAYER[y % 2 + 1][x % 2 + 1] * 0.2 - 0.2)))
    end
    BASE[y][x] = l
  end
end

-- What sets one drawing apart, all quiet and kept off the edges: a pore or two, a wet sheen on a lobe, or a short strand of sinew
local function details(img, ramp, seed, sinew)
  for i = 1, 2 do
    local x, y = 3 + hash(i, 7, seed) % 10, 3 + hash(i, 8, seed) % 10
    if BASE[y][x] >= 4 and hash(i, 9, seed) < 450 then img:drawPixel(x, y, ramp[2]) end
  end
  local sx, sy = 3 + hash(1, 10, seed) % 10, 3 + hash(1, 11, seed) % 10
  for dy = 0, 1 do
    for dx = 0, 1 do
      if BASE[sy + dy][sx + dx] >= 5 and (dx + dy) < 2 then img:drawPixel(sx + dx, sy + dy, ramp[7]) end
    end
  end
  if sinew then
    local x, y = 5 + hash(3, 3, seed) % 6, 4 + hash(3, 6, seed) % 5
    for step = 0, 3 do
      img:drawPixel(x, y + step, SINEW[(step % 3 == 0) and 1 or 2])
      if hash(step, 4, seed) < 400 then x = x + ((hash(step, 5, seed) < 500) and 1 or -1) end
    end
  end
end

-- From the folds' shade to the palette: creases a shadow, lobes in the three middle tones
local TONE = { 2, 3, 4, 4, 5, 6 }
-- How far in from its edges a drawing may change the folds' light, so its joins stay the shared ones
local EDGE = 3

local function flesh(ramp, seed, sinew)
  local img = Image(SIZE, SIZE, ColorMode.RGB)
  -- Each drawing lights some of its middle lobes a shade up and others down, so neighbouring blocks never show the same face
  local lift = {}
  for i, p in ipairs(POINTS) do
    lift[p] = (hash(i, 12, seed) % 3) - 1
  end
  for y = 0, SIZE - 1 do
    for x = 0, SIZE - 1 do
      local tone = TONE[BASE[y][x]]
      local edge = math.min(x, SIZE - 1 - x, y, SIZE - 1 - y)
      if edge >= EDGE and BASE[y][x] >= 3 then
        local _, _, cell = nearest(x, y)
        tone = math.max(3, math.min(6, tone + lift[cell]))
      elseif edge == EDGE - 1 and BASE[y][x] >= 3 and BAYER[y % 2 + 1][x % 2 + 1] < 2 then
        local _, _, cell = nearest(x, y)
        tone = math.max(3, math.min(6, tone + lift[cell]))
      end
      img:drawPixel(x, y, ramp[tone])
    end
  end
  details(img, ramp, seed, sinew)
  return img
end

for v = 0, 3 do
  flesh(FLESH, 101 + v * 17, v == 2):saveAs(out .. "/block/vile_flesh_" .. v .. ".png")
  flesh(BRUISED, 503 + v * 23, false):saveAs(out .. "/block/vile_flesh_bruised_" .. v .. ".png")
end

-- The mounds' own flesh
flesh(RAW, 907, false):saveAs(out .. "/block/underway/flesh_lobe.png")

-- Eyes set in the flesh, seen from above, for the faces of the growths' eye boxes: a 4x3 one at 0,0, a 3x2 at 0,4, a 2x2 at 0,8 and a slit
-- 3x1 at 0,12. A dark lid line round a pale veined white, a yellow iris and a slit pupil
local function eyes()
  local img = Image(16, 16, ColorMode.RGB)
  img:clear(RAW[4])
  for y = 0, 3 do
    for x = 8, 15 do
      img:drawPixel(x, y, (x + y) % 3 == 0 and RAW[2] or RAW[1])
    end
  end
  local LID, WHITE, VEIN, IRIS, IRIS_DARK, PUPIL = RAW[1], hex("C9BE8A"), hex("8E3A30"), hex("B8B83C"), hex("7E8226"), hex("0A0A04")
  local rows = {
    { 0, 0, { "LWIWL", "WIPIW", "LWIVL" } },
    { 0, 4, { "WIW", "LPI" } },
    { 0, 8, { "IW", "PI" } },
    { 0, 12, { "WPW" } } }
  local colours = { L = LID, W = WHITE, V = VEIN, I = IRIS, D = IRIS_DARK, P = PUPIL }
  for _, eye in ipairs(rows) do
    for dy, row in ipairs(eye[3]) do
      for dx = 1, #row do
        img:drawPixel(eye[1] + dx - 1, eye[2] + dy - 1, colours[row:sub(dx, dx)])
      end
    end
  end
  img:saveAs(out .. "/block/underway/flesh_eye.png")
end

eyes()

-- Bands round the tentacle run across, suckers in a row down its middle, lit along the top of each band
local function tendril()
  local img = Image(16, 16, ColorMode.RGB)
  for y = 0, 15 do
    for x = 0, 15 do
      local band = y % 4
      local l = band == 0 and 5 or band == 3 and 2 or 4
      if band == 1 and BAYER[y % 2 + 1][x % 2 + 1] == 0 then l = 5 end
      img:drawPixel(x, y, RAW[l])
    end
  end
  for row = 0, 3 do
    local sx = 7 + (row % 2) * 2 - 1
    img:drawPixel(sx, row * 4 + 1, SINEW[1])
    img:drawPixel(sx + 1, row * 4 + 1, RAW[2])
  end
  img:saveAs(out .. "/block/underway/tendril.png")
end

tendril()
