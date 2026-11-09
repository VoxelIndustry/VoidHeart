package net.voxelindustry.voidheart.common.energy;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.voxelindustry.voidheart.common.energy.creative.CreativeEnergyContent;
import net.voxelindustry.voidheart.common.energy.creative.CreativeSources;
import net.voxelindustry.voidheart.common.energy.engine.RavenousEngineContent;
import net.voxelindustry.voidheart.common.energy.grid.VoidGridContent;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneContent;
import net.voxelindustry.voidheart.common.energy.reactor.ContravoidReactorContent;
import net.voxelindustry.voidheart.common.energy.siphon.DeepSiphonContent;
import net.voxelindustry.voidheart.common.energy.spike.LeySpikeContent;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import net.voxelindustry.voidheart.common.energy.tear.VoidTearContent;
import net.voxelindustry.voidheart.common.ley.flow.LeyFlowContent;

/**
 * The energy system's one startup call from VoidHeart, handing off to each package's own registrar so no two packages edit the same file.
 */
public final class EnergySetup
{
    private EnergySetup()
    {
    }

    public static void register()
    {
        CreativeEnergyContent.register();

        VoidGridContent.register();
        RubrumContent.register();
        LeyFlowContent.register();
        LeyStoneContent.register();
        RavenousEngineContent.register();
        LeySpikeContent.register();
        VoidTearContent.register();
        ContravoidReactorContent.register();
        DeepSiphonContent.register();

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CreativeSources.clear());
    }
}
