---
name: aseprite-texture
description: Create, edit, inspect and preview VoidHeart textures and sprites with the Aseprite CLI, using bundled scripts for palettes, enlarged previews, seam checks and screenshot crops. Use whenever a task needs a new or changed PNG in the mod, including blocks, items, entities and particles, or when matching the mod's colors, checking a texture for transparency or seams, or enlarging part of a game test screenshot. Pillow isn't installed, so use this rather than Python for any image work, even small edits.
---

# Aseprite textures

Aseprite runs headless here and is the user's chosen tool. Pillow isn't installed, so don't reach for Python.

## Running it

The executable isn't on `PATH`. Run it from Bash and write the path out literally:

```bash
"/c/Program Files (x86)/Steam/steamapps/common/Aseprite/Aseprite.exe" -b --script-param "key=value" --script "path/to/script.lua"
```

- **Use Bash, not PowerShell.** `print` output doesn't reach a PowerShell capture, so scripts appear to return nothing.
- **Keep the path literal.** Worktree sessions refuse a command whose name comes from a variable like `$A`.
- **Create Lua files with the Write tool.** PowerShell's file output adds a BOM, which fails with `unexpected symbol near '<\239>'`.
- **Put `--script-param` before `--script`.** Relative paths resolve against the current directory.
- **Check for errors.** A failed script exits non-zero. `attempt to index a nil value (local 'sprite')` after `File not found` means a wrong path.

## Bundled scripts

All are in `.claude/skills/aseprite-texture/scripts/`.

| Script | Use | Params |
|---|---|---|
| `palette.lua` | Print size, frame count, opaque, partial and clear pixel counts, and the top colors | `files=a.png;b.png`, optional `top=12` |
| `preview.lua` | Build an enlarged sheet on magenta, then open it with the Read tool | `files=a.png;b.png`, `out=sheet.png`, optional `scale=8`, `tile=1` |
| `crop.lua` | Enlarge part of an image, such as a game test screenshot | `src=`, `out=`, `x=`, `y=`, `w=`, `h=`, optional `scale=4` |

Use `tile=3` for block surfaces. It repeats the texture 3x3, which shows whether joined blocks read as one surface or as separate tiles.

## Making a texture

1. **Start from the closest existing texture.** Run `palette.lua` on it and on a neighbor, and preview both. Editing one keeps the style far better than drawing from nothing.
2. **Write the script in the scratchpad, and save the result there first.** Only copy it into `src/main/resources/assets/voidheart/textures/` once the preview looks right. If you're replacing a texture, look at the current one first.
3. **Preview it next to its neighbors** at the same scale. Check it at real size too, since detail that reads at 8x can turn to noise in the game.
4. **Check it in the game** with the `verify-in-game` skill, because lighting, glow and model UVs change how it looks.

This pattern works for any image, including indexed PNGs:

```lua
local sprite = app.open(app.params["src"])
local img = Image(sprite.width, sprite.height, ColorMode.RGB)
img:drawSprite(sprite, 1)
sprite:close()

local pc = app.pixelColor
for px in img:pixels() do
  local v = px()
  if pc.rgbaA(v) > 0 then
    px(pc.rgba(pc.rgbaR(v), pc.rgbaG(v), pc.rgbaB(v), pc.rgbaA(v)))
  end
end
img:saveAs(app.params["out"])
```

Drawing into a fresh RGB `Image` avoids reading `sprite.cels[1].image` directly, which can be smaller than the sprite and, in the 51 indexed PNGs here, holds palette indexes instead of colors. For a new texture, start from `Image(16, 16, ColorMode.RGB)` and use `img:drawPixel(x, y, pc.rgba(r, g, b, a))`.

## Mod conventions

**Sizes:**

| Kind | Usual size |
|---|---|
| Blocks and items | 16x16 (a few blocks are 32x32) |
| Particles | 6x6 |
| Entities | 64x64, 64x32 or 32x16 |
| Animations | One PNG with frames stacked vertically and a `.png.mcmeta`, such as `block/portal.png` at 16x256 |

Aseprite sees a stacked animation as one tall frame, so edit each frame's band and keep the height a multiple of the frame size.

**Transparency:** 26.3 picks a placed block's chunk layer from its texture, so any partial alpha makes the block translucent (`6eaa6fe`). If `palette.lua` reports `partial` above 0 on a texture for a solid block, fix it. Apply ghost or glow transparency when drawing instead.

**Colors,** as measured from current textures. Re-run `palette.lua` on the nearest texture rather than treating these as a fixed palette:

| Family | Colors | Seen in |
|---|---|---|
| Voidstone, dark to light | `#261B29 #2D262F #312C38 #37303A #3B353E #3E3C45 #45464B` | `block/void_pillar_inactive.png` |
| Ley teal, dark to light | `#145037 #277D63 #36C5B0 #6BDBC0 #73D1DF` | `block/lamp/voidlamp_on.png`, glyphs |
| Unlit accents | `#130D0E #1B1017 #221E18` | `*_inactive` glyphs |
| Beholder violet | `#5B22A8 #8A3DF0 #8F78B8 #BFA9E8 #CBB8F2 #D9C8FF` | `block/beholder_eye.png` |

**Paired states** usually use suffixes like `_active` and `_inactive`, or `_on`. Check whether the new texture needs a pair.

**Aseprite sources:** only `block/conduit/conduit.aseprite` and `sprites/ravenous_gold.aseprite` exist, so PNGs are normally the source of truth. If you change a PNG made from one of these, tell the user that the source no longer matches, or update it too.
