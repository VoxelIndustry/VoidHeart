-- Draws every crystal habit texture from the plain shard and bundle sprites, keeping each crystal's own ramp under the habit's look
-- (docs/plans/crystallography.md, Looks). Still textures are 8 stacked frames and need a .png.mcmeta beside them.
-- Run from the repository root:
--   Aseprite.exe -b --script-param "src=src/main/resources/assets/voidheart/textures" --script .claude/tools/crystal_habits.lua
-- Params: src = textures folder holding block/ and item/; out = where to write them, src by default.
local pc = app.pixelColor
local SRC = app.params["src"]
local OUT = app.params["out"] ~= nil and app.params["out"] ~= "" and app.params["out"] or SRC

local RAMPS = {
  azurium  = { 0x223A99, 0x2C56B8, 0x3D7ED9, 0x68BBF7, 0x9EECFF, 0xCCFFF2, 0xFFFFE0 },
  rubrum   = { 0x7A1637, 0xAD1A2E, 0xE0361F, 0xFF5926, 0xFF9238, 0xFFD580, 0xFFF9D9 },
  memoriam = { 0xA63C1C, 0xCC5A18, 0xF28313, 0xFFB433, 0xFFE173, 0xFFFAC2, 0xFFFFEB },
}
local TAKEN = {
  azurium  = { "clear", "deep", "fouled", "scoured", "searing", "still" },
  rubrum   = { "fouled", "scoured", "searing", "still", "wrung" },
  memoriam = { "deep", "fouled", "scoured", "wrung" },
}

local function rgb(hex) return { (hex >> 16) & 0xFF, (hex >> 8) & 0xFF, hex & 0xFF } end
local function mix(a, b, f) return { a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f, a[3] + (b[3] - a[3]) * f } end
local function scale(a, f) return { a[1] * f, a[2] * f, a[3] * f } end
local function clamp(v, lo, hi) return math.max(lo, math.min(hi, v)) end
local function hash(x, y, s)
  local n = (x * 374761393 + y * 668265263 + s * 982451653) & 0xFFFFFFFF
  n = ((n ~ (n >> 13)) * 1274126177) & 0xFFFFFFFF
  n = n ~ (n >> 16)
  return (n & 0xFFFF) / 65536
end

-- Clear: see-through pale glass by ramp step, opacity rising with lightness, and bright rims. The only habit with partial alpha, so it draws translucent
local CLEAR_GLASS = {
  { 45, 120, 215, 105 }, { 65, 150, 230, 125 }, { 95, 185, 245, 150 }, { 160, 225, 255, 195 },
  { 205, 245, 255, 225 }, { 240, 255, 255, 255 }, { 250, 255, 255, 255 },
}
local CLEAR_RIM, CLEAR_RIM_MIX, CLEAR_RIM_ALPHA = { 150, 220, 255 }, 0.35, 185
-- Deep: a black core with star specks under violet-dark edges
local VOID, VIOLET, PALE_VIOLET = rgb(0x0E0818), rgb(0x5B22A8), rgb(0xBFA9E8)
local STAR, STAR_DIM = rgb(0xF2ECFF), rgb(0x8F78B8)
-- Fouled: murky, dark veins, oily sheen, a few seep motes
local MURK, VEIN, MOTE = rgb(0x1E2A18), rgb(0x0A0F08), rgb(0x56702C)
local SHEENS = { rgb(0x9CC050), rgb(0xB07ACC), rgb(0x5FC0AC) }
-- Scoured: bleached rims round a hollow core
local BLEACH, ASH, HOLLOW = rgb(0xF6F2E8), rgb(0x7C766C), rgb(0x4A453E)
-- Searing: charred body, ember-bright tips
local CHAR = rgb(0x2A1612)
local EMBERS = { rgb(0xFFF6C0), rgb(0xFFC447), rgb(0xF27A1F), rgb(0xB8401A) }
-- Still: frost, with a slow sparkle
local FROST = { rgb(0x1F4650), rgb(0x386E7A), rgb(0x6FA3AD), rgb(0xAED3DA), rgb(0xDDF0F3) }
local RIME, GLINT = rgb(0xF6FCFD), rgb(0xFFFFFF)
local STILL_FRAMES = 8
-- Wrung: twisted shading and rust stains
local WRUNG_DARK, WRUNG_RED = rgb(0x3A0E10), rgb(0x9A1E1E)
local RUSTS = { rgb(0x4E2010), rgb(0x7E3818), rgb(0xA8582A) }

local function analyse(path)
  local sprite = app.open(path)
  local w, h = sprite.width, sprite.height
  local img = Image(w, h, ColorMode.RGB)
  img:drawSprite(sprite, 1)
  sprite:close()
  local px = {}
  for y = 0, h - 1 do
    for x = 0, w - 1 do
      local v = img:getPixel(x, y)
      if pc.rgbaA(v) > 0 then px[y * w + x] = { r = pc.rgbaR(v), g = pc.rgbaG(v), b = pc.rgbaB(v) } end
    end
  end
  return px, w, h
end

local function nearestIndex(ramp, p)
  local best, bestD = 0, math.huge
  for i, hex in ipairs(ramp) do
    local c = rgb(hex)
    local d = (c[1] - p.r) ^ 2 + (c[2] - p.g) ^ 2 + (c[3] - p.b) ^ 2
    if d < bestD then best, bestD = i - 1, d end
  end
  return best
end

-- Steps to the nearest clear pixel, so 1 is the rim
local function distances(px, w, h)
  local dist = {}
  local queue, head = {}, 1
  for y = 0, h - 1 do
    for x = 0, w - 1 do
      local k = y * w + x
      if px[k] then
        local rim = false
        for _, d in ipairs({ { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } }) do
          local nx, ny = x + d[1], y + d[2]
          if nx < 0 or ny < 0 or nx >= w or ny >= h or not px[ny * w + nx] then rim = true end
        end
        if rim then dist[k] = 1; queue[#queue + 1] = k end
      end
    end
  end
  while head <= #queue do
    local k = queue[head]; head = head + 1
    local x, y = k % w, k // w
    for _, d in ipairs({ { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } }) do
      local nx, ny = x + d[1], y + d[2]
      local nk = ny * w + nx
      if nx >= 0 and ny >= 0 and nx < w and ny < h and px[nk] and not dist[nk] then
        dist[nk] = dist[k] + 1
        queue[#queue + 1] = nk
      end
    end
  end
  return dist
end

-- Opaque pixels straight above, so 0 is a top surface
local function above(px, w, x, y)
  local n = 0
  local yy = y - 1
  while yy >= 0 and px[yy * w + x] do n = n + 1; yy = yy - 1 end
  return n
end

local function paint(habit, kind, px, w, h, frame)
  local ramp = RAMPS[kind]
  local dist = distances(px, w, h)
  local out = {}
  for y = 0, h - 1 do
    for x = 0, w - 1 do
      local k = y * w + x
      local p = px[k]
      if p then
        local idx = nearestIndex(ramp, p)
        local t = idx / 6
        local base = rgb(ramp[idx + 1])
        local d = dist[k]
        local c
        if habit == "clear" then
          local glass = CLEAR_GLASS[idx + 1]
          c = { glass[1], glass[2], glass[3], glass[4] }
          if d == 1 then
            local rim = mix(c, CLEAR_RIM, CLEAR_RIM_MIX)
            c = { math.floor(rim[1]), math.floor(rim[2]), math.floor(rim[3]), math.max(glass[4], CLEAR_RIM_ALPHA) }
          end
        elseif habit == "deep" then
          if d >= 2 or idx == 0 then
            c = mix(VOID, base, 0.08 + 0.14 * t)
            local s = hash(x, y, 11)
            if s < 0.11 then c = STAR elseif s < 0.2 then c = STAR_DIM end
          else
            c = scale(mix(base, VIOLET, 0.5), 0.7 + 0.3 * t)
            if t >= 0.6 then c = mix(c, PALE_VIOLET, 0.4) end
          end
        elseif habit == "fouled" then
          c = mix(base, MURK, 0.55)
          local jitter = math.floor(hash(0, y, 21) * 3)
          if (x + y + jitter) % 6 == 0 and hash(x, y, 22) < 0.85 then
            c = VEIN
          elseif t >= 0.5 and hash(x, y, 23) < 0.5 then
            c = mix(c, SHEENS[(x + y) % 3 + 1], 0.55)
          end
        elseif habit == "scoured" then
          if d == 1 then
            c = mix(BLEACH, base, 0.08 + 0.14 * (1 - t))
          elseif d == 2 then
            c = mix(ASH, base, 0.1)
          else
            c = mix(HOLLOW, base, 0.08)
          end
        elseif habit == "searing" then
          c = mix(base, CHAR, 0.5)
          local a = above(px, w, x, y)
          if a <= 3 then c = mix(c, EMBERS[a + 1], a == 3 and 0.5 or 1) end
        elseif habit == "still" then
          -- Kept a shade darker than the crystal, so the white rime on its tops stands out and it never reads as Clear's pale glass
          c = mix(base, FROST[1 + math.floor(t * 2 + 0.5)], 0.65)
          if above(px, w, x, y) == 0 then
            c = mix(c, RIME, 0.85)
          elseif d == 1 and hash(x, y, 41) < 0.3 then
            c = mix(c, RIME, 0.6)
          end
        elseif habit == "wrung" then
          local wave = math.sin((x - y) * 1.15) * 0.42
          local twisted = math.floor(clamp(t + wave, 0, 1) * 6 + 0.5)
          c = mix(mix(rgb(ramp[twisted + 1]), WRUNG_DARK, 0.3), WRUNG_RED, 0.15)
          local n = (hash(x // 3, y // 3, 51) + hash((x + 1) // 3, (y + 2) // 3, 52)) / 2
          if n < 0.32 then c = mix(c, RUSTS[1 + math.floor(t * 2.99)], 0.8) end
        end
        if c then out[k] = c end
      end
    end
  end

  if habit == "fouled" then
    local motes = 0
    for y = 2, h - 1 do
      for x = 0, w - 1 do
        local k = y * w + x
        if motes < 2 and px[k] and above(px, w, x, y) == 0 and hash(x, y, 31) < 0.18 then
          local mk = (y - 2) * w + x
          if not px[mk] and not px[(y - 1) * w + x] then out[mk] = MOTE; motes = motes + 1 end
        end
      end
    end
  end

  if habit == "still" and frame then
    -- Two glints on light pixels near the top, each showing for two frames of the loop
    local spots = {}
    for y = 0, h - 1 do
      for x = 0, w - 1 do
        local k = y * w + x
        if px[k] and nearestIndex(RAMPS[kind], px[k]) >= 3 and above(px, w, x, y) <= 2 then
          spots[#spots + 1] = { x = x, y = y, s = hash(x, y, 61) }
        end
      end
    end
    table.sort(spots, function(a, b) return a.s < b.s end)
    local shown = nil
    if frame == 2 or frame == 3 then shown = spots[1] elseif frame == 5 or frame == 6 then shown = spots[2] end
    if shown then
      out[shown.y * w + shown.x] = GLINT
      if frame == 2 or frame == 5 then
        for _, dd in ipairs({ { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } }) do
          local nx, ny = shown.x + dd[1], shown.y + dd[2]
          if nx >= 0 and ny >= 0 and nx < w and ny < h then out[ny * w + nx] = RIME end
        end
      end
    end
  end
  return out
end

local function write(habit, kind, srcPath, outPath)
  local px, w, h = analyse(srcPath)
  local frames = habit == "still" and STILL_FRAMES or 1
  local img = Image(w, h * frames, ColorMode.RGB)
  for f = 0, frames - 1 do
    local out = paint(habit, kind, px, w, h, habit == "still" and f or nil)
    for k, c in pairs(out) do
      local x, y = k % w, k // w
      img:drawPixel(x, y + f * h, pc.rgba(math.floor(clamp(c[1], 0, 255)), math.floor(clamp(c[2], 0, 255)), math.floor(clamp(c[3], 0, 255)), c[4] or 255))
    end
  end
  img:saveAs(outPath)
  print(outPath)
end

-- Rubrum Cells carry the habit of the bundle sealed in them. Only the shard column is redrawn: its lit part in the habit's look, with the habit's
-- brightest colour at the fill line, and its unlit part tinted so even an empty cell shows its habit
local CELL_HABITS = { "searing", "still", "wrung" }
local CELL_LEVELS = { "empty", "third", "two_thirds", "full" }
local CELL_UNLIT = { [0x5A1230] = 0, [0x7A1637] = 1 }
local CELL_DARK = {
  searing = { rgb(0x2E1712), rgb(0x4A2416) },
  still   = { rgb(0x16323A), rgb(0x24505A) },
  wrung   = { rgb(0x2C0B0D), rgb(0x4A1414) },
}

local function cellPixel(habit, x, y, base, t, top)
  if habit == "searing" then
    -- Hotter than plain rubrum: yellow embers flickering up a column that glows white at the fill line
    if top then return EMBERS[1] end
    local c = mix(base, EMBERS[2 + (y % 2)], 0.55)
    if hash(x, y, 71) < 0.3 then c = EMBERS[2] end
    return c
  elseif habit == "still" then
    if top then return RIME end
    local c = mix(base, FROST[3 + math.floor(t + 0.5)], 0.88)
    if hash(x, y, 72) < 0.25 then c = mix(c, FROST[2], 0.5) end
    return c
  else
    -- Deep crimson wrung into darker twists, with rust where it is stained
    if top then return rgb(0xE8323A) end
    local twist = (x + y) % 3 == 0
    local c = mix(base, WRUNG_RED, 0.6)
    if twist then c = mix(c, WRUNG_DARK, 0.55) end
    if hash(x, y, 73) < 0.25 then c = mix(c, RUSTS[2], 0.7) end
    return c
  end
end

local function writeCell(habit, srcPath, outPath)
  local px, w, h = analyse(srcPath)
  local img = Image(w, h, ColorMode.RGB)
  local function hex(p) return (p.r << 16) | (p.g << 8) | p.b end
  local function lit(k)
    local p = px[k]
    return p ~= nil and CELL_UNLIT[hex(p)] == nil and nearestIndex(RAMPS.rubrum, p) >= 2 and p.r > 0xC0 and p.g < 0xE0
  end
  for y = 0, h - 1 do
    for x = 0, w - 1 do
      local k = y * w + x
      local p = px[k]
      if p then
        local c = { p.r, p.g, p.b }
        local unlit = CELL_UNLIT[hex(p)]
        if unlit ~= nil then
          c = CELL_DARK[habit][unlit + 1]
          if habit == "wrung" and hash(x, y, 74) < 0.3 then c = mix(c, RUSTS[1], 0.7) end
        elseif lit(k) then
          local idx = nearestIndex(RAMPS.rubrum, p)
          c = cellPixel(habit, x, y, rgb(RAMPS.rubrum[idx + 1]), idx / 6, y == 0 or not lit(k - w))
        end
        img:drawPixel(x, y, pc.rgba(math.floor(clamp(c[1], 0, 255)), math.floor(clamp(c[2], 0, 255)), math.floor(clamp(c[3], 0, 255)), 255))
      end
    end
  end
  img:saveAs(outPath)
  print(outPath)
end

for _, habit in ipairs(CELL_HABITS) do
  for _, level in ipairs(CELL_LEVELS) do
    writeCell(habit, SRC .. "/item/rubrum_cell_" .. level .. ".png", OUT .. "/item/rubrum_cell_" .. habit .. "_" .. level .. ".png")
  end
end

for kind, habits in pairs(TAKEN) do
  for _, habit in ipairs(habits) do
    for _, size in ipairs({ "small", "medium", "large" }) do
      write(habit, kind, SRC .. "/block/" .. size .. "_" .. kind .. "_bundle.png", OUT .. "/block/" .. size .. "_" .. kind .. "_bundle_" .. habit .. ".png")
    end
    write(habit, kind, SRC .. "/item/" .. kind .. "_shard.png", OUT .. "/item/" .. kind .. "_shard_" .. habit .. ".png")
  end
end
