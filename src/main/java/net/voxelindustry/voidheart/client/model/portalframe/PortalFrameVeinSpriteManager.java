package net.voxelindustry.voidheart.client.model.portalframe;

import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import net.voxelindustry.voidheart.common.block.PortalFrameStateProperties.FrameConnection;

import java.text.NumberFormat;
import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class PortalFrameVeinSpriteManager
{
    private static final SpriteIdentifier[] FRAME_SPRITE_IDENTIFIERS = new SpriteIdentifier[60];
    private static final Sprite[]           FRAME_SPRITES            = new Sprite[60];

    private static final SpriteIdentifier[] CORE_SPRITE_IDENTIFIERS = new SpriteIdentifier[4];
    private static final Sprite[]           CORE_SPRITES            = new Sprite[4];

    private static Sprite VOIDBRICK_SPRITE;

    static
    {
        var format = NumberFormat.getInstance();
        format.setMinimumIntegerDigits(2);

        for (int i = 0; i < 60; i++)
        {
            FRAME_SPRITE_IDENTIFIERS[i] = getFrameSpriteIdentifier("portal_" + format.format(i));
        }

        for (int i = 0; i < 4; i++)
        {
            CORE_SPRITE_IDENTIFIERS[i] = getCoreSpriteIdentifier("core_" + i);
        }
    }

    public static void registerSprites(Consumer<Identifier> spriteRegistrar)
    {
        for (var spriteIdentifier : FRAME_SPRITE_IDENTIFIERS)
            spriteRegistrar.accept(spriteIdentifier.getTextureId());

        for (SpriteIdentifier coreSpriteIdentifier : CORE_SPRITE_IDENTIFIERS)
            spriteRegistrar.accept(coreSpriteIdentifier.getTextureId());
    }

    public static void loadSprites()
    {
        for (int i = 0; i < 60; i++)
            FRAME_SPRITES[i] = FRAME_SPRITE_IDENTIFIERS[i].getSprite();
        for (int i = 0; i < 4; i++)
            CORE_SPRITES[i] = CORE_SPRITE_IDENTIFIERS[i].getSprite();

        VOIDBRICK_SPRITE = new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, new Identifier(MODID, "block/voidstone_bricks")).getSprite();
    }

    public static SpriteIdentifier[] getFrameSpriteIdentifiers()
    {
        return FRAME_SPRITE_IDENTIFIERS;
    }

    public static Sprite getCoreSprite()
    {
        return CORE_SPRITES[0];
    }

    public static Sprite getCoreOverlaySprite()
    {
        return CORE_SPRITES[3];
    }

    public static Sprite getCoreBrokenOverlaySprite()
    {
        return CORE_SPRITES[2];
    }

    public static Sprite getBackgroundSpriteForFront(int variant)
    {
        return FRAME_SPRITES[28 + variant];
    }

    public static Sprite getOverlaySpriteForFront(int variant)
    {
        return FRAME_SPRITES[30 + 28 + variant];
    }

    public static Sprite getOverlaySpriteForSide(FrameConnection left, FrameConnection right, FrameConnection up, FrameConnection down, int variant)
    {
        return getSpriteForSide(left, right, up, down, variant, 30);
    }

    public static Sprite getBackgroundSpriteForSide(FrameConnection left, FrameConnection right, FrameConnection up, FrameConnection down, int variant)
    {
        var sprite = getSpriteForSide(left, right, up, down, variant, 0);

        if (sprite == null)
            return VOIDBRICK_SPRITE;
        return sprite;
    }

    private static Sprite getSpriteForSide(FrameConnection left, FrameConnection right, FrameConnection up, FrameConnection down, int variant, int offset)
    {
        if (!left.isConnected() && !right.isConnected() && !up.isConnected() && !down.isConnected())
            return null;

        if (left == FrameConnection.INTERIOR && right == FrameConnection.INTERIOR ||
                up == FrameConnection.INTERIOR && down == FrameConnection.INTERIOR)
            return FRAME_SPRITES[28 + variant + offset];

        if (left == FrameConnection.INTERIOR)
        {
            if (right == FrameConnection.FRAME)
                return FRAME_SPRITES[28 + variant + offset];
            return FRAME_SPRITES[10 + variant + offset];
        }
        if (right == FrameConnection.INTERIOR)
        {
            if (left == FrameConnection.FRAME)
                return FRAME_SPRITES[28 + variant + offset];
            return FRAME_SPRITES[8 + variant + offset];
        }

        if (down == FrameConnection.INTERIOR)
            return FRAME_SPRITES[4 + variant + offset];
        if (up == FrameConnection.INTERIOR)
            return FRAME_SPRITES[6 + variant + offset];

        if (left == FrameConnection.FRAME)
        {
            if (right == FrameConnection.FRAME)
                return up == FrameConnection.FRAME ? FRAME_SPRITES[6 + variant + offset] : FRAME_SPRITES[4 + variant + offset];

            if (up == FrameConnection.FRAME)
                return FRAME_SPRITES[2 + offset];
            if (down == FrameConnection.FRAME)
                return FRAME_SPRITES[0 + offset];
        }

        if (right == FrameConnection.FRAME)
        {
            if (up == FrameConnection.FRAME)
                return FRAME_SPRITES[3 + offset];
            if (down == FrameConnection.FRAME)
                return FRAME_SPRITES[1 + offset];
        }

        return null;
    }

    private static SpriteIdentifier getFrameSpriteIdentifier(String from)
    {
        return new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, new Identifier(MODID, "block/portal/" + from));
    }

    private static SpriteIdentifier getCoreSpriteIdentifier(String from)
    {
        return new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, new Identifier(MODID, "block/portalcore/" + from));
    }
}