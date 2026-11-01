-- Crops a region of an image and enlarges it, for inspecting game test screenshots up close.
-- Params: src=shot.png out=crop.png x=0 y=0 w=100 h=100 [scale=4]
local x0, y0 = tonumber(app.params["x"]), tonumber(app.params["y"])
local w, h = tonumber(app.params["w"]), tonumber(app.params["h"])
local scale = tonumber(app.params["scale"] or "4")

local sprite = app.open(app.params["src"])
local img = Image(sprite.width, sprite.height, ColorMode.RGB)
img:drawSprite(sprite, 1)
sprite:close()

local out = Image(w * scale, h * scale, ColorMode.RGB)
for y = 0, h - 1 do
  for x = 0, w - 1 do
    local v = img:getPixel(x0 + x, y0 + y)
    for dy = 0, scale - 1 do
      for dx = 0, scale - 1 do
        out:drawPixel(x * scale + dx, y * scale + dy, v)
      end
    end
  end
end

out:saveAs(app.params["out"])
