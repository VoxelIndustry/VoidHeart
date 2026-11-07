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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.content.research.KnowledgeColumnBlock;
import net.voxelindustry.voidheart.common.content.research.KnowledgeRepositoryBlock;
import net.voxelindustry.voidheart.common.content.research.KnowledgeRepositoryDisplay;
import net.voxelindustry.voidheart.common.content.research.KnowledgeRepositoryStatus;
import net.voxelindustry.voidheart.common.content.research.KnowledgeRepositoryTile;
import net.voxelindustry.voidheart.common.content.research.KnowledgeState;
import net.voxelindustry.voidheart.common.content.research.PlayerKnowledge;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.function.Consumer;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Every state a repository can be seen in, each asserted on the client that draws it before its frames are taken.
 */
public class KnowledgeRepositoryFxClientTest implements FabricClientGameTest
{
    private static final int      GROUND     = -61;
    private static final BlockPos REPOSITORY = new BlockPos(0, GROUND + 1, 4);
    private static final BlockPos ELSEWHERE  = new BlockPos(0, GROUND + 1, -20);

    private static final Identifier ARCHIVE  = Identifier.fromNamespaceAndPath("voidheart", "archive_expansion");
    private static final Identifier RESEARCH = Identifier.fromNamespaceAndPath("voidheart", "patient_study");
    private static final double     COST     = 30;

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
            context.getInput().pressKey(InputConstants.KEY_F1);

            server.runOnServer(minecraft -> placeRepository(minecraft, Direction.NORTH));
            frame(context, server);

            // No columns around it: what it gathers comes out of the air
            setKnowledge(server, 10);
            expectStatus(context, KnowledgeRepositoryStatus.GATHERING);
            context.waitTicks(40);
            context.takeScreenshot("repository-gathering-air");
            closeUp(context, server);
            context.takeScreenshot("repository-gathering-close");
            frame(context, server);

            // Bound elsewhere, it has nothing to show
            server.runOnServer(minecraft -> Knowledge.bind(minecraft, player(minecraft).getUUID(), GlobalPos.of(player(minecraft).level().dimension(), ELSEWHERE)));
            expectStatus(context, KnowledgeRepositoryStatus.DORMANT);
            context.waitTicks(15);
            context.takeScreenshot("repository-dormant");
            server.runOnServer(minecraft -> Knowledge.bind(minecraft, player(minecraft).getUUID(), GlobalPos.of(player(minecraft).level().dimension(), REPOSITORY)));

            server.runOnServer(KnowledgeRepositoryFxClientTest::buildColumns);
            server.runOnServer(minecraft -> Knowledge.of(minecraft, player(minecraft).getUUID()).grant(ARCHIVE));
            context.waitTicks(60);
            expectStatus(context, KnowledgeRepositoryStatus.GATHERING);
            frameWide(context, server);
            for (int moment = 0; moment < 3; moment++)
            {
                context.takeScreenshot("repository-gathering-columns-" + moment);
                context.waitTicks(12);
            }

            frame(context, server);
            setKnowledge(server, 10_000);
            expectStatus(context, KnowledgeRepositoryStatus.FULL);
            context.waitTicks(30);
            context.takeScreenshot("repository-full");

            // Full pool, research going
            update(server, data ->
            {
                data.getRevealed().add(RESEARCH);
                data.getProgress().put(RESEARCH, COST * 0.4);
                data.toggleActive(RESEARCH);
            });
            setKnowledge(server, 200);
            expectStatus(context, KnowledgeRepositoryStatus.RESEARCHING);
            context.waitTicks(40);
            context.takeScreenshot("repository-researching");
            closeUp(context, server);
            context.takeScreenshot("repository-researching-close");
            for (int moment = 0; moment < 3; moment++)
            {
                context.waitTicks(8);
                context.takeScreenshot("repository-researching-glyphs-" + moment);
            }

            server.runCommand("time set midnight");
            context.waitTicks(10);
            context.takeScreenshot("repository-researching-night");
            server.runCommand("time set noon");

            // Camera-facing, so it must read the same from the other side and from each turn of the block
            aimFrom(context, server, 0.5, GROUND + 0.8, 6.5, 0.5, GROUND + 2.4, 4.5, 5);
            context.takeScreenshot("repository-researching-behind");
            aimFrom(context, server, 3, GROUND + 2, 4.5, 0.5, GROUND + 2.3, 4.5, 5);
            context.takeScreenshot("repository-researching-side");
            for (var facing : new Direction[] { Direction.EAST, Direction.SOUTH, Direction.WEST })
            {
                server.runOnServer(minecraft -> turn(minecraft, facing));
                context.waitTicks(4);
                aimFrom(context, server, 0.5 + facing.getStepX() * 2.2, GROUND + 1.2, 4.5 + facing.getStepZ() * 2.2, 0.5, GROUND + 2.1, 4.5, 3);
                context.takeScreenshot("repository-tablet-" + facing.getName());
            }
            server.runOnServer(minecraft -> turn(minecraft, Direction.NORTH));

            frame(context, server);
            setKnowledge(server, 0);
            expectStatus(context, KnowledgeRepositoryStatus.STARVED);
            context.waitTicks(30);
            context.takeScreenshot("repository-starved");
            closeUp(context, server);
            context.takeScreenshot("repository-starved-close");

            // Feeding through the same call a click makes
            frame(context, server);
            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_PEARL, 8));
                player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(REPOSITORY), Direction.UP, REPOSITORY, false));
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            });
            context.waitTicks(6);
            context.takeScreenshot("repository-fed");
            expectStatus(context, KnowledgeRepositoryStatus.RESEARCHING);

            // Finishing: the ring fills and flares, and the icon stays a moment
            closeUp(context, server);
            setKnowledge(server, 200);
            update(server, data -> data.getProgress().put(RESEARCH, COST - 0.2));
            context.waitFor(client -> display(client).getFlare().at(1) > 0, 200);
            context.takeScreenshot("repository-finish-flare-0");
            context.waitTicks(4);
            context.takeScreenshot("repository-finish-flare-1");
            context.waitTicks(10);
            context.takeScreenshot("repository-finish-linger");
            if (context.computeOnClient(client -> display(client).getIcon().isEmpty()))
                throw new AssertionError("The finished research should still show for a moment");
            context.waitTicks(60);
            context.takeScreenshot("repository-finish-after");
            if (context.computeOnClient(client -> display(client).getIcon().isPresent()))
                throw new AssertionError("The icon should be gone once the finished research has lingered");
            server.runOnServer(minecraft ->
            {
                if (!KnowledgeState.get(minecraft).of(player(minecraft).getUUID()).getCompleted().contains(RESEARCH))
                    throw new AssertionError("The research should be finished");
            });
            expectStatus(context, KnowledgeRepositoryStatus.GATHERING);

            // Past the fade distance only the tablet is left
            aimFrom(context, server, 0.5, GROUND + 1, -22, 0.5, GROUND + 2, 4.5, 5);
            context.takeScreenshot("repository-far");

            // Translucent and additive over each other, a frozen sweep shows anything fighting
            update(server, data ->
            {
                data.getRevealed().add(Identifier.fromNamespaceAndPath("voidheart", "vast_archive"));
                data.toggleActive(Identifier.fromNamespaceAndPath("voidheart", "vast_archive"));
            });
            setKnowledge(server, 100);
            context.waitTicks(30);
            server.runCommand("tick freeze");
            for (int step = 0; step < 8; step++)
            {
                aimFrom(context, server, 0.5 + step * 0.05, GROUND + 1, 2 + step * 0.08, 0.5, GROUND + 2.5, 4.5, 2);
                context.takeScreenshot("repository-sweep-" + step);
            }
            server.runCommand("tick unfreeze");

            context.getInput().pressKey(InputConstants.KEY_F1);
        }
    }

    private static void frame(ClientGameTestContext context, TestServerContext server)
    {
        aimFrom(context, server, 0.5, GROUND + 1, 0.5, 0.5, GROUND + 2.3, 4.5, 3);
    }

    private static void frameWide(ClientGameTestContext context, TestServerContext server)
    {
        aimFrom(context, server, 0.5, GROUND + 2, -1.5, 0.5, GROUND + 1.8, 5.5, 3);
    }

    private static void closeUp(ClientGameTestContext context, TestServerContext server)
    {
        aimFrom(context, server, 0.5, GROUND + 0.9, 2.6, 0.5, GROUND + 2.3, 4.5, 3);
    }

    private static void setKnowledge(TestServerContext server, double amount)
    {
        update(server, data ->
        {
            data.addKnowledge(-Double.MAX_VALUE / 2);
            data.addKnowledge(amount);
        });
    }

    private static void update(TestServerContext server, Consumer<PlayerKnowledge> change)
    {
        server.runOnServer(minecraft -> change.accept(Knowledge.of(minecraft, player(minecraft).getUUID())));
    }

    private static void expectStatus(ClientGameTestContext context, KnowledgeRepositoryStatus status)
    {
        context.waitFor(client -> tile(client).getStatus() == status, 60);
    }

    private static KnowledgeRepositoryTile tile(Minecraft client)
    {
        return (KnowledgeRepositoryTile) client.level.getBlockEntity(REPOSITORY);
    }

    private static KnowledgeRepositoryDisplay display(Minecraft client)
    {
        return tile(client).getDisplay();
    }

    private static void placeRepository(MinecraftServer minecraft, Direction facing)
    {
        var level = player(minecraft).level();
        level.setBlockAndUpdate(REPOSITORY, VoidHeartBlocks.KNOWLEDGE_REPOSITORY.defaultBlockState().setValue(KnowledgeRepositoryBlock.FACING, facing));
        var repository = (KnowledgeRepositoryTile) level.getBlockEntity(REPOSITORY);
        repository.setOwner(player(minecraft).getUUID());
        Knowledge.bind(minecraft, player(minecraft).getUUID(), GlobalPos.of(level.dimension(), REPOSITORY));
    }

    private static void turn(MinecraftServer minecraft, Direction facing)
    {
        var level = player(minecraft).level();
        level.setBlockAndUpdate(REPOSITORY, level.getBlockState(REPOSITORY).setValue(KnowledgeRepositoryBlock.FACING, facing));
    }

    private static void buildColumns(MinecraftServer minecraft)
    {
        var level = player(minecraft).level();
        var full = VoidHeartBlocks.KNOWLEDGE_COLUMN.defaultBlockState()
                .setValue(KnowledgeColumnBlock.NORTH_EAST, true)
                .setValue(KnowledgeColumnBlock.SOUTH_WEST, true)
                .setValue(KnowledgeColumnBlock.SOUTH_EAST, true);
        for (int x = 0; x < 2; x++)
            for (int z = 0; z < 2; z++)
            {
                level.setBlockAndUpdate(REPOSITORY.offset(-5 + x, 0, 2 + z), full);
                level.setBlockAndUpdate(REPOSITORY.offset(4 + x, 0, 2 + z), full);
            }
    }
}
