package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeLayout;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

/**
 * A gold ingot clicked onto the source pillar crafts through the forge: the void beams form over the warm-up and
 * dissolve once it stops, a full output slot holds the next craft back and taking the output starts it.
 */
public class ShatterForgeClientTest implements FabricClientGameTest
{
    private static final int FORGE_Y = -60;
    private static final BlockPos FORGE = new BlockPos(0, FORGE_Y, 0);
    private static final Direction TRACK = Direction.NORTH;
    private static final int TRACK_PAIRS = 2;
    private static final BlockPos PILLAR = ShatterForgeLayout.endSlot(FORGE, TRACK, TRACK_PAIRS);
    private static final double BEAM_Y = FORGE_Y + ShatterForgeTile.BEAM_HEIGHT;
    private static final double TRACK_MIDDLE_Z = (FORGE.getZ() + PILLAR.getZ()) / 2D + 0.5;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode creative @a");
            server.runCommand("give @a gold_ingot");
            server.runOnServer(ShatterForgeClientTest::buildForge);
            context.waitTicks(20);
            // The client is shared between tests, an earlier one may have left the camera behind the player
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            // The HUD, hand, chat and recipe toast would sit over the captures
            context.getInput().pressKey(InputConstants.KEY_F1);

            aimFrom(context, server, 7.5, FORGE_Y + 2, TRACK_MIDDLE_Z, 0.5, BEAM_Y, TRACK_MIDDLE_Z, 10);
            if (context.computeOnClient(minecraft -> forge(minecraft).getBeamFormation(1)) != 0)
                throw new AssertionError("Expected no beam on an idle forge");
            context.takeScreenshot("forge-idle");

            clickPillar(context, server);
            if (server.computeOnServer(minecraft -> pillar(minecraft).getStack().isEmpty()))
                throw new AssertionError("Expected the clicked ingot on the source pillar");
            context.waitFor(minecraft -> forge(minecraft).isCrafting(), 40);

            aimFrom(context, server, 7.5, FORGE_Y + 2, TRACK_MIDDLE_Z, 0.5, BEAM_Y, TRACK_MIDDLE_Z, 3);
            context.waitFor(minecraft -> forge(minecraft).getBeamFormation(1) >= 0.5F, 60);
            context.takeScreenshot("forge-beam-forming");
            context.waitFor(minecraft -> forge(minecraft).getBeamFormation(1) >= 1F, 60);
            context.takeScreenshot("forge-beam-formed");

            // Frozen, the animation holds still: only the camera moves between frames, so anything popping between them flickers
            server.runCommand("tick freeze");
            for (int step = 0; step < 6; step++)
            {
                aimFrom(context, server, 4.5 + step * 0.05, FORGE_Y + 1, TRACK_MIDDLE_Z + step * 0.08, 0.5, BEAM_Y, TRACK_MIDDLE_Z, 2);
                context.takeScreenshot(TestScreenshotOptions.of("forge-beam-sweep-" + step).withSize(1280, 720));
            }
            server.runCommand("tick unfreeze");

            aimFrom(context, server, -6.5, FORGE_Y + 2, TRACK_MIDDLE_Z, 0.5, BEAM_Y, TRACK_MIDDLE_Z, 3);
            context.takeScreenshot("forge-beam-opposite");
            aimFrom(context, server, 0.5, FORGE_Y, PILLAR.getZ() - 4.5, 0.5, BEAM_Y, TRACK_MIDDLE_Z, 3);
            context.takeScreenshot("forge-beam-along-track");
            aimFrom(context, server, 0.5, FORGE_Y + 8, TRACK_MIDDLE_Z + 0.5, 0.5, BEAM_Y, TRACK_MIDDLE_Z, 3);
            context.takeScreenshot("forge-beam-above");

            if (!context.computeOnClient(minecraft -> forge(minecraft).isCrafting()))
                throw new AssertionError("Expected the captures to fit within the craft");

            aimFrom(context, server, 7.5, FORGE_Y + 2, TRACK_MIDDLE_Z, 0.5, BEAM_Y, TRACK_MIDDLE_Z, 1);
            context.waitFor(minecraft -> !forge(minecraft).isCrafting(), 100);
            context.waitFor(minecraft -> forge(minecraft).getBeamFormation(1) <= 0.5F, 60);
            context.takeScreenshot("forge-beam-dissolving");
            context.waitFor(minecraft -> forge(minecraft).getBeamFormation(1) == 0F, 60);
            context.takeScreenshot("forge-beam-gone");
            context.getInput().pressKey(InputConstants.KEY_F1);

            server.runOnServer(minecraft ->
            {
                if (!pillar(minecraft).getStack().isEmpty())
                    throw new AssertionError("Expected the craft to consume the ingot");
                if (!forge(minecraft).getStack().is(VoidHeartItems.RAVENOUS_GOLD_INGOT))
                    throw new AssertionError("Expected ravenous gold in the output, got " + forge(minecraft).getStack());
            });

            clickPillar(context, server);
            context.waitTicks(30);
            if (server.computeOnServer(minecraft -> forge(minecraft).isCrafting()))
                throw new AssertionError("Expected a full output slot to hold the next craft back");

            aimFrom(context, server, 0.5, FORGE_Y, -3.5, 0.5, FORGE_Y + 0.875, 0.5, 10);
            context.getInput().pressKey(options -> options.keyUse);
            context.waitTicks(5);
            server.runOnServer(minecraft ->
            {
                if (!player(minecraft).getInventory().contains(stack -> stack.is(VoidHeartItems.RAVENOUS_GOLD_INGOT)))
                    throw new AssertionError("Expected the output in the player's inventory");
                if (!forge(minecraft).isCrafting())
                    throw new AssertionError("Expected taking the output to start the next craft");
            });
        }
    }

    private static void buildForge(MinecraftServer minecraft)
    {
        var level = minecraft.overworld();
        level.setBlockAndUpdate(FORGE, VoidHeartBlocks.SHATTER_FORGE.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.Z));
        for (var expected : ShatterForgeLayout.expected(FORGE, TRACK, TRACK_PAIRS))
        {
            var block = switch (expected.part())
            {
                case CONDUIT -> VoidHeartBlocks.VOID_CONDUIT;
                case PILLAR -> VoidHeartBlocks.VOID_PILLAR;
                default -> VoidHeartBlocks.VOID_MONOLITH;
            };
            level.setBlockAndUpdate(expected.pos(), block.defaultBlockState());
        }
    }

    private static void clickPillar(ClientGameTestContext context, TestServerContext server)
    {
        aimFrom(context, server, PILLAR.getX() + 0.5, FORGE_Y, PILLAR.getZ() - 3.5, PILLAR.getX() + 0.5, FORGE_Y + 0.5, PILLAR.getZ() + 0.5, 10);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(5);
    }

    /**
     * Places the player's feet there and turns their eyes toward the point.
     */
    private static void aimFrom(ClientGameTestContext context, TestServerContext server, double feetX, double feetY, double feetZ, double x, double y, double z, int settleTicks)
    {
        double dx = x - feetX;
        double dy = y - (feetY + 1.62);
        double dz = z - feetZ;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        server.runCommand("tp @a " + feetX + " " + feetY + " " + feetZ + " " + yaw + " " + pitch);
        // Landing ends creative flight, so it is set again each time or the player falls away from the framing
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
        context.waitTicks(settleTicks);
    }

    private static ServerPlayer player(MinecraftServer minecraft)
    {
        return minecraft.getPlayerList().getPlayers().getFirst();
    }

    private static ShatterForgeTile forge(MinecraftServer minecraft)
    {
        return (ShatterForgeTile) minecraft.overworld().getBlockEntity(FORGE);
    }

    private static VoidPillarTile pillar(MinecraftServer minecraft)
    {
        return (VoidPillarTile) minecraft.overworld().getBlockEntity(PILLAR);
    }

    private static ShatterForgeTile forge(Minecraft minecraft)
    {
        return (ShatterForgeTile) minecraft.level.getBlockEntity(FORGE);
    }
}
