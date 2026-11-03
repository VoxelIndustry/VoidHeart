package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.research.KnowledgeRepositoryBlock;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

/**
 * A tablet laid on a lectern through the client's own click, the look of the result and the particles of the change.
 */
public class KnowledgeLecternClientTest implements FabricClientGameTest
{
    private static final int      GROUND  = -61;
    private static final BlockPos LECTERN = new BlockPos(0, GROUND + 1, 3);

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
            context.waitTicks(20);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                player.level().setBlockAndUpdate(LECTERN, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.NORTH));
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.KNOWLEDGE_TABLET));
            });
            aimFrom(context, server, 0.5, GROUND + 1, 0.5, 0.5, GROUND + 1.8, 3.5, 5);
            context.takeScreenshot("lectern-tablet-in-hand");

            context.getInput().pressKey(InputConstants.KEY_F1);
            context.takeScreenshot("lectern-before");

            // Through the client, since the tablet has to beat the lectern on both sides of the connection
            context.runOnClient(client ->
            {
                var hit = new BlockHitResult(Vec3.atCenterOf(LECTERN), Direction.NORTH, LECTERN, false);
                client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
            });
            context.waitTicks(2);
            context.takeScreenshot("lectern-corruption-start");
            context.waitTicks(6);
            context.takeScreenshot("lectern-corruption-middle");
            context.waitTicks(15);
            context.takeScreenshot("lectern-corruption-end");

            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                var state = player.level().getBlockState(LECTERN);
                if (!state.is(VoidHeartBlocks.KNOWLEDGE_REPOSITORY))
                    throw new AssertionError("A click with the tablet should corrupt the lectern, got " + state);
                if (state.getValue(KnowledgeRepositoryBlock.FACING) != Direction.NORTH)
                    throw new AssertionError("The repository should keep the way the lectern faced");
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            });
            context.waitTicks(60);

            aimFrom(context, server, 0.5, GROUND + 1, 1.5, 0.5, GROUND + 1.8, 3.5, 3);
            context.takeScreenshot("lectern-front");
            aimFrom(context, server, 2.2, GROUND + 1, 1.8, 0.5, GROUND + 1.6, 3.5, 3);
            context.takeScreenshot("lectern-three-quarter");
            aimFrom(context, server, 2.5, GROUND + 0.4, 3.5, 0.5, GROUND + 1.8, 3.5, 3);
            context.takeScreenshot("lectern-grazing");
            aimFrom(context, server, -1.2, GROUND + 1, 5.2, 0.5, GROUND + 1.5, 3.5, 3);
            context.takeScreenshot("lectern-back");

            // Each direction, so the blockstate rotations show side by side
            server.runOnServer(minecraft ->
            {
                var level = player(minecraft).level();
                int offset = -3;
                for (var facing : Direction.Plane.HORIZONTAL)
                {
                    level.setBlockAndUpdate(new BlockPos(offset, GROUND + 1, 6), VoidHeartBlocks.KNOWLEDGE_REPOSITORY.defaultBlockState().setValue(KnowledgeRepositoryBlock.FACING, facing));
                    offset += 2;
                }
            });
            context.waitTicks(5);
            aimFrom(context, server, -0.5, GROUND + 4, 2.5, -0.5, GROUND + 1.5, 6.5, 3);
            context.takeScreenshot("lectern-facings");

            context.getInput().pressKey(InputConstants.KEY_F1);
            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartBlocks.KNOWLEDGE_REPOSITORY));
                player.getInventory().setItem(1, new ItemStack(VoidHeartItems.KNOWLEDGE_TABLET));
            });
            context.waitTicks(5);
            context.takeScreenshot("lectern-repository-in-hand");
            context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            context.waitTicks(5);
            context.takeScreenshot("lectern-inventory");
            context.setScreen(() -> null);
        }
    }

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
}
