-- Builds the atlas the window into a sanctum is traced through (sanctum_window.fsh): the hall's tiles from the mod's own block textures in
-- the top half, and the apse at the hall's end painted in the bottom half, with the mass heaped in it. Alpha below 255 marks what glows:
-- almost nothing for the mass's eyes, about half for the specks of the writhing stone. Run from the repository root, after sanctum_textures.lua.
local root = app.params["root"] or "src/main/resources/assets/voidheart/textures"
local pc = app.pixelColor

local function hex(h, a)
  return pc.rgba(tonumber(h:sub(1, 2), 16), tonumber(h:sub(3, 4), 16), tonumber(h:sub(5, 6), 16), a or 255)
end

local function hash(x, y, salt)
  local h = (x * 374761393 + y * 668265263 + salt * 2246822519) % 4294967296
  h = ((h ~ (h >> 13)) * 1274126177) % 4294967296
  return (h ~ (h >> 16)) % 1000
end

local function load(path)
  local sprite = app.open(root .. "/" .. path)
  local img = Image(sprite.width, sprite.height, ColorMode.RGB)
  img:drawSprite(sprite, 1)
  sprite:close()
  return img
end

local function scaled(c, k)
  return pc.rgba(math.floor(pc.rgbaR(c) * k), math.floor(pc.rgbaG(c) * k), math.floor(pc.rgbaB(c) * k), pc.rgbaA(c))
end

local atlas = Image(64, 64, ColorMode.RGB)
atlas:clear(hex("000000"))
local tiles = {
  { 0, 0, "block/voidstone_tile.png" }, { 16, 0, "block/vile_flesh_0.png" }, { 32, 0, "block/voidstone_bricks.png" }, { 48, 0, "block/voidstone_pillar_side.png" },
  { 0, 16, "block/voidstone_tile_small.png" }, { 16, 16, "block/underway/tendril.png" }, { 32, 16, "block/vile_flesh_bruised_0.png" }, { 48, 16, "entity/wall_passage_smoke.png" } }
for _, t in ipairs(tiles) do
  local img = load(t[3])
  -- Larger textures, as the veil's smoke, are averaged down to a tile
  local k = img.width // 16
  for y = 0, 15 do
    for x = 0, 15 do
      local r, g, b = 0, 0, 0
      for sy = 0, k - 1 do
        for sx = 0, k - 1 do
          local c = img:getPixel(x * k + sx, y * k + sy)
          r, g, b = r + pc.rgbaR(c), g + pc.rgbaG(c), b + pc.rgbaB(c)
        end
      end
      atlas:drawPixel(t[1] + x, t[2] + y, pc.rgba(r // (k * k), g // (k * k), b // (k * k), 255))
    end
  end
end

-- The apse: the hall's back wall in shadow, an arched recess, the walls each side of it taken by writhing stone, and the mass heaped in it
local bricks = load("block/voidstone_bricks.png")
local writhing = load("block/voidstone_writhing.png")
local FLESH = { hex("24151A"), hex("33202A"), hex("442A30"), hex("54343A"), hex("623E43"), hex("72494C"), hex("8E6262") }
local GULLET = hex("120609")
local TOOTH = hex("A49A88")
local EYE = hex("C8D040", 8)
local W, H, TOP = 64, 32, 32

local function put(x, y, c)
  if x >= 0 and x < W and y >= 0 and y < H then atlas:drawPixel(x, TOP + y, c) end
end

for y = 0, H - 1 do
  for x = 0, W - 1 do
    local c = scaled(bricks:getPixel(x % 16, y % 16), 0.6)
    local recess = math.abs(x - 32) <= 13 and (y >= 9 or (x - 32) ^ 2 + (y - 9) ^ 2 <= 13 * 13)
    if recess then c = scaled(c, 0.45) end
    -- Writhing stone creeping in from both sides, ragged at its edge
    local reach = 13 + (hash(y, 0, 5) % 5)
    if (x < reach or x >= W - reach) and not recess then
      c = writhing:getPixel(x % 16, y % 16)
      local r, g, b = pc.rgbaR(c), pc.rgbaG(c), pc.rgbaB(c)
      if r > 140 and b > 140 then c = pc.rgba(r, g, b, 140) else c = pc.rgba(r, g, b, 255) end
    end
    put(x, y, c)
  end
end

-- The mass, in three tiers askew, lobed and creased like the model, with a gash down its middle tier
local function tier(x0, x1, y0, y1, salt)
  for y = y0, y1 do
    for x = x0, x1 do
      local edge = math.min(x - x0, x1 - x, y - y0)
      local l = 4 + (hash(x // 3, y // 2, salt) % 3) - 1
      if y == y0 then l = l + 1 end
      if edge == 0 and hash(x, y, salt + 1) < 450 then l = l - 2 end
      if (x + y * 2 + salt) % 7 == 0 and hash(x, y, salt + 2) < 500 then l = 2 end
      put(x, y, FLESH[math.max(1, math.min(7, l))])
    end
  end
end
tier(19, 45, 24, 31, 1)
tier(23, 41, 17, 23, 2)
tier(27, 37, 12, 16, 3)
tier(15, 20, 27, 31, 4)
tier(44, 49, 28, 31, 5)

for y = 17, 28 do
  put(31, y, GULLET)
  put(32, y, GULLET)
  put(33, y, y % 2 == 0 and TOOTH or GULLET)
  put(30, y, y % 2 == 1 and TOOTH or FLESH[6])
end
-- Tentacles lying out from its foot across the floor
for _, t in ipairs({ { 18, 31, -1 }, { 46, 31, 1 }, { 22, 31, -1 }, { 43, 30, 1 } }) do
  local x = t[1]
  for step = 0, 5 do
    put(x, t[2], FLESH[3 + step % 2])
    x = x + t[3]
  end
end
-- Its eyes, all over it, which glow
for _, e in ipairs({ { 25, 26 }, { 39, 27 }, { 28, 19 }, { 36, 18 }, { 32, 13 }, { 21, 29 }, { 43, 29 }, { 34, 25 } }) do
  put(e[1], e[2], EYE)
end

atlas:saveAs(root .. "/misc/sanctum_window.png")
