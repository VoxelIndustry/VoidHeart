-- Writes the sin inlay masks (keyed in four grays), the palette key, each sin's palette, and the colored trident entity overlays.
-- Params: out=<textures root>, e.g. src/main/resources/assets/voidheart/textures
local out = app.params["out"]
local pc = app.pixelColor

local function hex(s)
  return pc.rgba(tonumber(s:sub(2, 3), 16), tonumber(s:sub(4, 5), 16), tonumber(s:sub(6, 7), 16), 255)
end

-- Lightest to darkest. Masks only use these four, the atlas swaps them for a sin's palette
local KEY = { "#F0F0F0", "#C0C0C0", "#909090", "#606060" }

local PALETTES = {
  pride    = { "#CDB9E6", "#A088C2", "#7A6399", "#5A4875" },
  wrath    = { "#D2604A", "#A8463A", "#7A302F", "#4E2022" },
  gluttony = { "#FDF55F", "#DFF939", "#A5BC10", "#818F24" },
  sloth    = { "#7D8896", "#5E6875", "#47505C", "#2E333C" },
  greed    = { "#F2A66A", "#DB8C50", "#B5653A", "#7A3E28" },
  flare    = { "#FFFFFF", "#FFF8E4", "#FFEBB8", "#FFD98A" },
}

local function save(img, path)
  img:saveAs(out .. "/" .. path)
end

local function strip(colors, path)
  local img = Image(#colors, 1, ColorMode.RGB)
  for i, c in ipairs(colors) do img:drawPixel(i - 1, 0, hex(c)) end
  save(img, path)
end

-- points: list of {x, y, shade} with shade 1 (light) to 4 (dark)
local function mask(size, points, colors)
  local img = Image(size, size, ColorMode.RGB)
  img:clear(pc.rgba(0, 0, 0, 0))
  for _, p in ipairs(points) do img:drawPixel(p[1], p[2], hex(colors[p[3]])) end
  return img
end

local MASKS = {
  sword = { 16, { {13, 2, 1}, {12, 3, 1}, {11, 4, 2}, {10, 5, 2}, {9, 6, 3}, {8, 7, 4} } },
  axe = { 16, { {10, 3, 1}, {9, 3, 2}, {8, 4, 2}, {8, 5, 3}, {9, 6, 4} } },
  -- Diggable tools: along the pickaxe's head and down its point, and down the spine of the shovel's blade
  pickaxe = { 16, { {7, 3, 1}, {8, 3, 1}, {9, 3, 2}, {10, 3, 2}, {13, 7, 2}, {13, 8, 3}, {13, 9, 3}, {13, 10, 4} } },
  shovel = { 16, { {13, 3, 1}, {12, 4, 1}, {11, 5, 2}, {10, 6, 3} } },
  spear = { 16, { {13, 1, 1}, {12, 2, 1}, {11, 3, 2}, {10, 4, 2}, {9, 5, 3} } },
  spear_in_hand = { 32, { {2, 1, 1}, {3, 2, 1}, {4, 3, 2}, {5, 4, 2}, {6, 5, 3} } },
  mace = { 16, { {9, 1, 1}, {8, 2, 1}, {7, 3, 2}, {6, 4, 3}, {10, 2, 2}, {11, 3, 3} } },
  trident = { 16, { {10, 2, 1}, {9, 3, 2}, {8, 4, 3},
                    {12, 2, 1}, {11, 3, 1}, {10, 4, 2}, {9, 5, 3},
                    {13, 5, 1}, {12, 6, 2}, {11, 7, 3} } },
  bow = { 16, { {14, 2, 1}, {13, 2, 2}, {12, 2, 2}, {11, 2, 3}, {2, 11, 3}, {2, 12, 2}, {2, 13, 2}, {2, 14, 1} } },
  bow_pulling_0 = { 16, { {14, 2, 1}, {13, 2, 2}, {12, 2, 2}, {11, 2, 3}, {2, 11, 3}, {2, 12, 2}, {2, 13, 2}, {2, 14, 1} } },
  bow_pulling_1 = { 16, { {14, 2, 1}, {13, 2, 2}, {12, 2, 2}, {11, 3, 3}, {3, 11, 3}, {2, 12, 2}, {2, 13, 2}, {2, 14, 1} } },
  bow_pulling_2 = { 16, { {14, 3, 1}, {13, 3, 2}, {12, 3, 2}, {11, 3, 3}, {3, 11, 3}, {3, 12, 2}, {3, 13, 2}, {3, 14, 1} } },
  crossbow = { 16, { {11, 2, 1}, {10, 2, 2}, {9, 2, 2}, {8, 2, 3}, {2, 8, 3}, {2, 9, 2}, {2, 10, 2}, {2, 11, 1} } },
}

strip(KEY, "palettes/sin/palette_key.png")
for name, colors in pairs(PALETTES) do strip(colors, "palettes/sin/" .. name .. ".png") end
for name, m in pairs(MASKS) do save(mask(m[1], m[2], KEY), "sin/" .. name .. ".png") end

-- The trident's 3D model reads an entity texture, outside the item atlas, so each sin gets its own overlay
local TRIDENT_ENTITY = {}
for x = 0, 3 do
  table.insert(TRIDENT_ENTITY, {x, 2, 2})
  table.insert(TRIDENT_ENTITY, {x, 3, 3})
end
for x = 4, 7 do
  table.insert(TRIDENT_ENTITY, {x, 5, 2})
  table.insert(TRIDENT_ENTITY, {x, 6, 3})
end
for name, colors in pairs(PALETTES) do
  save(mask(32, TRIDENT_ENTITY, colors), "entity/sin_trident/" .. name .. ".png")
end
print("written to " .. out)
