package net.voxelindustry.voidheart.client.model.monolith;

import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.sprite.Material;
import java.text.NumberFormat;
import net.minecraft.resources.Identifier;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidMonolithSpriteManager
{
    private static final ModelDebugName DEBUG_NAME = () -> "voidheart:VoidMonolithSpriteManager";

    private static final Material[] SPRITE_IDENTIFIERS = new Material[24];
    private static final Material BROKEN_SPRITE_IDENTIFIER = getSpriteIdentifier("broken_overlay");

    private static final Material.Baked[] SPRITES = new Material.Baked[24];
    public static Material.Baked BROKEN_SPRITE;

    static
    {
        var format = NumberFormat.getInstance();
        format.setMinimumIntegerDigits(2);

        for (int i = 0; i < 24; i++)
        {
            SPRITE_IDENTIFIERS[i] = getSpriteIdentifier("monolith" + format.format(i));
        }
    }

    public static void loadSprites(MaterialBaker materials)
    {
        for (int i = 0; i < 24; i++)
            SPRITES[i] = materials.get(SPRITE_IDENTIFIERS[i], DEBUG_NAME);
        BROKEN_SPRITE = materials.get(BROKEN_SPRITE_IDENTIFIER, DEBUG_NAME);
    }

    public static Material.Baked getTopSprite(int variant)
    {
        return SPRITES[variant];
    }

    public static Material.Baked getMiddleSprite(int variant)
    {
        return SPRITES[variant + 3];
    }

    public static Material.Baked getBottomSprite(int variant)
    {
        return SPRITES[variant + 6];
    }

    public static Material.Baked getAboveSprite(int variant)
    {
        return SPRITES[variant + 9];
    }

    public static Material.Baked getTopOverlaySprite(int variant)
    {
        return SPRITES[variant + 12];
    }

    public static Material.Baked getMiddleOverlaySprite(int variant)
    {
        return SPRITES[variant + 15];
    }

    public static Material.Baked getBottomOverlaySprite(int variant)
    {
        return SPRITES[variant + 18];
    }

    public static Material.Baked getAboveOverlaySprite(int variant)
    {
        return SPRITES[variant + 21];
    }

    public static Material.Baked getBrokenSprite()
    {
        return BROKEN_SPRITE;
    }

    private static Material getSpriteIdentifier(String from)
    {
        return new Material(Identifier.fromNamespaceAndPath(MODID, "block/monolith/" + from));
    }
}