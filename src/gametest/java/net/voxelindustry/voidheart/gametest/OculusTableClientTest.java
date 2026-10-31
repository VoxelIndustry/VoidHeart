package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.core.BlockPos;
import net.voxelindustry.voidheart.client.render.oculus.OculusRenderer;
import net.voxelindustry.voidheart.common.content.oculus.OculusTableStructure;
import net.voxelindustry.voidheart.common.content.oculus.OculusView;

/**
 * Builds recognizable land around a table and captures its hologram from a few angles.
 * Screenshots land in the client game test run directory.
 */
public class OculusTableClientTest implements FabricClientGameTest
{
    private static final int GROUND = -61;
    private static final BlockPos TABLE = new BlockPos(0, GROUND + 1, 0);

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
            buildLand(server);

            server.runCommand("fill -1 " + TABLE.getY() + " -1 1 " + TABLE.getY() + " 1 voidheart:oculus_table");
            server.runCommand("fill 2 " + TABLE.getY() + " -1 2 " + TABLE.getY() + " -1 voidheart:oculus_table");
            // Spectators float where they are teleported and have no hand or hotbar in the shots
            server.runCommand("gamemode spectator @a");

            context.waitTicks(80);
            connection.waitForChunksRender();
            view(context, server, "oculus-angled-1-32", 1, GROUND + 4, 5, 180, 40);
            view(context, server, "oculus-sky-noon", 1, GROUND + 3, 7, 180, 10);
            server.runCommand("time set 20000");
            view(context, server, "oculus-sky-night", 1, GROUND + 3, 7, 180, 10);
            server.runCommand("time set noon");
            view(context, server, "oculus-top-1-32", 1, GROUND + 9, 0.5, 180, 90);

            // A red slab dropped into the projected land must show up once its chunk is rebuilt
            server.runCommand("fill 24 " + (GROUND + 1) + " 20 40 " + (GROUND + 3) + " 36 red_wool");
            context.waitTicks(30);
            context.takeScreenshot("oculus-top-1-32-changed");

            // Powering any block of the table puts it to sleep, unpowering wakes it
            var powerPos = TABLE.east().east().south();
            server.runCommand("setblock " + powerPos.getX() + " " + powerPos.getY() + " " + powerPos.getZ() + " redstone_block");
            context.waitTicks(30);
            expectAsleep(server, true);
            view(context, server, "oculus-asleep", 1, GROUND + 4, 5, 180, 40);
            server.runCommand("setblock " + powerPos.getX() + " " + powerPos.getY() + " " + powerPos.getZ() + " air");
            context.waitTicks(30);
            expectAsleep(server, false);

            zoomIn(server);
            zoomIn(server);
            context.waitTicks(80);
            view(context, server, "oculus-angled-1-16", 1, GROUND + 4, 5, 180, 40);

            zoomIn(server);
            zoomIn(server);
            zoomIn(server);
            zoomIn(server);
            context.waitTicks(80);
            view(context, server, "oculus-side-1-4", 6, GROUND + 2, 0.5, 90, 20);
            view(context, server, "oculus-side-far-1-4", 12, GROUND + 4, 0.5, 90, 20);
            view(context, server, "oculus-top-1-4", 1, GROUND + 6, 0.5, 180, 90);
            context.runOnClient(minecraft -> System.out.println(OculusRenderer.describe()));
        }
    }

    private static void view(ClientGameTestContext context, TestServerContext server, String name, double x, double y, double z, float yaw, float pitch)
    {
        server.runCommand("tp @a " + x + " " + y + " " + z + " " + yaw + " " + pitch);
        context.waitTicks(10);
        context.takeScreenshot(name);
    }

    private static void expectAsleep(TestServerContext server, boolean asleep)
    {
        server.runOnServer(minecraft ->
        {
            var tile = OculusTableStructure.getTile(minecraft.overworld(), TABLE);
            if (tile == null || tile.getView().asleep() != asleep)
                throw new AssertionError("Expected the table " + (asleep ? "asleep" : "awake") + ", got " + (tile == null ? null : tile.getView()));
        });
    }

    private static void zoomIn(TestServerContext server)
    {
        server.runOnServer(minecraft -> OculusTableStructure.updateView(minecraft.overworld(), TABLE, OculusView::zoomIn));
    }

    /**
     * A stepped stone pyramid west, a pond and sand beach east, a gold tower north and a few trees south.
     */
    private static void buildLand(TestServerContext server)
    {
        for (int step = 0; step < 6; step++)
            server.runCommand("fill " + (-30 + step) + " " + (GROUND + 1 + step) + " " + (-6 + step) + " " + (-18 - step) + " " + (GROUND + 1 + step) + " " + (6 - step) + " stone");
        server.runCommand("fill 14 " + GROUND + " -10 34 " + GROUND + " 10 sand");
        server.runCommand("fill 16 " + GROUND + " -8 32 " + GROUND + " 8 water");
        server.runCommand("fill -2 " + (GROUND + 1) + " -36 2 " + (GROUND + 12) + " -32 gold_block");
        // A pit leaves the bottom of close windows empty, where the void floor shows
        server.runCommand("fill -6 " + (GROUND - 2) + " 2 -3 " + GROUND + " 5 air");
        for (int tree = 0; tree < 4; tree++)
            server.runCommand("place feature minecraft:oak " + (-12 + tree * 8) + " " + (GROUND + 1) + " " + (14 + tree % 2 * 6));
        for (int mob = 0; mob < 3; mob++)
        {
            server.runCommand("summon cow " + (8 + mob * 3) + " " + (GROUND + 1) + " " + (-12 + mob * 2) + " {NoAI:1b}");
            server.runCommand("summon husk " + (-8 - mob * 3) + " " + (GROUND + 1) + " " + (-12 + mob * 2) + " {NoAI:1b,PersistenceRequired:1b}");
        }
    }
}
