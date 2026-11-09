package net.voxelindustry.voidheart.client.render.energy;

import net.voxelindustry.voidheart.client.render.energy.engine.RavenousEngineClient;
import net.voxelindustry.voidheart.client.render.energy.grid.VoidGridClient;
import net.voxelindustry.voidheart.client.render.energy.ley.LeyStoneClient;
import net.voxelindustry.voidheart.client.render.energy.ley_taint.LeyTaintClient;
import net.voxelindustry.voidheart.client.render.energy.reactor.ContravoidReactorClient;
import net.voxelindustry.voidheart.client.render.energy.siphon.DeepSiphonClient;
import net.voxelindustry.voidheart.client.render.energy.spike.LeySpikeClient;
import net.voxelindustry.voidheart.client.render.energy.storage.RubrumClient;
import net.voxelindustry.voidheart.client.render.energy.fissure.VoidFissureClient;

/**
 * The energy system's one client startup call from VoidHeartClient, handing off to each package's client registrar.
 */
public final class EnergyClientSetup
{
    private EnergyClientSetup()
    {
    }

    public static void register()
    {
        VoidGridClient.register();
        RubrumClient.register();
        LeyTaintClient.register();
        LeyStoneClient.register();
        RavenousEngineClient.register();
        LeySpikeClient.register();
        VoidFissureClient.register();
        ContravoidReactorClient.register();
        DeepSiphonClient.register();
    }
}
