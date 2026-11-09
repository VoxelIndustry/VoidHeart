package net.voxelindustry.voidheart.client.render.energy.fissure;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.voxelindustry.voidheart.common.energy.fissure.VoidFissureContent;

/**
 * Client startup registrar for W9 Void Fissure, called once by EnergyClientSetup: the crack's renderer and the seep drawn across the land.
 */
public final class VoidFissureClient
{
    private VoidFissureClient()
    {
    }

    public static void register()
    {
        BlockEntityRenderers.register(VoidFissureContent.VOID_FISSURE_TILE, VoidFissureRender::new);

        ClientTickEvents.END_CLIENT_TICK.register(SeepEffects::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(SeepEffects::collectSubmits);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SeepEffects.clear());
    }
}
