package net.voxelindustry.voidheart.common.energy.gnawer;

import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;

import static net.voxelindustry.voidheart.compat.rei.EnergyRei.line;
import static net.voxelindustry.voidheart.compat.rei.EnergyRei.page;

/**
 * REI hand-off for the Gnawer, called through EnergyRei from the client plugin. Only loaded when REI is present.
 */
public final class GnawerRei
{
    private GnawerRei()
    {
    }

    public static void registerCategories(CategoryRegistry registry)
    {
    }

    public static void registerDisplays(DisplayRegistry registry)
    {
        page(GnawerContent.GNAWER, "gnawer",
                line("gnawer.eats", GnawerBalance.MAX_WIDTH, GnawerBalance.RANGE),
                line("gnawer.cost", GnawerBalance.cost(1.5F, false, false), GnawerBalance.MAX_DRAW),
                line("gnawer.modes"),
                line("gnawer.skips"));
        page(GnawerContent.GNAWING_STAKE, "gnawer",
                line("gnawing_stake.marks"));
    }
}
