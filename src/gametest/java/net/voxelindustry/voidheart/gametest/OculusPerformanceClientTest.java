package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.voxelindustry.voidheart.client.render.oculus.OculusRenderer;
import net.voxelindustry.voidheart.common.content.oculus.OculusTableStructure;
import net.voxelindustry.voidheart.common.content.oculus.OculusView;

/**
 * Measures a table of the largest size over generated terrain at every zoom: how long the hologram takes to fill,
 * what each chunk costs to build and what a frame costs once it is built. Results are written to the log.
 */
public class OculusPerformanceClientTest implements FabricClientGameTest
{
    private static final int SIZE = 8;
    private static final int SETTLE_TIMEOUT = 1200;
    private static final int STEADY_TICKS = 100;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> settings.setSeed("oculus"))
                .create())
        {
            var server = world.getServer();
            var connection = world.getConnection();
            connection.waitForChunksRender();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode spectator @a");

            // The spawn is on land and already loaded, unlike an arbitrary spot whose height would read as the world bottom
            var spawn = server.computeOnServer(minecraft -> minecraft.getPlayerList().getPlayers().getFirst().blockPosition());
            int surface = server.computeOnServer(minecraft -> minecraft.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ()));
            int x = spawn.getX(), z = spawn.getZ();
            int tableY = surface + 4;
            var table = new BlockPos(x, tableY, z);
            server.runCommand("tp @a " + (x + 0.5) + " " + (tableY + 9) + " " + (z + SIZE + 2) + " 180 52");
            context.waitTicks(20);
            connection.waitForChunksRender();

            OculusRenderer.takeStats();
            server.runCommand("fill " + (x - SIZE / 2) + " " + tableY + " " + (z - SIZE / 2) + " " + (x + SIZE / 2 - 1) + " " + tableY + " " + (z + SIZE / 2 - 1) + " voidheart:oculus_table");

            for (int zoom = 0; zoom < OculusView.ZOOM_STEPS; zoom++)
            {
                if (zoom > 0)
                    server.runOnServer(minecraft -> OculusTableStructure.updateView(minecraft.overworld(), table, OculusView::zoomIn));
                context.waitTicks(2);

                int ticks;
                try
                {
                    ticks = context.waitFor(minecraft -> OculusRenderer.isSettled(), SETTLE_TIMEOUT);
                } catch (AssertionError e)
                {
                    log(context, "never settled: " + OculusRenderer.summary());
                    throw e;
                }
                int scale = new OculusView(zoom, java.util.Optional.empty(), false).scale();
                log(context, "1:" + scale + " filled in " + ticks + " ticks | " + OculusRenderer.summary());
                log(context, "1:" + scale + " while filling: " + OculusRenderer.takeStats());

                context.waitTicks(STEADY_TICKS);
                log(context, "1:" + scale + " built, steady: " + OculusRenderer.takeStats());
                context.takeScreenshot("oculus-performance-1-" + scale);
            }

            // Following the viewer drags the view along, measured while flying across the land
            server.runOnServer(minecraft -> OculusTableStructure.updateView(minecraft.overworld(), table, view -> view.zoomOut().zoomOut()));
            context.waitFor(minecraft -> OculusRenderer.isSettled(), SETTLE_TIMEOUT);
            OculusRenderer.takeStats();
            server.runOnServer(minecraft ->
            {
                var player = minecraft.getPlayerList().getPlayers().getFirst();
                OculusTableStructure.updateView(minecraft.overworld(), table, view -> view.following(java.util.Optional.of(player.getUUID())));
            });
            for (int step = 1; step <= 10; step++)
            {
                server.runCommand("tp @a " + (x + step * 6 + 0.5) + " " + (tableY + 6) + " " + (z + SIZE + 3) + " 180 35");
                context.waitTicks(10);
            }
            log(context, "1:8 following a moving player: " + OculusRenderer.takeStats() + " | " + OculusRenderer.summary());
        }
    }

    private static void log(ClientGameTestContext context, String line)
    {
        context.runOnClient(minecraft -> System.out.println("[Oculus performance] " + line));
    }
}
