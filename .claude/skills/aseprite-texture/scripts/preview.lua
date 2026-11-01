-- Builds one enlarged contact sheet of the given images, on a magenta background so transparency shows.
-- Params: files=a.png;b.png out=sheet.png [scale=8] [tile=1]
-- With tile=3 each image repeats 3x3, which shows seams between blocks of the same texture.
local scale = tonumber(app.params["scale"] or "8")
local tile = tonumber(app.params["tile"] or "1")
local gap = 4

local images = {}
local width, height = gap, 0
for path in string.gmatch(app.params["files"], "[^;]+") do
  local sprite = app.open(path)
  local img = Image(sprite.width, sprite.height, ColorMode.RGB)
  img:drawSprite(sprite, 1)
  sprite:close()
  table.insert(images, img)
  width = width + img.width * tile * scale + gap
  height = math.max(height, img.height * tile * scale)
end

local sheet = Image(width, height + gap * 2, ColorMode.RGB)
sheet:clear(Color{ r = 255, g = 0, b = 255, a = 255 })

local left = gap
for _, img in ipairs(images) do
  for ty = 0, tile - 1 do
    for tx = 0, tile - 1 do
      for y = 0, img.height - 1 do
        for x = 0, img.width - 1 do
          local v = img:getPixel(x, y)
          if app.pixelColor.rgbaA(v) > 0 then
            local ox = left + (tx * img.width + x) * scale
            local oy = gap + (ty * img.height + y) * scale
            for dy = 0, scale - 1 do
              for dx = 0, scale - 1 do
                sheet:drawPixel(ox + dx, oy + dy, v)
              end
            end
          end
        end
      end
    end
  end
  left = left + img.width * tile * scale + gap
end

sheet:saveAs(app.params["out"])
