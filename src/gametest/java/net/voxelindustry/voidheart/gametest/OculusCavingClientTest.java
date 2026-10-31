package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.voxelindustry.voidheart.client.render.oculus.OculusRenderer;
import net.voxelindustry.voidheart.common.content.oculus.OculusTableStructure;
import net.voxelindustry.voidheart.common.content.oculus.OculusTableTile;
import net.voxelindustry.voidheart.common.content.oculus.OculusView;

import java.util.Optional;

/**
 * A table shows the hollows of a house and of a tunnel through a hill when asked. Following a player, it turns to
 * the hollows by itself as a roof closes over them, showing both for a while, and back once they are in the open.
 */
public class OculusCavingClientTest implements FabricClientGameTest
{
    private static final int GROUND = -61;
    private static final BlockPos TABLE = new BlockPos(0, GROUND + 1, 0);
    private static final BlockPos ANCHOR = new BlockPos(-2, GROUND + 1, -2);
    private static final int MIXED_TICKS = 4 * 20;
    private static final int SETTLE_TIMEOUT = 600;

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

            // A stone house east of the table, and a hill west of it with a tunnel running through
            fill(server, 10, GROUND + 1, -5, 20, GROUND + 7, 5, "stone_bricks");
            fill(server, 11, GROUND + 1, -4, 19, GROUND + 6, 4, "air");
            fill(server, -24, GROUND + 1, -10, -10, GROUND + 15, 6, "stone");
            fill(server, -22, GROUND + 3, -4, -12, GROUND + 6, 0, "air");
            fill(server, -2, TABLE.getY(), -2, 2, TABLE.getY(), 2, "voidheart:oculus_table");
            // Sees all the land shown, not just the table's own short sight
            server.runCommand("setblock 0 " + TABLE.getY() + " -12 voidheart:beholder");
            // Flying rather than spectating, as tables do not follow spectators
            server.runCommand("gamemode creative @a");
            server.runOnServer(minecraft ->
            {
                var player = minecraft.getPlayerList().getPlayers().getFirst();
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            });
            server.runCommand("tp @a 0.5 " + (GROUND + 4) + " 6.5 180 50");
            server.runOnServer(minecraft -> OculusTableStructure.updateView(minecraft.overworld(), TABLE, view -> view.zoomIn().zoomIn()));

            settle(context);
            expectBlend(context, 0, "the surface by default");
            context.takeScreenshot("oculus-caving-surface-1-16");

            server.runOnServer(minecraft -> OculusTableStructure.updateView(minecraft.overworld(), TABLE, OculusView::toggleCaving));
            context.waitTicks(40);
            settle(context);
            expectBlend(context, 1, "the hollows once asked for");
            context.takeScreenshot("oculus-caving-hollows-1-16");

            // Following the player, out in the open: back to the surface at once
            server.runOnServer(minecraft ->
            {
                var player = minecraft.getPlayerList().getPlayers().getFirst();
                OculusTableStructure.updateView(minecraft.overworld(), TABLE, view -> view.toggleCaving().following(Optional.of(player.getUUID())));
            });
            context.waitTicks(40);
            expectBlend(context, 0, "the surface while the followed player is in the open");

            // A glass roof closes over them: both show for a while, then the hollows
            fill(server, -6, GROUND + 7, 0, 6, GROUND + 7, 12, "glass");
            context.waitTicks(40);
            expectBlend(context, 0.5F, "both while the followed player just went under cover");
            settle(context);
            context.takeScreenshot("oculus-caving-mixed-1-16");
            context.waitTicks(MIXED_TICKS);
            expectBlend(context, 1, "the hollows once the followed player stayed under cover");
            context.takeScreenshot("oculus-caving-following-1-16");

            fill(server, -6, GROUND + 7, 0, 6, GROUND + 7, 12, "air");
            context.waitTicks(40);
            expectBlend(context, 0.5F, "both while the followed player just came out");
            context.waitTicks(MIXED_TICKS);
            expectBlend(context, 0, "the surface once the followed player stayed out");
        }
    }

    private static void settle(ClientGameTestContext context)
    {
        context.waitTicks(5);
        context.waitFor(minecraft -> OculusRenderer.isSettled(), SETTLE_TIMEOUT);
    }

    private static void expectBlend(ClientGameTestContext context, float expected, String what)
    {
        float blend = context.computeOnClient(minecraft -> OculusRenderer.cavingBlend(ANCHOR).orElse(-1F));
        if (Math.abs(blend - expected) > 0.02F)
        {
            var state = context.computeOnClient(minecraft -> "eyes at " + minecraft.player.getEyeY() + ", covered up to "
                    + minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, minecraft.player.getBlockX(), minecraft.player.getBlockZ())
                    + ", " + (minecraft.level.getBlockEntity(ANCHOR) instanceof OculusTableTile tile ? tile.getView() : "no table"));
            throw new AssertionError("Expected " + what + ", blend " + expected + " but got " + blend + ", " + state);
        }
    }

    private static void fill(TestServerContext server, int x0, int y0, int z0, int x1, int y1, int z1, String block)
    {
        server.runCommand("fill " + x0 + " " + y0 + " " + z0 + " " + x1 + " " + y1 + " " + z1 + " " + block);
    }
}
