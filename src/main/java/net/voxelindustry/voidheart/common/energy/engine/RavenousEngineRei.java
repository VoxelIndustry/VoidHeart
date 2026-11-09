package net.voxelindustry.voidheart.common.energy.engine;

import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;

import static net.voxelindustry.voidheart.compat.rei.EnergyRei.amount;
import static net.voxelindustry.voidheart.compat.rei.EnergyRei.line;
import static net.voxelindustry.voidheart.compat.rei.EnergyRei.page;

/**
 * REI hand-off for W5 Ravenous engine, called through EnergyRei from the client plugin. Only loaded when REI is present.
 */
public final class RavenousEngineRei
{
    private RavenousEngineRei()
    {
    }

    public static void registerCategories(CategoryRegistry registry)
    {
    }

    public static void registerDisplays(DisplayRegistry registry)
    {
        page(RavenousEngineContent.RAVENOUS_ENGINE, "void_energy",
                line("ravenous_engine.burn", amount(RavenousEngineBalance.TANK)),
                line("ravenous_engine.rates",
                        Math.round(RavenousEngineBalance.STILLNESS_DENSITY * RavenousEngineBalance.STILLNESS_BURN),
                        Math.round(RavenousEngineBalance.FIERY_DENSITY * RavenousEngineBalance.FIERY_BURN),
                        Math.round(RavenousEngineBalance.SUFFERING_DENSITY * RavenousEngineBalance.SUFFERING_BURN)),
                line("ravenous_engine.waste"));
    }
}
