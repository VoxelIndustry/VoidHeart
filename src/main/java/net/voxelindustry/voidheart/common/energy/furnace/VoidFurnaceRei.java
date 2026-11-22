package net.voxelindustry.voidheart.common.energy.furnace;

import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.plugin.common.BuiltinPlugin;

import static net.voxelindustry.voidheart.compat.rei.EnergyRei.line;
import static net.voxelindustry.voidheart.compat.rei.EnergyRei.page;

/**
 * REI hand-off for the Void Furnace, called through EnergyRei from the client plugin: it shows as a workstation of the smelting recipes, with a page
 * on what it burns. Only loaded when REI is present.
 */
public final class VoidFurnaceRei
{
    private VoidFurnaceRei()
    {
    }

    public static void registerCategories(CategoryRegistry registry)
    {
        registry.addWorkstations(BuiltinPlugin.SMELTING, EntryStacks.of(VoidFurnaceContent.VOID_FURNACE));
    }

    public static void registerDisplays(DisplayRegistry registry)
    {
        page(VoidFurnaceContent.VOID_FURNACE, "void_furnace",
                line("void_furnace.smelts", VoidFurnaceBalance.paceText(VoidFurnaceBalance.COLD_SPEED), VoidFurnaceBalance.paceText(VoidFurnaceBalance.HOT_SPEED),
                        VoidFurnaceBalance.HEAT_UP_TICKS / 20, VoidFurnaceBalance.COOL_TICKS / 20),
                line("void_furnace.energy", VoidFurnaceBalance.paceText(VoidFurnaceBalance.DRAW_PER_PACE),
                        VoidFurnaceBalance.paceText(VoidFurnaceBalance.DRAW_PER_PACE), Math.round(VoidFurnaceBalance.itemEnergy(200, false))),
                line("void_furnace.fiery", VoidFurnaceBalance.FIERY_PER_ITEM, VoidFurnaceBalance.paceText(VoidFurnaceBalance.FIERY_FACTOR),
                        VoidFurnaceBalance.paceText(VoidFurnaceBalance.pace(1, true)), VoidFurnaceBalance.paceText(VoidFurnaceBalance.FIERY_DRAW_PER_PACE)),
                line("void_furnace.hoppers"),
                line("void_furnace.officiant"));
    }
}
