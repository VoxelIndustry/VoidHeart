package net.voxelindustry.voidheart.common.energy.grid;

import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;

import static net.voxelindustry.voidheart.compat.rei.EnergyRei.line;
import static net.voxelindustry.voidheart.compat.rei.EnergyRei.page;

/**
 * REI hand-off for W1 Void grid, called through EnergyRei from the client plugin. Only loaded when REI is present.
 */
public final class VoidGridRei
{
    private VoidGridRei()
    {
    }

    public static void registerCategories(CategoryRegistry registry)
    {
    }

    public static void registerDisplays(DisplayRegistry registry)
    {
        page(VoidGridContent.AZURIUM_PYLON, "void_energy",
                line("azurium_pylon.field", VoidGridBalance.SMALL_RADIUS, VoidGridBalance.MEDIUM_RADIUS, VoidGridBalance.LARGE_RADIUS),
                line("azurium_pylon.cap", VoidGridBalance.SMALL_CAP, VoidGridBalance.MEDIUM_CAP, VoidGridBalance.LARGE_CAP),
                line("azurium_pylon.arc"));
    }
}
