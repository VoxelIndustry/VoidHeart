package net.voxelindustry.voidheart.client.model.portalframe;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;
import net.voxelindustry.voidheart.common.block.PortalFrameStateProperties.FrameConnection;

import java.text.NumberFormat;
import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class PortalFrameVeinSpriteManager
{
    private static final Material[] FRAME_SPRITE_IDENTIFIERS = new Material[60];
    private static final TextureAtlasSprite[]           FRAME_SPRITES            = new TextureAtlasSprite[60];

    private static final Material[] CORE_SPRITE_IDENTIFIERS = new Material[4];
    private static final TextureAtlasSprite[]           CORE_SPRITES            = new TextureAtlasSprite[4];

    private static TextureAtlasSprite VOIDBRICK_SPRITE;

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
            spriteRegistrar.accept(spriteIdentifier.texture());

        for (Material coreSpriteIdentifier : CORE_SPRITE_IDENTIFIERS)
            spriteRegistrar.accept(coreSpriteIdentifier.texture());
    }

    public static void loadSprites()
    {
        for (int i = 0; i < 60; i++)
            FRAME_SPRITES[i] = FRAME_SPRITE_IDENTIFIERS[i].sprite();
        for (int i = 0; i < 4; i++)
            CORE_SPRITES[i] = CORE_SPRITE_IDENTIFIERS[i].sprite();

        VOIDBRICK_SPRITE = new Material(InventoryMenu.BLOCK_ATLAS, Identifier.fromNamespaceAndPath(MODID, "block/voidstone_bricks")).sprite();
    }

    public static Material[] getFrameSpriteIdentifiers()
    {
        return FRAME_SPRITE_IDENTIFIERS;
    }

    public static TextureAtlasSprite getCoreSprite()
    {
        return CORE_SPRITES[0];
    }

    public static TextureAtlasSprite getCoreOverlaySprite()
    {
        return CORE_SPRITES[3];
    }

    public static TextureAtlasSprite getCoreBrokenOverlaySprite()
    {
        return CORE_SPRITES[2];
    }

    public static TextureAtlasSprite getBackgroundSpriteForFront(int variant)
    {
        return FRAME_SPRITES[28 + variant];
    }

    public static TextureAtlasSprite getOverlaySpriteForFront(int variant)
    {
        return FRAME_SPRITES[30 + 28 + variant];
    }

    public static TextureAtlasSprite getOverlaySpriteForSide(FrameConnection left, FrameConnection right, FrameConnection up, FrameConnection down, int variant)
    {
        return getSpriteForSide(left, right, up, down, variant, 30);
    }

    public static TextureAtlasSprite getBackgroundSpriteForSide(FrameConnection left, FrameConnection right, FrameConnection up, FrameConnection down, int variant)
    {
        var sprite = getSpriteForSide(left, right, up, down, variant, 0);

        if (sprite == null)
            return VOIDBRICK_SPRITE;
        return sprite;
    }

    private static TextureAtlasSprite getSpriteForSide(FrameConnection left, FrameConnection right, FrameConnection up, FrameConnection down, int variant, int offset)
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

    private static Material getFrameSpriteIdentifier(String from)
    {
        return new Material(InventoryMenu.BLOCK_ATLAS, Identifier.fromNamespaceAndPath(MODID, "block/portal/" + from));
    }

    private static Material getCoreSpriteIdentifier(String from)
    {
        return new Material(InventoryMenu.BLOCK_ATLAS, Identifier.fromNamespaceAndPath(MODID, "block/portalcore/" + from));
    }
}