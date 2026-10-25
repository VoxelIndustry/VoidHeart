package net.voxelindustry.voidheart.client.model.monolith;

import java.text.NumberFormat;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidMonolithSpriteManager
{
    private static final Material[] SPRITE_IDENTIFIERS = new Material[24];
    private static final Material BROKEN_SPRITE_IDENTIFIER = getSpriteIdentifier("broken_overlay");

    private static final TextureAtlasSprite[] SPRITES = new TextureAtlasSprite[24];
    public static TextureAtlasSprite BROKEN_SPRITE;

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
            SPRITES[i] = SPRITE_IDENTIFIERS[i].sprite();
        BROKEN_SPRITE = BROKEN_SPRITE_IDENTIFIER.sprite();
    }

    public static TextureAtlasSprite getTopSprite(int variant)
    {
        return SPRITES[variant];
    }

    public static TextureAtlasSprite getMiddleSprite(int variant)
    {
        return SPRITES[variant + 3];
    }

    public static TextureAtlasSprite getBottomSprite(int variant)
    {
        return SPRITES[variant + 6];
    }

    public static TextureAtlasSprite getAboveSprite(int variant)
    {
        return SPRITES[variant + 9];
    }

    public static TextureAtlasSprite getTopOverlaySprite(int variant)
    {
        return SPRITES[variant + 12];
    }

    public static TextureAtlasSprite getMiddleOverlaySprite(int variant)
    {
        return SPRITES[variant + 15];
    }

    public static TextureAtlasSprite getBottomOverlaySprite(int variant)
    {
        return SPRITES[variant + 18];
    }

    public static TextureAtlasSprite getAboveOverlaySprite(int variant)
    {
        return SPRITES[variant + 21];
    }

    public static TextureAtlasSprite getBrokenSprite()
    {
        return BROKEN_SPRITE;
    }

    private static Material getSpriteIdentifier(String from)
    {
        return new Material(InventoryMenu.BLOCK_ATLAS, new ResourceLocation(MODID, "block/monolith/" + from));
    }
}