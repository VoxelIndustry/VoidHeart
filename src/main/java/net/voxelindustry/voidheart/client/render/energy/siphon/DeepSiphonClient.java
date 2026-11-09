package net.voxelindustry.voidheart.client.render.energy.siphon;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.voxelindustry.voidheart.common.energy.siphon.DeepSiphonContent;

/**
 * Client startup registrar for W11 Deep siphon, called once by EnergyClientSetup: the siphon's threads and crown, and the arcs at the armillary it delivers
 * through.
 */
public final class DeepSiphonClient
{
    private DeepSiphonClient()
    {
    }

    public static void register()
    {
        BlockEntityRenderers.register(DeepSiphonContent.DEEP_SIPHON_TILE, DeepSiphonRender::new);

        ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register(SiphonArcRender::onLoad);
        ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register(SiphonArcRender::onUnload);
        ClientTickEvents.END_CLIENT_TICK.register(SiphonArcRender::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(SiphonArcRender::collectSubmits);
    }
}
