package net.voxelindustry.voidheart.common.content.armor;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A module set in one of the Void Armor's grids on its base.
 */
public class ArmorModuleItem extends Item
{
    private final ArmorModule module;

    public ArmorModuleItem(Properties properties, ArmorModule module)
    {
        super(properties.stacksTo(16));
        this.module = module;
    }

    public ArmorModule module()
    {
        return module;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        tooltip.accept(Component.translatable(MODID + ".armor_module." + module.getSerializedName() + ".gives").withStyle(ChatFormatting.AQUA));
        var support = module.restsOn();
        if (support != null)
            tooltip.accept(Component.translatable(MODID + ".armor_module.rests_on",
                    Component.translatable("item." + MODID + "." + support.itemName())).withStyle(ChatFormatting.GOLD));
        if (module.unique())
            tooltip.accept(Component.translatable(MODID + ".armor_module.unique").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable(MODID + ".armor_module.fits." + (module.grids().size() == ArmorGridKind.values().length
                ? "any" : module.grids().iterator().next().key())).withStyle(ChatFormatting.GRAY));

        var shape = module.shape();
        for (int y = 0; y < shape.height(); y++)
        {
            var row = new StringBuilder();
            for (int x = 0; x < shape.width(); x++)
            {
                int cellX = x;
                int cellY = y;
                row.append(shape.cells().stream().anyMatch(cell -> cell.x() == cellX && cell.y() == cellY) ? "■" : "□");
            }
            tooltip.accept(Component.literal(row.toString()).withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
