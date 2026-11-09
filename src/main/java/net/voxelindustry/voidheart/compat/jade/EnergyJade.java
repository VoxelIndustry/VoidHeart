package net.voxelindustry.voidheart.compat.jade;

import net.voxelindustry.voidheart.common.energy.engine.RavenousEngineJade;
import net.voxelindustry.voidheart.common.energy.grid.VoidGridJade;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneJade;
import net.voxelindustry.voidheart.common.energy.reactor.ContravoidReactorJade;
import net.voxelindustry.voidheart.common.energy.siphon.DeepSiphonJade;
import net.voxelindustry.voidheart.common.energy.spike.LeySpikeJade;
import net.voxelindustry.voidheart.common.energy.storage.RubrumJade;
import net.voxelindustry.voidheart.common.energy.tear.VoidTearJade;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;

/**
 * VoidHeartJadePlugin's one call into the energy system, handing off to each package.
 */
public final class EnergyJade
{
    private EnergyJade()
    {
    }

    public static void register(IWailaCommonRegistration registration)
    {
        VoidGridJade.register(registration);
        RubrumJade.register(registration);
        LeyStoneJade.register(registration);
        RavenousEngineJade.register(registration);
        LeySpikeJade.register(registration);
        VoidTearJade.register(registration);
        ContravoidReactorJade.register(registration);
        DeepSiphonJade.register(registration);
    }

    public static void registerClient(IWailaClientRegistration registration)
    {
        VoidGridJade.registerClient(registration);
        RubrumJade.registerClient(registration);
        LeyStoneJade.registerClient(registration);
        RavenousEngineJade.registerClient(registration);
        LeySpikeJade.registerClient(registration);
        VoidTearJade.registerClient(registration);
        ContravoidReactorJade.registerClient(registration);
        DeepSiphonJade.registerClient(registration);
    }
}
