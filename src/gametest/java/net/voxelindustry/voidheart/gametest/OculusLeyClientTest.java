package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.voxelindustry.voidheart.common.content.oculus.OculusTableStructure;
import net.voxelindustry.voidheart.common.content.oculus.OculusView;
import net.voxelindustry.voidheart.common.ley.LeyLattice;

/**
 * A table next to a ley node, its ley layer turned on.
 */
public class OculusLeyClientTest implements FabricClientGameTest
{
    private static final int TABLE_Y = -60;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set noon");

            var node = server.computeOnServer(minecraft -> LeyLattice.get(minecraft.overworld()).flatMap(lattice -> lattice.nearestNode(0, 0)).orElse(null));
            if (node == null)
                throw new AssertionError("Expected a ley lattice in the overworld");

            // The table stands a few blocks off the node so the node shows right next to it in the hologram
            int x = node.x() + 6, z = node.z();
            server.runCommand("gamemode spectator @a");
            server.runCommand("forceload add " + (x - 16) + " " + (z - 16) + " " + (x + 16) + " " + (z + 16));
            server.runCommand("tp @a " + x + " " + (TABLE_Y + 9) + " " + z + " 180 90");
            context.waitTicks(40);
            world.getConnection().waitForChunksRender();
            server.runCommand("fill " + (x - 2) + " " + TABLE_Y + " " + (z - 2) + " " + (x + 2) + " " + TABLE_Y + " " + (z + 2) + " voidheart:oculus_table");
            context.waitTicks(20);

            var table = new BlockPos(x, TABLE_Y, z);
            server.runOnServer(minecraft -> OculusTableStructure.updateView(minecraft.overworld(), table, OculusView::toggleLeyLines));
            context.waitTicks(80);
            context.takeScreenshot("oculus-ley-top-1-32");

            server.runOnServer(minecraft -> OculusTableStructure.updateView(minecraft.overworld(), table, OculusView::zoomIn));
            server.runCommand("tp @a " + (x + 0.5) + " " + (TABLE_Y + 4) + " " + (z + 5.5) + " 180 40");
            context.waitTicks(80);
            context.takeScreenshot("oculus-ley-angled-1-16");
        }
    }
}
