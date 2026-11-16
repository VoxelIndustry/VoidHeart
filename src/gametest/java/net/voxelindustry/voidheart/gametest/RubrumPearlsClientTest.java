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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.energy.machine.MachinePower;
import net.voxelindustry.voidheart.common.energy.machine.PowerMeter;
import net.voxelindustry.voidheart.common.energy.pearl.HangingPearlBlock;
import net.voxelindustry.voidheart.common.energy.pearl.HangingPearlTile;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceStorage;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousRelayTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.energy.pearl.PearlBalance;
import net.voxelindustry.voidheart.common.energy.pearl.PearlContent;
import net.voxelindustry.voidheart.common.energy.pearl.PearlGrowth;
import net.voxelindustry.voidheart.common.energy.pearl.PearlTremor;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.gametest.GameTestKit.FLAT_FEET;
import static net.voxelindustry.voidheart.gametest.GameTestKit.aimFrom;
import static net.voxelindustry.voidheart.gametest.GameTestKit.clearArea;
import static net.voxelindustry.voidheart.gametest.GameTestKit.clearScene;
import static net.voxelindustry.voidheart.gametest.GameTestKit.expect;
import static net.voxelindustry.voidheart.gametest.GameTestKit.freezeTime;
import static net.voxelindustry.voidheart.gametest.GameTestKit.player;
import static net.voxelindustry.voidheart.gametest.GameTestKit.waitFor;

/**
 * Collapsing Rubrum Pearls hung from chains in a creative well's field. A kindled seed hangs only under an upright chain. Six pearls in reach of
 * each other grow twice as fast as one alone, a pearl without void energy or Fiery pauses, a pearl whose chain is broken drops its seed carrying its
 * growth, which hangs again where it left off, and a mature pearl is picked with an empty hand. One relay feeds every pearl it links, from a storage of Fiery
 * before its collector. Captures show each stage by day and night, a pearl drinking in a relay's Fiery through threads, a choir shuddering
 * together and the after-images it leaves, and the items.
 */
public class RubrumPearlsClientTest implements FabricClientGameTest
{
    private static final int P = FLAT_FEET + 2;

    // Its field reaches 16 blocks, so every pearl but the starved one stands well inside it
    private static final BlockPos WELL = new BlockPos(-2, FLAT_FEET, 6);

    // Six pearls all within 4 blocks of each other
    private static final List<BlockPos> CHOIR = List.of(
            new BlockPos(-8, P, 0), new BlockPos(-6, P, 0), new BlockPos(-4, P, 0),
            new BlockPos(-7, P, 2), new BlockPos(-5, P, 2), new BlockPos(-6, P, 3));
    private static final BlockPos LONE     = new BlockPos(8, P, 0);
    private static final BlockPos THIRSTY  = new BlockPos(12, P, 4);
    private static final BlockPos SIDEWAYS = new BlockPos(14, P + 1, 6);
    private static final BlockPos GROUND   = new BlockPos(16, FLAT_FEET - 1, 6);

    // One pearl at each stage, in a row away from the choir
    private static final float[]  STAGE_SHARES = {0.1F, 0.4F, 0.65F, 0.9F, 1F};
    private static final int      ROW_Z        = -6;

    // A pearl fed through a relay, far enough from the others that the relay links it alone
    private static final BlockPos FED_PEARL      = new BlockPos(2, P, 14);
    private static final BlockPos FEED_RELAY     = new BlockPos(5, P - 1, 15);
    private static final BlockPos FEED_COLLECTOR = new BlockPos(7, FLAT_FEET, 18);
    private static final int      FEED_FIERY     = 4_000;
    // Two more pearls on the same relay, and a storage of Fiery it feeds them all from before it draws on the collector
    private static final List<BlockPos> MORE_FED     = List.of(new BlockPos(6, P, 18), new BlockPos(9, P, 13));
    private static final BlockPos       FEED_STORAGE = new BlockPos(5, FLAT_FEET, 19);
    private static final int            STORED_FIERY = 20_000;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        var camera = context.computeOnClient(minecraft -> minecraft.options.getCameraType());
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamemode creative @a");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            clearArea(server, 0);
            clearScene(server);
            server.runCommand("setblock " + WELL.getX() + " " + WELL.getY() + " " + WELL.getZ() + " voidheart:creative_void_well");
            context.waitTicks(5);

            hangsOnlyUnderUprightChains(context, server);
            choirGrowsTwiceAsFast(context, server);
            pausesWhenStarved(context, server);
            dropsItsSeedWithItsGrowth(context, server);
            pickedWhenMature(context, server);
            showStages(context, server);
            showFedThroughRelay(context, server);
            showChoirShuddering(context, server);
            showItems(context, server);
            clearScene(server);
        }
        finally
        {
            context.runOnClient(minecraft -> minecraft.options.setCameraType(camera));
        }
    }

    // Placing

    private static void hangsOnlyUnderUprightChains(ClientGameTestContext context, TestServerContext server)
    {
        chain(server, LONE.above(), "y");
        server.runCommand("setblock " + SIDEWAYS.getX() + " " + SIDEWAYS.getY() + " " + SIDEWAYS.getZ() + " iron_chain[axis=x]");
        server.runCommand("tp @a " + (LONE.getX() + 0.5) + " " + FLAT_FEET + " " + (LONE.getZ() - 2.5));
        context.waitTicks(3);

        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            hangFrom(player, LONE.above(), new ItemStack(PearlContent.KINDLED_FRACTAL_SEED));
            hangFrom(player, SIDEWAYS, new ItemStack(PearlContent.KINDLED_FRACTAL_SEED));
            // On the grass's top face, with nothing above to hang from
            var hit = new BlockHitResult(Vec3.atCenterOf(GROUND).add(0, 0.5, 0), Direction.UP, GROUND, false);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PearlContent.KINDLED_FRACTAL_SEED));
            player.gameMode.useItemOn(player, minecraft.overworld(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
        context.waitTicks(2);

        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            expect(level.getBlockState(LONE).is(PearlContent.HANGING_RUBRUM_PEARL), "A kindled seed used under an upright chain should hang there");
            expect(level.getBlockState(SIDEWAYS.below()).isAir(), "A kindled seed should not hang under a sideways chain");
            expect(level.getBlockState(GROUND.above()).isAir(), "A kindled seed should not stand on the ground");
        });
        server.runCommand("setblock " + SIDEWAYS.getX() + " " + SIDEWAYS.getY() + " " + SIDEWAYS.getZ() + " air");
    }

    // Growing

    private static void choirGrowsTwiceAsFast(ClientGameTestContext context, TestServerContext server)
    {
        for (var pos : CHOIR)
            hang(server, pos);
        hang(server, THIRSTY);
        context.waitTicks(3);

        server.runOnServer(minecraft ->
        {
            for (var pos : CHOIR)
                feedFromZero(pearl(minecraft, pos));
            feedFromZero(pearl(minecraft, LONE));
            pearl(minecraft, THIRSTY).setShare(0);
        });
        context.waitTicks(60);

        double[] before = new double[2];
        server.runOnServer(minecraft ->
        {
            for (var pos : CHOIR)
            {
                var choir = pearl(minecraft, pos).choir().size();
                expect(choir == CHOIR.size() - 1, "Each of six pearls in reach should count the other five, " + pos.toShortString() + " counts "
                        + pearl(minecraft, pos).choir() + ", fed " + CHOIR.stream().map(other -> pearl(minecraft, other).isFed()).toList());
            }
            expect(pearl(minecraft, LONE).choir().isEmpty(), "The lone pearl should count no others");
            before[0] = pearl(minecraft, CHOIR.getFirst()).growth().progress();
            before[1] = pearl(minecraft, LONE).growth().progress();
            expect(before[1] > 0, "A fed pearl in the well's field should have grown");
        });

        context.waitTicks(200);
        server.runOnServer(minecraft ->
        {
            var choir = pearl(minecraft, CHOIR.getFirst()).growth().progress() - before[0];
            var lone = pearl(minecraft, LONE).growth().progress() - before[1];
            var ratio = choir / lone;
            expect(lone > 150, "The lone pearl should grow about a tick a tick, it grew " + lone + " in 200");
            expect(ratio > 1.85 && ratio < 2.15, "Six pearls should each grow twice as fast as one alone, the ratio is " + ratio);

            var thirsty = pearl(minecraft, THIRSTY);
            expect(thirsty.growth().progress() == 0, "A pearl without Fiery should not grow, it grew " + thirsty.growth().progress());
            expect(thirsty.isFieryShort() && !thirsty.isFed(), "A pearl without Fiery should say it is thirsty");
        });
    }

    private static void pausesWhenStarved(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("setblock " + WELL.getX() + " " + WELL.getY() + " " + WELL.getZ() + " air");
        // Power is judged a whole second at a time, so the second the well went in still reads weak
        context.waitTicks(2 * PowerMeter.WINDOW + 5);
        double[] before = new double[1];
        server.runOnServer(minecraft ->
        {
            var lone = pearl(minecraft, LONE);
            expect(lone.power() == MachinePower.UNPOWERED, "A pearl out of any field should be unpowered, it is " + lone.power());
            before[0] = lone.growth().progress();
        });
        context.waitTicks(100);
        server.runOnServer(minecraft ->
        {
            var now = pearl(minecraft, LONE).growth().progress();
            expect(now == before[0], "A starved pearl should pause, it went from " + before[0] + " to " + now);
        });
        server.runCommand("setblock " + WELL.getX() + " " + WELL.getY() + " " + WELL.getZ() + " voidheart:creative_void_well");
        context.waitTicks(5);
    }

    // Taking down and picking

    private static void dropsItsSeedWithItsGrowth(ClientGameTestContext context, TestServerContext server)
    {
        double[] grown = new double[1];
        server.runOnServer(minecraft ->
        {
            var lone = pearl(minecraft, LONE);
            lone.setShare(0.4);
            grown[0] = lone.growth().progress();
            // The chain gone, the pearl falls with it
            minecraft.overworld().setBlock(LONE.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        });
        context.waitTicks(5);

        ItemStack[] dropped = new ItemStack[1];
        server.runOnServer(minecraft ->
        {
            expect(minecraft.overworld().getBlockState(LONE).isAir(), "The pearl should fall with its chain");
            var seeds = minecraft.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(LONE).inflate(2),
                    entity -> entity.getItem().is(PearlContent.KINDLED_FRACTAL_SEED));
            expect(seeds.size() == 1, "The fallen pearl should drop its seed, found " + seeds.size());
            var growth = seeds.getFirst().getItem().get(PearlContent.PEARL_GROWTH);
            expect(growth != null && Math.abs(growth - grown[0]) < 20, "The seed should carry the growth it had, " + grown[0] + ", it carries " + growth);
            dropped[0] = seeds.getFirst().getItem().copy();
            seeds.getFirst().discard();
        });

        chain(server, LONE.above(), "y");
        context.waitTicks(2);
        server.runOnServer(minecraft -> hangFrom(player(minecraft), LONE.above(), dropped[0]));
        context.waitTicks(2);
        server.runOnServer(minecraft ->
        {
            var share = pearl(minecraft, LONE).growth().share();
            expect(Math.abs(share - 0.4F) < 0.001F, "A seed hung again should go on from its growth, it is at " + share);
            expect(minecraft.overworld().getBlockState(LONE).getValue(HangingPearlBlock.STAGE) == PearlGrowth.Stage.BUDDING.ordinal(),
                    "A pearl two fifths grown should show its budding stage");
        });
    }

    private static void pickedWhenMature(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft -> pearl(minecraft, LONE).setShare(1));
        context.waitTicks(3);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            expect(level.getBlockState(LONE).getValue(HangingPearlBlock.STAGE) == PearlGrowth.Stage.MATURE.ordinal(), "A grown pearl should be mature");
            expect(pearl(minecraft, LONE).spaceFor(EssenceRegistry.FIERY) == 0,
                    "A mature pearl should take no more Fiery");

            var player = player(minecraft);
            player.getInventory().clearContent();
            var hit = new BlockHitResult(Vec3.atCenterOf(LONE), Direction.NORTH, LONE, false);
            player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
            expect(level.getBlockState(LONE).isAir(), "Picking a mature pearl should take it off its chain");
            expect(player.getInventory().countItem(PearlContent.COLLAPSING_RUBRUM_PEARL) == 1, "Picking a mature pearl should give one Collapsing Rubrum Pearl");
            expect(level.getEntitiesOfClass(ItemEntity.class, new AABB(LONE).inflate(2)).isEmpty(), "A picked pearl should not also drop");
        });
    }

    // Looks

    private static void showStages(ClientGameTestContext context, TestServerContext server)
    {
        for (int i = 0; i < STAGE_SHARES.length; i++)
            hang(server, stagePos(i));
        context.waitTicks(3);
        server.runOnServer(minecraft ->
        {
            for (int i = 0; i < STAGE_SHARES.length; i++)
            {
                var pearl = pearl(minecraft, stagePos(i));
                pearl.fillFiery();
                pearl.setShare(STAGE_SHARES[i]);
            }
        });
        context.waitTicks(40);

        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, 0.5, FLAT_FEET + 1, ROW_Z - 5.5, 0.5, P + 0.6, ROW_Z + 0.5, 5);
        context.takeScreenshot("pearl-stages-noon");
        for (int i = 0; i < STAGE_SHARES.length; i++)
        {
            var pos = stagePos(i);
            aimFrom(context, server, pos.getX() + 0.9, FLAT_FEET + 0.9, pos.getZ() - 1.1, pos.getX() + 0.5, P + 0.6, pos.getZ() + 0.5, 3);
            context.takeScreenshot("pearl-stage-" + i + "-close");
        }

        freezeTime(server, "midnight");
        aimFrom(context, server, 0.5, FLAT_FEET + 1, ROW_Z - 5.5, 0.5, P + 0.6, ROW_Z + 0.5, 5);
        context.takeScreenshot("pearl-stages-midnight");
        var mature = stagePos(STAGE_SHARES.length - 1);
        aimFrom(context, server, mature.getX() + 0.5, FLAT_FEET + 0.9, mature.getZ() - 1.2, mature.getX() + 0.5, P + 0.6, mature.getZ() + 0.5, 3);
        context.takeScreenshot("pearl-mature-midnight-close");
        // The other side, since the rim turns to the camera
        aimFrom(context, server, mature.getX() + 0.5, FLAT_FEET + 0.9, mature.getZ() + 2.2, mature.getX() + 0.5, P + 0.6, mature.getZ() + 0.5, 3);
        context.takeScreenshot("pearl-mature-midnight-back");
        freezeTime(server, "noon");
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    /**
     * A relay carries a collector's Fiery to a pearl, its beam splitting short of the pearl into threads that sink into it. Caught as the threads
     * grow, then formed from beside, from the far side and from behind the relay, by night, and in a short sweep held still.
     */
    private static void showFedThroughRelay(ClientGameTestContext context, TestServerContext server)
    {
        hang(server, FED_PEARL);
        for (var pos : MORE_FED)
            hang(server, pos);
        context.waitTicks(3);
        server.runOnServer(minecraft ->
        {
            pearl(minecraft, FED_PEARL).setShare(0.6);
            pearl(minecraft, MORE_FED.get(0)).setShare(0.3);
            pearl(minecraft, MORE_FED.get(1)).setShare(0.85);
            var level = minecraft.overworld();
            level.setBlockAndUpdate(FEED_COLLECTOR, VoidHeartBlocks.RAVENOUS_COLLECTOR.defaultBlockState());
            collector(minecraft).storage().attune(EssenceRegistry.FIERY);
            collector(minecraft).storage().fill(EssenceRegistry.FIERY, FEED_FIERY);

            var stored = new EssenceStorage(EssenceRegistry.EMPTY, RavenousStorageTile.CAPACITY);
            stored.attune(EssenceRegistry.FIERY);
            stored.fill(EssenceRegistry.FIERY, STORED_FIERY);
            var stack = new ItemStack(VoidHeartBlocks.RAVENOUS_STORAGE);
            stack.set(VoidHeartDataComponents.ESSENCE_STORAGE, stored);
            level.setBlockAndUpdate(FEED_STORAGE, VoidHeartBlocks.RAVENOUS_STORAGE.defaultBlockState());
            VoidHeartBlocks.RAVENOUS_STORAGE.setPlacedBy(level, FEED_STORAGE, VoidHeartBlocks.RAVENOUS_STORAGE.defaultBlockState(), null, stack);

            level.setBlockAndUpdate(FEED_RELAY, VoidHeartBlocks.RAVENOUS_RELAY.defaultBlockState());
        });

        // One relay feeds every pearl it links in the same route, the storage first, so it is drawn down before the collector refills it
        waitFor(context, server, 400, () -> "Every pearl on the relay should be given Fiery, they hold "
                        + server.computeOnServer(minecraft -> fedPearls().stream().map(pos -> pearl(minecraft, pos).fiery()).toList()),
                minecraft -> fedPearls().stream().allMatch(pos -> pearl(minecraft, pos).fiery() > 0));
        server.runOnServer(minecraft ->
        {
            var stored = storage(minecraft).storage().amount();
            expect(stored < STORED_FIERY, "The relay should feed the pearls from the storage, which still holds " + stored);
        });

        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, 2.8, FLAT_FEET + 0.9, 16.5, 2.8, P + 0.62, 14.6, 3);
        // The beam reaches its split about four fifths of the way through its growth, the threads grow on from there
        context.waitFor(minecraft -> feedGrowth(minecraft) >= 0.8F, 400);
        server.runCommand("tick freeze");
        context.takeScreenshot("pearl-feed-forming");
        server.runCommand("tick unfreeze");

        context.waitFor(minecraft -> feedGrowth(minecraft) >= 1, 200);
        context.waitTicks(40);
        server.runOnServer(minecraft ->
        {
            expect(minecraft.overworld().getBlockEntity(FEED_RELAY) instanceof RavenousRelayTile relay && relay.linkedReceivers().containsAll(fedPearls()),
                    "The relay should link every pearl");
            for (var pos : fedPearls())
                expect(pearl(minecraft, pos).isFed(), "Every pearl given Fiery through the relay in the well's field should be fed, "
                        + pos.toShortString() + " is not");
        });

        context.takeScreenshot("pearl-feed-side");
        aimFrom(context, server, 2.6, FLAT_FEET + 0.6, 12.6, 2.8, P + 0.62, 14.6, 3);
        context.takeScreenshot("pearl-feed-far-side");
        aimFrom(context, server, 4.0, FLAT_FEET + 0.7, 15.2, 2.5, P + 0.73, 14.5, 3);
        context.takeScreenshot("pearl-feed-along");

        server.runCommand("tick freeze");
        for (int step = 0; step < 4; step++)
        {
            aimFrom(context, server, 2.8 + step * 0.06, FLAT_FEET + 0.9, 16.2 - step * 0.05, 2.8, P + 0.62, 14.6, 2);
            context.takeScreenshot("pearl-feed-sweep-" + step);
        }
        server.runCommand("tick unfreeze");

        // Close on the split, where the beam parts into threads, from beside and from above
        aimFrom(context, server, 3.1, FLAT_FEET + 0.83, 15.55, 3.18, P + 0.45, 14.73, 6);
        context.takeScreenshot("pearl-feed-split-side");
        aimFrom(context, server, 3.4, FLAT_FEET + 1.48, 13.9, 3.18, P + 0.45, 14.73, 6);
        context.takeScreenshot("pearl-feed-split-above");

        freezeTime(server, "midnight");
        aimFrom(context, server, 2.8, FLAT_FEET + 0.9, 16.5, 2.8, P + 0.62, 14.6, 3);
        context.takeScreenshot("pearl-feed-midnight");
        freezeTime(server, "noon");
        context.getInput().pressKey(InputConstants.KEY_F1);

        // The relay would otherwise go on feeding through the later scenes
        server.runCommand("setblock " + FEED_RELAY.getX() + " " + FEED_RELAY.getY() + " " + FEED_RELAY.getZ() + " air");
    }

    /**
     * How far the relay's beam into the fed pearl has grown on the client, 0 while it does not flow.
     */
    private static float feedGrowth(Minecraft minecraft)
    {
        if (minecraft.level == null || !(minecraft.level.getBlockEntity(FEED_RELAY) instanceof RavenousRelayTile relay))
            return 0;
        var link = relay.getOutgoingStrands(0).get(FED_PEARL);
        return link == null || !link.active() ? 0 : link.growth();
    }

    private static List<BlockPos> fedPearls()
    {
        var pearls = new ArrayList<>(MORE_FED);
        pearls.addFirst(FED_PEARL);
        return pearls;
    }

    private static RavenousStorageTile storage(MinecraftServer minecraft)
    {
        if (minecraft.overworld().getBlockEntity(FEED_STORAGE) instanceof RavenousStorageTile storage)
            return storage;
        throw new AssertionError("Expected a storage at " + FEED_STORAGE.toShortString());
    }

    private static RavenousCollectorTile collector(MinecraftServer minecraft)
    {
        if (minecraft.overworld().getBlockEntity(FEED_COLLECTOR) instanceof RavenousCollectorTile collector)
            return collector;
        throw new AssertionError("Expected a collector at " + FEED_COLLECTOR.toShortString());
    }

    /**
     * Waits until the choir has fallen into step, then for a burst two of them share, and holds it still for a short sweep.
     */
    private static void showChoirShuddering(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            for (var pos : CHOIR)
            {
                pearl(minecraft, pos).fillFiery();
                pearl(minecraft, pos).setShare(0.8);
            }
        });
        context.waitTicks(PearlTremor.SYNC_TICKS + 60);
        context.waitFor(minecraft -> sharedBurst(minecraft) > 0.5F, 1200);
        server.runCommand("tick freeze");

        context.getInput().pressKey(InputConstants.KEY_F1);
        for (int step = 0; step < 4; step++)
        {
            aimFrom(context, server, -6 + step * 0.1, FLAT_FEET + 1.2, -3.2, -5.5, P + 0.5, 1.5, 2);
            context.takeScreenshot("pearl-choir-sweep-" + step);
        }

        // Close on the choir's outer pearl from outside it, its after-images held still, by day and by night; with the choir in step all shudder at once
        var outer = CHOIR.get(2);
        aimFrom(context, server, outer.getX() + 2.1, FLAT_FEET + 0.9, outer.getZ() + 0.5, outer.getX() + 0.5, P + 0.7, outer.getZ() + 0.5, 3);
        context.takeScreenshot("pearl-afterimage-close");
        freezeTime(server, "midnight");
        context.waitTicks(3);
        context.takeScreenshot("pearl-afterimage-midnight");
        freezeTime(server, "noon");
        server.runCommand("tick unfreeze");
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    private static float sharedBurst(Minecraft minecraft)
    {
        if (minecraft.level == null || !(minecraft.level.getBlockEntity(CHOIR.getFirst()) instanceof HangingPearlTile first)
                || !(minecraft.level.getBlockEntity(CHOIR.get(1)) instanceof HangingPearlTile second))
            return 0;
        var time = minecraft.level.getGameTime();
        var a = PearlTremor.at(first.seed(), first.choirSeed(), first.sync(1), time);
        var b = PearlTremor.at(second.seed(), second.choirSeed(), second.sync(1), time);
        return a.shared() && b.shared() ? Math.min(a.intensity(), b.intensity()) : 0;
    }

    private static void showItems(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(PearlContent.COLLAPSING_RUBRUM_PEARL));
            player.getInventory().setItem(1, new ItemStack(PearlContent.KINDLED_FRACTAL_SEED));
            player.getInventory().setItem(2, new ItemStack(PearlContent.FRACTAL_RUBRUM_SEED));
            player.getInventory().setItem(3, grownSeed(0.35));
            player.getInventory().setItem(4, grownSeed(0.6));
            player.getInventory().setItem(5, grownSeed(0.9));
            player.getInventory().setSelectedSlot(0);
        });
        aimFrom(context, server, 0.5, FLAT_FEET, ROW_Z - 4.5, 0.5, P + 0.6, ROW_Z + 0.5, 5);
        context.takeScreenshot("pearl-items-in-hand");
        for (int slot = 1; slot <= 3; slot++)
        {
            var selected = slot == 3 ? 4 : slot;
            // The client picks the held slot and tells the server, not the other way round
            context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(selected));
            context.waitTicks(10);
            context.takeScreenshot("pearl-items-in-hand-slot-" + selected);
        }
        context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(0));
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitTicks(3);
        context.takeScreenshot("pearl-items-inventory");
        context.setScreen(() -> null);

        // Dropped beside the mature pearl still hanging, to compare the item with its block
        var mature = stagePos(STAGE_SHARES.length - 1);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var dropped = List.of(new ItemStack(PearlContent.FRACTAL_RUBRUM_SEED), new ItemStack(PearlContent.KINDLED_FRACTAL_SEED),
                    grownSeed(0.6), new ItemStack(PearlContent.COLLAPSING_RUBRUM_PEARL));
            for (int i = 0; i < dropped.size(); i++)
            {
                var item = new ItemEntity(level, mature.getX() - 0.4 + i * 0.45, FLAT_FEET, mature.getZ() - 0.5, dropped.get(i), 0, 0, 0);
                item.setNeverPickUp();
                level.addFreshEntity(item);
            }
        });
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, mature.getX() + 0.5, FLAT_FEET + 0.4, mature.getZ() - 2.6, mature.getX() + 0.5, FLAT_FEET + 1.2, mature.getZ(), 10);
        context.takeScreenshot("pearl-item-dropped-beside-block");
        context.getInput().pressKey(InputConstants.KEY_F1);
        clearScene(server);
    }

    // Helpers

    private static ItemStack grownSeed(double share)
    {
        var seed = new ItemStack(PearlContent.KINDLED_FRACTAL_SEED);
        seed.set(PearlContent.PEARL_GROWTH, share * PearlBalance.GROWTH_TICKS);
        return seed;
    }

    private static BlockPos stagePos(int i)
    {
        return new BlockPos(-4 + i * 2, P, ROW_Z);
    }

    private static void chain(TestServerContext server, BlockPos top, String axis)
    {
        server.runCommand("setblock " + top.getX() + " " + (top.getY() + 2) + " " + top.getZ() + " voidheart:voidstone_bricks");
        server.runCommand("setblock " + top.getX() + " " + (top.getY() + 1) + " " + top.getZ() + " iron_chain[axis=" + axis + "]");
        server.runCommand("setblock " + top.getX() + " " + top.getY() + " " + top.getZ() + " iron_chain[axis=" + axis + "]");
    }

    private static void hang(TestServerContext server, BlockPos pearl)
    {
        chain(server, pearl.above(), "y");
        server.runCommand("setblock " + pearl.getX() + " " + pearl.getY() + " " + pearl.getZ() + " voidheart:hanging_rubrum_pearl");
    }

    /**
     * Uses a stack on the underside of a chain, as a player hanging a seed does.
     */
    private static void hangFrom(ServerPlayer player, BlockPos chain, ItemStack stack)
    {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atCenterOf(chain).add(0, -0.5, 0), Direction.DOWN, chain, false);
        player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static void feedFromZero(HangingPearlTile pearl)
    {
        pearl.fillFiery();
        pearl.setShare(0);
    }

    private static HangingPearlTile pearl(MinecraftServer minecraft, BlockPos pos)
    {
        if (minecraft.overworld().getBlockEntity(pos) instanceof HangingPearlTile pearl)
            return pearl;
        throw new AssertionError("Expected a hanging pearl at " + pos.toShortString());
    }
}
