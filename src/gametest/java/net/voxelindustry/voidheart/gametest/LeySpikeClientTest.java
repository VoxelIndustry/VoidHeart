package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.energy.spike.AzuriumSpikeBlock;
import net.voxelindustry.voidheart.common.energy.spike.AzuriumSpikeTile;
import net.voxelindustry.voidheart.common.energy.spike.LeySpikeBalance;
import net.voxelindustry.voidheart.common.energy.spike.LeySpikeContent;
import net.voxelindustry.voidheart.common.ley.Ley;
import net.voxelindustry.voidheart.common.ley.LeyNode;
import net.voxelindustry.voidheart.common.ley.TaintStage;
import net.voxelindustry.voidheart.common.ley.flow.LatticeLeyFlow;
import net.voxelindustry.voidheart.common.ley.flow.LeyTopology;
import net.voxelindustry.voidheart.common.ley.taint.LeyTaint;
import net.voxelindustry.voidheart.common.ley.taint.LeyTaintState;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * W8: an Azurium Spike placed with a real click on a real ley node of a flat world, feeding a stand-in machine through a pylon. Light bleeding gives a
 * quarter of the flow and never taints; nothing drawing means nothing converted; bleeding all of it, set with real clicks, taints the node, and a collector
 * attuned with an azurium shard fills with Contravoid in proportion to the taint. The spike drops whole, and keeps its setting and lean through a reload.
 * Run it alone with -PgameTest=LeySpike.
 */
public class LeySpikeClientTest implements FabricClientGameTest
{
    private static final long   ASK       = 200;
    private static final int    MEASURED  = 40;
    // Light conversion and resting are checked over several 20-tick taint windows
    private static final int    WINDOWS   = 200;
    private static final int    HEAVY_TICKS = 2400;
    private static final float  FESTERING = 60;
    // A stand-in entity id for whoever is mining, as the level tracks cracks per breaker
    private static final int    BREAKER   = 779;

    // The stand-in machine draws after everything else in the overworld ticked, as a machine block entity would
    private static volatile BlockPos machine;
    private static volatile long     ask;
    private static long drawn;
    private static boolean hooked;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        EnergyTestKit.expectRegistered("azurium_spike");
        hook();

        TestWorldSave save;
        BlockPos spikePos;
        try (var world = context.worldBuilder().create())
        {
            save = world.getWorldSave();
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamemode creative @a");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            server.runOnServer(LeySpikeClientTest::clearTaint);

            var node = findNode(server);
            var scene = new Scene(node);
            spikePos = scene.spike;
            server.runCommand("forceload add " + (node.x() - 24) + " " + (node.z() - 24) + " " + (node.x() + 24) + " " + (node.z() + 24));
            aimFrom(context, server, node.x() + 0.5, FLAT_FEET, node.z() - 3.5, node.x() + 0.5, FLAT_FEET - 0.5, node.z() + 0.5, 10);
            world.getConnection().waitForChunksRender();

            placeWithRealClick(context, server, scene);
            checkIdleWithoutField(context, server, scene);
            buildGrid(context, server, scene);

            checkLightConversion(context, server, scene);
            checkNothingTakenNothingConverted(context, server, scene);
            setHeavyWithRealClicks(context, server, scene);
            checkHeavyTaints(context, server, scene);
            checkContravoidFollowsTaint(context, server, scene);
            showJade(context, server, scene);
            sweep(context, server, scene);
            showCracks(context, server, scene);
            checkBreakingDropsTheSpike(context, server, scene);
            ask = 0;
        }

        try (var world = save.open())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);
            server.runOnServer(minecraft ->
            {
                if (!(minecraft.overworld().getBlockEntity(spikePos) instanceof AzuriumSpikeTile spike))
                    throw new AssertionError("The replaced spike is gone after a reload");
                expect(spike.setting() == 2, "The spike should keep its 75% setting through a reload, has " + spike.setting());
                expect(spike.leanDegrees() >= AzuriumSpikeTile.MIN_LEAN_DEGREES, "The spike should keep its lean through a reload, leans " + spike.leanDegrees());
                clearTaint(minecraft);
            });
            machine = null;
        }
    }

    /**
     * Where each piece stands around the node: the spike on it, a pylon whose medium bundle's field covers both the spike and the machine, and a collector
     * within the spike's Contravoid range.
     */
    private record Scene(LeyNode node, BlockPos spike, BlockPos pylon, BlockPos machine, BlockPos collector)
    {
        Scene(LeyNode node)
        {
            this(node, new BlockPos(node.x(), FLAT_FEET, node.z()), new BlockPos(node.x() + 4, FLAT_FEET, node.z() + 2),
                    new BlockPos(node.x() + 8, FLAT_FEET, node.z() + 2), new BlockPos(node.x() - 3, FLAT_FEET, node.z() + 2));
        }
    }

    private static void hook()
    {
        if (hooked)
            return;
        hooked = true;
        ServerTickEvents.END_LEVEL_TICK.register(level ->
        {
            var at = machine;
            if (level.dimension() != Level.OVERWORLD || at == null || ask <= 0)
                return;
            drawn += VoidEnergy.draw(level, at, ask);
        });
    }

    /**
     * The nearest node to the origin whose whole flow is under the spike's cap, so bleeding all of it claims a share of 1 and taints at the full rate.
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

    private static void placeWithRealClick(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        server.runOnServer(minecraft ->
        {
            var inventory = player(minecraft).getInventory();
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(LeySpikeContent.AZURIUM_SPIKE));
        });
        aimFrom(context, server, scene.spike.getX() + 0.5, FLAT_FEET, scene.spike.getZ() - 2.5, scene.spike.getX() + 0.5, FLAT_FEET, scene.spike.getZ() + 0.5, 5);
        context.takeScreenshot("spike-item-hand");
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(5);

        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            expect(level.getBlockState(scene.spike).is(LeySpikeContent.AZURIUM_SPIKE)
                    && level.getBlockState(scene.spike).getValue(AzuriumSpikeBlock.PART) == AzuriumSpikeBlock.Part.BASE, "The spike's base was not placed on the node");
            expect(level.getBlockState(scene.spike.above()).is(LeySpikeContent.AZURIUM_SPIKE)
                    && level.getBlockState(scene.spike.above()).getValue(AzuriumSpikeBlock.PART) == AzuriumSpikeBlock.Part.TIP, "The spike placed no tip above");
            expect(level.getBlockEntity(scene.spike.above()) == null, "The tip has a block entity of its own");
            var spike = spike(minecraft, scene);
            expect(spike.leanDegrees() >= AzuriumSpikeTile.MIN_LEAN_DEGREES && spike.leanDegrees() <= AzuriumSpikeTile.MAX_LEAN_DEGREES,
                    "The spike should be driven in at a slight lean, leans " + spike.leanDegrees());
            expect(spike.setting() == LeySpikeBalance.DEFAULT_SETTING, "A new spike should bleed lightly");
            expect(spike.node(level).orElse(-1) == scene.node.id(), "The spike should bleed the node it stands on");
            player(minecraft).getInventory().setItem(0, ItemStack.EMPTY);
        });
    }

    /**
     * Without a field nothing takes its energy, so it converts nothing and claims nothing on the node.
     */
    private static void checkIdleWithoutField(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        context.waitTicks(30);
        server.runOnServer(minecraft ->
        {
            var spike = spike(minecraft, scene);
            expect(spike.lastFlow() > 0, "The spike should feel the node's flow");
            expect(spike.lastLey() == 0 && spike.lastOutput() == 0 && spike.isIdle(), "The spike converted ley while nothing could take its energy");
            expect(LatticeLeyFlow.claimedOnNode(minecraft.overworld(), scene.node.id()) == 0, "An idle spike holds a claim on the node");
        });
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, scene.spike.getX() + 2.2, FLAT_FEET + 1.4, scene.spike.getZ() - 1.6, scene.spike.getX() + 0.5, FLAT_FEET + 1, scene.spike.getZ() + 0.5, 5);
        context.takeScreenshot("spike-idle");
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    private static void buildGrid(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        server.runCommand("setblock " + at(scene.pylon) + " voidheart:azurium_pylon");
        server.runCommand("setblock " + at(scene.pylon.above()) + " voidheart:medium_azurium_bundle[facing=up]");
        server.runCommand("setblock " + at(scene.machine) + " voidheart:voidstone_chiseled");
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            level.setBlockAndUpdate(scene.collector, VoidHeartBlocks.RAVENOUS_COLLECTOR.defaultBlockState());
            // Attuned through the real interaction, with the catalyst the user chose
            var player = player(minecraft);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.AZURIUM_SHARD));
            useOn(player, scene.collector);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            expect(collector(minecraft, scene).storage().type() == EssenceRegistry.CONTRAVOID, "An azurium shard did not attune the collector to Contravoid");
        });
        machine = scene.machine;
        ask = ASK;
        context.waitTicks(60);
    }

    /**
     * A quarter of the flow, all of it taken: 8 VE a tick for each ley, and the clean node takes no taint and gives no Contravoid.
     */
    private static void checkLightConversion(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        double[] expected = server.computeOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var spike = spike(minecraft, scene);
            double flow = Ley.lineFlowAt(level, scene.spike);
            double ley = flow * 0.25;
            expect(Math.abs(spike.lastLey() - ley) < ley * 1.0E-3, "Light bleeding should convert a quarter of the flow, " + ley + ", converted " + spike.lastLey());
            expect(scene.pylon.above().equals(spike.outputTarget()),
                    "The spike's arc should land on the pylon's bundle, lands on " + spike.outputTarget());
            drawn = 0;
            return new double[]{ley, level.getGameTime()};
        });
        context.waitTicks(MEASURED);
        server.runOnServer(minecraft ->
        {
            long ticks = minecraft.overworld().getGameTime() - (long) expected[1];
            double perTick = drawn / (double) ticks;
            double want = expected[0] * LeySpikeBalance.VE_PER_LEY;
            expect(Math.abs(perTick - want) <= 1, "The machine should draw about " + want + " VE a tick from the light spike, drew " + perTick);
        });
        expectClient(context, "The client does not see the spike converting toward the pylon", minecraft ->
                minecraft.level.getBlockEntity(scene.spike) instanceof AzuriumSpikeTile spike && !spike.isIdle() && scene.pylon.above().equals(spike.outputTarget()));

        context.getInput().pressKey(InputConstants.KEY_F1);
        for (int frame = 0; frame < 3; frame++)
        {
            aimFrom(context, server, scene.spike.getX() + 2.2, FLAT_FEET + 1.4, scene.spike.getZ() - 1.6, scene.spike.getX() + 0.5, FLAT_FEET + 1, scene.spike.getZ() + 0.5, frame == 0 ? 15 : 6);
            context.takeScreenshot("spike-light-" + frame);
        }
        aimFrom(context, server, scene.spike.getX() + 3, FLAT_FEET + 3.5, scene.spike.getZ() - 6, scene.spike.getX() + 3, FLAT_FEET + 1.2, scene.spike.getZ() + 1.5, 5);
        context.takeScreenshot("spike-light-arc");
        context.getInput().pressKey(InputConstants.KEY_F1);

        context.waitTicks(WINDOWS);
        server.runOnServer(minecraft ->
        {
            float taint = Ley.taint(minecraft.overworld(), scene.node.id());
            expect(taint == 0, "Light bleeding should never taint the node, it holds " + taint);
            expect(collector(minecraft, scene).storage().amount() == 0, "A clean node should give no Contravoid");
        });
    }

    /**
     * With the machine stopped, the grid still takes a tick of what its pylons can pass into transit (128 VE for a medium bundle), then nothing: the
     * spike stops converting and releases its claim.
     */
    private static void checkNothingTakenNothingConverted(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        ask = 0;
        int buffered = (int) Math.ceil(128 / server.computeOnServer(minecraft -> spike(minecraft, scene).lastLey() * LeySpikeBalance.VE_PER_LEY));
        waitFor(context, server, buffered + 20, () -> "The spike kept converting with nothing drawing", minecraft -> spike(minecraft, scene).lastLey() == 0);
        for (int tick = 0; tick < 10; tick++)
        {
            server.runOnServer(minecraft ->
            {
                var spike = spike(minecraft, scene);
                expect(spike.lastLey() == 0 && spike.lastOutput() == 0, "The spike converted " + spike.lastLey() + " ley with nothing drawing");
                expect(LatticeLeyFlow.claimedOnNode(minecraft.overworld(), scene.node.id()) == 0, "The spike still claims on the node with nothing drawing");
            });
            context.waitTick();
        }
        context.waitTicks(AzuriumSpikeTile.IDLE_GRACE_TICKS + 5);
        expectClient(context, "The client still sees the spike converting with nothing drawing", minecraft ->
                minecraft.level.getBlockEntity(scene.spike) instanceof AzuriumSpikeTile spike && spike.isIdle());
        ask = ASK;
    }

    /**
     * Three real clicks with an empty hand take it from 25% to 100%.
     */
    private static void setHeavyWithRealClicks(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        aimFrom(context, server, scene.spike.getX() + 0.5, FLAT_FEET, scene.spike.getZ() - 2, scene.spike.getX() + 0.5, FLAT_FEET + 1.3, scene.spike.getZ() + 0.5, 5);
        expectClient(context, "The player is not looking at the spike", minecraft -> minecraft.hitResult instanceof BlockHitResult hit
                && (hit.getBlockPos().equals(scene.spike) || hit.getBlockPos().equals(scene.spike.above())));
        for (int click = 0; click < 3; click++)
            rightClick(context);
        var message = overlay(context);
        expect(message.contains("100%"), "Using the spike should say it bleeds all of the flow, says '" + message + "'");
        server.runOnServer(minecraft -> expect(spike(minecraft, scene).setting() == 3, "Three uses should set the spike to bleed all of the flow, set to "
                + spike(minecraft, scene).setting()));
    }

    /**
     * All of a flow under the cap is a share of 1: about 2 taint a minute, less the healing.
     */
    private static void checkHeavyTaints(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        context.waitTicks(40);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var spike = spike(minecraft, scene);
            double flow = Ley.lineFlowAt(level, scene.spike);
            expect(Math.abs(spike.lastLey() - flow) < flow * 1.0E-3, "Heavy bleeding should convert the whole flow, " + flow + ", converted " + spike.lastLey());
        });

        context.getInput().pressKey(InputConstants.KEY_F1);
        for (int frame = 0; frame < 3; frame++)
        {
            aimFrom(context, server, scene.spike.getX() + 2.2, FLAT_FEET + 1.4, scene.spike.getZ() - 1.6, scene.spike.getX() + 0.5, FLAT_FEET + 1, scene.spike.getZ() + 0.5, frame == 0 ? 15 : 4);
            context.takeScreenshot("spike-heavy-" + frame);
        }
        context.getInput().pressKey(InputConstants.KEY_F1);

        float half = waitAndReadTaint(context, server, scene, HEAVY_TICKS / 2);
        float full = waitAndReadTaint(context, server, scene, HEAVY_TICKS / 2);
        expect(half > 0.5F, "Heavy bleeding should taint the node within a minute, it holds " + half);
        expect(full > half + 0.5F, "Heavy bleeding should keep raising the taint, " + half + " then " + full);
        expect(full < 6, "Two minutes at the full rate should give about 3.9 taint, the node holds " + full);
        server.runOnServer(minecraft ->
        {
            var held = collector(minecraft, scene).storage().amount();
            expect(held > 0, "Once the node is tainted the collector should fill with Contravoid, it holds " + held);
        });

        // Nothing drawing: once the window in progress closes, nothing is converted and the node only heals, about 0.08 a minute at this taint
        ask = 0;
        context.waitTicks(60);
        float settled = server.computeOnServer(minecraft -> Ley.taint(minecraft.overworld(), scene.node.id()));
        context.waitTicks(1200);
        server.runOnServer(minecraft ->
        {
            float rested = Ley.taint(minecraft.overworld(), scene.node.id());
            expect(spike(minecraft, scene).lastLey() == 0, "The spike converted ley with nothing drawing");
            expect(rested < settled - 0.05F, "With nothing drawing the node should heal, " + settled + " then " + rested);
        });
        ask = ASK;
    }

    private static float waitAndReadTaint(ClientGameTestContext context, TestServerContext server, Scene scene, int ticks)
    {
        context.waitTicks(ticks);
        return server.computeOnServer(minecraft -> Ley.taint(minecraft.overworld(), scene.node.id()));
    }

    /**
     * On a Festering node the collector takes ley × 0.085 × taint/100 Contravoid a tick, and its strands reach into the spike.
     */
    private static void checkContravoidFollowsTaint(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        server.runCommand("execute positioned " + scene.node.x() + " " + FLAT_FEET + " " + scene.node.z() + " run voidheart ley taint set " + FESTERING);
        expect(server.computeOnServer(minecraft -> Ley.stage(minecraft.overworld(), scene.node.id())) == TaintStage.FESTERING, "The node should be Festering");
        context.waitTicks(40);

        long[] start = server.computeOnServer(minecraft -> new long[]{minecraft.overworld().getGameTime(), collector(minecraft, scene).storage().amount()});
        double[] rate = {0};
        int samples = 200;
        for (int tick = 0; tick < samples; tick++)
        {
            server.runOnServer(minecraft ->
            {
                var level = minecraft.overworld();
                rate[0] += spike(minecraft, scene).lastLey() * LeySpikeBalance.CONTRAVOID_PER_LEY * Ley.taint(level, scene.node.id()) / 100;
            });
            context.waitTick();
        }
        long[] end = server.computeOnServer(minecraft -> new long[]{minecraft.overworld().getGameTime(), collector(minecraft, scene).storage().amount()});
        long gathered = end[1] - start[1];
        expect(gathered > 0, "A Festering node should give the collector Contravoid");
        // Handed over once a second, so up to a second's worth can sit on either side of the window
        double perSecond = rate[0] / samples * LeySpikeBalance.EMIT_TICKS;
        expect(Math.abs(gathered - rate[0]) <= perSecond + 1, "The collector should gather about " + rate[0] + " Contravoid over " + (end[0] - start[0])
                + " ticks, gathered " + gathered);

        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, scene.spike.getX() - 1, FLAT_FEET + 2, scene.spike.getZ() - 4, scene.collector.getX() + 1.5, FLAT_FEET + 0.8, scene.collector.getZ() + 0.5, 25);
        context.takeScreenshot("spike-contravoid-collector");
        aimFrom(context, server, scene.spike.getX() + 2.5, FLAT_FEET + 5, scene.spike.getZ() - 6, scene.spike.getX() + 0.5, FLAT_FEET, scene.spike.getZ() + 0.5, 30);
        context.takeScreenshot("spike-festering-ground");
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    private static void showJade(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        aimFrom(context, server, scene.spike.getX() + 0.5, FLAT_FEET, scene.spike.getZ() - 2.2, scene.spike.getX() + 0.5, FLAT_FEET + 0.8, scene.spike.getZ() + 0.5, 30);
        context.takeScreenshot("spike-jade");
        aimFrom(context, server, scene.spike.getX() + 0.5, FLAT_FEET + 0.5, scene.spike.getZ() - 2.2, scene.spike.getX() + 0.5, FLAT_FEET + 1.7, scene.spike.getZ() + 0.5, 30);
        expectClient(context, "The player is not looking at the tip", minecraft -> minecraft.hitResult instanceof BlockHitResult hit
                && hit.getBlockPos().equals(scene.spike.above()));
        context.takeScreenshot("spike-jade-tip");
    }

    /**
     * Bands and the arc are additive and wrap solid crystal, so look for flicker while the camera moves.
     */
    private static void sweep(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        context.getInput().pressKey(InputConstants.KEY_F1);
        server.runCommand("tick freeze");
        for (int step = 0; step < 6; step++)
        {
            aimFrom(context, server, scene.spike.getX() + 1.8 + step * 0.05, FLAT_FEET + 1.2 + step * 0.03, scene.spike.getZ() - 1.4 + step * 0.08,
                    scene.spike.getX() + 0.5, FLAT_FEET + 1, scene.spike.getZ() + 0.5, 2);
            context.takeScreenshot("spike-bands-sweep-" + step);
        }
        server.runCommand("tick unfreeze");
        // From the far side of the lean
        aimFrom(context, server, scene.spike.getX() - 1.6, FLAT_FEET + 1.4, scene.spike.getZ() + 2.4, scene.spike.getX() + 0.5, FLAT_FEET + 1, scene.spike.getZ() + 0.5, 5);
        context.takeScreenshot("spike-other-side");
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    /**
     * Only the renderer draws the spike, and the game hands no cracks to a block entity drawn off screen, so it must find them on the base and the tip.
     */
    private static void showCracks(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, scene.spike.getX() + 1.4, FLAT_FEET + 1.1, scene.spike.getZ() - 1.0, scene.spike.getX() + 0.5, FLAT_FEET + 1, scene.spike.getZ() + 0.5, 5);
        context.takeScreenshot("spike-uncracked");
        context.runOnClient(minecraft -> minecraft.level.destroyBlockProgress(BREAKER, scene.spike, 7));
        context.waitTicks(2);
        context.takeScreenshot("spike-cracks-base");
        context.runOnClient(minecraft ->
        {
            minecraft.level.destroyBlockProgress(BREAKER, scene.spike, -1);
            minecraft.level.destroyBlockProgress(BREAKER, scene.spike.above(), 9);
        });
        context.waitTicks(2);
        context.takeScreenshot("spike-cracks-tip");
        context.runOnClient(minecraft -> minecraft.level.destroyBlockProgress(BREAKER, scene.spike.above(), -1));
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    /**
     * Breaking the tip with a pickaxe pulls the whole stake out as one spike, which a real click places again.
     */
    private static void checkBreakingDropsTheSpike(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var player = player(minecraft);
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_PICKAXE));
            player.gameMode.destroyBlock(scene.spike.above());

            expect(level.getBlockState(scene.spike).isAir() && level.getBlockState(scene.spike.above()).isAir(), "Breaking the tip should pull out the whole spike");
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(scene.spike).inflate(3));
            expect(drops.size() == 1 && drops.getFirst().getItem().is(LeySpikeContent.AZURIUM_SPIKE.asItem()) && drops.getFirst().getItem().getCount() == 1,
                    "Breaking the spike should drop one spike, dropped " + drops.stream().map(drop -> drop.getItem().toString()).toList());
            drops.forEach(ItemEntity::discard);

            player.setGameMode(GameType.CREATIVE);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(LeySpikeContent.AZURIUM_SPIKE));
            useOn(player, scene.spike.below());
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            spike(minecraft, scene).setSetting(2);
        });
        context.waitTicks(5);
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
        if (minecraft.overworld().getBlockEntity(scene.spike) instanceof AzuriumSpikeTile spike)
            return spike;
        throw new AssertionError("No Azurium Spike at " + scene.spike.toShortString());
    }

    private static RavenousCollectorTile collector(MinecraftServer minecraft, Scene scene)
    {
        if (minecraft.overworld().getBlockEntity(scene.collector) instanceof RavenousCollectorTile collector)
            return collector;
        throw new AssertionError("No collector at " + scene.collector.toShortString());
    }

    private static String at(BlockPos pos)
    {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
