package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.screen.ResearchScreen;
import net.voxelindustry.voidheart.common.content.research.CogitantTile;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.content.research.KnowledgeRepositoryTile;
import net.voxelindustry.voidheart.common.content.research.MeatProxyTile;
import net.voxelindustry.voidheart.common.content.research.VoidLoomBlock;
import net.voxelindustry.voidheart.common.content.research.VoidLoomTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Cogitants, the Void Loom and Meat Proxies counted by a repository, strain holding a research back until a proxy bears it, and the grafts.
 */
public class KnowledgeUpgradesClientTest implements FabricClientGameTest
{
    private static final int      GROUND     = -61;
    private static final BlockPos REPOSITORY = new BlockPos(0, GROUND + 1, 6);
    private static final BlockPos[] COGITANTS = { new BlockPos(2, GROUND + 1, 6), new BlockPos(2, GROUND + 1, 7), new BlockPos(-2, GROUND + 1, 7) };
    private static final BlockPos LOOM       = new BlockPos(0, GROUND + 1, 9);
    private static final BlockPos SPARE_LOOM = new BlockPos(4, GROUND + 1, 9);
    private static final BlockPos PROXY      = new BlockPos(-2, GROUND + 1, 5);
    private static final BlockPos DROPPED    = new BlockPos(-4, GROUND + 1, 3);
    private static final BlockPos[] EXTRA_PROXIES = { new BlockPos(-4, GROUND + 1, 5), new BlockPos(-4, GROUND + 1, 6), new BlockPos(-4, GROUND + 1, 7) };

    private static final Identifier COGITATION   = id("cogitation");
    private static final Identifier VOID_FLESH   = id("void_flesh");
    private static final Identifier FAR_SIGHT    = id("far_sight");
    private static final Identifier SHOULDER_EYE = id("shoulder_eye");

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
                var level = player.level();
                level.setBlockAndUpdate(REPOSITORY, VoidHeartBlocks.KNOWLEDGE_REPOSITORY.defaultBlockState());
                if (level.getBlockEntity(REPOSITORY) instanceof KnowledgeRepositoryTile repository)
                    repository.setOwner(player.getUUID());
                Knowledge.bind(minecraft, player.getUUID(), GlobalPos.of(level.dimension(), REPOSITORY));
                for (var cogitant : COGITANTS)
                    level.setBlockAndUpdate(cogitant, VoidHeartBlocks.COGITANT.defaultBlockState());
                level.setBlockAndUpdate(LOOM, VoidHeartBlocks.VOID_LOOM.defaultBlockState().setValue(VoidLoomBlock.FACING, Direction.NORTH));
                level.setBlockAndUpdate(SPARE_LOOM, VoidHeartBlocks.VOID_LOOM.defaultBlockState().setValue(VoidLoomBlock.FACING, Direction.NORTH));

                player.getInventory().setItem(0, new ItemStack(VoidHeartBlocks.COGITANT));
                player.getInventory().setItem(1, new ItemStack(VoidHeartBlocks.MEAT_PROXY));
                player.getInventory().setItem(2, new ItemStack(VoidHeartBlocks.VOID_LOOM));
                player.getInventory().setItem(3, new ItemStack(VoidHeartItems.VOID_VEIN));
                player.getInventory().setItem(4, new ItemStack(VoidHeartItems.VOID_MARROW));
            });
            context.waitTicks(50);

            // Without the research the Cogitants are only promised and add nothing
            server.runOnServer(minecraft ->
            {
                var data = knowledge(minecraft);
                if (data.getCogitants() != 0)
                    throw new AssertionError("Cogitants should count for nothing before Cogitation, got " + data.getCogitants());
                if (!data.hasLoom())
                    throw new AssertionError("The loom should be held by the repository");
                if (!(tile(minecraft, LOOM) instanceof VoidLoomTile loom) || !loom.isActive())
                    throw new AssertionError("The first loom should weave");
                if (!(tile(minecraft, SPARE_LOOM) instanceof VoidLoomTile spare) || spare.isActive() || spare.getLink().isPresent())
                    throw new AssertionError("A second loom should be left free");
                data.grant(COGITATION);
            });
            context.waitTicks(50);

            server.runOnServer(minecraft ->
            {
                var data = knowledge(minecraft);
                if (data.getCogitants() != 3)
                    throw new AssertionError("Three Cogitants should count once researched, got " + data.getCogitants());
                double rate = data.rate(Knowledge.rules(), Knowledge.tree(minecraft.registryAccess()));
                if (Math.abs(rate - (0.1 + 3 * 0.02)) > 1E-6)
                    throw new AssertionError("Three Cogitants should add 0.06 per second, got a rate of " + rate);
                if (!(tile(minecraft, COGITANTS[0]) instanceof CogitantTile cogitant) || !cogitant.isActive())
                    throw new AssertionError("A counted Cogitant should be thinking");

                // The loom makes the second insight cost 10 * 1.075 rather than 11
                data.addKnowledge(10 + 10.75 + 1E-3 - data.getKnowledge());
                if (!data.buyInsight(Knowledge.rules()) || !data.buyInsight(Knowledge.rules()))
                    throw new AssertionError("Two insights should be affordable with the loom");
                if (data.getKnowledge() > 2E-3)
                    throw new AssertionError("The woven insights should have cost exactly 20.75, left " + data.getKnowledge());
            });

            // Strain: attuned once, Watcher Unbound weighs 20 and waits
            server.runOnServer(minecraft ->
            {
                var uuid = player(minecraft).getUUID();
                Knowledge.attune(minecraft, uuid, 1);
                var data = knowledge(minecraft);
                data.grant(VOID_FLESH);
                data.grant(FAR_SIGHT);
                data.addInsights(40);
                data.addKnowledge(200);
                if (!data.reveal(SHOULDER_EYE, Knowledge.tree(minecraft.registryAccess())) || !data.toggleActive(SHOULDER_EYE))
                    throw new AssertionError("Watcher Unbound should be revealed and started");
            });
            context.waitTicks(60);
            server.runOnServer(minecraft ->
            {
                var data = knowledge(minecraft);
                if (!data.isStrained(Knowledge.rules(), Knowledge.tree(minecraft.registryAccess())))
                    throw new AssertionError("Strain 20 should be beyond what an attunement of 1 bears");
                if (data.getProgress().getOrDefault(SHOULDER_EYE, 0D) > 0)
                    throw new AssertionError("A strained research should not progress, got " + data.getProgress().get(SHOULDER_EYE));
            });

            aimFrom(context, server, 4.5, GROUND + 2.5, 2.5, 0.5, GROUND + 1.6, 7, 10);
            context.getInput().pressKey(InputConstants.KEY_F1);
            context.waitTicks(5);
            context.takeScreenshot("upgrades-strained");
            openTablet(context, server, "strained");

            // A proxy takes the missing 10 and wears while it does
            server.runOnServer(minecraft -> player(minecraft).level().setBlockAndUpdate(PROXY, VoidHeartBlocks.MEAT_PROXY.defaultBlockState()));
            context.waitTicks(100);
            server.runOnServer(minecraft ->
            {
                var data = knowledge(minecraft);
                if (data.isStrained(Knowledge.rules(), Knowledge.tree(minecraft.registryAccess())))
                    throw new AssertionError("The proxy should bear the missing strain");
                if (data.getProgress().getOrDefault(SHOULDER_EYE, 0D) <= 0)
                    throw new AssertionError("The research should run once its strain is borne");
                if (!(tile(minecraft, PROXY) instanceof MeatProxyTile proxy) || proxy.getLeft() >= Knowledge.proxySeconds() || !proxy.isBearing())
                    throw new AssertionError("The proxy should be wearing and bearing");
            });
            context.waitFor(client -> client.level.getBlockEntity(PROXY) instanceof MeatProxyTile proxy && proxy.isBearing(), 100);

            // Only three proxies work for a repository, a fourth is left idle
            server.runOnServer(minecraft ->
            {
                for (var extra : EXTRA_PROXIES)
                    player(minecraft).level().setBlockAndUpdate(extra, VoidHeartBlocks.MEAT_PROXY.defaultBlockState());
            });
            context.waitTicks(50);
            server.runOnServer(minecraft ->
            {
                int claimed = knowledge(minecraft).getProxies().size();
                if (claimed != 3)
                    throw new AssertionError("Exactly three proxies should count, got " + claimed);
                long idle = java.util.stream.Stream.concat(java.util.stream.Stream.of(PROXY), java.util.Arrays.stream(EXTRA_PROXIES))
                        .filter(pos -> tile(minecraft, pos) instanceof MeatProxyTile proxy && proxy.getLink().isEmpty())
                        .count();
                if (idle != 1)
                    throw new AssertionError("One of four proxies should be idle, got " + idle);
                for (var extra : EXTRA_PROXIES)
                    player(minecraft).level().removeBlock(extra, false);
            });
            context.waitTicks(50);

            context.takeScreenshot("upgrades-bearing");
            aimFrom(context, server, -3.5, GROUND + 2.2, 3.2, -1.5, GROUND + 1.5, 5.5, 10);
            context.takeScreenshot("upgrades-proxy-close");
            aimFrom(context, server, 3.5, GROUND + 2.2, 7.5, 2.5, GROUND + 1.4, 6.5, 10);
            context.takeScreenshot("upgrades-cogitant-close");
            aimFrom(context, server, 2.5, GROUND + 2.4, 12.5, 0.5, GROUND + 1.5, 8.5, 10);
            context.takeScreenshot("upgrades-loom-behind");

            // The loom's strands are translucent and cross each other, a frozen sweep shows whether they hold steady
            server.runCommand("tick freeze");
            for (int step = 0; step < 6; step++)
            {
                aimFrom(context, server, 3 + step * 0.08, GROUND + 2.3, 9.5 + step * 0.05, 0.5, GROUND + 1.6, 8, 4);
                context.takeScreenshot("upgrades-loom-sweep-" + step);
            }
            server.runCommand("tick unfreeze");

            // A proxy worn out gives out and lets the strain fall back on the player
            server.runOnServer(minecraft ->
            {
                if (tile(minecraft, PROXY) instanceof MeatProxyTile proxy)
                    proxy.restore(3);
            });
            context.waitTicks(200);
            server.runOnServer(minecraft ->
            {
                if (player(minecraft).level().getBlockState(PROXY).is(VoidHeartBlocks.MEAT_PROXY))
                    throw new AssertionError("A worn out proxy should give out");
                if (!knowledge(minecraft).isStrained(Knowledge.rules(), Knowledge.tree(minecraft.registryAccess())))
                    throw new AssertionError("The research should wait again once the proxy gave out");
            });

            // A partly worn proxy keeps what is left of it when broken
            server.runOnServer(minecraft ->
            {
                var level = player(minecraft).level();
                level.setBlockAndUpdate(DROPPED, VoidHeartBlocks.MEAT_PROXY.defaultBlockState());
                if (tile(minecraft, DROPPED) instanceof MeatProxyTile proxy)
                    proxy.restore(1000);
                level.destroyBlock(DROPPED, true);
                var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(DROPPED).inflate(2));
                var endurance = drops.stream().map(item -> item.getItem().get(VoidHeartDataComponents.PROXY_ENDURANCE)).filter(java.util.Objects::nonNull).findFirst();
                if (endurance.isEmpty() || Math.abs(endurance.get() - 1000) > 1E-6)
                    throw new AssertionError("The dropped proxy should keep 1000 seconds, got " + endurance);
                drops.forEach(ItemEntity::discard);
            });

            // Grafts: each only after the step before, and the Vein makes hunger drain faster outside the void
            server.runCommand("gamemode survival @a");
            context.waitTicks(5);
            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                var marrow = new ItemStack(VoidHeartItems.VOID_MARROW);
                marrow.finishUsingItem(player.level(), player);
                if (knowledge(minecraft).getAttunement() != 1 || marrow.isEmpty())
                    throw new AssertionError("The Marrow should be refused before the Vein");

                var vein = new ItemStack(VoidHeartItems.VOID_VEIN);
                vein.finishUsingItem(player.level(), player);
                if (knowledge(minecraft).getAttunement() != 2 || !vein.isEmpty())
                    throw new AssertionError("The Vein should be eaten and raise attunement to 2, got " + knowledge(minecraft).getAttunement());

                float before = exhaustion(player);
                player.causeFoodExhaustion(1);
                float exhaustion = exhaustion(player) - before;
                if (Math.abs(exhaustion - 1.5F) > 1E-4)
                    throw new AssertionError("Hunger should drain 1.5 times as fast outside the void, got " + exhaustion);

                marrow.finishUsingItem(player.level(), player);
                if (knowledge(minecraft).getAttunement() != 3)
                    throw new AssertionError("The Marrow should raise attunement to 3");
                player.removeAllEffects();
            });
            server.runCommand("gamemode creative @a");
            context.waitTicks(40);

            // With attunement 3 the strain of 20 is borne unaided, and the tablet says so
            server.runOnServer(minecraft ->
            {
                if (knowledge(minecraft).isStrained(Knowledge.rules(), Knowledge.tree(minecraft.registryAccess())))
                    throw new AssertionError("Attunement 3 should bear Watcher Unbound alone");
            });

            context.getInput().pressKey(InputConstants.KEY_F1);
            context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
            aimFrom(context, server, 0.5, GROUND + 1, 2.5, 0.5, GROUND + 1.5, 6, 10);
            context.takeScreenshot("upgrades-items-hotbar");
            context.runOnClient(client -> client.player.getInventory().setSelectedSlot(1));
            context.waitTicks(10);
            context.takeScreenshot("upgrades-proxy-in-hand");

            // The tablet once the research is borne unaided
            openTablet(context, server, "borne");
        }
    }

    /** Opens the repository with an empty hand, selects Watcher Unbound and captures its tablet and the knowledge card. */
    private static void openTablet(ClientGameTestContext context, TestServerContext server, String name)
    {
        context.runOnClient(client -> client.player.getInventory().setSelectedSlot(8));
        context.waitTicks(3);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            var hit = new BlockHitResult(Vec3.atCenterOf(REPOSITORY), Direction.UP, REPOSITORY, false);
            player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        });
        context.waitFor(client -> client.gui.screen() instanceof ResearchScreen, 100);
        context.waitTicks(30);
        click(context, context.computeOnClient(client -> ((ResearchScreen) client.gui.screen()).nodePosition(SHOULDER_EYE)));
        context.waitTicks(10);
        context.takeScreenshot("upgrades-tablet-" + name);
        hover(context, context.computeOnClient(client -> ((ResearchScreen) client.gui.screen()).readoutPosition()));
        context.waitTicks(15);
        context.takeScreenshot("upgrades-card-" + name);
        context.runOnClient(client -> client.player.closeContainer());
        context.waitTicks(5);
    }

    /** FoodData has no getter for it, and the test runs with the names it is compiled with. */
    private static float exhaustion(ServerPlayer player)
    {
        try
        {
            var field = net.minecraft.world.food.FoodData.class.getDeclaredField("exhaustionLevel");
            field.setAccessible(true);
            return field.getFloat(player.getFoodData());
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError("Could not read the exhaustion of the player", e);
        }
    }

    private static net.minecraft.world.level.block.entity.BlockEntity tile(MinecraftServer minecraft, BlockPos pos)
    {
        return player(minecraft).level().getBlockEntity(pos);
    }
}
