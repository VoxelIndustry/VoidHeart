package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.screen.ResearchScreen;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.content.research.KnowledgeColumnBlock;
import net.voxelindustry.voidheart.common.content.research.KnowledgeColumnTile;
import net.voxelindustry.voidheart.common.content.research.RepositoryBoundTile;
import net.voxelindustry.voidheart.common.content.research.KnowledgeRepositoryTile;
import net.voxelindustry.voidheart.common.content.research.KnowledgeState;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.Objects;
import java.util.UUID;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Columns placed with real clicks, counted by a repository, drawn as a floating cluster.
 */
public class KnowledgeColumnClientTest implements FabricClientGameTest
{
    private static final int      GROUND     = -61;
    private static final BlockPos SPACE      = new BlockPos(0, GROUND + 1, 3);
    private static final BlockPos REPOSITORY = new BlockPos(-3, GROUND + 1, 3);
    private static final BlockPos CLUSTER    = new BlockPos(12, GROUND + 1, 12);

    private static final BlockPos   STRANGERS_REPOSITORY = new BlockPos(-3, GROUND + 1, 0);
    private static final UUID       STRANGER             = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final Identifier ARCHIVE              = Identifier.fromNamespaceAndPath("voidheart", "archive_expansion");
    private static final int        BREAKER              = 777;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamemode creative @a");
            context.waitTicks(20);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartBlocks.KNOWLEDGE_COLUMN, 16));
                player.getInventory().setItem(1, new ItemStack(VoidHeartItems.KNOWLEDGE_TABLET));
            });
            aimFrom(context, server, 0.5, GROUND + 1, 0.5, 0.5, GROUND + 1.5, 3.5, 5);
            context.takeScreenshot("column-item-in-hand");

            // Each click is on the ground under its own quarter
            click(context, new Vec3(0.25, GROUND + 1, 3.25), Direction.UP, SPACE.below());
            expectMask(context, server, SPACE, 0b0001);
            click(context, new Vec3(0.75, GROUND + 1, 3.25), Direction.UP, SPACE.below());
            expectMask(context, server, SPACE, 0b0011);
            // The side of a column looking into the free half fills it
            click(context, new Vec3(0.75, GROUND + 1.5, 3.5), Direction.SOUTH, SPACE);
            expectMask(context, server, SPACE, 0b1011);
            click(context, new Vec3(0.25, GROUND + 1, 3.75), Direction.UP, SPACE.below());
            expectMask(context, server, SPACE, 0b1111);
            // The top of a column stacks above that one quarter, not in a free neighbor below
            click(context, new Vec3(0.75, GROUND + 2, 3.75), Direction.UP, SPACE);
            expectMask(context, server, SPACE.above(), 0b1000);
            // A full space takes no more, and whatever is placed against it goes to the next space
            click(context, new Vec3(1, GROUND + 1.5, 3.25), Direction.EAST, SPACE);
            expectMask(context, server, SPACE.east(), 0b0001);

            aimFrom(context, server, 0.5, GROUND + 1, 0.5, 0.5, GROUND + 1.5, 3.25, 15);
            server.runOnServer(minecraft -> expectLink(minecraft, SPACE, null));
            context.takeScreenshot("column-unlinked-jade");

            // Without the research they are only promised: dark, still, worth nothing
            server.runOnServer(minecraft -> placeRepository(minecraft, REPOSITORY, player(minecraft).getUUID(), false));
            context.waitTicks(50);
            server.runOnServer(minecraft ->
            {
                expectLink(minecraft, SPACE, player(minecraft).getUUID());
                expectActive(minecraft, SPACE, false);
                expectColumns(minecraft, player(minecraft).getUUID(), 0);
            });
            context.waitTicks(35);
            context.takeScreenshot("column-pending-jade");

            // And go to anyone who can use them, coming back once that one is gone
            server.runOnServer(minecraft -> placeRepository(minecraft, STRANGERS_REPOSITORY, STRANGER, true));
            context.waitTicks(50);
            server.runOnServer(minecraft ->
            {
                expectLink(minecraft, SPACE, STRANGER);
                expectActive(minecraft, SPACE, true);
                expectColumns(minecraft, STRANGER, 6);
                expectColumns(minecraft, player(minecraft).getUUID(), 0);
                player(minecraft).level().setBlockAndUpdate(STRANGERS_REPOSITORY, Blocks.AIR.defaultBlockState());
            });
            context.waitTicks(100);
            server.runOnServer(minecraft ->
            {
                expectLink(minecraft, SPACE, player(minecraft).getUUID());
                expectActive(minecraft, SPACE, false);
                Knowledge.of(minecraft, player(minecraft).getUUID()).grant(ARCHIVE);
            });
            context.waitTicks(50);
            server.runOnServer(minecraft ->
            {
                expectLink(minecraft, SPACE, player(minecraft).getUUID());
                expectActive(minecraft, SPACE, true);
                expectColumns(minecraft, player(minecraft).getUUID(), 6);
                double cap = KnowledgeState.get(minecraft).of(player(minecraft).getUUID()).cap(Knowledge.rules(), Knowledge.tree(minecraft.registryAccess()));
                if (cap != 30 + 6 * 40)
                    throw new AssertionError("Six columns at 40 should hold 270, got " + cap);
            });
            context.waitTicks(35);
            context.takeScreenshot("column-linked-jade");

            // Columns serve one repository at a time, and a stranger's has to wait for them to be free
            server.runOnServer(minecraft -> placeRepository(minecraft, STRANGERS_REPOSITORY, STRANGER, true));
            context.waitTicks(50);
            server.runOnServer(minecraft ->
            {
                expectLink(minecraft, SPACE, player(minecraft).getUUID());
                expectColumns(minecraft, STRANGER, 0);
                player(minecraft).level().setBlockAndUpdate(REPOSITORY, Blocks.AIR.defaultBlockState());
            });
            context.waitTicks(100);
            server.runOnServer(minecraft ->
            {
                expectLink(minecraft, SPACE, STRANGER);
                expectColumns(minecraft, STRANGER, 6);
                expectColumns(minecraft, player(minecraft).getUUID(), 0);
                player(minecraft).level().setBlockAndUpdate(STRANGERS_REPOSITORY, Blocks.AIR.defaultBlockState());
            });
            context.waitTicks(50);
            server.runOnServer(minecraft -> expectLink(minecraft, SPACE, null));
            server.runOnServer(minecraft -> placeRepository(minecraft, REPOSITORY, player(minecraft).getUUID(), true));
            context.waitTicks(50);
            server.runOnServer(minecraft ->
            {
                expectLink(minecraft, SPACE, player(minecraft).getUUID());
                expectColumns(minecraft, player(minecraft).getUUID(), 6);
            });

            openColumnPage(context, server);

            context.getInput().pressKey(InputConstants.KEY_F1);

            aimFrom(context, server, 0.5, GROUND + 1.5, 0.8, 0.8, GROUND + 1.5, 3.5, 40);
            context.takeScreenshot("column-small-close");
            context.runOnClient(client -> client.level.destroyBlockProgress(BREAKER, SPACE, 6));
            context.waitTicks(2);
            context.takeScreenshot("column-break-linked");
            context.runOnClient(client -> client.level.destroyBlockProgress(BREAKER, SPACE, -1));

            server.runOnServer(KnowledgeColumnClientTest::brokenColumnsDropAndUncount);
            context.waitTicks(50);
            server.runOnServer(minecraft -> expectColumns(minecraft, player(minecraft).getUUID(), 2));

            // Out of reach of the repository: nothing floats or burns
            server.runOnServer(KnowledgeColumnClientTest::buildCluster);
            context.waitTicks(10);
            aimFrom(context, server, 9.5, GROUND + 2.2, 10, 13.5, GROUND + 2, 13.5, 3);
            context.takeScreenshot("column-cluster-unlinked");
            context.runOnClient(client -> client.level.destroyBlockProgress(BREAKER, CLUSTER, 6));
            context.waitTicks(2);
            context.takeScreenshot("column-break-unlinked");
            context.runOnClient(client -> client.level.destroyBlockProgress(BREAKER, CLUSTER, -1));

            // Behind the cluster, out of the framing below
            server.runOnServer(minecraft ->
            {
                player(minecraft).level().setBlockAndUpdate(REPOSITORY, Blocks.AIR.defaultBlockState());
                placeRepository(minecraft, CLUSTER.offset(6, 0, 1), player(minecraft).getUUID(), true);
            });
            context.waitTicks(10);
            context.takeScreenshot("column-cluster-rising-start");
            context.waitTicks(40);
            context.takeScreenshot("column-cluster-rising-middle");
            context.waitTicks(40);
            server.runOnServer(minecraft -> expectLink(minecraft, CLUSTER, player(minecraft).getUUID()));

            aimFrom(context, server, 7.5, GROUND + 0.2, 14, 14, GROUND + 1.8, 14, 3);
            for (int moment = 0; moment < 6; moment++)
            {
                context.takeScreenshot("column-wave-" + moment);
                context.waitTicks(23);
            }
            aimFrom(context, server, 14, GROUND + 5, 8.5, 14, GROUND + 2, 14, 3);
            context.takeScreenshot("column-wave-above");

            // Solid and opaque, but neighbors slide past each other, so a frozen sweep shows any fighting faces
            server.runCommand("tick freeze");
            for (int step = 0; step < 8; step++)
            {
                aimFrom(context, server, 9.5 + step * 0.05, GROUND + 2.2, 10 + step * 0.08, 13.5, GROUND + 2, 13.5, 2);
                context.takeScreenshot("column-sweep-" + step);
            }
            server.runCommand("tick unfreeze");

            context.getInput().pressKey(InputConstants.KEY_F1);
        }
    }

    private static void openColumnPage(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            var hit = new BlockHitResult(Vec3.atCenterOf(REPOSITORY), Direction.UP, REPOSITORY, false);
            player.gameMode.useItemOn(player, player.level(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
        });
        context.waitFor(client -> client.gui.screen() instanceof ResearchScreen);
        context.waitTicks(30);

        var columns = context.computeOnClient(client -> ResearchScreen.snapshot.columns());
        if (columns.count() != 6 || columns.capacity() != 40 || columns.radius() != 8)
            throw new AssertionError("The screen should be told what the columns come to, got " + columns);

        clickScreen(context, context.computeOnClient(client -> screen(client).nodePosition(ARCHIVE)));
        clickScreen(context, context.computeOnClient(client -> screen(client).unlockPosition(0)));
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(3);
        context.takeScreenshot("column-page");

        context.runOnClient(client -> client.player.closeContainer());
        context.waitFor(client -> client.gui.screen() == null);
    }

    private static void clickScreen(ClientGameTestContext context, int[] guiPosition)
    {
        double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
        context.getInput().setCursorPos(guiPosition[0] * scale, guiPosition[1] * scale);
        context.waitTicks(2);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
    }

    private static ResearchScreen screen(Minecraft client)
    {
        return (ResearchScreen) client.gui.screen();
    }

    private static void expectLink(MinecraftServer minecraft, BlockPos pos, UUID owner)
    {
        var column = (KnowledgeColumnTile) player(minecraft).level().getBlockEntity(pos);
        var linked = column.getLink().map(RepositoryBoundTile.Link::owner).orElse(null);
        if (!Objects.equals(linked, owner))
            throw new AssertionError("Expected the columns at " + pos + " linked to " + owner + ", got " + column.getLink());
    }

    private static void expectActive(MinecraftServer minecraft, BlockPos pos, boolean active)
    {
        var column = (KnowledgeColumnTile) player(minecraft).level().getBlockEntity(pos);
        if (column.isActive() != active)
            throw new AssertionError("Expected the columns at " + pos + (active ? " in use" : " only promised"));
    }

    private static void expectColumns(MinecraftServer minecraft, UUID owner, int columns)
    {
        int counted = KnowledgeState.get(minecraft).of(owner).getColumns();
        if (counted != columns)
            throw new AssertionError("Expected " + owner + " to have " + columns + " columns, got " + counted);
    }

    private static void placeRepository(MinecraftServer minecraft, BlockPos pos, UUID owner, boolean researched)
    {
        var level = player(minecraft).level();
        level.setBlockAndUpdate(pos, VoidHeartBlocks.KNOWLEDGE_REPOSITORY.defaultBlockState());
        if (level.getBlockEntity(pos) instanceof KnowledgeRepositoryTile repository)
            repository.setOwner(owner);
        Knowledge.bind(minecraft, owner, GlobalPos.of(level.dimension(), pos));
        if (researched)
            Knowledge.of(minecraft, owner).grant(ARCHIVE);
    }

    private static void click(ClientGameTestContext context, Vec3 location, Direction face, BlockPos pos)
    {
        context.runOnClient(client -> client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, new BlockHitResult(location, face, pos, false)));
        context.waitTicks(4);
    }

    private static void expectMask(ClientGameTestContext context, TestServerContext server, BlockPos pos, int mask)
    {
        server.runOnServer(minecraft ->
        {
            var state = player(minecraft).level().getBlockState(pos);
            if (!state.is(VoidHeartBlocks.KNOWLEDGE_COLUMN) || KnowledgeColumnBlock.maskOf(state) != mask)
                throw new AssertionError("Expected columns " + Integer.toBinaryString(mask) + " at " + pos + ", got " + state);
        });
    }

    private static void brokenColumnsDropAndUncount(MinecraftServer minecraft)
    {
        var player = player(minecraft);
        var level = player.level();
        var state = level.getBlockState(SPACE);
        var drops = Block.getDrops(state, level, SPACE, level.getBlockEntity(SPACE), player, new ItemStack(Items.DIAMOND_PICKAXE));
        int dropped = drops.stream().filter(stack -> stack.is(VoidHeartBlocks.KNOWLEDGE_COLUMN.asItem())).mapToInt(ItemStack::getCount).sum();
        if (dropped != 4)
            throw new AssertionError("A full space should drop its 4 columns, got " + drops);
        level.setBlockAndUpdate(SPACE, Blocks.AIR.defaultBlockState());
    }

    // Four spaces wide and deep with a taller heart, so that the rings of the wave show
    private static void buildCluster(MinecraftServer minecraft)
    {
        var level = player(minecraft).level();
        var full = VoidHeartBlocks.KNOWLEDGE_COLUMN.defaultBlockState()
                .setValue(KnowledgeColumnBlock.NORTH_EAST, true)
                .setValue(KnowledgeColumnBlock.SOUTH_WEST, true)
                .setValue(KnowledgeColumnBlock.SOUTH_EAST, true);
        for (int x = 0; x < 4; x++)
            for (int z = 0; z < 4; z++)
            {
                boolean heart = (x == 1 || x == 2) && (z == 1 || z == 2);
                for (int y = 0; y < (heart ? 2 : 1); y++)
                    level.setBlockAndUpdate(CLUSTER.offset(x, y, z), full);
            }
    }
}
