package net.voxelindustry.voidheart.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Square button showing an item icon, framed in green while its option is on.
 */
public class IconToggleButton extends Button
{
    public static final int SIZE = 20;

    private static final int ON_FRAME  = 0xFF3FD06A;
    private static final int OFF_SHADE = 0x60000000;

    private final ItemStack            icon;
    private final @Nullable Identifier offSprite;
    private final BooleanSupplier      selected;

    public IconToggleButton(int x, int y, ItemStack icon, Component label, BooleanSupplier selected, OnPress onPress)
    {
        this(x, y, icon, null, label, selected, onPress);
    }

    /**
     * @param offSprite block atlas sprite drawn instead of the icon while the option is off
     */
    public IconToggleButton(int x, int y, ItemStack icon, @Nullable Identifier offSprite, Component label, BooleanSupplier selected, OnPress onPress)
    {
        super(x, y, SIZE, SIZE, label, onPress, DEFAULT_NARRATION);
        this.icon = icon;
        this.offSprite = offSprite;
        this.selected = selected;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a)
    {
        extractDefaultSprite(graphics);
        if (offSprite != null && !selected.getAsBoolean())
        {
            var sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(offSprite);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, getX() + 2, getY() + 2, 16, 16);
        }
        else
            graphics.item(icon, getX() + 2, getY() + 2);

        if (selected.getAsBoolean())
        {
            graphics.outline(getX(), getY(), width, height, ON_FRAME);
            graphics.outline(getX() + 1, getY() + 1, width - 2, height - 2, ON_FRAME);
        }
        else
            graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, OFF_SHADE);
    }
}
