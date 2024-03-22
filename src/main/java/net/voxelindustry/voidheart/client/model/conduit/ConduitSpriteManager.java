package net.voxelindustry.voidheart.client.model.conduit;

import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class ConduitSpriteManager
{
    private static final SpriteIdentifier[] SPRITE_IDENTIFIERS = new SpriteIdentifier[24];

    private static final Sprite[] SPRITES = new Sprite[24];

    static
    {
        for (int i = 0; i < 12; i++)
            SPRITE_IDENTIFIERS[i] = getSpriteIdentifier("conduit" + i);
        for (int i = 12; i < 24; i++)
            SPRITE_IDENTIFIERS[i] = getSpriteIdentifier("conduit_lit" + (i - 11));
    }

    public static void loadSprites()
    {
        for (int i = 0; i < 24; i++)
            SPRITES[i] = SPRITE_IDENTIFIERS[i].getSprite();
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

    public static Sprite getSpriteFor(boolean up, boolean down, boolean left, boolean right, boolean lit)
    {
        return SPRITES[getSpriteIndexFor(up, down, left, right) + (lit ? 12 : 0)];
    }


    private static SpriteIdentifier getSpriteIdentifier(String from)
    {
        return new SpriteIdentifier(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, new Identifier(MODID, "block/conduit/" + from));
    }
}