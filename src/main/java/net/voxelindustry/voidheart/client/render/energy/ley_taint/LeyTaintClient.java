package net.voxelindustry.voidheart.client.render.energy.ley_taint;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.voxelindustry.voidheart.common.ley.taint.LeyTaintPayload;

/**
 * Client registrar for W3 Ley flow and taint: the tainted nodes from the server, the veins on the ground and the Old Anchor
 * flicker. The Oculus ley layer reads {@link LeyTaintView} itself.
 */
public final class LeyTaintClient
{
    private static boolean registered;

    private LeyTaintClient()
    {
    }

    public static void register()
    {
        if (registered)
            return;
        registered = true;

        ClientPlayNetworking.registerGlobalReceiver(LeyTaintPayload.TYPE, (payload, context) -> LeyTaintView.receive(payload));
        ClientTickEvents.END_CLIENT_TICK.register(client ->
        {
            if (client.level != null && !client.isPaused())
                LeyTaintView.tick();
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(LeyTaintGroundRender::collectSubmits);
        LevelRenderEvents.COLLECT_SUBMITS.register(OldAnchorFlickerRender::collectSubmits);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
        {
            LeyTaintView.clear();
            LeyTaintGroundRender.clear();
            OldAnchorFlickerRender.clear();
        });
    }
}
