-- Paints the Void Armor's skin and glow from the box layout its model writes (VoidArmorSkinTest, DUNGEON_PREVIEW=true).
-- Params: layout=build/void-armor-skin/layout.json, out=<folder for void_armor.png and void_armor_glow.png>
-- Every face of every box is filled opaque, speckled a shade up or down, lit along its top edge and darkened along its bottom one,
-- as the mod's mob skins are. Plates are edged in dull Ravenous Gold at their hem and laced to the plate above with violet cords.

local pc = app.pixelColor

local function hex(s)
  return pc.rgba(tonumber(s:sub(2, 3), 16), tonumber(s:sub(4, 5), 16), tonumber(s:sub(6, 7), 16), 255)
end

local function ramp(list)
  local out = {}
  for i, s in ipairs(list) do out[i] = hex(s) end
  return out
end

local RAMPS = {
  plate   = ramp({ "#120E16", "#1A1420", "#211A28", "#2A2232", "#332A3C", "#3B3146" }),
  lacquer = ramp({ "#0E0A12", "#150F1C", "#1C1426", "#241A30", "#2C2139", "#342842" }),
  core    = ramp({ "#0A070D", "#100C14", "#16111C", "#1C1622", "#221B29" }),
  gold    = ramp({ "#33381A", "#454C21", "#59622A", "#6D7834", "#828E40", "#96A24E" }),
  piston  = ramp({ "#24212A", "#34303B", "#47424F", "#5B5565", "#706A7A", "#87818F" }),
}
-- The shade each material sits at in its ramp, before the edges and speckle move it
local BASE = { plate = 3, lacquer = 3, core = 2, gold = 3, piston = 3 }

local CORD_DARK  = hex("#2E1752")
local CORD_GLOW  = hex("#3A1670")
-- Texels between the cords lacing a plate to the one above
local CORD_STEP  = 4
local SLIT_DARK  = hex("#0A070D")
local SLIT_GLOW  = hex("#BFA9E8")
local SLIT_END   = hex("#6A2CC0")
local VENT_SLAT  = hex("#0A070D")
local VENT_GLOW  = hex("#3A1666")
local GRILLE     = hex("#0A070D")

local LENSES = {
  lens_night     = { dark = ramp({ "#08171A", "#0C2A22", "#145037", "#1E6A50" }), glow = hex("#145037"), core = hex("#36C5B0") },
  lens_spectral  = { dark = ramp({ "#1C1626", "#2A2238", "#3A3150", "#4D4268" }), glow = hex("#4D4268"), core = hex("#BFA9E8") },
  lens_apparatus = { dark = ramp({ "#081418", "#0C2A2E", "#14444A", "#1E5A60" }), glow = hex("#14444A"), core = hex("#36C5B0") },
  tube           = { dark = ramp({ "#2A0E18", "#4A1E2C", "#6E2234" }), glow = hex("#8A2A40"), core = hex("#D4415E") },
}

-- A small fixed generator, so painting the same layout twice gives the same skin
local seed = 1
local function random()
  seed = (seed * 1103515245 + 12345) % 2147483648
  return seed / 2147483648
end

local layout
do
  local file = io.open(app.params["layout"], "r")
  layout = json.decode(file:read("a"))
  file:close()
end

local W, H = layout.width, layout.height
local skin = Image(W, H, ColorMode.RGB)
local glow = Image(W, H, ColorMode.RGB)
skin:clear(pc.rgba(0, 0, 0, 0))
glow:clear(pc.rgba(0, 0, 0, 0))

local function shade(list, index)
  if index < 1 then index = 1 end
  if index > #list then index = #list end
  return list[index]
end

-- Fills a face texel by texel. kind: "side" for upright faces, "top" or "bottom"
local function metal(material, x0, y0, x1, y1, kind)
  local list = RAMPS[material]
  local base = BASE[material]
  if kind == "top" then base = base + 1 elseif kind == "bottom" then base = base - 1 end
  for y = y0, y1 - 1 do
    for x = x0, x1 - 1 do
      local index = base
      local r = random()
      if r < 0.2 then index = index - 1 elseif r > 0.8 then index = index + 1 end
      if kind == "side" then
        if y == y0 then index = index + 1 end
        if y == y1 - 1 and y1 - y0 > 1 then index = index - 1 end
      end
      skin:drawPixel(x, y, shade(list, index))
    end
  end
end

local function faces(box)
  local u, v, w, h, d = box.u, box.v, box.w, box.h, box.d
  local function span(a, b) return math.floor(a), math.ceil(b) end
  local f = {}
  local y0, y1 = span(v, v + d)
  local sy0, sy1 = span(v + d, v + d + h)
  f.top    = { kind = "top",    rect = { span(u + d, u + d + w) }, y0 = y0, y1 = y1 }
  f.bottom = { kind = "bottom", rect = { span(u + d + w, u + d + 2 * w) }, y0 = y0, y1 = y1 }
  f.right  = { kind = "side",   rect = { span(u, u + d) }, y0 = sy0, y1 = sy1 }
  f.front  = { kind = "side",   rect = { span(u + d, u + d + w) }, y0 = sy0, y1 = sy1, front = true }
  f.left   = { kind = "side",   rect = { span(u + d + w, u + 2 * d + w) }, y0 = sy0, y1 = sy1 }
  f.back   = { kind = "side",   rect = { span(u + 2 * d + w, u + 2 * d + 2 * w) }, y0 = sy0, y1 = sy1 }
  return f
end

local ORDER = { "right", "front", "left", "back", "top", "bottom" }

-- The hem of a plate in gold on each upright face, and on its broad faces the cords lacing it to the plate above
local function laced(face, broad)
  local x0, x1 = face.rect[1], face.rect[2]
  if face.y1 - face.y0 < 2 or x1 - x0 < 2 then return end
  for x = x0, x1 - 1 do
    skin:drawPixel(x, face.y1 - 1, shade(RAMPS.gold, ((x % 2 == 0) and 3 or 2)))
  end
  if not broad or face.y1 - face.y0 < 3 then return end
  for x = x0 + 2, x1 - 3, CORD_STEP do
    skin:drawPixel(x, face.y0, CORD_DARK)
    glow:drawPixel(x, face.y0, CORD_GLOW)
  end
end

for index, box in ipairs(layout.boxes) do
  seed = index * 7919
  local f = faces(box)
  local material = box.material

  for _, name in ipairs(ORDER) do
    local face = f[name]
    local x0, x1 = face.rect[1], face.rect[2]
    local painted = RAMPS[material] and material or "plate"
    if material == "mask" or material == "slit" or material == "vent" then painted = "plate" end
    if LENSES[material] then
      local lens = LENSES[material]
      -- A lens looks out of its front, a battery tube out of the frame's back
      local out = (material == "tube") and (name == "back") or (material ~= "tube" and face.front)
      for y = face.y0, face.y1 - 1 do
        for x = x0, x1 - 1 do
          -- Speckled glass, lit at its top edge like the plates around it
          local index = 2
          local r = random()
          if r < 0.25 then index = 1 elseif r > 0.8 then index = 3 end
          if y == face.y0 then index = index + 1 end
          skin:drawPixel(x, y, shade(lens.dark, index))
          if material == "tube" then
            if out then glow:drawPixel(x, y, (y > face.y0 and x == x0 + 1) and lens.core or lens.glow) end
          elseif out then
            -- A lens glows from a small core, dimming toward its rim, and its sides stay dark
            local cx, cy = (x0 + x1 - 1) / 2, (face.y0 + face.y1 - 1) / 2
            local far = math.max(math.abs(x - cx), math.abs(y - cy))
            if far < 1 then glow:drawPixel(x, y, lens.core)
            elseif r > 0.35 then glow:drawPixel(x, y, lens.glow) end
          end
        end
      end
    else
      metal(painted, x0, face.y0, x1, face.y1, face.kind)
    end

    if face.kind == "side" and (material == "plate" or material == "lacquer") then
      laced(face, name == "front" or name == "back")
    end

    if face.front and material == "slit" then
      -- The Dragoon's slit, cut short of the lenses at its ends: violet along it, lighter only at its middle
      local row = math.floor((face.y0 + face.y1 - 1) / 2)
      local mid = (x0 + x1 - 1) / 2
      for x = x0 + 2, x1 - 3 do
        skin:drawPixel(x, row, SLIT_DARK)
        glow:drawPixel(x, row, math.abs(x - mid) < 1.5 and SLIT_GLOW or SLIT_END)
      end
    end

    if face.front and material == "mask" then
      -- A mouth grille of short dark bars low on the mask, and a gold hem
      local bottom = face.y1 - 1
      for y = bottom - 2, bottom - 1 do
        for x = x0 + 2, x1 - 3 do
          if (x - x0) % 2 == 0 and y > face.y0 then skin:drawPixel(x, y, GRILLE) end
        end
      end
      for x = x0, x1 - 1 do skin:drawPixel(x, bottom, shade(RAMPS.gold, 3)) end
    end

    if face.kind == "side" and material == "vent" then
      for y = face.y0 + 1, face.y1 - 1, 2 do
        for x = x0 + 1, x1 - 2 do
          skin:drawPixel(x, y, VENT_SLAT)
          if face.front or name == "back" then glow:drawPixel(x, y, VENT_GLOW) end
        end
      end
    end
  end
end

local out = app.params["out"]
skin:saveAs(out .. "/void_armor.png")
glow:saveAs(out .. "/void_armor_glow.png")
print("painted " .. #layout.boxes .. " boxes")
