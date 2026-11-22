package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
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
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().pressKey(InputConstants.KEY_F1);

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

            context.getInput().pressKey(InputConstants.KEY_F1);
        }
    }
}
