package net.voxelindustry.voidheart.client.model.portalframe;

import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class PortalFrameVeinSpriteManager
{
    private static final SpriteIdentifier[] SPRITE_IDENTIFIERS =
            {
                    getSpriteIdentifier("portal_frame_overlay"),
                    getSpriteIdentifier("portal_frame_overlay_core"),
                    getSpriteIdentifier("portal_frame_overlay_core_inactive"),
                    getSpriteIdentifier("portal_frame_overlay_core_broken"),
                    getSpriteIdentifier("portal_frame_overlay_down"),
                    getSpriteIdentifier("portal_frame_overlay_left"),
                    getSpriteIdentifier("portal_frame_overlay_right"),
                    getSpriteIdentifier("portal_frame_overlay_up")
            };

    private static final Map<Direction, Sprite> frameSpriteCache = new HashMap<>();

    private static Sprite coreActiveSprite;
    private static Sprite coreInactiveSprite;
    private static Sprite coreBrokenSprite;

    private static Function<SpriteIdentifier, Sprite> spriteGetter;

    public static void updateSpriteGetter(Function<SpriteIdentifier, Sprite> newGetter)
    {
        spriteGetter = newGetter;
    }

    public static SpriteIdentifier[] getSpriteIdentifiers()
    {
        return SPRITE_IDENTIFIERS;
    }

    public static Sprite getFrameSprite(Direction dir)
    {
        return frameSpriteCache.computeIfAbsent(dir, direction ->
        {
            switch (direction)
            {
                case DOWN:
                    return spriteGetter.apply(SPRITE_IDENTIFIERS[4]);
                case UP:
                    return spriteGetter.apply(SPRITE_IDENTIFIERS[7]);
                case NORTH:
                    return spriteGetter.apply(SPRITE_IDENTIFIERS[0]);
                case SOUTH:
                    return spriteGetter.apply(SPRITE_IDENTIFIERS[0]);
                case WEST:
                    return spriteGetter.apply(SPRITE_IDENTIFIERS[5]);
                case EAST:
                    return spriteGetter.apply(SPRITE_IDENTIFIERS[6]);
            }
            return null;
        });
    }

    public static Sprite getActiveCoreSprite()
    {
        if (coreActiveSprite == null)
            coreActiveSprite = spriteGetter.apply(SPRITE_IDENTIFIERS[1]);
        return coreActiveSprite;
    }

    public static Sprite getInactiveCoreSprite()
    {
        if (coreInactiveSprite == null)
            coreInactiveSprite = spriteGetter.apply(SPRITE_IDENTIFIERS[2]);
        return coreInactiveSprite;
    }

    public static Sprite getBrokenCoreSprite()
    {
        if (coreBrokenSprite == null)
            coreBrokenSprite = spriteGetter.apply(SPRITE_IDENTIFIERS[3]);
        return coreBrokenSprite;
    }

    private static SpriteIdentifier getSpriteIdentifier(String from)
    {
        return new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, new Identifier(MODID, "block/portal/" + from));
    }
}