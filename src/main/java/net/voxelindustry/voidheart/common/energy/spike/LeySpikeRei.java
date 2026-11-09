package net.voxelindustry.voidheart.common.energy.spike;

import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;

import static net.voxelindustry.voidheart.compat.rei.EnergyRei.line;
import static net.voxelindustry.voidheart.compat.rei.EnergyRei.page;

/**
 * REI hand-off for W8 Spike, called through EnergyRei from the client plugin. Only loaded when REI is present.
 */
public final class LeySpikeRei
{
    private LeySpikeRei()
    {
    }

    public static void registerCategories(CategoryRegistry registry)
    {
    }

    public static void registerDisplays(DisplayRegistry registry)
    {
        page(LeySpikeContent.AZURIUM_SPIKE, "ley_conversion",
                line("azurium_spike.bleed", LeySpikeBalance.VE_PER_LEY, Math.round(LeySpikeBalance.MAX_LEY)),
                line("azurium_spike.share"),
                line("azurium_spike.taint"),
                line("azurium_spike.contravoid", LeySpikeBalance.CONTRAVOID_RANGE));
    }
}
