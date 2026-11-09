package net.voxelindustry.voidheart.client.render.energy.grid;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/**
 * Client startup registrar for W1 Void grid, called once by EnergyClientSetup: the grid's arcs, glow and fields, drawn for the whole level at once.
 */
public final class VoidGridClient
{
    private VoidGridClient()
    {
    }

    public static void register()
    {
        ClientTickEvents.END_CLIENT_TICK.register(VoidGridRender::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(VoidGridRender::collectSubmits);
    }
}
