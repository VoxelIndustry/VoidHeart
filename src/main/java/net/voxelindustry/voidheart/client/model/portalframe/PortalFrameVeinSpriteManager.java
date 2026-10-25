package net.voxelindustry.voidheart.client.model.portalframe;

import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.block.PortalFrameStateProperties.FrameConnection;

import java.text.NumberFormat;
import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class PortalFrameVeinSpriteManager
{
    private static final ModelDebugName DEBUG_NAME = () -> "voidheart:PortalFrameVeinSpriteManager";

    private static final Material[] FRAME_SPRITE_IDENTIFIERS = new Material[60];
    private static final Material.Baked[]           FRAME_SPRITES            = new Material.Baked[60];

    private static final Material[] CORE_SPRITE_IDENTIFIERS = new Material[4];
    private static final Material.Baked[]           CORE_SPRITES            = new Material.Baked[4];

    private static Material.Baked VOIDBRICK_SPRITE;

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

    public static void loadSprites(MaterialBaker materials)
    {
        for (int i = 0; i < 60; i++)
            FRAME_SPRITES[i] = materials.get(FRAME_SPRITE_IDENTIFIERS[i], DEBUG_NAME);
        for (int i = 0; i < 4; i++)
            CORE_SPRITES[i] = materials.get(CORE_SPRITE_IDENTIFIERS[i], DEBUG_NAME);

        VOIDBRICK_SPRITE = materials.get(new Material(Identifier.fromNamespaceAndPath(MODID, "block/voidstone_bricks")), DEBUG_NAME);
    }

    public static Material[] getFrameSpriteIdentifiers()
    {
        return FRAME_SPRITE_IDENTIFIERS;
    }

    public static Material.Baked getCoreSprite()
    {
        return CORE_SPRITES[0];
    }

    public static Material.Baked getCoreOverlaySprite()
    {
        return CORE_SPRITES[3];
    }

    public static Material.Baked getCoreBrokenOverlaySprite()
    {
        return CORE_SPRITES[2];
    }

    public static Material.Baked getBackgroundSpriteForFront(int variant)
    {
        return FRAME_SPRITES[28 + variant];
    }

    public static Material.Baked getOverlaySpriteForFront(int variant)
    {
        return FRAME_SPRITES[30 + 28 + variant];
    }

    public static Material.Baked getOverlaySpriteForSide(FrameConnection left, FrameConnection right, FrameConnection up, FrameConnection down, int variant)
    {
        return getSpriteForSide(left, right, up, down, variant, 30);
    }

    public static Material.Baked getBackgroundSpriteForSide(FrameConnection left, FrameConnection right, FrameConnection up, FrameConnection down, int variant)
    {
        var sprite = getSpriteForSide(left, right, up, down, variant, 0);

        if (sprite == null)
            return VOIDBRICK_SPRITE;
        return sprite;
    }

    private static Material.Baked getSpriteForSide(FrameConnection left, FrameConnection right, FrameConnection up, FrameConnection down, int variant, int offset)
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
        return new Material(Identifier.fromNamespaceAndPath(MODID, "block/portal/" + from));
    }

    private static Material getCoreSpriteIdentifier(String from)
    {
        return new Material(Identifier.fromNamespaceAndPath(MODID, "block/portalcore/" + from));
    }
}