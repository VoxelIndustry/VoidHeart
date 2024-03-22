package net.voxelindustry.voidheart.client.model.monolith;

import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;

import java.text.NumberFormat;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidMonolithSpriteManager
{
    private static final SpriteIdentifier[] SPRITE_IDENTIFIERS = new SpriteIdentifier[24];
    private static final SpriteIdentifier BROKEN_SPRITE_IDENTIFIER = getSpriteIdentifier("broken_overlay");

    private static final Sprite[] SPRITES = new Sprite[24];
    public static Sprite BROKEN_SPRITE;

    static
    {
        var format = NumberFormat.getInstance();
        format.setMinimumIntegerDigits(2);

        for (int i = 0; i < 24; i++)
        {
            SPRITE_IDENTIFIERS[i] = getSpriteIdentifier("monolith" + format.format(i));
        }
    }

    public static void loadSprites()
    {
        for (int i = 0; i < 24; i++)
            SPRITES[i] = SPRITE_IDENTIFIERS[i].getSprite();
        BROKEN_SPRITE = BROKEN_SPRITE_IDENTIFIER.getSprite();
    }

    public static Sprite getTopSprite(int variant)
    {
        return SPRITES[variant];
    }

    public static Sprite getMiddleSprite(int variant)
    {
        return SPRITES[variant + 3];
    }

    public static Sprite getBottomSprite(int variant)
    {
        return SPRITES[variant + 6];
    }

    public static Sprite getAboveSprite(int variant)
    {
        return SPRITES[variant + 9];
    }

    public static Sprite getTopOverlaySprite(int variant)
    {
        return SPRITES[variant + 12];
    }

    public static Sprite getMiddleOverlaySprite(int variant)
    {
        return SPRITES[variant + 15];
    }

    public static Sprite getBottomOverlaySprite(int variant)
    {
        return SPRITES[variant + 18];
    }

    public static Sprite getAboveOverlaySprite(int variant)
    {
        return SPRITES[variant + 21];
    }

    public static Sprite getBrokenSprite()
    {
        return BROKEN_SPRITE;
    }

    private static SpriteIdentifier getSpriteIdentifier(String from)
    {
        return new SpriteIdentifier(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, new Identifier(MODID, "block/monolith/" + from));
    }
}