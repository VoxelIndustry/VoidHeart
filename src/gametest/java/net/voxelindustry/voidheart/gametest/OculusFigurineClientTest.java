package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.voxelindustry.voidheart.client.render.oculus.OculusFigurine;
import net.voxelindustry.voidheart.common.content.oculus.OculusTableStructure;
import net.voxelindustry.voidheart.common.content.oculus.OculusTableTile;
import net.voxelindustry.voidheart.common.content.oculus.OculusView;

import java.util.Optional;

/**
 * A table following a player shows them as a small figure under their pin, striding, swinging and sneaking with them.
 */
public class OculusFigurineClientTest implements FabricClientGameTest
{
    private static final int GROUND = -61;
    private static final BlockPos TABLE = new BlockPos(0, GROUND + 1, 0);

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("fill -2 " + TABLE.getY() + " -2 2 " + TABLE.getY() + " 2 voidheart:oculus_table");
            server.runCommand("setblock 0 " + TABLE.getY() + " -12 voidheart:beholder");
            server.runCommand("gamemode creative @a");
            server.runCommand("item replace entity @a weapon.mainhand with diamond_sword");
            server.runCommand("tp @a 0.5 " + TABLE.getY() + " 4.5 180 24");
            context.waitTicks(20);
            expectFigures(context, 0, "no figure while the table follows no one");

            // Closest zoom, following the player
            server.runOnServer(minecraft ->
            {
                var player = minecraft.getPlayerList().getPlayers().getFirst();
                OculusTableStructure.updateView(minecraft.overworld(), TABLE,
                        view -> new OculusView(OculusView.ZOOM_STEPS - 1, Optional.of(player.getUUID()), false, false, false));
            });
            context.waitTicks(60);
            expectFigures(context, 1, "the followed player's figure");
            context.takeScreenshot("oculus-figure-standing-1-4");

            // Strafing keeps the player facing the table while their figure strides across it
            context.getInput().holdKey(options -> options.keyLeft);
            context.waitTicks(8);
            context.takeScreenshot("oculus-figure-walking-1-4");
            context.getInput().releaseKey(options -> options.keyLeft);
            context.waitTicks(10);

            context.runOnClient(minecraft -> minecraft.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false));
            context.waitTicks(2);
            context.takeScreenshot("oculus-figure-swinging-1-4");
            context.waitTicks(10);

            context.getInput().holdKey(options -> options.keyShift);
            context.waitTicks(10);
            context.takeScreenshot("oculus-figure-sneaking-1-4");
            context.getInput().releaseKey(options -> options.keyShift);

            server.runOnServer(minecraft -> OculusTableStructure.updateView(minecraft.overworld(), TABLE, view -> view.following(Optional.empty())));
            context.waitTicks(20);
            expectFigures(context, 0, "the figure gone once the table stops following");

            // Put to sleep by redstone, the table shows nobody
            server.runOnServer(minecraft ->
            {
                var player = minecraft.getPlayerList().getPlayers().getFirst();
                OculusTableStructure.updateView(minecraft.overworld(), TABLE, view -> view.following(Optional.of(player.getUUID())));
            });
            context.waitTicks(20);
            expectFigures(context, 1, "the figure back once followed again");
            server.runCommand("setblock 3 " + TABLE.getY() + " 0 redstone_block");
            context.waitTicks(30);
            expectFigures(context, 0, "no figure on a sleeping table");
        }
    }

    private static void expectFigures(ClientGameTestContext context, int expected, String what)
    {
        int drawn = context.computeOnClient(minecraft -> OculusFigurine.drawnLastFrame());
        if (drawn != expected)
        {
            var view = context.computeOnClient(minecraft ->
                    minecraft.level.getBlockEntity(new BlockPos(-2, TABLE.getY(), -2)) instanceof OculusTableTile tile ? tile.getView().toString() : "no table");
            throw new AssertionError("Expected " + what + ", " + expected + " figures drawn but got " + drawn + ", client view " + view);
        }
    }
}
