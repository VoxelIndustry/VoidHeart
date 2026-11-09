package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.voxelindustry.voidheart.client.render.energy.ley_taint.LeyTaintLook;
import net.voxelindustry.voidheart.client.render.energy.ley_taint.LeyTaintView;
import net.voxelindustry.voidheart.common.content.oculus.OculusTableStructure;
import net.voxelindustry.voidheart.common.content.oculus.OculusView;
import net.voxelindustry.voidheart.common.ley.Ley;
import net.voxelindustry.voidheart.common.ley.LeyEdge;
import net.voxelindustry.voidheart.common.ley.LeyNode;
import net.voxelindustry.voidheart.common.ley.TaintStage;
import net.voxelindustry.voidheart.common.ley.anchor.LeyAnchorState;
import net.voxelindustry.voidheart.common.ley.flow.LatticeLeyFlow;
import net.voxelindustry.voidheart.common.ley.flow.LeyFlowMath;
import net.voxelindustry.voidheart.common.ley.flow.LeyTopology;
import net.voxelindustry.voidheart.common.ley.taint.LeyTaint;
import net.voxelindustry.voidheart.common.ley.taint.LeyTaintState;

import java.util.function.Predicate;

import static net.voxelindustry.voidheart.gametest.GameTestKit.FLAT_FEET;
import static net.voxelindustry.voidheart.gametest.GameTestKit.aimFrom;
import static net.voxelindustry.voidheart.gametest.GameTestKit.expect;
import static net.voxelindustry.voidheart.gametest.GameTestKit.freezeTime;
import static net.voxelindustry.voidheart.gametest.GameTestKit.waitFor;

/**
 * W3 Ley flow and taint on a real node of a flat world: the flow and upstream-first claims, taint passed downstream and slowed
 * by salt, the veins on the ground at each stage, an Old Anchor guttering, the Oculus ley layer, and a node healing clean.
 * Run it alone with -PgameTest=LeyFlow.
 */
public class LeyFlowClientTest implements FabricClientGameTest
{
    private static final float TAINTED = 35, FESTERING = 65, BREACHED = 95;
    private static final double EPSILON = 1.0E-6;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamemode creative @a");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            var node = server.computeOnServer(minecraft -> LeyTopology.get(minecraft.overworld()).flatMap(topology -> topology.lattice().nearestNode(0, 0)).orElse(null));
            expect(node != null, "Expected a ley lattice in the overworld");
            int x = node.x(), z = node.z();
            server.runCommand("forceload add " + (x - 40) + " " + (z - 40) + " " + (x + 40) + " " + (z + 40));
            aimFrom(context, server, x + 7.5, FLAT_FEET + 6, z + 7.5, x + 0.5, FLAT_FEET, z + 0.5, 40);
            world.getConnection().waitForChunksRender();

            checkFlow(server, node);
            checkClaimsServeUpstreamFirst(context, server);
            checkSpreadAndSalt(context, server, node);

            // An awake Old Anchor stands on the node, as on the real lattice's best connected nodes
            server.runCommand("setblock " + x + " " + FLAT_FEET + " " + z + " voidheart:old_anchor[half=lower,active=true]");
            server.runCommand("setblock " + x + " " + (FLAT_FEET + 1) + " " + z + " voidheart:old_anchor[half=upper,active=true]");
            server.runOnServer(minecraft -> LeyAnchorState.get(minecraft.overworld()).markPlaced(node.id(), FLAT_FEET));
            var table = placeOculus(context, server, node);
            context.getInput().pressKey(InputConstants.KEY_F1);

            context.takeScreenshot("ley-taint-clean");
            showStage(context, server, node, TAINTED, TaintStage.TAINTED, "ley-taint-tainted");
            showStage(context, server, node, FESTERING, TaintStage.FESTERING, "ley-taint-festering");
            sweep(context, server, node);
            showStage(context, server, node, BREACHED, TaintStage.BREACHED, "ley-taint-breached");
            checkBreachedAnchorGutters(context, node);

            aimFrom(context, server, x + 0.5, FLAT_FEET + 30, z + 18.5, x + 0.5, FLAT_FEET, z + 0.5, 10);
            context.takeScreenshot("ley-taint-breached-wide");
            aimFrom(context, server, x + 3.5, FLAT_FEET + 1, z + 3.5, x + 0.5, FLAT_FEET + 1.2, z + 0.5, 10);
            context.takeScreenshot("ley-taint-breached-anchor");
            freezeTime(server, "midnight");
            aimFrom(context, server, x + 7.5, FLAT_FEET + 6, z + 7.5, x + 0.5, FLAT_FEET, z + 0.5, 20);
            context.takeScreenshot("ley-taint-breached-night");
            freezeTime(server, "noon");

            lookAtOculus(context, server, table, "ley-taint-oculus-breached");
            checkHealsClean(context, server, node, table);

            context.getInput().pressKey(InputConstants.KEY_F1);
        }
    }

    private static void checkFlow(TestServerContext server, LeyNode node)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            expect(Ley.provider() instanceof LatticeLeyFlow, "Expected W3's flow to be installed, got " + Ley.provider());

            double expected = LeyFlowMath.nodeFlow(node.power(), LatticeLeyFlow.moonPhase(level), LatticeLeyFlow.isAwakened(level, node.id()), 0);
            double flow = Ley.lineFlowAt(level, new BlockPos(node.x(), FLAT_FEET, node.z()));
            expect(expected > 0 && Math.abs(flow - expected) < EPSILON, "Node flow should be power × tide × awakening, expected " + expected + " got " + flow);

            LeyTaint.set(level, node.id(), 100);
            double tainted = Ley.lineFlowAt(level, new BlockPos(node.x(), FLAT_FEET, node.z()));
            expect(Math.abs(tainted - expected / 2) < EPSILON, "A fully tainted node should carry half its flow, got " + tainted + " of " + expected);
            LeyTaint.set(level, node.id(), 0);
        });
    }

    /**
     * Two claimants on one real line, the downstream one claiming first each tick: the upstream one is still served first.
     */
    private static void checkClaimsServeUpstreamFirst(ClientGameTestContext context, TestServerContext server)
    {
        var spots = server.computeOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var topology = LeyTopology.get(level).orElseThrow();
            for (var region : topology.lattice().regionsAround(0, 0, 1024, true))
            {
                for (var edge : region.edges())
                {
                    var upstream = spotAlong(topology, edge, 0.3);
                    var downstream = spotAlong(topology, edge, 0.7);
                    if (upstream != null && downstream != null && LatticeLeyFlow.lineFlow(level, topology, edge) > 1)
                        return new BlockPos[] { upstream, downstream };
                }
            }
            return null;
        });
        expect(spots != null, "Expected a line with two spots well clear of its nodes");

        double flow = server.computeOnServer(minecraft -> Ley.lineFlowAt(minecraft.overworld(), spots[0]));
        double[] grants = new double[2];
        for (int tick = 0; tick < 4; tick++)
        {
            server.runOnServer(minecraft ->
            {
                grants[1] = Ley.claim(minecraft.overworld(), spots[1], flow);
                grants[0] = Ley.claim(minecraft.overworld(), spots[0], flow * 0.75);
            });
            context.waitTick();
        }
        expect(Math.abs(grants[0] - flow * 0.75) < EPSILON, "Upstream should get all it asked, " + flow * 0.75 + ", got " + grants[0]);
        expect(Math.abs(grants[1] - flow * 0.25) < EPSILON, "Downstream should get what upstream left, " + flow * 0.25 + ", got " + grants[1]);
    }

    private static BlockPos spotAlong(LeyTopology topology, LeyEdge edge, double t)
    {
        int x = (int) Math.round(edge.fromX() + (edge.toX() - edge.fromX()) * t);
        int z = (int) Math.round(edge.fromZ() + (edge.toZ() - edge.fromZ()) * t);
        var attachment = topology.attachment(x, z);
        return attachment.onLine() && attachment.edge().equals(edge) ? new BlockPos(x, FLAT_FEET, z) : null;
    }

    private static void checkSpreadAndSalt(ClientGameTestContext context, TestServerContext server, LeyNode node)
    {
        var downstream = server.computeOnServer(minecraft -> LeyTopology.get(minecraft.overworld()).orElseThrow().downstream(node.id()));
        if (downstream.length > 0)
        {
            server.runOnServer(minecraft ->
            {
                LeyTaint.forgetSalt();
                Ley.addTaint(minecraft.overworld(), node.id(), 10);
            });
            context.waitTicks(25);
            server.runOnServer(minecraft ->
            {
                for (long next : downstream)
                {
                    float taint = Ley.taint(minecraft.overworld(), next);
                    expect(taint > 3.5F && taint < 10, "A downstream neighbour should take 40% of a gain of 10, got " + taint);
                }
            });
        }

        // Salt within 8 blocks of the node halves what it gains
        server.runCommand("setblock " + (node.x() + 5) + " " + FLAT_FEET + " " + node.z() + " voidheart:salt_ward");
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            LeyTaint.set(level, node.id(), 0);
            LeyTaint.forgetSalt();
            Ley.addTaint(level, node.id(), 10);
            float taint = Ley.taint(level, node.id());
            expect(Math.abs(taint - 5) < 0.01F, "Salt should halve the gain of 10, got " + taint);
        });
        server.runCommand("setblock " + (node.x() + 5) + " " + FLAT_FEET + " " + node.z() + " air");
        context.waitTicks(25);
        server.runOnServer(minecraft ->
        {
            LeyTaint.forgetSalt();
            clearTaint(minecraft);
        });
        context.waitTicks(25);
    }

    private static void clearTaint(MinecraftServer minecraft)
    {
        var level = minecraft.overworld();
        var state = LeyTaintState.get(level);
        for (long tainted : new LongArrayList(state.tainted()))
            state.set(tainted, 0);
    }

    private static void showStage(ClientGameTestContext context, TestServerContext server, LeyNode node, float taint, TaintStage stage, String screenshot)
    {
        server.runCommand("execute positioned " + node.x() + " " + FLAT_FEET + " " + node.z() + " run voidheart ley taint set " + taint);
        expect(server.computeOnServer(minecraft -> Ley.stage(minecraft.overworld(), node.id())) == stage, "The node should be " + stage);
        waitForClient(context, 60, "the client to see the node " + stage, minecraft ->
        {
            var seen = LeyTaintView.at(node.x(), node.z());
            return seen != null && seen.stage() == stage;
        });
        context.waitTicks(LeyTaintLook.EASE_TICKS + 10);
        aimFrom(context, server, node.x() + 7.5, FLAT_FEET + 6, node.z() + 7.5, node.x() + 0.5, FLAT_FEET, node.z() + 0.5, 5);
        context.takeScreenshot(screenshot);
    }

    /**
     * Veins, darkened ground and the anchor overlay are layered, so look for flicker while the camera moves.
     */
    private static void sweep(ClientGameTestContext context, TestServerContext server, LeyNode node)
    {
        for (int step = 0; step < 6; step++)
            aimFrom(context, server, node.x() + 6.5 + step * 0.07, FLAT_FEET + 3.5 + step * 0.04, node.z() + 5.5 - step * 0.05,
                    node.x() + 0.5, FLAT_FEET, node.z() + 0.5, 2);
        for (int step = 0; step < 6; step++)
        {
            aimFrom(context, server, node.x() + 6.5 + step * 0.07, FLAT_FEET + 3.5 + step * 0.04, node.z() + 5.5 - step * 0.05,
                    node.x() + 0.5, FLAT_FEET, node.z() + 0.5, 2);
            context.takeScreenshot("ley-taint-festering-sweep-" + step);
        }
    }

    private static void checkBreachedAnchorGutters(ClientGameTestContext context, LeyNode node)
    {
        int mauve = 0;
        for (int tick = 0; tick < 100; tick++)
        {
            if (context.computeOnClient(minecraft ->
            {
                var seen = LeyTaintView.at(node.x(), node.z());
                return seen != null && seen.anchorY() == FLAT_FEET && seen.flicker().mauve(LeyTaintView.ticks()) > 0.5F;
            }))
                mauve++;
            context.waitTick();
        }
        expect(mauve > 60, "A breached anchor should show mostly mauve, it did on " + mauve + " of 100 ticks");
    }

    private static BlockPos placeOculus(ClientGameTestContext context, TestServerContext server, LeyNode node)
    {
        // On the diagonal farthest from every line, so the veins don't run under the table
        var spokes = server.computeOnServer(minecraft -> LeyTopology.get(minecraft.overworld()).orElseThrow().edges(node.id()));
        double best = -1, bestAngle = 0;
        for (int i = 0; i < 8; i++)
        {
            double angle = Math.PI / 4 * i + Math.PI / 8;
            double nearest = Double.MAX_VALUE;
            for (var edge : spokes)
            {
                double spoke = edge.from() == node.id() ? Math.atan2(edge.toZ() - node.z(), edge.toX() - node.x()) : Math.atan2(edge.fromZ() - node.z(), edge.fromX() - node.x());
                nearest = Math.min(nearest, Math.abs(Math.IEEEremainder(angle - spoke, 2 * Math.PI)));
            }
            if (nearest > best)
            {
                best = nearest;
                bestAngle = angle;
            }
        }
        int tableX = node.x() + (int) Math.round(Math.cos(bestAngle) * 12);
        int tableZ = node.z() + (int) Math.round(Math.sin(bestAngle) * 12);
        server.runCommand("fill " + (tableX - 2) + " " + FLAT_FEET + " " + (tableZ - 2) + " " + (tableX + 2) + " " + FLAT_FEET + " " + (tableZ + 2) + " voidheart:oculus_table");
        context.waitTicks(20);
        var table = new BlockPos(tableX, FLAT_FEET, tableZ);
        server.runOnServer(minecraft ->
        {
            OculusTableStructure.updateView(minecraft.overworld(), table, OculusView::toggleLeyLines);
            OculusTableStructure.updateView(minecraft.overworld(), table, OculusView::zoomIn);
        });
        return table;
    }

    private static void lookAtOculus(ClientGameTestContext context, TestServerContext server, BlockPos table, String screenshot)
    {
        aimFrom(context, server, table.getX() + 0.5, FLAT_FEET + 3, table.getZ() + 4.5, table.getX() + 0.5, FLAT_FEET + 1, table.getZ() + 0.5, 80);
        context.takeScreenshot(screenshot);
    }

    /**
     * Just over the Tainted threshold, the node heals under it within a couple of minutes and its veins and tones fade.
     */
    private static void checkHealsClean(ClientGameTestContext context, TestServerContext server, LeyNode node, BlockPos table)
    {
        float before = server.computeOnServer(minecraft -> Ley.taint(minecraft.overworld(), node.id()));
        context.waitTicks(1200);
        float after = server.computeOnServer(minecraft -> Ley.taint(minecraft.overworld(), node.id()));
        expect(after < before - 0.5F, "A resting node should heal about 0.8 a minute at 95, went from " + before + " to " + after);

        server.runCommand("execute positioned " + node.x() + " " + FLAT_FEET + " " + node.z() + " run voidheart ley taint set 20.3");
        context.waitTicks(LeyTaintLook.EASE_TICKS + 20);
        aimFrom(context, server, node.x() + 7.5, FLAT_FEET + 6, node.z() + 7.5, node.x() + 0.5, FLAT_FEET, node.z() + 0.5, 5);
        context.takeScreenshot("ley-taint-healing-tainted");

        waitFor(context, server, 3000, "the node to heal under Tainted", minecraft -> Ley.stage(minecraft.overworld(), node.id()) == TaintStage.CLEAN);
        waitForClient(context, 200, "the veins to fade once the node is clean", minecraft -> LeyTaintView.at(node.x(), node.z()) == null);
        context.takeScreenshot("ley-taint-healed");
        lookAtOculus(context, server, table, "ley-taint-oculus-healed");
    }

    private static void waitForClient(ClientGameTestContext context, int maxTicks, String what, Predicate<Minecraft> condition)
    {
        for (int tick = 0; tick < maxTicks; tick++)
        {
            if (context.computeOnClient(condition::test))
                return;
            context.waitTick();
        }
        throw new AssertionError("Expected " + what + " within " + maxTicks + " ticks");
    }
}
