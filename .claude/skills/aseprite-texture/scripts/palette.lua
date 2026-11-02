-- Prints each file's size, transparency and most used colors.
-- Params: files=a.png;b.png [top=12]
local top = tonumber(app.params["top"] or "12")

for path in string.gmatch(app.params["files"], "[^;]+") do
  local sprite = app.open(path)
  local img = Image(sprite.width, sprite.height, ColorMode.RGB)
  img:drawSprite(sprite, 1)

  local counts, order = {}, {}
  local clear, partial, opaque = 0, 0, 0
  for px in img:pixels() do
    local v = px()
    local a = app.pixelColor.rgbaA(v)
    if a == 0 then
      clear = clear + 1
    else
      if a == 255 then opaque = opaque + 1 else partial = partial + 1 end
      if not counts[v] then
        counts[v] = 0
        table.insert(order, v)
      end
      counts[v] = counts[v] + 1
    end
  end
  table.sort(order, function(x, y) return counts[x] > counts[y] end)

  print(string.format("%s: %dx%d, %d frame(s), %d colors, pixels opaque=%d partial=%d clear=%d",
    path, sprite.width, sprite.height, #sprite.frames, #order, opaque, partial, clear))
  for i = 1, math.min(top, #order) do
    local v = order[i]
    print(string.format("  #%02X%02X%02X a=%3d x%d",
      app.pixelColor.rgbaR(v), app.pixelColor.rgbaG(v), app.pixelColor.rgbaB(v), app.pixelColor.rgbaA(v), counts[v]))
  end
  sprite:close()
end
