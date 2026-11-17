package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The beam focus must read as solid from every side: the prism floating above it, and the base whose front carries
 * the lit output lines over stone. Also captures it in the inventory and in hand.
 */
public class BeamFocusClientTest implements FabricClientGameTest
{
    private static final int Y = -58;
    private static final double PRISM_Y = Y + 21.75 / 16;
    private static final double BASE_Y = Y + 0.35;
    private static final double EYE = 1.62;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        // All tests share one client, so the camera is left as this one found it
        var previousCamera = context.computeOnClient(minecraft -> minecraft.options.getCameraType());
        try
        {
            run(context);
        }
        finally
        {
            context.runOnClient(minecraft -> minecraft.options.setCameraType(previousCamera));
        }
    }

    private static void run(ClientGameTestContext context)
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

            // F1 hides the HUD and hand over the block captures, pressed again so the item captures keep them
            context.getInput().pressKey(InputConstants.KEY_F1);
            try
            {
                capturePrism(context, server);
                captureBase(context, server);
            }
            finally
            {
                context.getInput().pressKey(InputConstants.KEY_F1);
            }
            captureItem(context, server);
        }
    }

    private static void capturePrism(ClientGameTestContext context, TestServerContext server)
    {
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
    }

    private static void captureBase(ClientGameTestContext context, TestServerContext server)
    {
        // The front of the base carries the lit output lines over solid stone, nothing behind it may show through
        aimFrom(context, server, 0.5, BASE_Y - EYE + 0.3, -1.3, 0.5, BASE_Y, 0.5, 5);
        context.takeScreenshot(TestScreenshotOptions.of("beam-focus-base-front").withSize(1280, 720));
        aimFrom(context, server, 1.8, BASE_Y - EYE + 0.2, -0.9, 0.5, BASE_Y, 0.5, 5);
        context.takeScreenshot(TestScreenshotOptions.of("beam-focus-base-front-angle").withSize(1280, 720));
        aimFrom(context, server, -0.8, BASE_Y - EYE - 0.4, -0.9, 0.5, BASE_Y, 0.5, 5);
        context.takeScreenshot(TestScreenshotOptions.of("beam-focus-base-front-low").withSize(1280, 720));

        // The lines sit just in front of the stone, so sweep close and at a grazing angle to show any z-fighting
        server.runCommand("tick freeze");
        try
        {
            for (int step = 0; step < 8; step++)
            {
                aimFrom(context, server, 0.2 + step * 0.05, BASE_Y - EYE + 0.1, -0.6 - step * 0.04, 0.5, BASE_Y, 0.5, 2);
                context.takeScreenshot(TestScreenshotOptions.of("beam-focus-base-sweep-" + step).withSize(1280, 720));
            }
            for (int step = 0; step < 4; step++)
            {
                aimFrom(context, server, 2.6 + step * 0.05, BASE_Y - EYE + 0.05, -0.15 - step * 0.02, 0.5, BASE_Y, 0.2, 2);
                context.takeScreenshot(TestScreenshotOptions.of("beam-focus-base-grazing-sweep-" + step).withSize(1280, 720));
            }
        }
        finally
        {
            server.runCommand("tick unfreeze");
        }
    }

    private static void captureItem(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var inventory = player(minecraft).getInventory();
            inventory.clearContent();
            inventory.setItem(0, new ItemStack(VoidHeartBlocks.BEAM_FOCUS));
            inventory.setSelectedSlot(0);
        });
        context.waitFor(minecraft -> holdsBeamFocus(minecraft), 100);
        if (!context.computeOnClient(BeamFocusClientTest::holdsBeamFocus))
            throw new AssertionError("Expected the client to hold the beam focus before the item captures");
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitTicks(5);
        context.takeScreenshot("beam-focus-inventory");
        context.setScreen(() -> null);

        // Looking at open sky, so the hand stands out
        aimFrom(context, server, 6.5, Y + 2, 6.5, 6.5, Y + 4, 20, 6);
        context.takeScreenshot("beam-focus-in-hand");
    }

    private static boolean holdsBeamFocus(Minecraft minecraft)
    {
        return minecraft.player != null && minecraft.player.getInventory().getSelectedItem().is(VoidHeartBlocks.BEAM_FOCUS.asItem());
    }
}
