package net.voxelindustry.voidheart.common.ley.flow;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.client.render.energy.ley_taint.LeyTaintClient;
import net.voxelindustry.voidheart.common.ley.Ley;
import net.voxelindustry.voidheart.common.ley.taint.LeyTaint;
import net.voxelindustry.voidheart.common.ley.taint.LeyTaintPayload;
import net.voxelindustry.voidheart.common.ley.taint.LeyTaintSync;

/**
 * Startup registrar for W3 Ley flow and taint, called once by EnergySetup: installs the real flow, runs taint on the overworld
 * and tells clients which nodes are tainted.
 */
public final class LeyFlowContent
{
    private LeyFlowContent()
    {
    }

    public static void register()
    {
        Ley.install(new LatticeLeyFlow());
        PayloadTypeRegistry.clientboundPlay().register(LeyTaintPayload.TYPE, LeyTaintPayload.CODEC);

        ServerTickEvents.END_LEVEL_TICK.register(level ->
        {
            if (level.dimension() == Level.OVERWORLD)
                LeyTaint.tick(level);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LeyTaintSync.forget(handler.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
        {
            LatticeLeyFlow.clear();
            LeyTaint.clear();
            LeyTaintSync.clear();
            LeyTopology.clear();
        });

        // EnergyClientSetup has no W3 hook yet, so the client side registers from here until I1 moves this call there
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT)
            LeyTaintClient.register();
    }
}
