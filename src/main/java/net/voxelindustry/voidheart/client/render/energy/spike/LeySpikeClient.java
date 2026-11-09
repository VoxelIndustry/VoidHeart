package net.voxelindustry.voidheart.client.render.energy.spike;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.voxelindustry.voidheart.common.energy.spike.LeySpikeContent;

/**
 * Client startup registrar for W8 Spike, called once by EnergyClientSetup: renderers, model layers and client payload handlers.
 */
public final class LeySpikeClient
{
    private LeySpikeClient()
    {
    }

    public static void register()
    {
        BlockEntityRenderers.register(LeySpikeContent.AZURIUM_SPIKE_TILE, AzuriumSpikeRender::new);
    }
}
