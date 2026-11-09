package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.voxelindustry.voidheart.common.content.athanor.AthanorTile;
import net.voxelindustry.voidheart.common.content.athanor.CrucibleTile;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousRelayTile;
import net.voxelindustry.voidheart.common.energy.VoidSeep;
import net.voxelindustry.voidheart.common.energy.fissure.FissureScars;
import net.voxelindustry.voidheart.common.energy.fissure.VoidFissureBalance;
import net.voxelindustry.voidheart.common.energy.fissure.VoidFissureContent;
import net.voxelindustry.voidheart.common.energy.fissure.VoidFissureTile;
import net.voxelindustry.voidheart.common.energy.grid.GridLayout;
import net.voxelindustry.voidheart.common.energy.grid.VoidGrid;
import net.voxelindustry.voidheart.common.energy.grid.VoidGridBalance;
import net.voxelindustry.voidheart.common.energy.grid.VoidGridContent;
import net.voxelindustry.voidheart.common.energy.machine.MachinePower;
import net.voxelindustry.voidheart.common.energy.reactor.ContravoidReactorContent;
import net.voxelindustry.voidheart.common.energy.reactor.ContravoidReactorTile;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorBalance;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorCycle.Cause;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorCycle.Phase;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorEssence;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorIntakeTile;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorLayout;
import net.voxelindustry.voidheart.common.energy.spike.AzuriumSpikeTile;
import net.voxelindustry.voidheart.common.energy.spike.LeySpikeBalance;
import net.voxelindustry.voidheart.common.energy.spike.LeySpikeContent;
import net.voxelindustry.voidheart.common.energy.storage.ConstructContents;
import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout;
import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout.Size;
import net.voxelindustry.voidheart.common.energy.storage.RubrumConstructTile;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import net.voxelindustry.voidheart.common.energy.storage.SocketBundle;
import net.voxelindustry.voidheart.common.energy.storage.SocketSet;
import net.voxelindustry.voidheart.common.ley.Ley;
import net.voxelindustry.voidheart.common.ley.LeyLattice;
import net.voxelindustry.voidheart.common.ley.LeyNode;
import net.voxelindustry.voidheart.common.ley.TaintStage;
import net.voxelindustry.voidheart.common.ley.flow.LatticeLeyFlow;
import net.voxelindustry.voidheart.common.ley.flow.LeyTopology;
import net.voxelindustry.voidheart.common.ley.taint.LeyTaint;
import net.voxelindustry.voidheart.common.ley.taint.LeyTaintState;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.List;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The second wave of the energy system working together, with real blocks. An Azurium Spike on a ley node made Festering charges a rubrum construct
 * through a pylon and fills a collector attuned to Contravoid with an azurium shard. Relays carry that Contravoid, and Fiery from a collector attuned
 * with a lava bucket, into the two intakes of a Contravoid reactor, which a real click lights from the construct's charge. Lit, it powers an athanor's
 * brew at the far end of a chain of pylons. Its Fiery then runs out, nothing refills it, and once the countdown ends a void wound takes the core's
 * place inside the standing shell while the nearest node takes the failure's taint. Opened from the side, the wound takes Contravoid from a relay
 * until it draws shut.
 * Run it alone with -PgameTest=EnergyWave2.
 */
public class EnergyWave2ClientTest implements FabricClientGameTest
{
    private static final int F = FLAT_FEET;

    private static final int   MEASURED  = 40;
    private static final int   SAMPLES   = 200;
    private static final float FESTERING = 60;
    // Enough to light it, as a construct a base has been filling would hold
    private static final long  CHARGE    = 250_000;
    // All the Fiery the reactor ever gets: it burns through it in well under a minute, and nothing refills it
    private static final int   FIERY     = 1_500;

    // Sums what the spike gives each tick, read after the block entities ticked
    private static volatile BlockPos tracked;
    private static double spikeEnergy;
    private static double contravoidOwed;
    private static boolean hooked;

    /**
     * Where everything stands, from the node the spike bleeds. The reactor's axis runs along X, Contravoid from the west and Fiery from the east, each
     * relay level with its intake so it sees it past the shell. Two large pylons cover the spike, the reactor and the construct; two more carry the
     * reactor's energy on to an athanor only the last one covers.
     */
    private record Scene(LeyNode node)
    {
        BlockPos at(int dx, int dy, int dz)
        {
            return new BlockPos(node.x() + dx, F + dy, node.z() + dz);
        }

        BlockPos spike()
        {
            return at(0, 0, 0);
        }

        BlockPos contravoid()
        {
            return at(3, 0, 0);
        }

        BlockPos contravoidRelay()
        {
            return at(6, 0, 0);
        }

        BlockPos core()
        {
            return at(10, 1, 0);
        }

        BlockPos fieryRelay()
        {
            return at(14, 0, 0);
        }

        BlockPos fiery()
        {
            return at(17, 0, 0);
        }

        BlockPos construct()
        {
            return at(10, 1, 8);
        }

        List<BlockPos> pylons()
        {
            return List.of(at(5, 0, 5), at(15, 0, 5), at(31, 0, 5), at(47, 0, 5));
        }

        BlockPos athanor()
        {
            return at(49, 1, 10);
        }

        // The middle of the shell's south face, opened to reach the wound
        BlockPos opening()
        {
            return core().south();
        }

        // In front of the opening and a little above it: low enough to see in under the shell's top, high enough to leave the view through it clear
        BlockPos woundRelay()
        {
            return at(10, 2, 5);
        }

        double x(double dx)
        {
            return node.x() + dx;
        }

        double z(double dz)
        {
            return node.z() + dz;
        }
    }

    @Override
    public void runTest(ClientGameTestContext context)
    {
        EnergyTestKit.expectRegistered("azurium_spike", "contravoid_reactor_core", "reactor_containment", "reactor_intake", "fissure_seed");
        hook();

        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode creative @a");
            server.runCommand("execute as @a run voidheart research grant voidheart:sulfur_rite");
            context.waitTicks(10);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            server.runOnServer(EnergyWave2ClientTest::clearTaint);

            var scene = new Scene(findNode(server));
            var spike = scene.spike();
            server.runCommand("forceload add " + (spike.getX() - 8) + " " + (spike.getZ() - 8) + " " + (spike.getX() + 56) + " " + (spike.getZ() + 16));
            aimFrom(context, server, scene.x(0.5), F, scene.z(-3.5), scene.x(0.5), F - 0.5, scene.z(0.5), 10);
            world.getConnection().waitForChunksRender();

            try
            {
                buildSpikeAndGrid(context, server, scene);
                checkSpikeGivesEnergyAndContravoid(context, server, scene);
                buildReactorAndFeed(context, server, scene);
                lightFromConstruct(context, server, scene);
                checkReactorPowersBrew(context, server, scene);
                starveIntoFailure(context, server, scene);
                closeWound(context, server, scene);
            } finally
            {
                tracked = null;
            }
        }
    }

    private static void hook()
    {
        if (hooked)
            return;
        hooked = true;
        ServerTickEvents.END_LEVEL_TICK.register(level ->
        {
            var at = tracked;
            if (at == null || level.dimension() != Level.OVERWORLD || !(level.getBlockEntity(at) instanceof AzuriumSpikeTile spike))
                return;
            spikeEnergy += spike.lastOutput();
            var node = spike.node(level);
            if (node.isPresent())
                contravoidOwed += spike.lastLey() * LeySpikeBalance.CONTRAVOID_PER_LEY * Ley.taint(level, node.getAsLong()) / 100;
        });
    }

    /**
     * The nearest node to the origin whose whole flow is under the spike's cap, so bleeding all of it is a share of 1.
     */
    private static LeyNode findNode(TestServerContext server)
    {
        var node = server.computeOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var topology = LeyTopology.get(level).orElseThrow(() -> new AssertionError("Expected a ley lattice in the overworld"));
            LeyNode best = null;
            for (var region : topology.lattice().regionsAround(0, 0, 1024, true))
            {
                for (var candidate : region.nodes())
                {
                    double flow = LatticeLeyFlow.nodeFlow(level, candidate.id());
                    if (flow < 3 || flow > LeySpikeBalance.MAX_LEY - 1)
                        continue;
                    if (best == null || (long) candidate.x() * candidate.x() + (long) candidate.z() * candidate.z() < (long) best.x() * best.x() + (long) best.z() * best.z())
                        best = candidate;
                }
            }
            return best;
        });
        expect(node != null, "Expected a ley node with a flow between 3 and 11 within 1024 blocks");
        return node;
    }

    // The spike

    /**
     * The spike placed on the node, four large pylons, an empty construct and an idle athanor, and a collector attuned to Contravoid by an azurium shard.
     */
    private static void buildSpikeAndGrid(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var player = player(minecraft);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(LeySpikeContent.AZURIUM_SPIKE));
            useOn(player, scene.spike().below());
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            expect(spike(minecraft, scene).node(level).orElse(-1) == scene.node().id(), "The spike should bleed the node it stands on");

            for (var pylon : scene.pylons())
                placePylon(level, pylon);

            level.setBlockAndUpdate(scene.construct(), RubrumContent.RUBRUM_CONSTRUCT_CORE.defaultBlockState());
            for (var offset : ConstructLayout.frame(Size.SMALL))
                level.setBlock(scene.construct().offset(offset), RubrumContent.RUBRUM_CONSTRUCT_FRAME.defaultBlockState(), Block.UPDATE_ALL);

            var athanor = scene.athanor();
            level.setBlockAndUpdate(athanor.below(), VoidHeartBlocks.ASPECT_INTAKE.defaultBlockState());
            level.setBlockAndUpdate(athanor, VoidHeartBlocks.ATHANOR.defaultBlockState());
            level.setBlockAndUpdate(athanor.above(), VoidHeartBlocks.ALEMBIC.defaultBlockState());
            for (var crucible : new BlockPos[]{ athanor.east(), athanor.west() })
            {
                level.setBlockAndUpdate(crucible.below(), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(crucible, VoidHeartBlocks.CRUCIBLE.defaultBlockState());
            }

            level.setBlockAndUpdate(scene.contravoid(), VoidHeartBlocks.RAVENOUS_COLLECTOR.defaultBlockState());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.AZURIUM_SHARD));
            useOn(player, scene.contravoid());
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            expect(collector(minecraft, scene.contravoid()).storage().type() == EssenceRegistry.CONTRAVOID,
                    "An azurium shard should attune the collector to Contravoid");
        });
        waitFor(context, server, 40, "The construct should form", minecraft -> construct(minecraft, scene).isFormed());
        server.runOnServer(minecraft ->
        {
            construct(minecraft, scene).restore(new ConstructContents(0, List.of(
                    new SocketSet.Entry(0, SocketBundle.LARGE_RUBRUM),
                    new SocketSet.Entry(1, SocketBundle.LARGE_AZURIUM),
                    new SocketSet.Entry(2, SocketBundle.LARGE_AZURIUM))));
            spike(minecraft, scene).setSetting(3);
        });
        server.runCommand("execute positioned " + scene.node().x() + " " + F + " " + scene.node().z() + " run voidheart ley taint set " + FESTERING);
        expect(server.computeOnServer(minecraft -> Ley.stage(minecraft.overworld(), scene.node().id())) == TaintStage.FESTERING, "The node should be Festering");

        var pylons = scene.pylons();
        waitFor(context, server, 80, "The first two pylons should cover the spike, the core's spot and the construct", minecraft ->
        {
            var level = minecraft.overworld();
            return covering(level, scene.spike()).contains(pylons.get(0)) && covering(level, scene.core()).equals(pylons.subList(0, 2))
                    && covering(level, scene.construct()).equals(pylons.subList(0, 2));
        });
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            expect(covering(level, scene.athanor()).equals(List.of(pylons.get(3))), "Only the last pylon should cover the athanor, covered by "
                    + covering(level, scene.athanor()));
        });
    }

    /**
     * Bleeding all of the Festering node's flow into the construct, the spike's energy charges it and its Contravoid fills the collector.
     */
    private static void checkSpikeGivesEnergyAndContravoid(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        context.waitTicks(40);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var spike = spike(minecraft, scene);
            double flow = Ley.lineFlowAt(level, scene.spike());
            expect(Math.abs(spike.lastLey() - flow) < flow * 1.0E-3, "The spike should convert the node's whole flow, " + flow + ", into the construct, converted "
                    + spike.lastLey());
            expect(scene.pylons().getFirst().above().equals(spike.outputTarget()), "The spike's arc should land on the nearest pylon's bundle, lands on "
                    + spike.outputTarget());
            tracked = scene.spike();
            spikeEnergy = 0;
            contravoidOwed = 0;
        });

        long[] start = server.computeOnServer(minecraft -> new long[]{construct(minecraft, scene).stored(), collector(minecraft, scene.contravoid()).storage().amount()});
        context.waitTicks(SAMPLES);
        double[] end = server.computeOnServer(minecraft -> new double[]{construct(minecraft, scene).stored(), collector(minecraft, scene.contravoid()).storage().amount(),
                spikeEnergy, contravoidOwed});

        double charged = end[0] - start[0];
        double gathered = end[1] - start[1];
        expect(end[2] > 0 && Math.abs(charged - end[2]) <= 2 * VoidGridBalance.LARGE_CAP, "The construct should take what the spike gives, " + end[2]
                + " VE, it took " + charged);
        // Handed over once a second, so up to a second's worth can sit on either side of the window
        double perSecond = end[3] / SAMPLES * LeySpikeBalance.EMIT_TICKS;
        expect(gathered > 0 && Math.abs(gathered - end[3]) <= perSecond + 1, "The collector should gather about " + end[3] + " Contravoid from the Festering node, it gathered "
                + gathered);

        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, scene.x(-1), F + 2, scene.z(-4), scene.x(2), F + 0.8, scene.z(0.5), 25);
        context.takeScreenshot("wave2-spike-collector");
        aimFrom(context, server, scene.x(3), F + 4, scene.z(-7), scene.x(4), F + 1.2, scene.z(3), 5);
        context.takeScreenshot("wave2-spike-arc");
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    // The reactor

    /**
     * The core placed by hand, its shell around it, and a relay on each side: Contravoid from the spike's collector into the west intake, Fiery from a
     * collector attuned with a lava bucket into the east one.
     */
    private static void buildReactorAndFeed(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        var core = scene.core();
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var player = player(minecraft);
            level.setBlockAndUpdate(core.below(), Blocks.STONE.defaultBlockState());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ContravoidReactorContent.CONTRAVOID_REACTOR_CORE));
            useOn(player, core.below());
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            expect(level.getBlockEntity(core) instanceof ContravoidReactorTile, "The core should be placed at " + core.toShortString());
            for (var expected : ReactorLayout.shell(Direction.Axis.X))
                level.setBlockAndUpdate(core.offset(expected.offset()), stateFor(expected.part()).defaultBlockState());
        });
        waitFor(context, server, 40, "The whole shell should complete the reactor", minecraft -> reactor(minecraft, scene).isComplete());

        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var player = player(minecraft);
            level.setBlockAndUpdate(scene.fiery(), VoidHeartBlocks.RAVENOUS_COLLECTOR.defaultBlockState());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
            useOn(player, scene.fiery());
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            var fiery = collector(minecraft, scene.fiery());
            expect(fiery.storage().type() == EssenceRegistry.FIERY, "A lava bucket should attune the collector to Fiery");
            fiery.storage().fill(EssenceRegistry.FIERY, FIERY);
            // The spike gives far less Contravoid a tick than a lit reactor burns, so the collector is topped up as a base's stock would be
            collector(minecraft, scene.contravoid()).storage().fill(EssenceRegistry.CONTRAVOID, RavenousCollectorTile.CAPACITY);

            level.setBlockAndUpdate(scene.contravoidRelay(), VoidHeartBlocks.RAVENOUS_RELAY.defaultBlockState());
            level.setBlockAndUpdate(scene.fieryRelay(), VoidHeartBlocks.RAVENOUS_RELAY.defaultBlockState());
        });
        waitFor(context, server, 400, "The relays should carry Contravoid into the west intake and Fiery into the east one", minecraft ->
                intake(minecraft, core.west()).holdsContravoid() && intake(minecraft, core.east()).held() == EssenceRegistry.FIERY);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            expect(level.getBlockEntity(scene.contravoidRelay()) instanceof RavenousRelayTile relay && relay.linkedReceivers().equals(List.of(core.west())),
                    "The Contravoid relay should link to the west intake alone");
            expect(level.getBlockEntity(scene.fieryRelay()) instanceof RavenousRelayTile relay && relay.linkedReceivers().equals(List.of(core.east())),
                    "The Fiery relay should link to the east intake alone");
        });

        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, scene.x(10.5), F + 4, scene.z(-7), scene.x(10.5), F + 1, scene.z(0.5), 20);
        context.takeScreenshot("wave2-reactor-fed");
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    /**
     * A real click on the shell lights it from the construct's charge. What the spike gives meanwhile goes into the ignition too.
     */
    private static void lightFromConstruct(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        var edge = scene.core().offset(1, 0, 1);
        long before = server.computeOnServer(minecraft ->
        {
            construct(minecraft, scene).setStored(CHARGE);
            spikeEnergy = 0;
            return CHARGE;
        });
        aimFrom(context, server, scene.x(13.5), F, scene.z(3.5), scene.x(11.7), F + 1.5, scene.z(1.9), 3);
        expectClient(context, "The player should be looking at the shell's south-east edge", minecraft -> minecraft.hitResult instanceof BlockHitResult hit
                && hit.getBlockPos().equals(edge));
        rightClick(context);
        server.runOnServer(minecraft -> expect(reactor(minecraft, scene).phase() == Phase.IGNITING, "A click on the whole, fuelled shell should start the ignition, it is "
                + reactor(minecraft, scene).phase()));

        context.waitTicks(40);
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, scene.x(19), F + 6, scene.z(-9), scene.x(10), F + 1.5, scene.z(1), 5);
        context.takeScreenshot("wave2-reactor-igniting");
        context.getInput().pressKey(InputConstants.KEY_F1);

        waitFor(context, server, 2_500, () -> "The reactor should light once it drew " + ReactorBalance.IGNITION + " VE", minecraft ->
                reactor(minecraft, scene).phase() == Phase.LIT);
        server.runOnServer(minecraft ->
        {
            var fromConstruct = before - construct(minecraft, scene).stored();
            // It starts charging the construct back the moment it lights, and the last draw delivered ahead may still be on its way
            expect(Math.abs(fromConstruct + spikeEnergy - ReactorBalance.IGNITION) <= 2_000, "Lighting should take " + ReactorBalance.IGNITION
                    + " VE between the construct and the spike, it took " + fromConstruct + " and " + spikeEnergy);
            expect(fromConstruct > ReactorBalance.IGNITION / 2, "The construct should pay most of the ignition, it paid " + fromConstruct);
        });
    }

    /**
     * Lit, it burns the relays' Contravoid and Fiery into energy that charges the construct beside it and runs on through two more pylons into an
     * athanor brewing at full speed.
     */
    private static void checkReactorPowersBrew(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        var athanor = scene.athanor();
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            ((CrucibleTile) level.getBlockEntity(athanor.east())).setStack(new ItemStack(Items.SULFUR));
            ((CrucibleTile) level.getBlockEntity(athanor.west())).setStack(new ItemStack(VoidHeartItems.QUICKSILVER));
            athanor(minecraft, scene).setStack(player(minecraft), new ItemStack(Items.GLASS_BOTTLE));
        });
        waitFor(context, server, 60, "The athanor should find its recipe", minecraft -> athanor(minecraft, scene).getCurrentRecipe() != null);
        server.runOnServer(minecraft ->
        {
            var tile = athanor(minecraft, scene);
            tile.getCurrentRecipe().getAspects().forEach(tile::receiveAspect);
        });
        waitFor(context, server, 60, "The athanor should start brewing once its aspects are in", minecraft -> athanor(minecraft, scene).isBrewing());
        context.waitTicks(25);

        long[] start = server.computeOnServer(minecraft -> new long[]{minecraft.overworld().getGameTime(), athanor(minecraft, scene).getProgress(),
                construct(minecraft, scene).stored(), intake(minecraft, scene.core().east()).amount()});
        context.waitTicks(MEASURED);
        long[] end = server.computeOnServer(minecraft -> new long[]{minecraft.overworld().getGameTime(), athanor(minecraft, scene).getProgress(),
                construct(minecraft, scene).stored(), intake(minecraft, scene.core().east()).amount(), reactor(minecraft, scene).lastOutput()});
        long ticks = end[0] - start[0];
        expect(end[1] - start[1] >= ticks - 1, "The brew should advance every tick on the reactor's energy, it advanced " + (end[1] - start[1]) + " in " + ticks + " ticks");
        expect(end[4] > 0 && start[3] > end[3], "The reactor should burn Fiery into energy, it gives " + end[4] + " VE a tick and its Fiery went from " + start[3]
                + " to " + end[3]);
        expect(end[2] > start[2], "What the brew leaves should charge the construct, it went from " + start[2] + " to " + end[2]);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            expect(athanor(minecraft, scene).getPower() == MachinePower.POWERED, "The brew should be POWERED, it is " + athanor(minecraft, scene).getPower());
            var grid = VoidGrid.get(level);
            expect(grid != null && grid.layout().arcs().length >= 3, "The four pylons should arc into one chain");
            expect(reactor(minecraft, scene).burning() == ReactorEssence.FIERY, "It should burn Fiery, burns " + reactor(minecraft, scene).burning());
        });
        expectClient(context, "The client should see it lit, with an arc to each of the two pylons covering it", minecraft ->
                minecraft.level.getBlockEntity(scene.core()) instanceof ContravoidReactorTile tile && tile.phase() == Phase.LIT
                        && tile.animation().arcs().size() == 2);

        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, scene.x(19), F + 6, scene.z(-9), scene.x(10), F + 1.5, scene.z(1), 5);
        context.takeScreenshot("wave2-reactor-lit");
        aimFrom(context, server, scene.x(10.5), F, scene.z(3.6), scene.x(10.5), F + 1.5, scene.z(0.5), 3);
        context.takeScreenshot("wave2-reactor-slit");
        aimFrom(context, server, scene.x(26), F + 22, scene.z(-20), scene.x(28), F, scene.z(5), 20);
        context.takeScreenshot("wave2-chain");
        aimFrom(context, server, athanor.getX() - 5, F + 3, athanor.getZ() - 5, athanor.getX() + 0.5, F + 1, athanor.getZ() + 0.5, 5);
        context.takeScreenshot("wave2-athanor");
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    /**
     * The Fiery runs out with nothing to refill it, which starts the warning. Left alone past its countdown, the core gives way to a real wound in its
     * place, the shell stands, and the nearest node takes the failure's taint.
     */
    private static void starveIntoFailure(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        var core = scene.core();
        var node = server.computeOnServer(minecraft -> LeyLattice.get(minecraft.overworld()).flatMap(lattice -> lattice.nearestNode(core.getX(), core.getZ()))
                .orElseThrow(() -> new AssertionError("Expected a ley node near the reactor")));

        waitFor(context, server, 4_000, "The Fiery should run out and start the warning", minecraft -> reactor(minecraft, scene).phase() == Phase.FAILING
                && Cause.ESSENCE_DRY.in(reactor(minecraft, scene).cycle().causes()));
        server.runOnServer(minecraft ->
        {
            expect(collector(minecraft, scene.fiery()).storage().amount() == 0 && intake(minecraft, core.east()).amount() < ReactorBalance.RECOVER_FUEL,
                    "It should warn only once every drop of Fiery was burned");
            expect(intake(minecraft, core.west()).holdsContravoid(), "The Contravoid side should still be fed");
        });
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, scene.x(16), F + 3, scene.z(-5), scene.x(10.5), F + 1, scene.z(0.5), 20);
        context.takeScreenshot("wave2-reactor-warning");
        context.getInput().pressKey(InputConstants.KEY_F1);

        waitFor(context, server, ReactorBalance.COUNTDOWN + 60, "The countdown should run out", minecraft -> reactor(minecraft, scene).phase() == Phase.COLLAPSING);
        float taintBefore = server.computeOnServer(minecraft -> Ley.taint(minecraft.overworld(), node.id()));
        waitFor(context, server, ReactorBalance.COLLAPSE + 20, "The collapse should end in a failure", minecraft ->
                !minecraft.overworld().getBlockState(core).is(ContravoidReactorContent.CONTRAVOID_REACTOR_CORE));

        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            expect(level.getBlockState(core).is(VoidFissureContent.VOID_WOUND) && level.getBlockEntity(core) instanceof VoidFissureTile wound && wound.isWound()
                    && wound.size() == VoidFissureBalance.WOUND_SIZE, "A wound of size " + VoidFissureBalance.WOUND_SIZE + " should take the core's place, found "
                    + level.getBlockState(core));
            var standing = ReactorLayout.shell(Direction.Axis.X).stream()
                    .filter(expected -> level.getBlockState(core.offset(expected.offset())).is(stateFor(expected.part()))).count();
            expect(standing == 26, "Nothing should blast: all 26 blocks of the shell should still stand, " + standing + " do");
            var taint = Ley.taint(level, node.id());
            expect(taint >= Math.min(100, taintBefore + ReactorBalance.FAILURE_TAINT) - 1, "The nearest node should take " + ReactorBalance.FAILURE_TAINT
                    + " taint, went from " + taintBefore + " to " + taint);
        });

        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, scene.x(16), F + 3, scene.z(-5), scene.x(10.5), F + 1, scene.z(0.5), 10);
        context.takeScreenshot("wave2-reactor-failed");
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    /**
     * Broken open from the side, the wound inside is in reach of a relay, which pays it Contravoid from the spike's collector until it draws shut.
     */
    private static void closeWound(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        var core = scene.core();
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.gameMode.destroyBlock(scene.opening());
            expect(minecraft.overworld().getBlockState(scene.opening()).isAir(), "The player should break the shell open");
        });
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, scene.x(10.5), F, scene.z(5.5), scene.x(10.5), F + 1.1, scene.z(0.5), 20);
        context.takeScreenshot("wave2-wound-open");
        context.getInput().pressKey(InputConstants.KEY_F1);

        server.runOnServer(minecraft ->
        {
            collector(minecraft, scene.contravoid()).storage().fill(EssenceRegistry.CONTRAVOID, RavenousCollectorTile.CAPACITY);
            minecraft.overworld().setBlockAndUpdate(scene.woundRelay(), VoidHeartBlocks.RAVENOUS_RELAY.defaultBlockState());
        });
        context.waitTicks(50);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var relay = (RavenousRelayTile) level.getBlockEntity(scene.woundRelay());
            expect(relay.linkedReceivers().contains(core) && relay.linkedRelays().contains(scene.contravoidRelay()), "The new relay should link to the wound "
                    + "through the opening, and to the Contravoid relay, it links to receivers " + relay.linkedReceivers() + " and relays " + relay.linkedRelays());
        });

        waitFor(context, server, 2_400, "The relay should pay the wound's cost", minecraft -> wound(minecraft, core).isClosing());
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, scene.x(10.5), F, scene.z(5.5), scene.x(10.5), F + 1.1, scene.z(0.5), 10);
        context.takeScreenshot("wave2-wound-closing");
        server.runOnServer(minecraft -> expect(wound(minecraft, core).paid() >= wound(minecraft, core).cost(), "The wound should be paid in full"));

        waitFor(context, server, 80, "The wound should draw shut", minecraft -> minecraft.overworld().getBlockState(core).isAir());
        server.runOnServer(minecraft -> expect(VoidSeep.strengthAt(minecraft.overworld(), core) == 0, "A closed wound should seep no more"));
        context.waitTicks(10);
        context.takeScreenshot("wave2-wound-scar");
        context.getInput().pressKey(InputConstants.KEY_F1);
        expectClient(context, "The closed wound should leave a scar on the client", minecraft ->
                FissureScars.scars().stream().anyMatch(scar -> scar.pos().equals(core) && scar.wound()));
    }

    // Helpers

    private static void placePylon(ServerLevel level, BlockPos pylon)
    {
        level.setBlockAndUpdate(pylon, VoidGridContent.AZURIUM_PYLON.defaultBlockState());
        level.setBlockAndUpdate(pylon.above(), VoidHeartBlocks.LARGE_AZURIUM_BUNDLE.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
    }

    private static List<BlockPos> covering(ServerLevel level, BlockPos pos)
    {
        return VoidGrid.pylonsCovering(level, pos).stream().map(GridLayout.Pylon::pos).sorted().toList();
    }

    private static Block stateFor(ReactorLayout.Part part)
    {
        return part == ReactorLayout.Part.INTAKE ? ContravoidReactorContent.REACTOR_INTAKE : ContravoidReactorContent.REACTOR_CONTAINMENT;
    }

    private static void clearTaint(MinecraftServer minecraft)
    {
        var state = LeyTaintState.get(minecraft.overworld());
        for (long tainted : new LongArrayList(state.tainted()))
            state.set(tainted, 0);
        LeyTaint.clear();
    }

    private static AzuriumSpikeTile spike(MinecraftServer minecraft, Scene scene)
    {
        if (minecraft.overworld().getBlockEntity(scene.spike()) instanceof AzuriumSpikeTile spike)
            return spike;
        throw new AssertionError("No Azurium Spike at " + scene.spike().toShortString());
    }

    private static RavenousCollectorTile collector(MinecraftServer minecraft, BlockPos pos)
    {
        if (minecraft.overworld().getBlockEntity(pos) instanceof RavenousCollectorTile collector)
            return collector;
        throw new AssertionError("No collector at " + pos.toShortString());
    }

    private static ContravoidReactorTile reactor(MinecraftServer minecraft, Scene scene)
    {
        if (minecraft.overworld().getBlockEntity(scene.core()) instanceof ContravoidReactorTile core)
            return core;
        throw new AssertionError("No Contravoid Reactor at " + scene.core().toShortString());
    }

    private static ReactorIntakeTile intake(MinecraftServer minecraft, BlockPos pos)
    {
        if (minecraft.overworld().getBlockEntity(pos) instanceof ReactorIntakeTile intake)
            return intake;
        throw new AssertionError("No Reactor Intake at " + pos.toShortString());
    }

    private static RubrumConstructTile construct(MinecraftServer minecraft, Scene scene)
    {
        if (minecraft.overworld().getBlockEntity(scene.construct()) instanceof RubrumConstructTile construct)
            return construct;
        throw new AssertionError("No construct at " + scene.construct().toShortString());
    }

    private static AthanorTile athanor(MinecraftServer minecraft, Scene scene)
    {
        if (minecraft.overworld().getBlockEntity(scene.athanor()) instanceof AthanorTile athanor)
            return athanor;
        throw new AssertionError("No athanor at " + scene.athanor().toShortString());
    }

    private static VoidFissureTile wound(MinecraftServer minecraft, BlockPos pos)
    {
        if (minecraft.overworld().getBlockEntity(pos) instanceof VoidFissureTile wound)
            return wound;
        throw new AssertionError("No wound at " + pos.toShortString());
    }
}
