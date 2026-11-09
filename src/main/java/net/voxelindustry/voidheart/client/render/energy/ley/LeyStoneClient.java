package net.voxelindustry.voidheart.client.render.energy.ley;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneContent;
import net.voxelindustry.voidheart.common.energy.ley.LeyStonePayloads;

/**
 * Client startup registrar for W4 Standing stones and wards, called once by EnergyClientSetup: renderers, model layers and client payload handlers.
 */
public final class LeyStoneClient
{
    private LeyStoneClient()
    {
    }

    public static void register()
    {
        BlockEntityRenderers.register(LeyStoneContent.LEY_STONE_TILE, LeyStoneRender::new);
        BlockEntityRenderers.register(LeyStoneContent.LEY_WARD_TILE, LeyWardRender::new);
        BlockEntityRenderers.register(LeyStoneContent.LEY_GROWER_TILE, LeyGrowerRender::new);

        ClientPlayNetworking.registerGlobalReceiver(LeyStonePayloads.WardHold.TYPE, (payload, context) -> LeyBursts.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(LeyStonePayloads.GrowerGlint.TYPE, (payload, context) -> LeyBursts.receive(payload));

        ClientTickEvents.END_CLIENT_TICK.register(LeyFieldRender::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(LeyFieldRender::collectSubmits);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LeyFieldRender.clear());
    }
}
