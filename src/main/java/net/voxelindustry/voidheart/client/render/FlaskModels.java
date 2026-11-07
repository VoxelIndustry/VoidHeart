package net.voxelindustry.voidheart.client.render;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The stack to draw for a flask set down in the world. The mod's flasks carry their 3D models in their own item models, but the vanilla glass bottle
 * is pointed at one of the mod's so vanilla's own model stays as it is.
 */
public final class FlaskModels
{
    private static final Identifier GLASS_BOTTLE_3D = Identifier.fromNamespaceAndPath(MODID, "glass_bottle_3d");

    private FlaskModels()
    {
    }

    public static ItemStack forDisplay(ItemStack stack)
    {
        if (!stack.is(Items.GLASS_BOTTLE))
            return stack;
        var display = stack.copy();
        display.set(DataComponents.ITEM_MODEL, GLASS_BOTTLE_3D);
        return display;
    }
}
