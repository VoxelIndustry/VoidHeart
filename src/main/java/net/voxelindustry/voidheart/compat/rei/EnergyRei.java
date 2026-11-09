package net.voxelindustry.voidheart.compat.rei;

import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import net.voxelindustry.voidheart.common.energy.engine.RavenousEngineRei;
import net.voxelindustry.voidheart.common.energy.grid.VoidGridRei;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneRei;
import net.voxelindustry.voidheart.common.energy.reactor.ContravoidReactorRei;
import net.voxelindustry.voidheart.common.energy.siphon.DeepSiphonRei;
import net.voxelindustry.voidheart.common.energy.spike.LeySpikeRei;
import net.voxelindustry.voidheart.common.energy.storage.RubrumRei;
import net.voxelindustry.voidheart.common.energy.tear.VoidTearRei;

/**
 * VoidHeartREIPlugin's one call into the energy system, handing off to each package.
 */
public final class EnergyRei
{
    private EnergyRei()
    {
    }

    public static void registerCategories(CategoryRegistry registry)
    {
        VoidGridRei.registerCategories(registry);
        RubrumRei.registerCategories(registry);
        LeyStoneRei.registerCategories(registry);
        RavenousEngineRei.registerCategories(registry);
        LeySpikeRei.registerCategories(registry);
        VoidTearRei.registerCategories(registry);
        ContravoidReactorRei.registerCategories(registry);
        DeepSiphonRei.registerCategories(registry);
    }

    public static void registerDisplays(DisplayRegistry registry)
    {
        VoidGridRei.registerDisplays(registry);
        RubrumRei.registerDisplays(registry);
        LeyStoneRei.registerDisplays(registry);
        RavenousEngineRei.registerDisplays(registry);
        LeySpikeRei.registerDisplays(registry);
        VoidTearRei.registerDisplays(registry);
        ContravoidReactorRei.registerDisplays(registry);
        DeepSiphonRei.registerDisplays(registry);
    }
}
