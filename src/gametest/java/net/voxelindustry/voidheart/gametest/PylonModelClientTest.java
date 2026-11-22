package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.energy.grid.AzuriumPylonBlock;
import net.voxelindustry.voidheart.common.energy.grid.VoidGridContent;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Close views of the Azurium Pylon's model, standing and hanging, where its claws meet the cap and where its stem narrows.
 */
public class PylonModelClientTest implements FabricClientGameTest
{
    private static final int GROUND = -60;
    private static final int HANG_X = 6;
    private static final int HANG_Y = GROUND + 3;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamemode creative @a");
            server.runCommand("fill -4 " + (GROUND - 1) + " -4 10 " + (GROUND - 1) + " 4 voidheart:voidstone_polished");
            server.runCommand("setblock 0 " + GROUND + " 0 voidheart:azurium_pylon");
            server.runCommand("setblock " + HANG_X + " " + (HANG_Y + 1) + " 0 voidheart:voidstone_polished");
            server.runOnServer(minecraft -> minecraft.overworld().setBlockAndUpdate(new BlockPos(HANG_X, HANG_Y, 0),
                    VoidGridContent.AZURIUM_PYLON.defaultBlockState().setValue(AzuriumPylonBlock.HANGING, true)));
            context.waitTicks(20);
            expect(server.computeOnServer(minecraft -> isPylon(minecraft.overworld().getBlockState(new BlockPos(0, GROUND, 0)), false)),
                    "A standing pylon should sit on the ground");
            expect(server.computeOnServer(minecraft -> isPylon(minecraft.overworld().getBlockState(new BlockPos(HANG_X, HANG_Y, 0)), true)),
                    "A hanging pylon should hang from the ceiling block");

            var saved = new ClientSettings(context.computeOnClient(minecraft -> minecraft.options.getCameraType()),
                    context.computeOnClient(minecraft -> minecraft.gui.hud.isHidden()));
            try
            {
                context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
                setHudHidden(context, true);
                capture(context, server);
                captureItem(context, server);
            }
            finally
            {
                context.setScreen(() -> null);
                restoreClient(context, server, saved);
            }
        }
    }

    private static boolean isPylon(BlockState state, boolean hanging)
    {
        return state.is(VoidGridContent.AZURIUM_PYLON) && state.getValue(AzuriumPylonBlock.HANGING) == hanging;
    }

    private static void setHudHidden(ClientGameTestContext context, boolean hidden)
    {
        context.runOnClient(minecraft ->
        {
            if (minecraft.gui.hud.isHidden() != hidden)
                minecraft.gui.hud.toggle();
        });
    }

    private static void capture(ClientGameTestContext context, TestServerContext server)
    {
        double top = GROUND + 0.95;
        double[][] around = {{0.5, -0.9}, {1.9, 0.5}, {0.5, 1.9}, {-0.9, 0.5}, {1.5, -0.5}};
        for (int i = 0; i < around.length; i++)
        {
            aimFrom(context, server, around[i][0], top - 1.62 + 0.35, around[i][1], 0.5, top, 0.5, 5);
            context.takeScreenshot(TestScreenshotOptions.of("pylon-top-" + i).withSize(1280, 720));
        }
        for (int i = 0; i < around.length; i++)
        {
            aimFrom(context, server, around[i][0], top - 1.62 + 0.02, around[i][1], 0.5, top - 0.05, 0.5, 5);
            context.takeScreenshot(TestScreenshotOptions.of("pylon-graze-" + i).withSize(1280, 720));
        }
        aimFrom(context, server, 0.5, GROUND - 0.1, -0.6, 0.5, GROUND + 0.5, 0.5, 5);
        context.takeScreenshot(TestScreenshotOptions.of("pylon-stem-down").withSize(1280, 720));
        aimFrom(context, server, 0.55, GROUND + 1.1, 0.0, 0.5, GROUND + 0.5, 0.5, 5);
        context.takeScreenshot(TestScreenshotOptions.of("pylon-stem-steep").withSize(1280, 720));
        aimFrom(context, server, -0.6, GROUND - 0.8, -0.6, 0.5, GROUND + 0.3, 0.5, 5);
        context.takeScreenshot(TestScreenshotOptions.of("pylon-stem-low").withSize(1280, 720));

        double hangTip = HANG_Y + 0.05;
        for (int i = 0; i < 3; i++)
        {
            aimFrom(context, server, HANG_X + around[i][0], hangTip - 1.62 - 0.35, around[i][1], HANG_X + 0.5, hangTip, 0.5, 5);
            context.takeScreenshot(TestScreenshotOptions.of("pylon-hanging-" + i).withSize(1280, 720));
        }
    }

    private static void captureItem(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getInventory().setItem(0, new ItemStack(VoidGridContent.AZURIUM_PYLON));
            player.getInventory().setSelectedSlot(0);
        });
        server.runCommand("tp @a 3 " + GROUND + " -3 facing 3 " + GROUND + " 3");
        setHudHidden(context, false);
        context.waitTicks(10);
        context.takeScreenshot("pylon-in-hand");
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitTicks(5);
        context.takeScreenshot("pylon-inventory");
        context.setScreen(() -> null);
        context.waitTicks(2);
    }
}
