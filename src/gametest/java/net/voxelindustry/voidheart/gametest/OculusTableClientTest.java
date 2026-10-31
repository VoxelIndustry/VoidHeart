package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.voxelindustry.voidheart.common.content.oculus.OculusTableStructure;
import net.voxelindustry.voidheart.common.content.oculus.OculusView;

/**
 * Builds recognizable land around a table and captures its hologram from a few angles.
 * Screenshots land in the client game test run directory.
 */
public class OculusTableClientTest implements FabricClientGameTest
{
    private static final int GROUND = -61;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            var connection = world.getConnection();
            connection.waitForChunksRender();

            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");

            // A stepped stone pyramid west, a pond east and a gold tower north, all within reach of a 3x3 table at 1:32
            for (int step = 0; step < 6; step++)
                server.runCommand("fill " + (-30 + step) + " " + (GROUND + 1 + step) + " " + (-6 + step) + " " + (-18 - step) + " " + (GROUND + 1 + step) + " " + (6 - step) + " stone");
            server.runCommand("fill 16 " + GROUND + " -8 32 " + GROUND + " 8 water");
            server.runCommand("fill -2 " + (GROUND + 1) + " -36 2 " + (GROUND + 12) + " -32 gold_block");

            server.runCommand("fill -1 " + (GROUND + 1) + " -1 1 " + (GROUND + 1) + " 1 voidheart:oculus_table");
            server.runCommand("gamemode creative @a");
            server.runCommand("tp @a 0.5 " + (GROUND + 5) + " 5.5 180 35");
            context.runOnClient(minecraft -> minecraft.player.getAbilities().flying = true);

            context.waitTicks(60);
            connection.waitForChunksRender();
            context.takeScreenshot("oculus-overview-1-32");

            server.runCommand("tp @a 0.5 " + (GROUND + 8) + " 0.5 180 89");
            context.waitTicks(10);
            context.takeScreenshot("oculus-top-1-32");

            server.runOnServer(minecraft -> OculusTableStructure.updateView(minecraft.overworld(), new BlockPos(0, GROUND + 1, 0), OculusView::zoomIn));
            server.runCommand("tp @a 0.5 " + (GROUND + 5) + " 5.5 180 35");
            context.waitTicks(60);
            context.takeScreenshot("oculus-overview-1-16");

            server.runCommand("tp @a 4.5 " + (GROUND + 3) + " 0.5 90 10");
            context.waitTicks(20);
            context.takeScreenshot("oculus-side-1-16");
        }
    }
}
