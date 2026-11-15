-- Draws the sanctum's mob textures from the same boxes their models are built of (LesserShoggothModel, VileFleshMassModel), so every face
-- a box shows is painted for what it is. Also the tentacle, bile and spawn egg textures.
-- Run with --script-param "out=<textures dir>" --script-param "egg=<an existing spawn egg png to take the shape from>".
local out = app.params["out"] or "src/main/resources/assets/voidheart/textures"
local eggShape = app.params["egg"] or (out .. "/item/eye_maw_spawn_egg.png")
local pc = app.pixelColor

local function hex(h, a)
  return pc.rgba(tonumber(h:sub(1, 2), 16), tonumber(h:sub(3, 4), 16), tonumber(h:sub(5, 6), 16), a or 255)
end
local CLEAR = pc.rgba(0, 0, 0, 0)

-- Dark to light: crease, shadow, dark, base, mid, lit, wet sheen. Muted, as in sanctum_textures.lua, rather than the research map's red
local FLESH = { hex("24151A"), hex("33202A"), hex("442A30"), hex("54343A"), hex("623E43"), hex("72494C"), hex("8E6262") }
local BAYER = { { 0, 2 }, { 3, 1 } }

local function hash(x, y, salt)
  local h = (x * 374761393 + y * 668265263 + salt * 2246822519) % 4294967296
  h = ((h ~ (h >> 13)) * 1274126177) % 4294967296
  return (h ~ (h >> 16)) % 1000
end

-- Calls paint(face, px, py, fx, fy, fw, fh) for every pixel of a box's unwrap, where (fx, fy) is the pixel within a face of size fw x fh
local function box(img, u, v, w, h, d, paint)
  w, h, d = math.floor(w + 0.5), math.floor(h + 0.5), math.floor(d + 0.5)
  local faces = {
    { "up", u + d, v, w, d }, { "down", u + d + w, v, w, d },
    { "east", u, v + d, d, h }, { "north", u + d, v + d, w, h }, { "west", u + d + w, v + d, d, h }, { "south", u + 2 * d + w, v + d, w, h } }
  for _, f in ipairs(faces) do
    for fy = 0, f[5] - 1 do
      for fx = 0, f[4] - 1 do
        local px, py = f[2] + fx, f[3] + fy
        if px >= 0 and py >= 0 and px < img.width and py < img.height then
          local c = paint(f[1], px, py, fx, fy, f[4], f[5])
          if c then img:drawPixel(px, py, c) end
        end
      end
    end
  end
end

-- Flesh drawn as lobes over the whole sheet: a jittered grid of cells, each lighter in its middle, parted by creases, a band darker toward the
-- bottom of every side so the faces keep their light from above
local function lobed(salt, cell)
  local function point(cx, cy)
    return cx * cell + hash(cx, cy, salt) % (cell * 10) / 10, cy * cell + hash(cx, cy, salt + 1) % (cell * 10) / 10
  end
  return function(face, px, py, fx, fy, fw, fh)
    local gx, gy = px // cell, py // cell
    local f1, f2 = 1e9, 1e9
    for oy = -1, 1 do
      for ox = -1, 1 do
        local x, y = point(gx + ox, gy + oy)
        local d = math.sqrt((px + 0.5 - x) ^ 2 + ((py + 0.5 - y) * 1.8) ^ 2)
        if d < f1 then f2 = f1; f1 = d elseif d < f2 then f2 = d end
      end
    end
    local gap = f2 - f1
    local level
    if gap < 0.55 then level = 1
    elseif gap < 1.4 then level = 2
    else
      local t = (1 - math.min(1, f1 / (cell * 0.62))) * 3.2 + BAYER[py % 2 + 1][px % 2 + 1] * 0.22 - 0.3
      level = 3 + math.max(0, math.min(3, math.floor(t)))
    end
    if face ~= "up" and face ~= "down" then
      if fy == 0 then level = math.min(7, level + 1) elseif fy == fh - 1 then level = math.max(1, level - 1) end
    end
    if level >= 5 and f1 < 0.8 and hash(gx, gy, salt + 2) % 4 == 0 then level = 7 end
    return FLESH[level]
  end
end

-- A ramp shaded per face: speckled a step up or down, the top row of a side lit and its bottom row darkened
local function ramped(ramp, salt, specks)
  return function(face, px, py, fx, fy, fw, fh)
    local i = 3
    local roll = hash(px, py, salt)
    if roll < 200 then i = i - 1 elseif roll > 800 then i = i + 1 end
    if face ~= "up" and face ~= "down" then
      if fy == 0 then i = i + 1 elseif fy == fh - 1 then i = i - 1 end
    elseif face == "up" then i = i + 1 end
    i = math.max(1, math.min(#ramp, i))
    local c = ramp[i]
    if specks then
      for _, s in ipairs(specks) do
        if hash(px, py, salt + s[1]) < s[2] then c = s[3] end
      end
    end
    return c
  end
end

local function eye(sclera, vein, iris, irisDark, pupil, rim)
  return function(face, px, py, fx, fy, fw, fh)
    if face ~= "north" then
      return (fy == 0 or fy == fh - 1) and rim or (hash(px, py, 5) < 200 and vein or sclera)
    end
    local cx, cy = (fw - 1) / 2, (fh - 1) / 2
    local dx, dy = math.abs(fx - cx), math.abs(fy - cy)
    if dx < 0.6 and dy < 0.6 then return pupil end
    if dx <= 1.1 and dy <= 1.1 then return (fx + fy) % 2 == 0 and iris or irisDark end
    if fx == 0 or fy == 0 or fx == fw - 1 or fy == fh - 1 then return rim end
    return hash(px, py, 6) < 150 and vein or sclera
  end
end

local function eyeGlow(color)
  return function(face, px, py, fx, fy, fw, fh)
    if face ~= "north" then return nil end
    local cx, cy = (fw - 1) / 2, (fh - 1) / 2
    local dx, dy = math.abs(fx - cx), math.abs(fy - cy)
    if dx >= 0.6 or dy >= 0.6 then
      if dx <= 1.1 and dy <= 1.1 then return color end
    end
    return nil
  end
end

local function blank(w, h)
  local img = Image(w, h, ColorMode.RGB)
  img:clear(CLEAR)
  return img
end

-- Lesser shoggoth: black protoplasm with an oily sheen of teal and violet, pale green eyes, red-black mouths with bone teeth
local function shoggoth()
  local tar = { hex("08060C"), hex("0E0B13"), hex("15111B"), hex("1C1724"), hex("251E2F"), hex("30283C") }
  local sheen = { { 13, 8, hex("4E6E3C") }, { 14, 6, hex("5E8C86") } }
  local base = ramped(tar, 1, sheen)
  -- An oily sheen in soft bands rather than specks: teal where the bands crest, violet in their troughs
  local flesh = function(face, px, py, fx, fy, fw, fh)
    local band = math.sin((px + py * 0.6) * 0.45 + math.sin(py * 0.3) * 1.5)
    if band > 0.82 and hash(px, py, 15) < 650 then return hex("1E4A48") end
    if band < -0.86 and hash(px, py, 16) < 600 then return hex("2E2346") end
    return base(face, px, py, fx, fy, fw, fh)
  end
  local img, glow = blank(128, 64), blank(128, 64)
  box(img, 0, 0, 18, 10, 18, flesh)
  for _, l in ipairs({ { 72, 0, 11, 7, 11 }, { 72, 18, 9, 6, 9 }, { 0, 28, 7, 5, 7 }, { 28, 28, 6, 5, 6 }, { 84, 33, 8, 4, 8 } }) do
    box(img, l[1], l[2], l[3], l[4], l[5], flesh)
  end
  local look = eye(hex("C9C79A"), hex("8E8A62"), hex("A8D45A"), hex("7FA840"), hex("0A0A04"), hex("15111B"))
  for i = 0, 7 do
    box(img, i * 8, 48, 3, 3, 1, look)
    box(glow, i * 8, 48, 3, 3, 1, eyeGlow(hex("5C8A2E")))
  end
  local mouth = function(face, px, py, fx, fy, fw, fh)
    if face ~= "north" then return tar[2] end
    if fy == 0 and fx % 2 == 1 then return hex("A49A88") end
    if fy == fh - 1 and fx % 2 == 0 then return hex("8E8676") end
    return (fx == 0 or fx == fw - 1) and hex("3A0A12") or hex("1E050A")
  end
  for i = 0, 2 do box(img, 64 + i * 12, 48, 5, 2, 1, mouth) end
  for i = 0, 2 do
    box(img, i * 18, 52, 3, 3, 6, flesh)
    box(img, 54 + i * 14, 52, 2, 2, 5, flesh)
  end
  img:saveAs(out .. "/entity/lesser_shoggoth.png")
  glow:saveAs(out .. "/entity/lesser_shoggoth_glow.png")
end

-- Vile Flesh Mass: the research map's muted wine flesh, bruised and specked, yellow eyes, a near black gullet between wet lips, bone teeth
local function mass()
  local flesh = lobed(2, 9)
  local creased = lobed(3, 12)
  local img, glow = blank(256, 256), blank(256, 256)
  box(img, 0, 0, 52, 13, 52, creased)
  box(img, 0, 65, 39, 15, 37, creased)
  box(img, 152, 65, 22, 12, 20, creased)
  for _, l in ipairs({ { 208, 0, 12, 11, 12 }, { 208, 23, 10, 9, 10 }, { 152, 97, 14, 9, 12 }, { 204, 97, 10, 8, 10 },
                       { 0, 138, 9, 7, 9 }, { 36, 138, 8, 6, 10 }, { 72, 138, 10, 5, 8 } }) do
    box(img, l[1], l[2], l[3], l[4], l[5], flesh)
  end
  local gullet = ramped({ hex("0A0408"), hex("12060C"), hex("1E0A10"), hex("2A0E16") }, 3, { { 24, 50, hex("3A161E") } })
  box(img, 152, 120, 7, 15, 2, gullet)
  local lip = function(face, px, py, fx, fy, fw, fh)
    local c = flesh(face, px, py, fx, fy, fw, fh)
    if face == "north" and (fx == 0 or fx == fw - 1) then return FLESH[7] end
    return c
  end
  box(img, 120, 120, 4, 15, 4, lip)
  box(img, 136, 120, 4, 15, 4, lip)
  -- The teeth are under a pixel off whole sizes, so their faces reach a little past a rounded unwrap: bone over the whole patch
  local bone = ramped({ hex("7E766A"), hex("A49A88"), hex("B4AC9C") }, 4)
  for y = 120, 124 do
    for x = 170, 179 do img:drawPixel(x, y, bone("north", x, y, x - 170, y - 120, 10, 5)) end
  end
  local look = eye(hex("D8CC8A"), hex("9A1C1C"), hex("C8D040"), hex("8A9420"), hex("0A0A04"), hex("2A0A08"))
  box(img, 104, 120, 3, 3, 1, look)
  box(img, 104, 124, 5, 5, 1, look)
  box(glow, 104, 120, 3, 3, 1, eyeGlow(hex("6A6E22")))
  box(glow, 104, 124, 5, 5, 1, eyeGlow(hex("6A6E22")))
  -- Tentacles: suckers along the underside of each segment
  local tentacle = function(face, px, py, fx, fy, fw, fh)
    if face == "down" and fx > 0 and fx < fw - 1 and fy % 2 == 1 then return (fx % 2 == 0) and hex("8C766E") or FLESH[2] end
    return flesh(face, px, py, fx, fy, fw, fh)
  end
  for _, s in ipairs({ { 0, 6, 10 }, { 32, 5, 9 }, { 60, 4, 8 }, { 84, 3, 7 } }) do
    box(img, s[1], 120, s[2], s[2], s[3], tentacle)
  end
  img:saveAs(out .. "/entity/vile_flesh_mass.png")
  glow:saveAs(out .. "/entity/vile_flesh_mass_glow.png")
end

-- The tentacle drawn along a path: bands round it, suckers down one side
local function tentacle()
  local img = Image(32, 32, ColorMode.RGB)
  for y = 0, 31 do
    for x = 0, 31 do
      local band = y % 6
      local level = band == 0 and 6 or band == 5 and 2 or band == 4 and 3 or 4
      if band == 1 and BAYER[y % 2 + 1][x % 2 + 1] < 2 then level = 5 end
      local c = FLESH[level]
      if x >= 12 and x <= 19 and band == 2 and (x % 3 == 0) then c = hex("8C766E") end
      if x >= 12 and x <= 19 and band == 3 and (x % 3 == 0) then c = FLESH[1] end
      img:drawPixel(x, y, c)
    end
  end
  img:saveAs(out .. "/entity/flesh_tentacle.png")
end

-- Bile: a glowing glob, and a pool fading out at its ragged edge, with bubbles
local function bile()
  local glob = blank(16, 16)
  for y = 0, 15 do
    for x = 0, 15 do
      local dx, dy = x - 7.5, y - 7.5
      local r = math.sqrt(dx * dx + dy * dy)
      if r < 3 then glob:drawPixel(x, y, hex("E2EC8C"))
      elseif r < 5 then glob:drawPixel(x, y, hex("A8C23E"))
      elseif r < 7.5 then glob:drawPixel(x, y, hex("6F8A24", math.floor(200 * (7.5 - r) / 2.5))) end
    end
  end
  glob:saveAs(out .. "/entity/bile_glob.png")

  local pool = blank(32, 32)
  for y = 0, 31 do
    for x = 0, 31 do
      local dx, dy = x - 15.5, y - 15.5
      local r = math.sqrt(dx * dx + dy * dy) + (hash(x, y, 51) % 3) * 0.6
      if r < 15 then
        local c = hash(x, y, 52) < 500 and hex("6F8A24") or hex("7E9A2C")
        if r > 12 then c = hex("4E5E1A") end
        if hash(x, y, 53) < 50 and r < 11 then c = hex("C9D86A") end
        local a = r < 12 and 230 or math.floor(230 * (15 - r) / 3)
        pool:drawPixel(x, y, pc.rgba(pc.rgbaR(c), pc.rgbaG(c), pc.rgbaB(c), a))
      end
    end
  end
  -- A few bubbles: a lit rim round a dark middle
  for _, b in ipairs({ { 10, 12 }, { 19, 9 }, { 21, 20 }, { 13, 21 } }) do
    pool:drawPixel(b[1], b[2], hex("E2EC8C", 240))
    pool:drawPixel(b[1] + 1, b[2], hex("C9D86A", 240))
    pool:drawPixel(b[1], b[2] + 1, hex("C9D86A", 240))
    pool:drawPixel(b[1] + 1, b[2] + 1, hex("4E5E1A", 240))
  end
  pool:saveAs(out .. "/entity/bile_pool.png")
end

-- Spawn eggs keep the shape of an existing egg, recoloured: light shades take the base, dark or saturated ones the spots
local function egg(name, base, spots)
  local sprite = app.open(eggShape)
  local src = Image(sprite.width, sprite.height, ColorMode.RGB)
  src:drawSprite(sprite, 1)
  sprite:close()
  local img = blank(src.width, src.height)
  for y = 0, src.height - 1 do
    for x = 0, src.width - 1 do
      local v = src:getPixel(x, y)
      if pc.rgbaA(v) > 0 then
        local r, g, b = pc.rgbaR(v), pc.rgbaG(v), pc.rgbaB(v)
        local light = (r + g + b) / 3
        local saturated = math.max(r, g, b) - math.min(r, g, b) > 60
        local c
        if saturated or light < 40 then
          c = spots[(hash(x, y, 61) % #spots) + 1]
          if light < 40 then c = spots[1] end
        else
          local i = math.max(1, math.min(#base, math.floor((light - 150) / 30) + 2))
          c = base[i]
        end
        img:drawPixel(x, y, c)
      end
    end
  end
  img:saveAs(out .. "/item/" .. name .. "_spawn_egg.png")
end

shoggoth()
mass()
tentacle()
bile()
egg("lesser_shoggoth", { hex("0E0B13"), hex("15111B"), hex("1C1724"), hex("251E2F") }, { hex("08060C"), hex("A8D45A"), hex("1E4A48") })
egg("vile_flesh_mass", { FLESH[3], FLESH[4], FLESH[5], FLESH[6] }, { FLESH[1], hex("C8D040"), hex("A49A88") })
