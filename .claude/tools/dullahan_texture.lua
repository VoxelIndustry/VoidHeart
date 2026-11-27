-- Draws the Dullahan's texture and its glow: the wraith beast's sheet on the top half, unchanged but for green eyes, and the rider's boxes on the
-- bottom half, laid out as DullahanModel's texOffs and sizes say. Rerun it after changing the beast's texture or the rider's boxes.
-- Params: beast=<wraith_beast.png>, glow=<wraith_beast_glow.png>, out=<dir>
local beastPath = app.params["beast"]
local glowPath = app.params["glow"]
local out = app.params["out"]

local seed = 9182736
local function rand()
  seed = (seed * 1103515245 + 12345) % 2147483648
  return seed / 2147483648
end

local function hex(h, a)
  return Color{ r = tonumber(h:sub(2, 3), 16), g = tonumber(h:sub(4, 5), 16), b = tonumber(h:sub(6, 7), 16), a = a or 255 }
end

local CLEAR = Color{ r = 0, g = 0, b = 0, a = 0 }

-- Dark plate, a shade under the beast's own blacks so the rider reads apart from it
local PLATE = { hex("#16121B"), hex("#1E1925"), hex("#26202E"), hex("#332B3D"), hex("#463C52") }
local BRONZE = { hex("#3A2618"), hex("#5A3E26"), hex("#8A6238"), hex("#B48A58") }
local CLOTH = { hex("#090D0A"), hex("#0F1610"), hex("#152017"), hex("#1C2A1E") }
local BONE = { hex("#5E584C"), hex("#7A7262"), hex("#968D7A"), hex("#B0A792") }
local STEEL = { hex("#1D2026"), hex("#2A2E36"), hex("#3A3F49"), hex("#626A74") }
local EMBER = { hex("#0C1408"), hex("#14240C") }
local GREEN = { hex("#2E7A12"), hex("#4FB81E"), hex("#7EE83A"), hex("#C8FF70") }

local W, H = 128, 128
local tex = Image(W, H, ColorMode.RGB)
local glow = Image(W, H, ColorMode.RGB)
tex:clear(CLEAR)
glow:clear(CLEAR)

local function copyTop(path, into, recolor)
  local sprite = app.open(path)
  local img = Image(sprite.width, sprite.height, ColorMode.RGB)
  img:drawSprite(sprite, 1)
  sprite:close()
  local pc = app.pixelColor
  for y = 0, img.height - 1 do
    for x = 0, img.width - 1 do
      local v = img:getPixel(x, y)
      if pc.rgbaA(v) > 0 then
        if recolor then
          local bright = pc.rgbaG(v) > 160
          into:drawPixel(x, y, bright and GREEN[3] or GREEN[1])
        else
          into:drawPixel(x, y, v)
        end
      end
    end
  end
end

copyTop(beastPath, tex, false)
copyTop(glowPath, glow, true)

local function put(img, x, y, c)
  if x >= 0 and y >= 0 and x < W and y < H then img:drawPixel(x, y, c) end
end

local function pick(ramp, lo, hi)
  return ramp[lo + math.floor(rand() * (hi - lo + 1))]
end

-- Faces of a box at texOffs (u, v), sized w x h x d, as vanilla lays them out. paint(face, x, y, lx, ly, fw, fh) colours each texel.
local function box(u, v, w, h, d, paint)
  -- Sizes need not be whole, so each face covers every texel it touches
  local faces = {
    { "up", u + d, v, w, d },
    { "down", u + d + w, v, w, d },
    { "right", u, v + d, d, h },
    { "front", u + d, v + d, w, h },
    { "left", u + d + w, v + d, d, h },
    { "back", u + d + w + d, v + d, w, h },
  }
  for _, f in ipairs(faces) do
    local name = f[1]
    local fx, fy = math.floor(f[2]), math.floor(f[3])
    local fw, fh = math.ceil(f[2] + f[4]) - fx, math.ceil(f[3] + f[5]) - fy
    for ly = 0, fh - 1 do
      for lx = 0, fw - 1 do
        paint(name, fx + lx, fy + ly, lx, ly, fw, fh)
      end
    end
  end
end

-- Plate: speckled a shade up or down, lit along its top edge and darkened along its bottom one
local function plate(trimRows)
  return function(face, x, y, lx, ly, fw, fh)
    local c = pick(PLATE, 2, 3)
    if face == "up" then c = pick(PLATE, 3, 4)
    elseif face == "down" then c = PLATE[1]
    elseif ly == 0 then c = PLATE[4]
    elseif ly == fh - 1 then c = PLATE[1]
    end
    if trimRows and face ~= "up" and face ~= "down" then
      for _, row in ipairs(trimRows) do
        local r = row < 0 and fh + row or row
        if ly == r then c = (lx % 3 == 0) and BRONZE[4] or BRONZE[3] end
      end
    end
    put(tex, x, y, c)
  end
end

-- Hips and the skirt of plates over them, with a bronze rim at the bottom
box(0, 64, 8.4, 3.3, 6, plate({ -1 }))
-- Thighs and greaves
box(30, 64, 3, 3, 6.5, plate(nil))
box(50, 64, 2.8, 8, 2.8, plate({ 5 }))
-- Arms, with a bronze cuff at the wrist
box(70, 64, 3, 10, 3, plate({ -2 }))

-- The breastplate: a ridge down its front, a bronze belt at the bottom and a bronze collar under the gorget
box(0, 76, 8, 11.2, 5, function(face, x, y, lx, ly, fw, fh)
  local c = pick(PLATE, 2, 3)
  if face == "up" then c = PLATE[3]
  elseif face == "down" then c = PLATE[1]
  else
    if ly == 0 then c = BRONZE[3] end
    if ly >= fh - 2 then c = (ly == fh - 2) and BRONZE[3] or BRONZE[2] end
    if face == "front" and (lx == 3 or lx == 4) and ly > 0 and ly < fh - 2 then c = (lx == 3) and PLATE[4] or PLATE[5] end
    if face == "front" and ly > 1 and ly < fh - 3 and (lx == 1 or lx == 6) and ly % 3 == 0 then c = PLATE[1] end
  end
  put(tex, x, y, c)
end)

-- The gorget: dark plate, its top the smouldering stump where the crown burns
box(28, 76, 5, 1.9, 4, function(face, x, y, lx, ly, fw, fh)
  local c = pick(PLATE, 2, 3)
  if face == "up" then
    c = EMBER[1 + math.floor(rand() * 2)]
    if rand() < 0.45 then put(glow, x, y, pick(GREEN, 1, 3)) end
  elseif ly == 0 then c = BRONZE[3]
  end
  put(tex, x, y, c)
end)

-- Pauldrons, bronze along their lower rim
box(48, 76, 3.7, 3.5, 6.4, plate({ -1 }))

-- The scythe's haft, ash-pale with dark wraps where the hand holds it, and its iron cap
box(84, 64, 1.2, 30, 1.2, function(face, x, y, lx, ly, fw, fh)
  local c = pick(BONE, 2, 3)
  if (ly >= 16 and ly <= 22 and ly % 2 == 0) then c = CLOTH[3] end
  if ly == 0 or ly == fh - 1 then c = BONE[1] end
  put(tex, x, y, c)
end)
box(90, 64, 1.8, 1.4, 1.8, function(face, x, y) put(tex, x, y, pick(STEEL, 2, 3)) end)

-- The blade: dark steel, its cutting edge along its lower side pale, and glowing green in the dark
box(90, 70, 0.8, 3, 12, function(face, x, y, lx, ly, fw, fh)
  local c = pick(STEEL, 2, 3)
  if face == "down" then
    c = STEEL[4]
    put(glow, x, y, GREEN[3])
  elseif face ~= "up" and ly == fh - 1 then
    c = STEEL[4]
    put(glow, x, y, (lx % 2 == 0) and GREEN[3] or GREEN[2])
  elseif face ~= "up" and ly == 0 then
    c = STEEL[1]
  end
  put(tex, x, y, c)
end)
box(90, 86, 0.7, 2.5, 2.5, function(face, x, y, lx, ly, fw, fh)
  local c = pick(STEEL, 2, 3)
  if face == "down" or ly == fh - 1 then
    c = STEEL[4]
    put(glow, x, y, GREEN[2])
  end
  put(tex, x, y, c)
end)

-- The cloak, a flat sheet of near-black cloth with a faint green cast, torn ragged at the hem
for ly = 0, 14 do
  for lx = 0, 17 do
    local c = pick(CLOTH, 2, 3)
    if lx % 4 == 1 and rand() < 0.6 then c = CLOTH[1] end
    if ly == 0 then c = CLOTH[4] end
    local hem = 15 - ly
    local torn = (hem <= 4) and (rand() < (5 - hem) * 0.22)
    if (lx * 7 + 3) % 5 == 0 and hem <= 3 then torn = true end
    if torn then c = CLEAR end
    put(tex, lx, 94 + ly, c)
  end
end

local function save(img, name)
  local sprite = Sprite(W, H, ColorMode.RGB)
  sprite.cels[1].image = img
  sprite:saveAs(out .. "/" .. name .. ".png")
  sprite:close()
end

save(tex, "dullahan")
save(glow, "dullahan_glow")
