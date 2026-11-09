package net.voxelindustry.voidheart.client.render.energy.engine;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.voxelindustry.voidheart.common.energy.engine.RavenousEngineContent;

/**
 * Client startup registrar for W5 Ravenous engine, called once by EnergyClientSetup: renderers, model layers and client payload handlers.
 */
public final class RavenousEngineClient
{
    private RavenousEngineClient()
    {
    }

    public static void register()
    {
        BlockEntityRenderers.register(RavenousEngineContent.RAVENOUS_ENGINE_TILE, RavenousEngineRender::new);
    }
}
