package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.divining.LeyDiviningRender;
import net.voxelindustry.voidheart.client.render.divining.LeySightPlan;
import net.voxelindustry.voidheart.common.content.divining.LeySight;
import net.voxelindustry.voidheart.common.ley.LeyEdge;
import net.voxelindustry.voidheart.common.ley.LeyHeights;
import net.voxelindustry.voidheart.common.ley.LeyLattice;
import net.voxelindustry.voidheart.common.ley.LeyLatticeGenerator;
import net.voxelindustry.voidheart.common.ley.LeyNode;
import net.voxelindustry.voidheart.common.ley.flow.LatticeLeyFlow;
import net.voxelindustry.voidheart.common.ley.flow.LeyTopology;
import net.voxelindustry.voidheart.common.ley.taint.LeyTaint;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import static net.voxelindustry.voidheart.gametest.GameTestKit.FLAT_GROUND;
import static net.voxelindustry.voidheart.gametest.GameTestKit.aimFrom;
import static net.voxelindustry.voidheart.gametest.GameTestKit.expect;
import static net.voxelindustry.voidheart.gametest.GameTestKit.freezeTime;
import static net.voxelindustry.voidheart.gametest.GameTestKit.overlay;
import static net.voxelindustry.voidheart.gametest.GameTestKit.player;

/**
 * The Ley Divining Rod on a real node of a flat world: raised with the use key, it reveals the lines and nodes around, spreading out, holding, then
 * fading back; it shows faintly through a wall, greys on a tainted line, and finds nothing outside the overworld. Run it alone with -PgameTest=LeyDiviningRod.
 */
public class LeyDiviningRodClientTest implements FabricClientGameTest
{
    private record Scene(LeyNode node, LeyEdge edge, double dirX, double dirZ, double lineY)
    {
        double alongX(double blocks)
        {
            return node.x() + 0.5 + dirX * blocks;
        }

        double alongZ(double blocks)
        {
            return node.z() + 0.5 + dirZ * blocks;
        }
    }

    @Override
    public void runTest(ClientGameTestContext context)
    {
        // All tests share one client, so the camera is left as this one found it
        var previousCamera = context.computeOnClient(minecraft -> minecraft.options.getCameraType());
        try
        {
            run(context);
        }
        finally
        {
            context.runOnClient(minecraft -> minecraft.options.setCameraType(previousCamera));
        }
    }

    private static void run(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamemode creative @a");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            var scene = findScene(server);
            int x = scene.node().x(), z = scene.node().z();
            server.runCommand("forceload add " + (x - 72) + " " + (z - 72) + " " + (x + 72) + " " + (z + 72));
            checkNothingOutsideOverworld(server);
            checkLineAcrossRegions(server);

            server.runCommand("clear @a");
            server.runCommand("item replace entity @a hotbar.0 with voidheart:ley_divining_rod");
            context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(0));

            // Beside the line, a little downstream of its node, looking along it back toward the node
            double sideX = -scene.dirZ() * 9, sideZ = scene.dirX() * 9;
            double feetX = scene.alongX(22) + sideX, feetZ = scene.alongZ(22) + sideZ;
            double feetY = scene.lineY() + 1;
            aimFrom(context, server, feetX, feetY, feetZ, scene.alongX(8), scene.lineY() + 1.5, scene.alongZ(8), 20);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("ley-rod-in-hand");

            context.getInput().pressKey(options -> options.keyUse);
            context.waitTicks(1);
            context.getInput().pressKey(InputConstants.KEY_F1);
            context.waitTicks(5);
            checkRevealed(context, server);
            context.takeScreenshot("ley-rod-spreading");

            context.waitTicks(LeySightPlan.SPREAD_TICKS + 10);
            context.takeScreenshot("ley-rod-held");
            server.runCommand("tick freeze");

            aimFrom(context, server, x + 0.5 - scene.dirX() * 10 + sideX * 0.6, scene.lineY() + 1, z + 0.5 - scene.dirZ() * 10 + sideZ * 0.6,
                    x + 0.5, scene.lineY() + 1, z + 0.5, 5);
            context.takeScreenshot("ley-rod-node");

            aimFrom(context, server, scene.alongX(20) + sideX * 2.5, scene.lineY() + 30, scene.alongZ(20) + sideZ * 2.5,
                    scene.alongX(20), scene.lineY(), scene.alongZ(20), 10);
            context.takeScreenshot("ley-rod-wide");

            // Camera facing ribbons, so from both sides of the strand
            sweep(context, server, scene, sideX, sideZ, "ley-rod-sweep-");
            sweep(context, server, scene, -sideX, -sideZ, "ley-rod-opposite-sweep-");

            // A wall between the player and the line: the strand should still show, faintly
            double wallX = scene.alongX(14) + sideX * 0.5, wallZ = scene.alongZ(14) + sideZ * 0.5;
            server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:stone_bricks", (int) wallX - 2, FLAT_GROUND + 1, (int) wallZ - 2,
                    (int) wallX + 2, (int) scene.lineY() + 4, (int) wallZ + 2));
            aimFrom(context, server, feetX, feetY, feetZ, scene.alongX(14), scene.lineY() + 0.5, scene.alongZ(14), 5);
            context.takeScreenshot("ley-rod-through-wall");
            server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:air", (int) wallX - 2, FLAT_GROUND + 1, (int) wallZ - 2,
                    (int) wallX + 2, (int) scene.lineY() + 4, (int) wallZ + 2));

            freezeTime(server, "midnight");
            aimFrom(context, server, feetX, feetY, feetZ, scene.alongX(8), scene.lineY() + 1.5, scene.alongZ(8), 5);
            context.takeScreenshot("ley-rod-night");
            freezeTime(server, "noon");
            server.runCommand("tick unfreeze");

            context.waitTicks(LeySightPlan.HOLD_END_TICKS - LeySightPlan.SPREAD_TICKS - 10);
            aimFrom(context, server, scene.alongX(20) + sideX * 2.5, scene.lineY() + 30, scene.alongZ(20) + sideZ * 2.5,
                    scene.alongX(20), scene.lineY(), scene.alongZ(20), 1);
            context.waitTicks(10);
            context.takeScreenshot("ley-rod-fading");
            context.waitTicks(LeySightPlan.FADE_TICKS);
            expect(!context.computeOnClient(minecraft -> LeyDiviningRender.isShowing()), "The reveal should be over after " + LeySightPlan.LIFE_TICKS + " ticks");

            checkTaintedLine(context, server, scene, feetX, feetY, feetZ);
            checkLeavesWithDimension(context, server, feetX, feetY, feetZ);

            context.getInput().pressKey(InputConstants.KEY_F1);
        }
    }

    private static Scene findScene(TestServerContext server)
    {
        var scene = server.computeOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var topology = LeyTopology.get(level).orElseThrow(() -> new AssertionError("Expected a ley lattice in the overworld"));
            var heights = LeyHeights.of(level);
            for (var region : topology.lattice().regionsAround(0, 0, 1024, true))
            {
                for (var node : region.nodes())
                {
                    for (var edge : topology.edges(node.id()))
                    {
                        if (edge.from() != node.id() || edge.length() < 48 || LatticeLeyFlow.lineFlow(level, topology, edge) <= 0.5)
                            continue;
                        double length = edge.length();
                        return new Scene(node, edge, (edge.toX() - edge.fromX()) / length, (edge.toZ() - edge.fromZ()) / length,
                                heights.nodeHeight(node.x(), node.z()) + 0.5);
                    }
                }
            }
            return null;
        });
        expect(scene != null, "Expected a node with a long, flowing line leaving it");
        return scene;
    }

    private static void checkNothingOutsideOverworld(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var sight = LeySight.collect(minecraft.getLevel(Level.NETHER), Vec3.ZERO);
            expect(sight.lines().isEmpty() && sight.nodes().isEmpty(), "The rod should find nothing in the nether, found " + sight.lines().size() + " lines");
        });
    }

    private static void checkRevealed(ClientGameTestContext context, TestServerContext server)
    {
        var counts = context.computeOnClient(minecraft -> LeyDiviningRender.shownCounts());
        expect(counts[0] > 0, "The rod should reveal the line beside the player, showed " + counts[0] + " lines and " + counts[1] + " nodes");
        expect(counts[1] > 0, "The rod should reveal the node within its radius, showed " + counts[1] + " nodes");
        boolean cooling = server.computeOnServer(minecraft -> player(minecraft).getCooldowns().isOnCooldown(new ItemStack(VoidHeartItems.LEY_DIVINING_ROD)));
        expect(cooling, "The rod should cool down once raised");
    }

    /**
     * Sweeps the camera in small steps near the strand while the game is frozen, so flicker would show as uneven frame differences.
     */
    private static void sweep(ClientGameTestContext context, TestServerContext server, Scene scene, double sideX, double sideZ, String name)
    {
        for (int step = 0; step < 8; step++)
        {
            aimFrom(context, server, scene.alongX(18) + sideX * 0.45 + step * 0.06, scene.lineY() + 0.6, scene.alongZ(18) + sideZ * 0.45 + step * 0.09,
                    scene.alongX(10), scene.lineY(), scene.alongZ(10), 2);
            context.takeScreenshot(name + step);
        }
    }

    /**
     * A region keeps only the lines with an end inside it. Raised beside a line whose ends both lie in other regions, the rod must still find it.
     */
    private static void checkLineAcrossRegions(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var lattice = LeyLattice.get(level).orElseThrow();
            int size = LeyLatticeGenerator.REGION_SIZE;
            for (var region : lattice.regionsAround(0, 0, size, true))
            {
                for (var edge : region.edges())
                {
                    for (double t = 0.1; t < 0.9; t += 0.02)
                    {
                        double px = edge.fromX() + (edge.toX() - edge.fromX()) * t, pz = edge.fromZ() + (edge.toZ() - edge.fromZ()) * t;
                        int regionX = Math.floorDiv((int) px, size), regionZ = Math.floorDiv((int) pz, size);
                        boolean fromHere = Math.floorDiv(edge.fromX(), size) == regionX && Math.floorDiv(edge.fromZ(), size) == regionZ;
                        boolean toHere = Math.floorDiv(edge.toX(), size) == regionX && Math.floorDiv(edge.toZ(), size) == regionZ;
                        // Far enough inside its region that the old lookup, by the rod's radius alone, would not have reached the ends' regions
                        double inside = Math.min(Math.min(px - regionX * size, (regionX + 1) * size - px), Math.min(pz - regionZ * size, (regionZ + 1) * size - pz));
                        if (fromHere || toHere || inside <= LeySight.RADIUS)
                            continue;

                        var sight = LeySight.collect(level, new Vec3(px, 0, pz));
                        boolean found = sight.lines().stream().anyMatch(line -> line.fromX() == edge.fromX() && line.fromZ() == edge.fromZ()
                                && line.toX() == edge.toX() && line.toZ() == edge.toZ());
                        expect(found, "The rod raised at " + (int) px + ", " + (int) pz + " should find the line passing under it, whose ends lie in other regions");
                        return;
                    }
                }
            }
            throw new AssertionError("Expected a line crossing a region whose ends both lie outside it, to check the rod finds it");
        });
    }

    /**
     * A reveal belongs to the world it was raised in, and stops showing once its bearer leaves for another dimension.
     */
    private static void checkLeavesWithDimension(ClientGameTestContext context, TestServerContext server, double feetX, double feetY, double feetZ)
    {
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(5);
        expect(context.computeOnClient(minecraft -> LeyDiviningRender.isShowing()), "The rod should reveal the lines again before leaving");
        server.runCommand("execute in minecraft:the_nether run tp @a 0 100 0");
        context.waitFor(minecraft -> minecraft.level != null && minecraft.level.dimension() == Level.NETHER, 200);
        context.waitTicks(5);
        expect(!context.computeOnClient(minecraft -> LeyDiviningRender.isShowing()), "The reveal should not follow its bearer into the nether");
        server.runCommand("execute in minecraft:overworld run tp @a " + feetX + " " + feetY + " " + feetZ);
        context.waitFor(minecraft -> minecraft.level != null && minecraft.level.dimension() == Level.OVERWORLD, 200);
    }

    private static void checkTaintedLine(ClientGameTestContext context, TestServerContext server, Scene scene, double feetX, double feetY, double feetZ)
    {
        server.runOnServer(minecraft ->
        {
            LeyTaint.set(minecraft.overworld(), scene.edge().from(), 100);
            LeyTaint.set(minecraft.overworld(), scene.edge().to(), 100);
        });
        aimFrom(context, server, feetX, feetY, feetZ, scene.alongX(8), scene.lineY() + 1.5, scene.alongZ(8), 5);
        server.runOnServer(minecraft ->
        {
            var sight = LeySight.collect(minecraft.overworld(), new Vec3(scene.alongX(10), scene.lineY(), scene.alongZ(10)));
            var line = sight.lines().stream().filter(shown -> shown.fromX() == scene.edge().fromX() && shown.fromZ() == scene.edge().fromZ()
                    && shown.toX() == scene.edge().toX() && shown.toZ() == scene.edge().toZ()).findFirst();
            expect(line.isPresent(), "The rod should find the tainted line it stands beside");
            expect(line.get().fromTaint() == 1 && line.get().toTaint() == 1, "A breached line's taint should reach the rod as 1, got " + line.get().fromTaint());
        });
        boolean cooling = server.computeOnServer(minecraft -> player(minecraft).getCooldowns().isOnCooldown(new ItemStack(VoidHeartItems.LEY_DIVINING_ROD)));
        expect(!cooling, "The rod should be ready again once its reveal is over");
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(LeySightPlan.SPREAD_TICKS + 15);
        var counts = context.computeOnClient(minecraft -> LeyDiviningRender.shownCounts());
        expect(counts[0] > 0, "Raised again on a tainted line, the rod should reveal it, showed " + counts[0] + " lines");
        context.takeScreenshot("ley-rod-tainted");
        // Earlier tests may leave their own message on the action bar, so only the rod's is checked
        expect(!overlay(context).contains("lies still"), "The rod should not say it finds nothing where ley runs, got " + overlay(context));
        server.runOnServer(minecraft ->
        {
            LeyTaint.set(minecraft.overworld(), scene.edge().from(), 0);
            LeyTaint.set(minecraft.overworld(), scene.edge().to(), 0);
        });
        context.waitTicks(LeySightPlan.LIFE_TICKS);
    }
}
