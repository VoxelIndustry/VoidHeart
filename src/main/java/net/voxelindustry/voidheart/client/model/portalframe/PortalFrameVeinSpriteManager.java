package net.voxelindustry.voidheart.client.model.portalframe;

import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

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

    public static void registerSprites(Consumer<Identifier> spriteRegistrar)
    {
        for (var spriteIdentifier : SPRITE_IDENTIFIERS)
            spriteRegistrar.accept(spriteIdentifier.getTextureId());
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
                    return SPRITE_IDENTIFIERS[4].getSprite();
                case UP:
                    return SPRITE_IDENTIFIERS[7].getSprite();
                case NORTH:
                    return SPRITE_IDENTIFIERS[0].getSprite();
                case SOUTH:
                    return SPRITE_IDENTIFIERS[0].getSprite();
                case WEST:
                    return SPRITE_IDENTIFIERS[5].getSprite();
                case EAST:
                    return SPRITE_IDENTIFIERS[6].getSprite();
            }
            return null;
        });
    }

    public static Sprite getActiveCoreSprite()
    {
        if (coreActiveSprite == null)
            coreActiveSprite = SPRITE_IDENTIFIERS[1].getSprite();
        return coreActiveSprite;
    }

    public static Sprite getInactiveCoreSprite()
    {
        if (coreInactiveSprite == null)
            coreInactiveSprite = SPRITE_IDENTIFIERS[2].getSprite();
        return coreInactiveSprite;
    }

    public static Sprite getBrokenCoreSprite()
    {
        if (coreBrokenSprite == null)
            coreBrokenSprite = SPRITE_IDENTIFIERS[3].getSprite();
        return coreBrokenSprite;
    }

    private static SpriteIdentifier getSpriteIdentifier(String from)
    {
        return new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, new Identifier(MODID, "block/portal/" + from));
    }
}