package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.voxelindustry.voidheart.client.render.oculus.OculusMarkers;
import net.voxelindustry.voidheart.client.render.oculus.OculusRenderer;
import net.voxelindustry.voidheart.common.config.VoidHeartConfig;
import net.voxelindustry.voidheart.common.content.oculus.OculusTableStructure;
import net.voxelindustry.voidheart.common.content.oculus.OculusView;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.function.Predicate;

/**
 * Presses the floating buttons of a table with real clicks and checks the server side view follows.
 */
public class OculusControlsClientTest implements FabricClientGameTest
{
    private static final int TABLE_Y = -60;
    private static final BlockPos TABLE = new BlockPos(0, TABLE_Y, 0);
    private static final BlockPos ANCHOR = new BlockPos(-1, TABLE_Y, -1);
    // The viewer stands south of a 3x3 table, so the strip faces south on the middle block's edge, buttons along +x
    private static final double PANEL_X = 0.5;
    private static final double PANEL_Y = TABLE_Y + 0.5 + 0.5;
    private static final double PANEL_Z = 2.2;
    private static final double[] BUTTON_OFFSETS = { -0.39, -0.13, 0.13, 0.39 };

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set noon");
            server.runCommand("fill -1 " + TABLE_Y + " -1 1 " + TABLE_Y + " 1 voidheart:oculus_table");
            server.runCommand("gamemode creative @a");

            aimAt(context, server, 1);
            context.takeScreenshot("oculus-controls-hover-zoom-in");
            context.getInput().pressKey(options -> options.keyUse);
            expectView(context, world, view -> view.zoom() == 1, "zoom in by right click");
            var midGlide = context.computeOnClient(minecraft -> OculusRenderer.drawnScale(ANCHOR).orElse(0F));
            context.runOnClient(minecraft -> System.out.println("[Oculus zoom] drawn scale right after zooming: " + midGlide));
            context.waitTicks(40);
            var settled = context.computeOnClient(minecraft -> OculusRenderer.drawnScale(ANCHOR).orElse(0F));
            if (Math.abs(settled - 24) > 0.05F)
                throw new AssertionError("Expected the zoom to glide to 1:24, drawn at 1:" + settled);

            // Flying above the table's south side, the player looks down at their own pin, clear of the controls.
            // At 1:24 with the window resting at -64, the pin sits 9/24 up the hologram and 3/24 south of its middle
            server.runOnServer(minecraft ->
            {
                var player = minecraft.getPlayerList().getPlayers().getFirst();
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            });
            double feetY = TABLE_Y + 5;
            aimFrom(context, server, PANEL_X, feetY, 3.5, 0.5, TABLE_Y + 0.5 + 1 / 16D + 1 / 256D + 9 / 24D + 0.07, -1 + 1.5 + 3 / 24D);
            context.takeScreenshot("oculus-gaze-name");
            var gazed = context.computeOnClient(minecraft -> OculusMarkers.gazedPlayer().orElse(null));
            var self = context.computeOnClient(minecraft -> minecraft.player.getUUID());
            if (!self.equals(gazed))
                throw new AssertionError("Expected the name of the player whose pin is looked at to show, got " + gazed);

            aimAt(context, server, 0);
            if (context.computeOnClient(minecraft -> OculusMarkers.gazedPlayer().isPresent()))
                throw new AssertionError("Expected no name once the pin is no longer looked at");

            aimAt(context, server, 2);
            context.getInput().pressKey(options -> options.keyAttack);
            expectView(context, world, view -> view.follow().isPresent(), "follow by left click");
            server.runOnServer(minecraft ->
            {
                if (!minecraft.overworld().getBlockState(TABLE.south()).is(VoidHeartBlocks.OCULUS_TABLE))
                    throw new AssertionError("The table under the button was broken by the click");
            });

            aimAt(context, server, 3);
            context.getInput().pressKey(options -> options.keyUse);
            expectView(context, world, OculusView::leyLines, "toggle ley lines");
            context.takeScreenshot("oculus-controls-after");

            // Following themselves, the player drags the view along as they walk away
            server.runCommand("tp @a 40 " + TABLE_Y + " 0.5");
            context.waitTicks(40);
            expectCenter(context, 40, "follow the player 40 blocks east");

            // Out of range, the table lets go and its view glides back home. The range is lowered so the table's chunk
            // keeps ticking, as it would with someone standing at the table
            server.runOnServer(minecraft -> VoidHeartConfig.get().oculusTableFollowRange = 32);
            server.runCommand("tp @a 50 " + TABLE_Y + " 0.5");
            context.waitTicks(40);
            expectView(context, world, view -> view.follow().isEmpty(), "stop following a player out of range");
            context.waitTicks(40);
            expectCenter(context, 0.5, "come back to the table");
        }
    }

    private static void expectCenter(ClientGameTestContext context, double x, String what)
    {
        var center = context.computeOnClient(minecraft -> OculusRenderer.displayCenter(new BlockPos(-1, TABLE_Y, -1)).orElse(null));
        if (center == null || Math.abs(center.x - x) > 1)
            throw new AssertionError("Expected the hologram to " + what + ", centered at " + center);
    }

    /**
     * Turns the player standing south of the table toward a button. {@code /tp facing} aims from the feet, so the
     * angles are computed from the eyes here.
     */
    private static void aimAt(ClientGameTestContext context, TestServerContext server, int button)
    {
        aimAtPoint(context, server, PANEL_X + BUTTON_OFFSETS[button], PANEL_Y, PANEL_Z);
    }

    private static void aimAtPoint(ClientGameTestContext context, TestServerContext server, double x, double y, double z)
    {
        aimFrom(context, server, PANEL_X, TABLE_Y, 4.5, x, y, z);
    }

    /**
     * Places the player's feet there and turns their eyes toward the point.
     */
    private static void aimFrom(ClientGameTestContext context, TestServerContext server, double feetX, double feetY, double feetZ, double x, double y, double z)
    {
        double dx = x - feetX;
        double dy = y - (feetY + 1.62);
        double dz = z - feetZ;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        server.runCommand("tp @a " + feetX + " " + feetY + " " + feetZ + " " + yaw + " " + pitch);
        context.waitTicks(10);
    }

    private static void expectView(ClientGameTestContext context, TestSingleplayerContext world, Predicate<OculusView> check, String what)
    {
        world.getConnection().waitForServerboundPackets();
        context.waitTicks(2);
        var view = world.getServer().computeOnServer(minecraft ->
        {
            var tile = OculusTableStructure.getTile(minecraft.overworld(), TABLE);
            return tile == null ? null : tile.getView();
        });
        if (view == null || !check.test(view))
            throw new AssertionError("Expected the table view to " + what + ", got " + view);
    }
}
