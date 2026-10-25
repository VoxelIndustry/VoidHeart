package net.voxelindustry.voidheart.client.model.conduit;

import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.sprite.Material;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

import net.minecraft.resources.Identifier;

public class ConduitSpriteManager
{
    private static final ModelDebugName DEBUG_NAME = () -> "voidheart:ConduitSpriteManager";

    private static final Material[] SPRITE_IDENTIFIERS = new Material[24];

    private static final Material.Baked[] SPRITES = new Material.Baked[24];

    static
    {
        for (int i = 0; i < 12; i++)
            SPRITE_IDENTIFIERS[i] = getSpriteIdentifier("conduit" + i);
        for (int i = 12; i < 24; i++)
            SPRITE_IDENTIFIERS[i] = getSpriteIdentifier("conduit_lit" + (i - 11));
    }

    public static void loadSprites(MaterialBaker materials)
    {
        for (int i = 0; i < 24; i++)
            SPRITES[i] = materials.get(SPRITE_IDENTIFIERS[i], DEBUG_NAME);
    }

    private static int getSpriteIndexFor(boolean up, boolean down, boolean left, boolean right)
    {
        if (up && down && left && right)
            return 5;

        if (up && down && left)
            return 6;
        if (up && down && right)
            return 4;
        if (left && right && down)
            return 1;
        if (left && right && up)
            return 9;

        if (up && down)
            return 7;
        if (left && right)
            return 3;

        if (up && left)
            return 10;
        if (up && right)
            return 8;
        if (down && left)
            return 2;
        if (down && right)
            return 0;
        return 11;
    }

    public static Material.Baked getSpriteFor(boolean up, boolean down, boolean left, boolean right, boolean lit)
    {
        return SPRITES[getSpriteIndexFor(up, down, left, right) + (lit ? 12 : 0)];
    }

    private static Material getSpriteIdentifier(String from)
    {
        return new Material(Identifier.fromNamespaceAndPath(MODID, "block/conduit/" + from));
    }
}