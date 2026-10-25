package net.voxelindustry.voidheart.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;

/**
 * Access to HUD sprites so they can be drawn inside the world.
 */
public class GuiSprites
{
    public static final Identifier HEART_CONTAINER = Identifier.withDefaultNamespace("hud/heart/container");
    public static final Identifier HEART_FULL      = Identifier.withDefaultNamespace("hud/heart/full");
    public static final Identifier HEART_HALF      = Identifier.withDefaultNamespace("hud/heart/half");

    public static final Identifier EXPERIENCE_BAR_BACKGROUND = Identifier.withDefaultNamespace("hud/experience_bar_background");
    public static final Identifier EXPERIENCE_BAR_PROGRESS   = Identifier.withDefaultNamespace("hud/experience_bar_progress");

    private static TextureAtlas atlas()
    {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.GUI);
    }

    public static TextureAtlasSprite get(Identifier sprite)
    {
        return atlas().getSprite(sprite);
    }

    public static RenderType renderType()
    {
        return RenderTypes.entityTranslucent(atlas().location());
    }
}
