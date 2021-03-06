package net.voxelindustry.voidheart.client.model.monolith;

import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidMonolithSpriteManager
{
    private static final SpriteIdentifier[] MONOLITH_SPRITES =
            {
                    getSpriteIdentifier("monolith_side"),
                    getSpriteIdentifier("monolith_side_lower"),
                    getSpriteIdentifier("monolith_side_middle"),
                    getSpriteIdentifier("monolith_side_upper")
            };

    private static final SpriteIdentifier[] GLYPH_SPRITES =
            {
                    getSpriteIdentifier("glyph/glyph1_active"),
                    getSpriteIdentifier("glyph/glyph2_active"),
                    getSpriteIdentifier("glyph/glyph3_active"),
                    getSpriteIdentifier("glyph/glyph4_active"),
                    getSpriteIdentifier("glyph/glyph5_active"),
                    getSpriteIdentifier("glyph/glyph6_active"),
                    getSpriteIdentifier("glyph/glyph7_active"),
                    getSpriteIdentifier("glyph/glyph8_active"),
                    getSpriteIdentifier("glyph/glyph9_active"),
                    getSpriteIdentifier("glyph/glyph10_active"),
                    getSpriteIdentifier("glyph/glyph11_active"),
                    getSpriteIdentifier("glyph/glyph12_active"),
                    getSpriteIdentifier("glyph/glyph13_active"),
                    getSpriteIdentifier("glyph/glyph14_active"),
                    getSpriteIdentifier("glyph/glyph15_active"),
                    getSpriteIdentifier("glyph/glyph16_active"),
                    getSpriteIdentifier("glyph/glyph1_inactive"),
                    getSpriteIdentifier("glyph/glyph2_inactive"),
                    getSpriteIdentifier("glyph/glyph3_inactive"),
                    getSpriteIdentifier("glyph/glyph4_inactive"),
                    getSpriteIdentifier("glyph/glyph5_inactive"),
                    getSpriteIdentifier("glyph/glyph6_inactive"),
                    getSpriteIdentifier("glyph/glyph7_inactive"),
                    getSpriteIdentifier("glyph/glyph8_inactive"),
                    getSpriteIdentifier("glyph/glyph9_inactive"),
                    getSpriteIdentifier("glyph/glyph10_inactive"),
                    getSpriteIdentifier("glyph/glyph11_inactive"),
                    getSpriteIdentifier("glyph/glyph12_inactive"),
                    getSpriteIdentifier("glyph/glyph13_inactive"),
                    getSpriteIdentifier("glyph/glyph14_inactive"),
                    getSpriteIdentifier("glyph/glyph15_inactive"),
                    getSpriteIdentifier("glyph/glyph16_inactive")
            };

    private static final Map<Direction, Sprite> frameSpriteCache = new HashMap<>();
    private static final Map<Integer, Sprite>   glyphSpriteCache = new HashMap<>();

    private static Function<SpriteIdentifier, Sprite> spriteGetter;

    public static void updateSpriteGetter(Function<SpriteIdentifier, Sprite> newGetter)
    {
        spriteGetter = newGetter;
    }

    public static SpriteIdentifier[] getMonolithSprites()
    {
        return MONOLITH_SPRITES;
    }

    public static SpriteIdentifier[] getGlyphSprites()
    {
        return GLYPH_SPRITES;
    }

    public static Sprite getFrameSprite(Direction dir)
    {
        return frameSpriteCache.computeIfAbsent(dir, direction ->
        {
            switch (direction)
            {
                case DOWN:
                    return spriteGetter.apply(MONOLITH_SPRITES[3]);
                case UP:
                    return spriteGetter.apply(MONOLITH_SPRITES[1]);
                case NORTH:
                    return spriteGetter.apply(MONOLITH_SPRITES[2]);
                default:
                    return spriteGetter.apply(MONOLITH_SPRITES[0]);
            }
        });
    }

    public static Sprite getGlyphSprite(int glyphIndex, boolean active)
    {
        return glyphSpriteCache.computeIfAbsent(glyphIndex + (active ? 0 : 16),
                index -> spriteGetter.apply(GLYPH_SPRITES[index]));
    }

    private static SpriteIdentifier getSpriteIdentifier(String from)
    {
        return new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, new Identifier(MODID, "block/monolith/" + from));
    }
}