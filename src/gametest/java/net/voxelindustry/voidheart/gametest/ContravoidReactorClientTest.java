package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceStorage;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousRelayTile;
import net.voxelindustry.voidheart.common.energy.VoidWounds;
import net.voxelindustry.voidheart.common.energy.VoidWoundsProvider;
import net.voxelindustry.voidheart.common.energy.grid.VoidGrid;
import net.voxelindustry.voidheart.common.energy.grid.VoidGridBalance;
import net.voxelindustry.voidheart.common.energy.grid.VoidGridContent;
import net.voxelindustry.voidheart.common.energy.reactor.ContravoidReactorContent;
import net.voxelindustry.voidheart.common.energy.reactor.ContravoidReactorTile;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorBalance;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorContainmentBlock;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorCycle.Cause;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorCycle.Phase;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorEssence;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorIntakeTile;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorLayout;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorLayout.Slit;
import net.voxelindustry.voidheart.common.energy.storage.ConstructContents;
import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout;
import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout.Size;
import net.voxelindustry.voidheart.common.energy.storage.RubrumConstructTile;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import net.voxelindustry.voidheart.common.energy.storage.SocketBundle;
import net.voxelindustry.voidheart.common.energy.storage.SocketSet;
import net.voxelindustry.voidheart.common.ley.Ley;
import net.voxelindustry.voidheart.common.ley.LeyLattice;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * W10 Contravoid reactor with real blocks: built around a core whose ghosts show the missing shell, refused while incomplete, lit by a real click
 * with a rubrum construct's charge, then fed Contravoid by a relay and Fiery by the review command. Its output needs all three large pylons around it.
 * It warns when its essence runs dry and recovers when refilled, warns on a breach and recovers when the block is set back, shuts down safely from
 * redstone halfway through a countdown, freezes while its chunk is unloaded, and finally fails: the core gives way to a void wound, the nearest ley
 * node takes the failure's taint, and the fuel inside is lost.
 * Run it alone with -PgameTest=ContravoidReactor.
 */
public class ContravoidReactorClientTest implements FabricClientGameTest
{
    private static final int F = FLAT_FEET;

    private static final BlockPos CORE = new BlockPos(0, F + 1, 0);
    // The axis runs along X: the Contravoid intake to the west, the essence intake to the east
    private static final BlockPos WEST_INTAKE = CORE.west();
    private static final BlockPos EAST_INTAKE = CORE.east();
    private static final BlockPos BREACHED    = CORE.offset(1, 1, 1);
    private static final BlockPos REDSTONE    = CORE.offset(1, -1, 2);

    private static final List<BlockPos> PYLONS = List.of(new BlockPos(-5, F, 5), new BlockPos(5, F, 5), new BlockPos(0, F, -6));
    private static final BlockPos CONSTRUCT = new BlockPos(0, F + 1, 9);
    private static final BlockPos COLLECTOR = new BlockPos(-7, F, 0);
    private static final BlockPos RELAY     = new BlockPos(-4, F, 0);

    private static final long CHARGE     = 250_000;
    private static final int  MEASURED   = 40;
    private static final int  ESSENCE    = 1_000;
    private static final int  FAR        = 3_000;

    private static final List<String> WOUNDS = new CopyOnWriteArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context)
    {
        EnergyTestKit.expectRegistered("contravoid_reactor_core", "reactor_containment", "reactor_intake");

        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamemode creative @a");
            context.waitTicks(10);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            // Records every wound opened, then hands it to whichever provider is installed: the default only logs until the Void Fissure lands
            var previous = server.computeOnServer(minecraft -> VoidWounds.provider());
            server.runOnServer(minecraft -> VoidWounds.install(recording(previous)));
            try
            {
                buildAroundCore(context, server);
                buildPylonsAndFeed(context, server);
                lightWithConstructCharge(context, server);
                checkOutputNeedsSeveralPylons(context, server);
                starveAndRecover(context, server);
                breachAndRecover(context, server);
                shutDownMidCountdown(context, server);
                freezeWhileUnloaded(context, server, () -> world.getConnection().waitForChunksRender());
                failInward(context, server);
            } finally
            {
                server.runOnServer(minecraft -> VoidWounds.install(previous));
            }
        }
    }

    private static VoidWoundsProvider recording(VoidWoundsProvider previous)
    {
        return (level, pos, size) ->
        {
            WOUNDS.add(pos.toShortString() + " size " + size);
            previous.open(level, pos, size);
        };
    }

    // Building

    /**
     * A lone core shows the whole shell as ghosts, refuses to light while it is incomplete, and opens its slits once whole.
     */
    private static void buildAroundCore(ClientGameTestContext context, TestServerContext server)
    {
        // Out of the way of the core, which goes where the world spawns players
        aimFrom(context, server, 4.5, F, -3.5, 0.5, F + 1, 0.5, 3);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ContravoidReactorContent.CONTRAVOID_REACTOR_CORE));
            minecraft.overworld().setBlockAndUpdate(CORE.below(), Blocks.STONE.defaultBlockState());
            useOn(player, CORE.below());
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            expect(minecraft.overworld().getBlockEntity(CORE) instanceof ContravoidReactorTile, "The core should be placed at " + CORE.toShortString());
            minecraft.overworld().setBlockAndUpdate(CORE.below(), Blocks.AIR.defaultBlockState());
        });
        server.runOnServer(minecraft -> minecraft.overworld().setBlockAndUpdate(CORE.below(), ContravoidReactorContent.REACTOR_CONTAINMENT.defaultBlockState()));

        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, 5.5, F + 2.5, -4.5, 0.5, F + 1, 0.5, 10);
        context.takeScreenshot("reactor-preview-core");
        context.getInput().pressKey(InputConstants.KEY_F1);

        // Half the shell, then a real click on it: refused, and nothing lights
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            for (var expected : ReactorLayout.shell(Direction.Axis.X))
            {
                if (expected.offset().getY() < 1)
                    level.setBlockAndUpdate(CORE.offset(expected.offset()), stateFor(expected.part()).defaultBlockState());
            }
        });
        aimFrom(context, server, 4.5, F, 0.5, 2, F + 1.5, 0.5, 5);
        expectClient(context, "The player should be looking at the east intake", minecraft -> minecraft.hitResult instanceof BlockHitResult hit
                && hit.getBlockPos().equals(EAST_INTAKE));
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.takeScreenshot("reactor-preview-half");
        context.getInput().pressKey(InputConstants.KEY_F1);
        rightClick(context);
        var refusal = overlay(context);
        expect(refusal.contains("incomplete") && refusal.contains("9"), "Using a half-built reactor should say 9 blocks are missing, said \"" + refusal + "\"");
        server.runOnServer(minecraft -> expect(core(minecraft).phase() == Phase.COLD, "A half-built reactor should stay cold, it is " + core(minecraft).phase()));

        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            for (var expected : ReactorLayout.shell(Direction.Axis.X))
                level.setBlockAndUpdate(CORE.offset(expected.offset()), stateFor(expected.part()).defaultBlockState());
        });
        waitFor(context, server, 40, "The whole shell should complete the reactor", minecraft -> core(minecraft).isComplete());
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            for (var direction : Direction.values())
            {
                var offset = BlockPos.ZERO.relative(direction);
                var expected = ReactorLayout.slitAt(Direction.Axis.X, offset);
                var state = level.getBlockState(CORE.offset(offset));
                if (state.is(ContravoidReactorContent.REACTOR_CONTAINMENT))
                    expect(state.getValue(ReactorContainmentBlock.SLIT) == expected, "The " + direction + " face should show slit " + expected + ", shows "
                            + state.getValue(ReactorContainmentBlock.SLIT));
                else
                    expect(expected == Slit.NONE && state.is(ContravoidReactorContent.REACTOR_INTAKE), "An intake should stand " + direction + " of the core");
            }
        });
    }

    private static Block stateFor(ReactorLayout.Part part)
    {
        return part == ReactorLayout.Part.INTAKE ? ContravoidReactorContent.REACTOR_INTAKE : ContravoidReactorContent.REACTOR_CONTAINMENT;
    }

    /**
     * Three large pylons around the reactor, a construct holding enough to light it, and a relay carrying Contravoid from a collector into the west
     * intake. The east intake then takes Fiery from the review command, and no Contravoid.
     */
    private static void buildPylonsAndFeed(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            for (var pylon : PYLONS)
                placePylon(minecraft, pylon);

            level.setBlockAndUpdate(CONSTRUCT, RubrumContent.RUBRUM_CONSTRUCT_CORE.defaultBlockState());
            for (var offset : ConstructLayout.frame(Size.SMALL))
                level.setBlock(CONSTRUCT.offset(offset), RubrumContent.RUBRUM_CONSTRUCT_FRAME.defaultBlockState(), Block.UPDATE_ALL);

            level.setBlockAndUpdate(COLLECTOR, VoidHeartBlocks.RAVENOUS_COLLECTOR.defaultBlockState());
            var collector = collector(minecraft);
            collector.storage().attune(EssenceRegistry.CONTRAVOID);
            collector.storage().fill(EssenceRegistry.CONTRAVOID, RavenousCollectorTile.CAPACITY);
            level.setBlockAndUpdate(RELAY, VoidHeartBlocks.RAVENOUS_RELAY.defaultBlockState());
        });
        waitFor(context, server, 40, "The construct should form", minecraft -> construct(minecraft).isFormed());
        server.runOnServer(minecraft -> construct(minecraft).restore(new ConstructContents(CHARGE, List.of(
                new SocketSet.Entry(0, SocketBundle.LARGE_RUBRUM),
                new SocketSet.Entry(1, SocketBundle.LARGE_AZURIUM),
                new SocketSet.Entry(2, SocketBundle.LARGE_AZURIUM)))));
        waitFor(context, server, 80, "The grid should find all three pylons covering the core and the construct", minecraft ->
                VoidGrid.pylonsCovering(minecraft.overworld(), CORE).size() == 3 && VoidGrid.pylonsCovering(minecraft.overworld(), CONSTRUCT).size() == 3);

        waitFor(context, server, 400, "The relay should carry Contravoid into the west intake", minecraft -> intake(minecraft, WEST_INTAKE).holdsContravoid());
        server.runOnServer(minecraft ->
        {
            expect(minecraft.overworld().getBlockEntity(RELAY) instanceof RavenousRelayTile relay && relay.linkedReceivers().contains(WEST_INTAKE),
                    "The relay should link to the west intake");
            expect(intake(minecraft, EAST_INTAKE).spaceFor(EssenceRegistry.CONTRAVOID) == 0, "The east intake should refuse Contravoid while the west holds it");
            expect(intake(minecraft, WEST_INTAKE).amountOf(EssenceRegistry.CONTRAVOID) == 0, "An intake should give nothing back to the relays");
        });
        fillFiery(server, ESSENCE);
        server.runOnServer(minecraft -> expect(intake(minecraft, EAST_INTAKE).held() == EssenceRegistry.FIERY,
                "The fill command should put Fiery in the east intake, it holds " + intake(minecraft, EAST_INTAKE).held()));
    }

    private static void placePylon(MinecraftServer minecraft, BlockPos pylon)
    {
        var level = minecraft.overworld();
        level.setBlockAndUpdate(pylon, VoidGridContent.AZURIUM_PYLON.defaultBlockState());
        level.setBlockAndUpdate(pylon.above(), VoidHeartBlocks.LARGE_AZURIUM_BUNDLE.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
    }

    // Lighting and output

    /**
     * A real click on the shell lights it: the arcs run in from the pylons while it draws its ignition from the construct, then it lights.
     */
    private static void lightWithConstructCharge(ClientGameTestContext context, TestServerContext server)
    {
        long before = server.computeOnServer(minecraft -> construct(minecraft).stored());
        aimFrom(context, server, 4.5, F, 0.5, 2, F + 1.5, 0.5, 3);
        rightClick(context);
        server.runOnServer(minecraft -> expect(core(minecraft).phase() == Phase.IGNITING, "A click on the whole, fuelled shell should start the ignition, it is "
                + core(minecraft).phase()));

        context.waitTicks(60);
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, 9, F + 6, -9, 0, F + 1.5, 1, 5);
        context.takeScreenshot("reactor-igniting-arcs");
        aimFrom(context, server, 0.5, F + 4.5, 0.5, 0.5, F + 1.5, 0.5, 3);
        context.takeScreenshot("reactor-igniting-sparks-top");
        context.getInput().pressKey(InputConstants.KEY_F1);
        expectClient(context, "The client should see the reactor igniting with arcs to its pylons", minecraft ->
                minecraft.level.getBlockEntity(CORE) instanceof ContravoidReactorTile tile && tile.phase() == Phase.IGNITING && !tile.animation().arcs().isEmpty());

        waitFor(context, server, 1_500, () -> "The reactor should light once it drew " + ReactorBalance.IGNITION + " VE", minecraft -> core(minecraft).phase() == Phase.LIT);
        server.runOnServer(minecraft ->
        {
            var drawn = before - construct(minecraft).stored();
            // The reactor starts charging it back the moment it lights, and the last draw delivered ahead may still be on its way
            expect(Math.abs(drawn - ReactorBalance.IGNITION) <= 2_000,
                    "Lighting should take " + ReactorBalance.IGNITION + " VE from the construct, it took " + drawn);
        });
    }

    /**
     * With the construct taking everything, it burns at its fullest: 400 VE a tick with Fiery, more than one large pylon passes. With two of the three
     * bundles taken off, the one pylon left caps what it gives.
     */
    private static void checkOutputNeedsSeveralPylons(ClientGameTestContext context, TestServerContext server)
    {
        context.waitTicks(20);
        long[] start = server.computeOnServer(minecraft -> new long[]{minecraft.overworld().getGameTime(), construct(minecraft).stored(),
                intake(minecraft, EAST_INTAKE).amount()});
        context.waitTicks(MEASURED);
        long[] end = server.computeOnServer(minecraft -> new long[]{minecraft.overworld().getGameTime(), construct(minecraft).stored(),
                intake(minecraft, EAST_INTAKE).amount(), core(minecraft).lastOutput()});
        long ticks = end[0] - start[0];
        long full = ReactorEssence.FIERY.fullOutput();
        long charged = end[1] - start[1];
        long burned = start[2] - end[2];
        expect(end[3] == full, "With room to fill, it should give its fullest " + full + " VE a tick, gave " + end[3]);
        expect(full > VoidGridBalance.LARGE_CAP, "Its fullest output should be more than one large pylon carries");
        expect(Math.abs(charged - full * ticks) <= full * 2, "The construct should take all " + full + " VE a tick through the three pylons, took " + charged
                + " in " + ticks + " ticks");
        expect(Math.abs(burned - 2 * ticks) <= 2, "It should cancel two pairs a tick, burned " + burned + " Fiery in " + ticks + " ticks");

        showLit(context, server);

        server.runOnServer(minecraft ->
        {
            for (var pylon : PYLONS.subList(1, 3))
                minecraft.overworld().setBlockAndUpdate(pylon.above(), Blocks.AIR.defaultBlockState());
        });
        waitFor(context, server, 80, "Only one pylon should cover the core once two bundles are gone", minecraft ->
                VoidGrid.pylonsCovering(minecraft.overworld(), CORE).size() == 1);
        context.waitTicks(20);
        long most = 0;
        for (int tick = 0; tick < 20; tick++)
        {
            most = Math.max(most, server.computeOnServer(minecraft -> core(minecraft).lastOutput()));
            context.waitTick();
        }
        expect(most <= VoidGridBalance.LARGE_CAP && most > 0, "One large pylon should cap the output at " + VoidGridBalance.LARGE_CAP
                + " VE a tick, it gave up to " + most);

        server.runOnServer(minecraft ->
        {
            for (var pylon : PYLONS.subList(1, 3))
                placePylon(minecraft, pylon);
        });
        waitFor(context, server, 80, "All three pylons should cover the core again", minecraft -> VoidGrid.pylonsCovering(minecraft.overworld(), CORE).size() == 3);
        fillFiery(server, ESSENCE);
    }

    private static void showLit(ClientGameTestContext context, TestServerContext server)
    {
        expectClient(context, "The client should see it lit, burning Fiery, with an arc to each pylon", minecraft ->
                minecraft.level.getBlockEntity(CORE) instanceof ContravoidReactorTile tile && tile.phase() == Phase.LIT && tile.burning() == ReactorEssence.FIERY
                        && tile.animation().arcs().size() == 3 && tile.contravoidSide() == 0);

        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, 9, F + 6, -9, 0, F + 1.5, 1, 5);
        context.takeScreenshot("reactor-lit-wide");
        // Level with the south slit, the only height its depth lets the eye see the point through, then down through the top one
        aimFrom(context, server, 0.5, F, 3.6, 0.5, F + 1.5, 0.5, 3);
        context.takeScreenshot("reactor-lit-slit-side");
        aimFrom(context, server, 0.6, F + 3.4, 0.5, 0.5, F + 1.5, 0.5, 3);
        context.takeScreenshot("reactor-lit-slit-top");

        server.runCommand("tick freeze");
        for (int step = 0; step < 6; step++)
        {
            aimFrom(context, server, 0.3 + step * 0.08, F + step * 0.01, 3.4, 0.5, F + 1.5, 0.5, 2);
            context.takeScreenshot("reactor-lit-sweep-" + step);
        }
        server.runCommand("tick unfreeze");
        context.getInput().pressKey(InputConstants.KEY_F1);

        aimFrom(context, server, 4.5, F, 0.5, 2, F + 1.5, 0.5, 30);
        context.takeScreenshot("reactor-jade-intake");
        aimFrom(context, server, 0.5, F, 4.5, 0.5, F + 0.5, 2, 30);
        context.takeScreenshot("reactor-jade-containment");
    }

    // Mistakes

    /**
     * Its essence running dry starts the warning, and refilling it ends it.
     */
    private static void starveAndRecover(ClientGameTestContext context, TestServerContext server)
    {
        starveEssence(context, server);
        int[] counted = new int[2];
        counted[0] = server.computeOnServer(minecraft -> core(minecraft).cycle().countdown());
        context.waitTicks(MEASURED);
        counted[1] = server.computeOnServer(minecraft -> core(minecraft).cycle().countdown());
        expect(counted[0] - counted[1] >= MEASURED - 2 && counted[0] - counted[1] <= MEASURED + 2, "The countdown should run a tick at a time, it went from "
                + counted[0] + " to " + counted[1] + " in " + MEASURED + " ticks");
        server.runOnServer(minecraft -> expect(core(minecraft).lastOutput() == 0, "A dry reactor should give nothing"));

        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, 6, F + 3, -5, 0.5, F + 1, 0.5, 3);
        context.takeScreenshot("reactor-warning-dry-early");
        aimFrom(context, server, 0.5, F, 3.6, 0.5, F + 1.5, 0.5, 3);
        context.takeScreenshot("reactor-warning-dry-slit");
        context.getInput().pressKey(InputConstants.KEY_F1);

        fillFiery(server, ESSENCE);
        waitFor(context, server, 5, "Refilling the essence should end the warning", minecraft -> core(minecraft).phase() == Phase.LIT);
        context.waitTicks(10);
        server.runOnServer(minecraft -> expect(core(minecraft).lastOutput() > 0, "A recovered reactor should give energy again"));
    }

    /**
     * Breaking a block of the containment while lit starts the warning, and setting it back ends it.
     */
    private static void breachAndRecover(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft -> minecraft.overworld().destroyBlock(BREACHED, true));
        waitFor(context, server, 5, "A breach should start the warning", minecraft -> core(minecraft).phase() == Phase.FAILING
                && Cause.BREACH.in(core(minecraft).cycle().causes()));
        context.waitTicks(20);
        server.runOnServer(minecraft -> expect(core(minecraft).lastOutput() > 0, "A breached reactor still burns while it warns"));

        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, 5, F + 4, 5, 1.5, F + 2.5, 1.5, 3);
        context.takeScreenshot("reactor-warning-breach");
        context.getInput().pressKey(InputConstants.KEY_F1);

        server.runOnServer(minecraft -> minecraft.overworld().setBlockAndUpdate(BREACHED, ContravoidReactorContent.REACTOR_CONTAINMENT.defaultBlockState()));
        waitFor(context, server, 5, "Setting the block back should end the warning", minecraft -> core(minecraft).phase() == Phase.LIT);
    }

    /**
     * A redstone signal halfway through a countdown shuts it down, and it never fails afterwards.
     */
    private static void shutDownMidCountdown(ClientGameTestContext context, TestServerContext server)
    {
        starveEssence(context, server);
        context.waitTicks(ReactorBalance.COUNTDOWN / 2);
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, 6, F + 3, -5, 0.5, F + 1, 0.5, 2);
        context.takeScreenshot("reactor-warning-dry-half");
        context.getInput().pressKey(InputConstants.KEY_F1);

        server.runOnServer(minecraft -> minecraft.overworld().setBlockAndUpdate(REDSTONE, Blocks.REDSTONE_BLOCK.defaultBlockState()));
        waitFor(context, server, 5, "A redstone signal should shut it down", minecraft -> core(minecraft).phase() == Phase.COLD);
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, 0.5, F, 3.6, 0.5, F + 1.5, 0.5, 15);
        context.takeScreenshot("reactor-shutdown-draining");
        context.getInput().pressKey(InputConstants.KEY_F1);

        context.waitTicks(ReactorBalance.COUNTDOWN);
        server.runOnServer(minecraft ->
        {
            expect(core(minecraft).phase() == Phase.COLD, "A shut down reactor should stay cold, it is " + core(minecraft).phase());
            expect(WOUNDS.isEmpty(), "A shutdown should never open a wound, opened " + WOUNDS);
        });

        // Held down by redstone, it refuses to light
        server.runOnServer(minecraft ->
        {
            fill(minecraft, EssenceRegistry.FIERY, ESSENCE);
            core(minecraft).use(player(minecraft));
            expect(core(minecraft).phase() == Phase.COLD, "Redstone should keep it from lighting");
            minecraft.overworld().setBlockAndUpdate(REDSTONE, Blocks.AIR.defaultBlockState());
        });
    }

    /**
     * Mid countdown, with every player gone far away, the reactor's chunk stops ticking and its countdown holds well past when it would have failed.
     */
    private static void freezeWhileUnloaded(ClientGameTestContext context, TestServerContext server, Runnable waitForChunks)
    {
        relight(context, server);
        starveEssence(context, server);
        int before = server.computeOnServer(minecraft -> core(minecraft).cycle().countdown());

        tp(server, FAR, F + 40, FAR);
        waitFor(context, server, 600, "The reactor's chunk should stop ticking once every player is far away", minecraft ->
                !minecraft.overworld().shouldTickBlocksAt(CORE));
        int read = server.computeOnServer(minecraft -> savedCountdown(minecraft));
        // Out of memory already: the last value read while it ticked is an upper bound
        int frozen = read >= 0 ? read : before;
        context.waitTicks(ReactorBalance.COUNTDOWN + 100);
        server.runOnServer(minecraft ->
        {
            expect(WOUNDS.isEmpty(), "An unloaded reactor should never fail, a wound opened at " + WOUNDS);
            var held = savedCountdown(minecraft);
            expect(held == -1 || held == read, "The countdown should hold while unloaded, it went from " + read + " to " + held);
        });

        tp(server, 4.5, F, 0.5);
        waitForChunks.run();
        waitFor(context, server, 200, "The reactor should tick again once the player is back", minecraft -> minecraft.overworld().shouldTickBlocksAt(CORE)
                && minecraft.overworld().getBlockEntity(CORE) instanceof ContravoidReactorTile);
        server.runOnServer(minecraft ->
        {
            var core = core(minecraft);
            var left = core.cycle().countdown();
            expect(core.phase() == Phase.FAILING, "Back in range, the reactor should still be warning, it is " + core.phase());
            expect(left <= frozen && left >= frozen - 40, "The countdown should go on from where it froze (" + frozen + ", " + before + " when the player left), it is at " + left);
            minecraft.overworld().setBlockAndUpdate(REDSTONE, Blocks.REDSTONE_BLOCK.defaultBlockState());
        });
        waitFor(context, server, 5, "Redstone should shut it down safely", minecraft -> core(minecraft).phase() == Phase.COLD);
        server.runOnServer(minecraft -> minecraft.overworld().setBlockAndUpdate(REDSTONE, Blocks.AIR.defaultBlockState()));
    }

    /**
     * The countdown the reactor holds, read without loading its chunk: -1 while the chunk is out of memory.
     */
    private static int savedCountdown(MinecraftServer minecraft)
    {
        var chunk = minecraft.overworld().getChunkSource().getChunkNow(CORE.getX() >> 4, CORE.getZ() >> 4);
        if (chunk == null)
            return -1;
        return chunk.getBlockEntity(CORE) instanceof ContravoidReactorTile core ? core.cycle().countdown() : -1;
    }

    /**
     * Starved past its countdown, the light collapses and the core gives way to a void wound, the nearest node takes the failure's taint and the fuel
     * inside is lost.
     */
    private static void failInward(ClientGameTestContext context, TestServerContext server)
    {
        relight(context, server);
        // No more Contravoid arrives, so what the intakes hold is all that can be lost
        server.runOnServer(minecraft -> minecraft.overworld().setBlockAndUpdate(RELAY, Blocks.AIR.defaultBlockState()));
        var node = server.computeOnServer(minecraft -> LeyLattice.get(minecraft.overworld()).flatMap(lattice -> lattice.nearestNode(CORE.getX(), CORE.getZ()))
                .orElseThrow(() -> new AssertionError("Expected a ley node near the reactor")));
        float taintBefore = server.computeOnServer(minecraft -> Ley.taint(minecraft.overworld(), node.id()));

        starveEssence(context, server);
        waitFor(context, server, ReactorBalance.COUNTDOWN + 10, "The countdown should run out", minecraft -> core(minecraft).phase() == Phase.COLLAPSING);
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, 0.5, F, 3.6, 0.5, F + 1.5, 0.5, 4);
        context.takeScreenshot("reactor-collapsing");
        context.getInput().pressKey(InputConstants.KEY_F1);

        waitFor(context, server, ReactorBalance.COLLAPSE + 10, "The collapse should end in a failure", minecraft ->
                !minecraft.overworld().getBlockState(CORE).is(ContravoidReactorContent.CONTRAVOID_REACTOR_CORE));
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            expect(WOUNDS.equals(List.of(CORE.toShortString() + " size " + ReactorBalance.WOUND_SIZE)), "One wound of size " + ReactorBalance.WOUND_SIZE
                    + " should open where the core stood, opened " + WOUNDS);
            var taint = Ley.taint(level, node.id());
            expect(taint >= Math.min(100, taintBefore + ReactorBalance.FAILURE_TAINT) - 1, "The nearest node should take " + ReactorBalance.FAILURE_TAINT
                    + " taint, went from " + taintBefore + " to " + taint);
            expect(intake(minecraft, WEST_INTAKE).amount() == 0 && intake(minecraft, EAST_INTAKE).amount() == 0, "The fuel inside should be lost");
            var standing = ReactorLayout.shell(Direction.Axis.X).stream()
                    .filter(expected -> level.getBlockState(CORE.offset(expected.offset())).is(stateFor(expected.part()))).count();
            expect(standing == 26, "Nothing should blast: all 26 blocks of the shell should still stand, " + standing + " do");
            for (var direction : Direction.values())
            {
                var state = level.getBlockState(CORE.relative(direction));
                if (state.is(ContravoidReactorContent.REACTOR_CONTAINMENT))
                    expect(state.getValue(ReactorContainmentBlock.SLIT) == Slit.NONE, "The slits should close once the core is gone");
            }
        });
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, 6, F + 3, -5, 0.5, F + 1, 0.5, 10);
        context.takeScreenshot("reactor-failed");
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    // Helpers

    private static void relight(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            construct(minecraft).setStored(CHARGE);
            fill(minecraft, EssenceRegistry.FIERY, ESSENCE);
            collector(minecraft).storage().fill(EssenceRegistry.CONTRAVOID, RavenousCollectorTile.CAPACITY);
            core(minecraft).use(player(minecraft));
        });
        waitFor(context, server, 1_500, "The reactor should light again", minecraft -> core(minecraft).phase() == Phase.LIT);
    }

    /**
     * Empties the essence intake at once, as a source running dry would.
     */
    private static void starveEssence(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft -> intake(minecraft, EAST_INTAKE).restore(new EssenceStorage(EssenceRegistry.EMPTY, ReactorBalance.INTAKE_TANK)));
        waitFor(context, server, 20, "An essence intake running dry should start the warning", minecraft -> core(minecraft).phase() == Phase.FAILING
                && Cause.ESSENCE_DRY.in(core(minecraft).cycle().causes()));
    }

    private static void fillFiery(TestServerContext server, int amount)
    {
        server.runCommand("voidheart energy reactor fill " + EAST_INTAKE.getX() + " " + EAST_INTAKE.getY() + " " + EAST_INTAKE.getZ() + " fiery " + amount);
    }

    private static void fill(MinecraftServer minecraft, net.voxelindustry.voidheart.common.content.essence.EssenceType type, int amount)
    {
        core(minecraft).fill(type, amount);
    }

    private static ContravoidReactorTile core(MinecraftServer minecraft)
    {
        if (minecraft.overworld().getBlockEntity(CORE) instanceof ContravoidReactorTile core)
            return core;
        throw new AssertionError("No Contravoid Reactor at " + CORE.toShortString());
    }

    private static ReactorIntakeTile intake(MinecraftServer minecraft, BlockPos pos)
    {
        if (minecraft.overworld().getBlockEntity(pos) instanceof ReactorIntakeTile intake)
            return intake;
        throw new AssertionError("No Reactor Intake at " + pos.toShortString());
    }

    private static RubrumConstructTile construct(MinecraftServer minecraft)
    {
        if (minecraft.overworld().getBlockEntity(CONSTRUCT) instanceof RubrumConstructTile construct)
            return construct;
        throw new AssertionError("No construct at " + CONSTRUCT.toShortString());
    }

    private static RavenousCollectorTile collector(MinecraftServer minecraft)
    {
        if (minecraft.overworld().getBlockEntity(COLLECTOR) instanceof RavenousCollectorTile collector)
            return collector;
        throw new AssertionError("No collector at " + COLLECTOR.toShortString());
    }
}
