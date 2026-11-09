package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousRelayTile;
import net.voxelindustry.voidheart.common.curse.Corruption;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.energy.VoidSeep;
import net.voxelindustry.voidheart.common.energy.VoidWounds;
import net.voxelindustry.voidheart.common.energy.fissure.FissureScars;
import net.voxelindustry.voidheart.common.energy.fissure.SeepCreatures;
import net.voxelindustry.voidheart.common.energy.fissure.SeepMath;
import net.voxelindustry.voidheart.common.energy.fissure.VoidFissureContent;
import net.voxelindustry.voidheart.common.energy.fissure.VoidFissureTile;
import net.voxelindustry.voidheart.common.energy.fissure.VoidFissures;
import net.voxelindustry.voidheart.common.energy.ley.LeyWardTile;
import net.voxelindustry.voidheart.common.energy.ley.WardStrength;
import net.voxelindustry.voidheart.common.ley.Ley;
import net.voxelindustry.voidheart.common.ley.LeyLattice;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.concurrent.atomic.AtomicLong;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * A Void Fissure torn open beside a real ley line, with a real click on a Fissure Seed:
 * <ul>
 *     <li>It powers a machine in a pylon's field, 16 a tick, and 32 once a small azurium bundle tears it wider; the wrong bundle does nothing.</li>
 *     <li>Inside its seep a powered ward weakens by the seep's strength, corruption rises faster, and the seep raises it on its own.</li>
 *     <li>The node of the line close by gains taint, and weak void creatures come through, up to the cap.</li>
 *     <li>{@code VoidWounds.open} leaves a wound on the ground that seeps and gives nothing.</li>
 *     <li>Width, seep and Contravoid paid survive a reload. A relay of Contravoid closes the fissure, a payment closes the wound, and both leave scars.</li>
 * </ul>
 */
public class VoidFissureClientTest implements FabricClientGameTest
{
    private static final int FISSURE_OUT  = 10;
    private static final int WARD_OUT     = 8;
    private static final int PYLON_ALONG  = 3;
    private static final int MACHINE_ALONG = 6;
    private static final int RELAY_ALONG  = -2;
    private static final int COLLECTOR_ALONG = -4;
    private static final int WOUND_ALONG  = -16;
    private static final int RISES        = 10;
    private static final int RISE         = 2;
    private static final int MEASURE_TICKS = 60;
    private static final float SEEP_RADIUS = 16;
    private static final int PREPAID      = 1000;

    private static final java.util.Map<BlockPos, Long> ASKS  = new java.util.concurrent.ConcurrentHashMap<>();
    private static final AtomicLong                    DRAWN = new AtomicLong();
    private static boolean hooked;

    private record Scene(BlockPos fissure, BlockPos ward, BlockPos leyStone, BlockPos pylon, BlockPos machine, BlockPos relay, BlockPos collector,
                         BlockPos wound, Vec3 along, Vec3 out)
    {
    }

    @Override
    public void runTest(ClientGameTestContext context)
    {
        EnergyTestKit.expectRegistered("fissure_seed");
        expect(BuiltInRegistries.BLOCK.containsKey(id("void_fissure")) && BuiltInRegistries.BLOCK.containsKey(id("void_wound")),
                "The fissure and wound blocks should be registered");
        expect(!BuiltInRegistries.ITEM.containsKey(id("void_fissure")) && !BuiltInRegistries.ITEM.containsKey(id("void_wound")),
                "The fissure and wound should have no item of their own");
        hook();

        TestWorldSave save;
        Scene scene;
        long upstream;
        try (var world = context.worldBuilder().create())
        {
            save = world.getWorldSave();
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("difficulty normal");
            server.runCommand("gamemode creative @a");
            context.waitTicks(10);

            scene = server.computeOnServer(minecraft -> findScene(minecraft.overworld()));
            var f = scene.fissure();
            server.runCommand("forceload add " + (f.getX() - 40) + " " + (f.getZ() - 40) + " " + (f.getX() + 40) + " " + (f.getZ() + 40));
            tp(server, f.getX() + 0.5, FLAT_FEET, f.getZ() + 0.5);
            context.waitTicks(40);
            world.getConnection().waitForChunksRender();

            float wardedBefore = buildWard(context, server, scene);
            upstream = placeSeed(context, server, scene);
            float taintAtOpening = server.computeOnServer(minecraft -> Ley.taint(minecraft.overworld(), upstream));

            checkPower(context, server, scene);
            checkSeep(context, server, scene, wardedBefore);
            checkCreatures(context, server, scene);
            checkWound(context, server, scene);
            screenshots(context, server, scene);
            checkTaint(server, upstream, taintAtOpening);

            server.runCommand("voidheart energy fissure " + at(f) + " pay " + PREPAID);
            context.waitTicks(5);
        }

        try (var world = save.open())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);
            checkReload(server, scene);
            closeFissure(context, server, scene);
            closeWound(context, server, scene);
        }
    }

    /**
     * Looks along nearby lines for one the fissure can sit 10 blocks from, with its seep, ward and wound all within the line's reach.
     */
    private static Scene findScene(ServerLevel level)
    {
        var lattice = LeyLattice.get(level).orElseThrow(() -> new AssertionError("Expected a ley lattice in the overworld"));
        for (int probe = 0; probe < 25; probe++)
        {
            var edge = lattice.nearestEdge((probe % 5 - 2) * 400, (probe / 5 - 2) * 400).orElse(null);
            if (edge == null || edge.length() < 64)
                continue;
            var along = new Vec3(edge.toX() - edge.fromX(), 0, edge.toZ() - edge.fromZ()).normalize();
            var onLine = new Vec3(edge.fromX() + (edge.toX() - edge.fromX()) * 0.5, FLAT_FEET, edge.fromZ() + (edge.toZ() - edge.fromZ()) * 0.5);
            for (int side : new int[] { 1, -1 })
            {
                var out = new Vec3(-along.z * side, 0, along.x * side);
                var fissure = at(onLine, along, out, 0, FISSURE_OUT);
                var line = Ley.lineNear(level, fissure, 48);
                if (line.isEmpty() || line.get().from() != edge.from())
                    continue;
                return new Scene(fissure, at(onLine, along, out, 0, FISSURE_OUT + WARD_OUT), at(onLine, along, out, 2, FISSURE_OUT + WARD_OUT),
                        at(onLine, along, out, PYLON_ALONG, FISSURE_OUT), at(onLine, along, out, MACHINE_ALONG, FISSURE_OUT),
                        at(onLine, along, out, RELAY_ALONG, FISSURE_OUT), at(onLine, along, out, COLLECTOR_ALONG, FISSURE_OUT),
                        at(onLine, along, out, WOUND_ALONG, FISSURE_OUT), along, out);
            }
        }
        throw new AssertionError("Found no long line near the origin");
    }

    private static BlockPos at(Vec3 origin, Vec3 along, Vec3 out, double a, double o)
    {
        return BlockPos.containing(origin.x + along.x * a + out.x * o, FLAT_FEET, origin.z + along.z * a + out.z * o);
    }

    /**
     * A ward powered by a creative ley stone, at full strength with no seep yet.
     *
     * @return how much corruption ten rises add beside it
     */
    private static float buildWard(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        setblock(server, scene.ward(), "voidheart:ley_ward[facing=north]");
        setblock(server, scene.leyStone(), "voidheart:creative_ley_stone");
        context.waitTicks(25);
        server.runOnServer(minecraft ->
        {
            var ward = ward(minecraft, scene);
            expect(ward.supply() > 0.99F && ward.health() > 0.99F, "The ward should be fully powered and whole before the fissure, supply " + ward.supply()
                    + ", health " + ward.health());
        });
        return raiseAt(server, beside(scene));
    }

    private static BlockPos beside(Scene scene)
    {
        return BlockPos.containing(scene.fissure().getX() + 0.5 + scene.out().x * 2, FLAT_FEET, scene.fissure().getZ() + 0.5 + scene.out().z * 2);
    }

    /**
     * A Fissure Seed placed with a real click on the ground tears open a width 1 fissure.
     *
     * @return the upstream node of the line close by
     */
    private static long placeSeed(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        var f = scene.fissure();
        aimCamera(server, f.getX() + 0.5 - scene.along().x * 3, FLAT_FEET, f.getZ() + 0.5 - scene.along().z * 3, f.getX() + 0.5, FLAT_FEET, f.getZ() + 0.5);
        context.waitTicks(2);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidFissureContent.FISSURE_SEED));
            useOn(player, f.below());
        });
        context.waitTicks(5);
        return server.computeOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            expect(level.getBlockState(f).is(VoidFissureContent.VOID_FISSURE), "Placing a seed should tear a fissure open, found " + level.getBlockState(f));
            var fissure = fissure(minecraft, scene);
            expect(fissure.width() == 1 && !fissure.isWound(), "A new fissure should be width 1, got " + fissure.width());
            expect(fissure.cost() == 2000, "Closing a width 1 fissure should cost 2000 Contravoid, costs " + fissure.cost());
            return Ley.lineNear(level, f, 48).orElseThrow().from();
        });
    }

    /**
     * A small pylon covers the fissure and a machine. The machine gets the fissure's 16 a tick, and 32 once a small bundle widens it.
     */
    private static void checkPower(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        setblock(server, scene.pylon(), "voidheart:azurium_pylon");
        setblock(server, scene.pylon().above(), "voidheart:small_azurium_bundle[facing=up]");
        context.waitTicks(60);

        float width1 = measure(context, server, scene.machine());
        server.runOnServer(minecraft -> expect(fissure(minecraft, scene).outputTarget() != null, "The fissure should show an arc to the pylon taking its energy"));
        stopDrawing(server);
        expect(width1 > 13 && width1 <= 16.3F, "A width 1 fissure should power the machine with about 16 a tick, gave " + width1);

        widenWith(server, scene, VoidHeartBlocks.LARGE_AZURIUM_BUNDLE.asItem());
        server.runOnServer(minecraft -> expect(fissure(minecraft, scene).width() == 1, "A large bundle should not widen a width 1 fissure"));
        widenWith(server, scene, VoidHeartBlocks.SMALL_AZURIUM_BUNDLE.asItem());
        server.runOnServer(minecraft ->
        {
            var fissure = fissure(minecraft, scene);
            expect(fissure.width() == 2, "A small bundle should tear the fissure to width 2, it is " + fissure.width());
            expect(fissure.cost() == 4000, "Closing a width 2 fissure should cost 4000, costs " + fissure.cost());
        });
        context.waitTicks(10);
        float width2 = measure(context, server, scene.machine());
        stopDrawing(server);
        expect(width2 > 28 && width2 <= 32.6F, "A width 2 fissure should power the machine with about 32 a tick, gave " + width2);
    }

    private static void widenWith(TestServerContext server, Scene scene, net.minecraft.world.item.Item bundle)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(bundle));
            useOn(player, scene.fissure());
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
    }

    /**
     * What the machine got a tick on average, drawing up to 100 a tick. It keeps drawing until {@link #stopDrawing}.
     */
    private static float measure(ClientGameTestContext context, TestServerContext server, BlockPos machine)
    {
        startDrawing(server, machine);
        context.waitTicks(10);
        server.runOnServer(minecraft -> DRAWN.set(0));
        context.waitTicks(MEASURE_TICKS);
        return server.computeOnServer(minecraft -> DRAWN.get()) / (float) MEASURE_TICKS;
    }

    private static void startDrawing(TestServerContext server, BlockPos machine)
    {
        server.runOnServer(minecraft ->
        {
            DRAWN.set(0);
            ASKS.put(machine, 100L);
        });
    }

    private static void stopDrawing(TestServerContext server)
    {
        server.runOnServer(minecraft -> ASKS.clear());
    }

    /**
     * At width 3 with its seep grown to 16 blocks, the ward 8 blocks away loses half its strength, corruption beside the fissure rises faster than it
     * did with the ward whole, and standing at the crack raises it on its own.
     */
    private static void checkSeep(ClientGameTestContext context, TestServerContext server, Scene scene, float wardedBefore)
    {
        widenWith(server, scene, VoidHeartBlocks.SMALL_AZURIUM_BUNDLE.asItem());
        server.runCommand("voidheart energy fissure " + at(scene.fissure()) + " seep " + SEEP_RADIUS);
        context.waitTicks(25);

        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var fissure = fissure(minecraft, scene);
            expect(fissure.width() == 3, "A second small bundle should tear the fissure to width 3, it is " + fissure.width());
            expect(Math.abs(fissure.seepRadius(level.getGameTime()) - SEEP_RADIUS) < 0.2F, "The seep should reach " + SEEP_RADIUS + " blocks");

            float atWard = VoidSeep.strengthAt(level, scene.ward());
            float expected = 1 - (float) Math.sqrt(scene.ward().distSqr(scene.fissure())) / SEEP_RADIUS;
            expect(Math.abs(atWard - expected) < 0.05F, "The seep at the ward should be about " + expected + ", is " + atWard);
            expect(VoidSeep.strengthAt(level, scene.fissure().offset(40, 0, 0)) == 0, "Outside the seep there should be none");

            var ward = ward(minecraft, scene);
            expect(Math.abs(ward.seep() - atWard) < 0.02F, "The ward should read the seep, reads " + ward.seep());
            expect(Math.abs(ward.health() - WardStrength.health(ward.taint(), atWard)) < 0.02F && ward.health() < 0.6F,
                    "The seep should weaken the ward to about " + WardStrength.health(ward.taint(), atWard) + ", health " + ward.health());
        });

        float seeped = raiseAt(server, beside(scene));
        expect(seeped > wardedBefore * 1.5F, "Inside the seep corruption should rise much faster beside the ward: " + seeped + " against " + wardedBefore + " before");
        float expected = server.computeOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var spot = beside(scene);
            float seep = VoidSeep.strengthAt(level, spot);
            return RISES * RISE * WardStrength.multiplier(ward(minecraft, scene).strength()) * SeepMath.corruptionMultiplier(seep);
        });
        expect(Math.abs(seeped - expected) <= 1.5F, "Corruption should rise about " + expected + " beside the fissure, rose " + seeped);

        // Three minutes at the crack, skipped rather than waited for
        var crack = scene.fissure();
        tp(server, crack.getX() + 0.5 + scene.out().x, FLAT_FEET, crack.getZ() + 0.5 + scene.out().z);
        int risen = server.computeOnServer(minecraft ->
        {
            var player = player(minecraft);
            Corruption.set(player, 0);
            VoidFissures.gatherRise(player, 180);
            int value = Corruption.get(player);
            Corruption.set(player, 0);
            return value;
        });
        expect(risen >= 1 && risen <= 4, "Three minutes at the crack should raise corruption by a point or a few, rose " + risen);
    }

    private static void checkCreatures(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        tp(server, scene.ward().getX() + 0.5, FLAT_FEET, scene.ward().getZ() + 0.5);
        server.runCommand("gamerule spawn_mobs true");
        int cap = server.computeOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var fissure = fissure(minecraft, scene);
            long now = level.getGameTime();
            Mob first = null;
            for (int i = 0; i < 20 && first == null; i++)
                first = SeepCreatures.tryBringThrough(level, fissure, now);
            expect(first != null, "A weak void creature should come through the seep");
            expect(SeepCreatures.types().contains(first.getType()), "Only skeletons, ghouls and small crabs come through, got " + first.getType());
            double distance = Math.sqrt(first.distanceToSqr(Vec3.atCenterOf(scene.fissure())));
            expect(distance >= 2.5 && distance <= SEEP_RADIUS + 1, "It should come through inside the seep, " + distance + " blocks from the crack");
            for (int i = 0; i < 80; i++)
                SeepCreatures.tryBringThrough(level, fissure, now);
            return SeepCreatures.alive(level, fissure, fissure.seepRadius(now));
        });
        expect(cap == SeepMath.maxAlive(3), "A width 3 seep should hold at most " + SeepMath.maxAlive(3) + " creatures, holds " + cap);
        var f = scene.fissure();
        aimFrom(context, server, f.getX() + 0.5 - scene.out().x * 12, FLAT_FEET + 9, f.getZ() + 0.5 - scene.out().z * 12, f.getX() + 0.5, FLAT_FEET, f.getZ() + 0.5, 10);
        context.takeScreenshot("fissure-creatures");
        server.runCommand("gamerule spawn_mobs false");
        server.runCommand("kill @e[type=!player]");
    }

    /**
     * A wound opened in the air drops onto the ground, gives nothing, and seeps once its seep has spread.
     */
    private static void checkWound(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        server.runOnServer(minecraft -> VoidWounds.open(minecraft.overworld(), scene.wound().above(3), 4));
        server.runCommand("voidheart energy fissure " + at(scene.wound()) + " seep 10");
        context.waitTicks(40);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            expect(level.getBlockState(scene.wound()).is(VoidFissureContent.VOID_WOUND), "The wound should lie on the ground, found " + level.getBlockState(scene.wound()));
            if (!(level.getBlockEntity(scene.wound()) instanceof VoidFissureTile wound))
                throw new AssertionError("No wound tile");
            expect(wound.isWound() && wound.lastOutput() == 0 && wound.averageOutput() == 0, "A wound should give no energy");
            expect(wound.cost() == 8000, "Closing a size 4 wound should cost 8000, costs " + wound.cost());
            expect(Math.abs(wound.seepRadius(level.getGameTime()) - 10) < 0.2F, "The wound's seep should be 10 blocks");
            float near = VoidSeep.strengthAt(level, scene.wound().relative(Direction.UP));
            expect(near > 0.8F, "The wound should seep like a fissure, " + near + " beside it");
        });
    }

    private static void checkTaint(TestServerContext server, long upstream, float before)
    {
        server.runOnServer(minecraft ->
        {
            float now = Ley.taint(minecraft.overworld(), upstream);
            expect(now > before + 0.05F, "The node of the line close by should gain taint from the fissure: " + before + " then " + now);
        });
    }

    private static void screenshots(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        context.getInput().pressKey(InputConstants.KEY_F1);
        // The machine draws throughout, so the fissure's arc shows
        startDrawing(server, scene.machine());
        var f = scene.fissure();
        double fx = f.getX() + 0.5, fz = f.getZ() + 0.5;

        var near = scene.out().scale(-3).add(scene.along().scale(-1.5));
        aimFrom(context, server, fx + near.x, FLAT_FEET + 1.5, fz + near.z, fx, FLAT_FEET, fz, 30);
        context.takeScreenshot("fissure-width3-close-noon");

        var high = scene.out().scale(-14).add(scene.along().scale(-6));
        aimFrom(context, server, fx + high.x, FLAT_FEET + 12, fz + high.z, fx, FLAT_FEET, fz, 40);
        context.takeScreenshot("fissure-width3-seep-noon");

        server.runCommand("time set midnight");
        context.waitTicks(30);
        context.takeScreenshot("fissure-width3-seep-night");

        server.runCommand("tick freeze");
        for (int step = 0; step < 5; step++)
        {
            aimFrom(context, server, fx + near.x + step * 0.06, FLAT_FEET + 1.2, fz + near.z + step * 0.09, fx, FLAT_FEET, fz, 2);
            context.takeScreenshot("fissure-crack-sweep-" + step);
        }
        server.runCommand("tick unfreeze");

        server.runCommand("voidheart energy fissure " + at(f) + " width 5");
        server.runCommand("voidheart energy fissure " + at(f) + " seep 32");
        context.waitTicks(40);
        var wide = scene.out().scale(-22).add(scene.along().scale(-10));
        aimFrom(context, server, fx + wide.x, FLAT_FEET + 18, fz + wide.z, fx, FLAT_FEET, fz, 40);
        context.takeScreenshot("fissure-width5-seep-night");
        aimFrom(context, server, fx + near.x * 1.5, FLAT_FEET + 2, fz + near.z * 1.5, fx, FLAT_FEET, fz, 10);
        context.takeScreenshot("fissure-width5-close-night");

        server.runOnServer(minecraft -> player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidFissureContent.FISSURE_SEED)));
        aimFrom(context, server, fx + high.x * 1.6, FLAT_FEET + 26, fz + high.z * 1.6, fx, FLAT_FEET, fz, 20);
        context.takeScreenshot("fissure-seep-ring-holding-seed");
        server.runOnServer(minecraft -> player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));

        server.runCommand("voidheart energy fissure " + at(f) + " width 3");
        server.runCommand("voidheart energy fissure " + at(f) + " seep " + SEEP_RADIUS);

        var w = scene.wound();
        var woundView = scene.out().scale(-4).add(scene.along().scale(2));
        aimFrom(context, server, w.getX() + 0.5 + woundView.x, FLAT_FEET + 2.5, w.getZ() + 0.5 + woundView.z, w.getX() + 0.5, FLAT_FEET, w.getZ() + 0.5, 20);
        context.takeScreenshot("void-wound-night");

        stopDrawing(server);
        server.runCommand("time set noon");
        context.waitTicks(5);
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    private static void checkReload(TestServerContext server, Scene scene)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var fissure = fissure(minecraft, scene);
            expect(fissure.width() == 3, "The fissure's width should survive a reload, it is " + fissure.width());
            expect(fissure.paid() == PREPAID, "Contravoid paid should survive a reload, " + fissure.paid() + " paid");
            expect(Math.abs(fissure.seepRadius(level.getGameTime()) - SEEP_RADIUS) < 0.5F, "The seep should survive a reload");
            expect(VoidSeep.strengthAt(level, scene.ward()) > 0.3F, "The seep should act again once its fissure is loaded");
            expect(level.getBlockEntity(scene.wound()) instanceof VoidFissureTile wound && wound.isWound(), "The wound should survive a reload");
        });
    }

    /**
     * A collector of Contravoid feeds the fissure through a relay until its cost is met. It draws shut, its seep thins, and it is gone.
     */
    private static void closeFissure(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            level.setBlockAndUpdate(scene.collector(), VoidHeartBlocks.RAVENOUS_COLLECTOR.defaultBlockState());
            if (!(level.getBlockEntity(scene.collector()) instanceof RavenousCollectorTile collector))
                throw new AssertionError("No collector");
            collector.storage().attune(EssenceRegistry.CONTRAVOID);
            collector.storage().fill(EssenceRegistry.CONTRAVOID, 6000 - PREPAID + 500);
            level.setBlockAndUpdate(scene.relay(), VoidHeartBlocks.RAVENOUS_RELAY.defaultBlockState());
        });
        context.waitTicks(30);
        server.runOnServer(minecraft -> expect(minecraft.overworld().getBlockEntity(scene.relay()) instanceof RavenousRelayTile relay
                && relay.linkedReceivers().contains(scene.fissure()), "The relay should link to the fissure"));

        waitFor(context, server, 900, "The relay did not pay the fissure's cost", minecraft -> fissure(minecraft, scene).isClosing());
        context.getInput().pressKey(InputConstants.KEY_F1);
        var f = scene.fissure();
        var near = scene.out().scale(-3).add(scene.along().scale(-1.5));
        aimFrom(context, server, f.getX() + 0.5 + near.x, FLAT_FEET + 1.8, f.getZ() + 0.5 + near.z, f.getX() + 0.5, FLAT_FEET, f.getZ() + 0.5, 18);
        context.takeScreenshot("fissure-closing");
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            float atWard = VoidSeep.strengthAt(level, scene.ward());
            expect(atWard < 0.4F, "The seep should thin out while the fissure closes, " + atWard + " at the ward");
            int left = level.getBlockEntity(scene.collector()) instanceof RavenousCollectorTile collector ? collector.storage().amount() : -1;
            expect(left >= 400 && left <= 600, "The fissure should take only what its cost needs, the collector kept " + left);
        });

        waitFor(context, server, 80, "The fissure did not close", minecraft -> minecraft.overworld().getBlockState(scene.fissure()).isAir());
        server.runOnServer(minecraft -> expect(VoidSeep.strengthAt(minecraft.overworld(), scene.ward()) == 0, "Once closed, the fissure should seep no more"));
        context.waitTicks(10);
        context.takeScreenshot("fissure-scar");
        context.getInput().pressKey(InputConstants.KEY_F1);
        expectClient(context, "The closed fissure should leave a scar on the client",
                minecraft -> FissureScars.scars().stream().anyMatch(scar -> scar.pos().equals(scene.fissure())));
        float machine = measure(context, server, scene.machine());
        stopDrawing(server);
        expect(machine == 0, "The closed fissure should power nothing, the machine got " + machine);
    }

    private static void closeWound(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        server.runCommand("voidheart energy fissure " + at(scene.wound()) + " pay 7999");
        context.waitTicks(5);
        server.runOnServer(minecraft -> expect(!fissure(minecraft, scene.wound()).isClosing(), "A wound should stay open one short of its cost"));
        server.runCommand("voidheart energy fissure " + at(scene.wound()) + " pay 5");
        waitFor(context, server, 80, "The wound did not close", minecraft -> minecraft.overworld().getBlockState(scene.wound()).isAir());
        server.runOnServer(minecraft -> expect(VoidSeep.strengthAt(minecraft.overworld(), scene.wound()) == 0, "A closed wound should seep no more"));
        context.waitTicks(5);
        expectClient(context, "The closed wound should leave a scar on the client",
                minecraft -> FissureScars.scars().stream().anyMatch(scar -> scar.pos().equals(scene.wound()) && scar.wound()));
    }

    private static int raiseAt(TestServerContext server, BlockPos pos)
    {
        tp(server, pos.getX() + 0.5, FLAT_FEET, pos.getZ() + 0.5);
        return server.computeOnServer(minecraft ->
        {
            var player = player(minecraft);
            Corruption.set(player, 0);
            for (int i = 0; i < RISES; i++)
                Corruption.raise(player, RISE);
            int risen = Corruption.get(player);
            Corruption.set(player, 0);
            return risen;
        });
    }

    private static VoidFissureTile fissure(MinecraftServer minecraft, Scene scene)
    {
        return fissure(minecraft, scene.fissure());
    }

    private static VoidFissureTile fissure(MinecraftServer minecraft, BlockPos pos)
    {
        if (minecraft.overworld().getBlockEntity(pos) instanceof VoidFissureTile fissure)
            return fissure;
        throw new AssertionError("Expected a fissure or wound at " + pos.toShortString() + ", found " + minecraft.overworld().getBlockState(pos));
    }

    private static LeyWardTile ward(MinecraftServer minecraft, Scene scene)
    {
        if (minecraft.overworld().getBlockEntity(scene.ward()) instanceof LeyWardTile ward)
            return ward;
        throw new AssertionError("Expected a ward at " + scene.ward());
    }

    /**
     * Draws for the machine after everything else in the overworld ticked, as a machine's block entity would.
     */
    private static void hook()
    {
        if (hooked)
            return;
        hooked = true;
        ServerTickEvents.END_LEVEL_TICK.register(level ->
        {
            if (level.dimension() != Level.OVERWORLD)
                return;
            ASKS.forEach((pos, amount) -> DRAWN.addAndGet(VoidEnergy.draw(level, pos, amount)));
        });
    }

    private static String at(BlockPos pos)
    {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static void setblock(TestServerContext server, BlockPos pos, String block)
    {
        server.runCommand("setblock " + at(pos) + " " + block);
    }
}
