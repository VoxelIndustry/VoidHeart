package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The beam focus prism floats above the block and must read as a solid crystal from every side, above and below.
 */
public class BeamFocusClientTest implements FabricClientGameTest
{
    private static final int Y = -58;
    private static final double PRISM_Y = Y + 21.75 / 16;
    private static final double EYE = 1.62;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamemode creative @a");
            server.runCommand("setblock 0 " + Y + " 0 voidheart:beam_focus[facing=north]");
            context.waitTicks(20);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().pressKey(InputConstants.KEY_F1);

            double[][] views = {
                    {0.5, -1.2}, {2.2, 0.5}, {0.5, 2.2}, {-1.2, 0.5},
                    {1.9, -0.9}, {-0.9, 1.9}
            };
            String[] names = {"north", "east", "south", "west", "northeast", "southwest"};
            for (int i = 0; i < views.length; i++)
            {
                aimFrom(context, server, views[i][0], PRISM_Y - EYE, views[i][1], 0.5, PRISM_Y, 0.5, 5);
                context.takeScreenshot(TestScreenshotOptions.of("beam-focus-prism-" + names[i]).withSize(1280, 720));
            }
            aimFrom(context, server, 1.6, PRISM_Y - EYE + 0.9, -0.6, 0.5, PRISM_Y, 0.5, 5);
            context.takeScreenshot(TestScreenshotOptions.of("beam-focus-prism-above").withSize(1280, 720));
            aimFrom(context, server, 1.6, PRISM_Y - EYE - 0.6, -0.6, 0.5, PRISM_Y, 0.5, 5);
            context.takeScreenshot(TestScreenshotOptions.of("beam-focus-prism-below").withSize(1280, 720));

            // The front of the base carries the lit output lines over solid stone, nothing behind it may show through
            double baseY = Y + 0.35;
            aimFrom(context, server, 0.5, baseY - EYE + 0.3, -1.3, 0.5, baseY, 0.5, 5);
            context.takeScreenshot(TestScreenshotOptions.of("beam-focus-base-front").withSize(1280, 720));
            aimFrom(context, server, 1.8, baseY - EYE + 0.2, -0.9, 0.5, baseY, 0.5, 5);
            context.takeScreenshot(TestScreenshotOptions.of("beam-focus-base-front-angle").withSize(1280, 720));
            aimFrom(context, server, -0.8, baseY - EYE - 0.4, -0.9, 0.5, baseY, 0.5, 5);
            context.takeScreenshot(TestScreenshotOptions.of("beam-focus-base-front-low").withSize(1280, 720));

            context.getInput().pressKey(InputConstants.KEY_F1);
        }
    }
}
