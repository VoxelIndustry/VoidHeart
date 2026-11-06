package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.mob.CrawlerMonster;
import net.voxelindustry.voidheart.common.content.mob.KernacianDragoon;
import net.voxelindustry.voidheart.common.content.mob.Legionary;
import net.voxelindustry.voidheart.common.content.mob.VoidMonster;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusBridges;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusRoster;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusTheme;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;

import java.util.TreeMap;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Mobs of real Daedalus districts: what each theme holds once its chunks generate, the Kernacian Dragoon standing guard and going back to its post, and mobs held at the
 * district's edge while a player stands on a bridge.
 */
public class DaedalusSpawningClientTest implements FabricClientGameTest
{
    private static final int VIEW_CHUNKS = 10;
    private static final int CHUNK_TIMEOUT_TICKS = 1200;
    private static final int ARRIVAL_TICKS = 40;
    private static final int LANE_CENTRE = 3;
    private static final int MIN_MOBS = 70;
    private static final int GUARD_X = 0;
    private static final int GROUND = -61;
    private static final int FEET = GROUND + 1;
    private static final int UNSEEN_TICKS = 80;
    private static final int SPOT_TICKS = 100;
    private static final int RETURN_TICKS = 600;
    private static final int FENCE_TICKS = 200;
    private static final double AT_POST = 1.2;
    private static final int WAKE_TICKS = 600;
    private static final double AWAY = 5;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        int[] view = new int[1];
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("difficulty normal");
            server.runCommand("gamemode spectator @a");
            context.waitTicks(20);
            context.runOnClient(minecraft ->
            {
                view[0] = minecraft.options.renderDistance().get();
                minecraft.options.renderDistance().set(VIEW_CHUNKS);
                minecraft.options.broadcastOptions();
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            });
            context.getInput().pressKey(InputConstants.KEY_F1);

            // Players with invulnerable abilities are never targeted, so effects keep this one alive instead
            server.runCommand("effect give @a resistance infinite 4 true");
            server.runCommand("effect give @a regeneration infinite 4 true");
            server.runCommand("effect give @a saturation infinite 0 true");
            checkGuard(context, server);
            for (var theme : DaedalusTheme.values())
                checkDistrict(context, world, server, theme);
            checkFence(context, world, server);

            context.getInput().pressKey(InputConstants.KEY_F1);
            server.runCommand("execute as @a run voidheart daedalus leave");
            context.waitTicks(10);
            context.runOnClient(minecraft ->
            {
                minecraft.options.renderDistance().set(view[0]);
                minecraft.options.broadcastOptions();
            });
        }
    }

    private static AABB bounds(DistrictPos pos)
    {
        return new AABB(pos.originX(), 0, pos.originZ(), pos.originX() + DistrictPos.SIZE, DistrictPos.HEIGHT, pos.originZ() + DistrictPos.SIZE);
    }

    // ---------------------------------------------------------------------------------------------
    // Districts
    // ---------------------------------------------------------------------------------------------

    private record Census(int mobs, String summary, BlockPos dragoon, float dragoonYaw)
    {
    }

    /**
     * Generates every chunk of the district, then checks what came to life in it.
     */
    private static Census census(MinecraftServer minecraft, DistrictPos pos)
    {
        var level = VoidDaedalus.level(minecraft);
        int firstX = pos.originX() >> 4;
        int firstZ = pos.originZ() >> 4;
        int chunks = DistrictPos.SIZE / 16;
        int nests = 0;
        for (int cz = firstZ; cz < firstZ + chunks; cz++)
        {
            for (int cx = firstX; cx < firstX + chunks; cx++)
            {
                var chunk = level.getChunk(cx, cz, ChunkStatus.FULL, true);
                for (var at : chunk.getBlockEntitiesPos())
                {
                    if (level.getBlockState(at).is(VoidHeartBlocks.BARNACLE_NEST))
                        nests++;
                }
            }
        }

        var theme = DaedalusTheme.at(pos);
        var roster = DaedalusRoster.of(theme);
        var counts = new TreeMap<String, Integer>();
        KernacianDragoon dragoon = null;
        int dragoons = 0;
        var mobs = level.getEntitiesOfClass(Mob.class, bounds(pos), mob -> mob instanceof VoidMonster);
        for (var mob : mobs)
        {
            var name = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();
            counts.merge(name, 1, Integer::sum);
            expect(level.noCollision(mob), name + " spawned inside a block at " + mob.position() + " in " + theme + ": " + level.getBlockState(mob.blockPosition()) + " / "
                                          + level.getBlockState(mob.blockPosition().above()) + " / " + level.getBlockState(mob.blockPosition().above(2)) + ", below " + level.getBlockState(mob.blockPosition().below()));
            expect(!level.getBlockState(mob.blockPosition().below()).isAir() || mob instanceof CrawlerMonster || mob.getType() == VoidHeartEntities.EYE_MAW || mob.getType() == VoidHeartEntities.ARCANE_EYE,
                    name + " spawned over nothing at " + mob.blockPosition() + " in " + theme);
            expect(mob.isPersistenceRequired(), name + " would despawn");
            if (mob instanceof KernacianDragoon found)
            {
                dragoon = found;
                dragoons++;
            }
            if (mob instanceof Legionary legionary)
            {
                expect(legionary.partner() != null && level.getEntity(legionary.partner()) instanceof Legionary, "A Legionary without its partner at " + mob.blockPosition() + " in " + theme);
            }
        }
        if (dragoons != 1)
        {
            var planned = net.voxelindustry.voidheart.common.world.daedalus.DistrictPlans.get(level.getSeed(), pos).mobs().placements().stream()
                    .filter(placement -> placement.mob() == net.voxelindustry.voidheart.common.world.daedalus.DaedalusMob.KERNACIAN_DRAGOON).toList();
            var everywhere = new java.util.ArrayList<String>();
            for (var entity : level.getAllEntities())
            {
                if (entity instanceof KernacianDragoon)
                    everywhere.add(entity.position().toString());
            }
            throw new AssertionError(dragoons + " dragoons in the " + theme + " district " + pos + " of " + mobs.size() + " mobs " + counts + ", planned " + planned + ", dragoons in the level " + everywhere);
        }
        expect(dragoon.post() != null && dragoon.post().equals(dragoon.blockPosition()), "The " + theme + " dragoon should stand at its post, it is at " + dragoon.blockPosition() + " for " + dragoon.post());
        expect(mobs.size() >= MIN_MOBS, "Only " + mobs.size() + " mobs in the " + theme + " district " + pos);
        expect(roster.hasNests() == (nests > 0), nests + " nests in " + theme);

        String summary = theme + " " + pos + ": " + mobs.size() + " mobs, " + nests + " nests " + counts;
        return new Census(mobs.size(), summary, dragoon.blockPosition(), dragoon.getYRot());
    }

    private static boolean entitiesLoaded(MinecraftServer minecraft, DistrictPos pos)
    {
        var level = VoidDaedalus.level(minecraft);
        for (int cz = pos.originZ() >> 4; cz < (pos.originZ() + DistrictPos.SIZE) >> 4; cz++)
        {
            for (int cx = pos.originX() >> 4; cx < (pos.originX() + DistrictPos.SIZE) >> 4; cx++)
            {
                if (!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(cx, cz)))
                    return false;
            }
        }
        return true;
    }

    private static void checkDistrict(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, DaedalusTheme theme)
    {
        var pos = closestDistrict(theme);
        // Chunks generated earlier around another district load their entities apart from their blocks, so the district is held loaded until they are all in
        String area = pos.originX() + " " + pos.originZ() + " " + (pos.originX() + DistrictPos.SIZE - 1) + " " + (pos.originZ() + DistrictPos.SIZE - 1);
        server.runCommand("tick freeze");
        server.runCommand("execute in voidheart:daedalus run forceload add " + area);
        boolean loaded = false;
        for (int waited = 0; waited < CHUNK_TIMEOUT_TICKS && !loaded; waited += 10)
        {
            context.waitTicks(10);
            loaded = server.computeOnServer(minecraft -> entitiesLoaded(minecraft, pos));
        }
        expect(loaded, "The entities of the " + theme + " district never loaded");
        var census = server.computeOnServer(minecraft -> census(minecraft, pos));
        server.runCommand("execute in voidheart:daedalus run forceload remove " + area);
        server.runCommand("tick unfreeze");
        System.out.println("Daedalus spawning: " + census.summary());

        var name = "daedalus-spawning-" + theme.getSerializedName();
        var dragoon = census.dragoon();
        double yaw = Math.toRadians(census.dragoonYaw());
        // In front of it, looking back at it, as whoever comes in by the door it watches
        var front = new Vec3(dragoon.getX() + 0.5 - Math.sin(yaw) * 3.5, dragoon.getY() + 1.6, dragoon.getZ() + 0.5 + Math.cos(yaw) * 3.5);
        shoot(context, world, server, name + "-dragoon", front.x, front.y - 1.6, front.z, census.dragoonYaw() + 180, 15);

        var group = server.computeOnServer(minecraft -> viewOfAMob(minecraft, pos));
        if (group != null)
            shoot(context, world, server, name + "-" + group.name(), group.x(), group.y(), group.z(), group.yaw(), group.pitch());
    }

    private record View(String name, double x, double y, double z, float yaw, float pitch)
    {
    }

    /**
     * Somewhere a few blocks from one of the district's own mobs with a clear line to it, favouring the Daedalus' mobs and groups.
     */
    private static View viewOfAMob(MinecraftServer minecraft, DistrictPos pos)
    {
        var level = VoidDaedalus.level(minecraft);
        var mobs = level.getEntitiesOfClass(Mob.class, bounds(pos), mob -> mob instanceof VoidMonster && !(mob instanceof KernacianDragoon));
        mobs.sort(java.util.Comparator.comparingInt((Mob mob) -> -level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(4), other -> other instanceof VoidMonster).size()));
        for (var mob : mobs)
        {
            var eye = mob.getEyePosition();
            for (int side = 0; side < Compass.COUNT; side++)
            {
                for (double distance = 4; distance >= 2.5; distance -= 0.5)
                {
                    var camera = new Vec3(mob.getX() + Compass.dx(side) * distance, mob.getY() + 1.6, mob.getZ() + Compass.dz(side) * distance);
                    var feet = BlockPos.containing(camera.x, mob.getY(), camera.z);
                    if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir())
                        continue;
                    var hit = level.clip(new ClipContext(camera, eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
                    if (hit.getType() != HitResult.Type.MISS)
                        continue;
                    var look = eye.subtract(camera);
                    float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
                    float pitch = (float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance()));
                    return new View(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath(), camera.x, mob.getY(), camera.z, yaw, pitch);
                }
            }
        }
        return null;
    }

    private static void shoot(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, String name, double x, double y, double z, double yaw, double pitch)
    {
        server.runCommand("execute in voidheart:daedalus run tp @a " + x + " " + y + " " + z + " " + yaw + " " + pitch);
        context.waitTicks(ARRIVAL_TICKS);
        world.getConnection().waitForChunksRender(false, CHUNK_TIMEOUT_TICKS);
        context.waitTicks(5);
        context.takeScreenshot(name);
    }

    // ---------------------------------------------------------------------------------------------
    // The dragoon on guard
    // ---------------------------------------------------------------------------------------------

    private static KernacianDragoon guard(MinecraftServer minecraft)
    {
        var found = minecraft.overworld().getEntitiesOfClass(KernacianDragoon.class, new AABB(GUARD_X - 40, GROUND - 5, -40, GUARD_X + 40, GROUND + 20, 40));
        expect(found.size() == 1, found.size() + " guard dragoons");
        return found.getFirst();
    }

    /**
     * On flat ground: it stays put while a player stands behind it, wakes when one walks in front of it, and goes back to its post facing as it did once they are gone.
     */
    private static void checkGuard(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var dragoon = VoidHeartEntities.KERNACIAN_DRAGOON.create(level, EntitySpawnReason.COMMAND);
            var post = new BlockPos(GUARD_X, FEET, 0);
            // Facing south, the way 0 yaw looks
            dragoon.snapTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 0, 0);
            dragoon.setYHeadRot(0);
            dragoon.setYBodyRot(0);
            dragoon.guard(post, 0);
            dragoon.setPersistenceRequired();
            level.addFreshEntity(dragoon);

            var player = player(minecraft);
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.teleportTo(level, GUARD_X + 0.5, FEET, -10.5, java.util.Set.of(), 0, 0, true);
        });
        context.waitTicks(UNSEEN_TICKS);
        server.runOnServer(minecraft ->
        {
            var dragoon = guard(minecraft);
            expect(dragoon.getTarget() == null, "A player behind the dragoon was spotted");
            expect(dragoon.isStandingGuard(), "The dragoon should still stand guard, it is at " + dragoon.position());
            expect(Math.abs(net.minecraft.util.Mth.wrapDegrees(dragoon.getYHeadRot())) < 1, "The dragoon turned its head to " + dragoon.getYHeadRot() + " on guard");
        });
        context.takeScreenshot("daedalus-spawning-guard-behind");

        server.runCommand("tp @a " + (GUARD_X + 0.5) + " " + FEET + " 10.5 180 0");
        boolean spotted = false;
        for (int waited = 0; waited < SPOT_TICKS && !spotted; waited += 5)
        {
            context.waitTicks(5);
            spotted = server.computeOnServer(minecraft -> guard(minecraft).getTarget() == player(minecraft));
        }
        expect(spotted, "The dragoon never spotted a player in front of it");
        // Backing off keeps it coming, so that it ends up well away from its post
        double moved = 0;
        for (int waited = 0; waited < WAKE_TICKS && moved < AWAY; waited += 10)
        {
            context.waitTicks(10);
            server.runOnServer(minecraft ->
            {
                var dragoon = guard(minecraft);
                var player = player(minecraft);
                if (player.distanceTo(dragoon) < 8)
                    player.teleportTo(minecraft.overworld(), player.getX(), FEET, dragoon.getZ() + 10, java.util.Set.of(), 180, 0, true);
            });
            moved = server.computeOnServer(minecraft -> guard(minecraft).position().distanceTo(Vec3.atBottomCenterOf(guard(minecraft).post())));
        }
        System.out.printf("Daedalus spawning: the woken dragoon came %.1f blocks from its post%n", moved);
        expect(moved >= AWAY, "The woken dragoon only came " + moved + " blocks from its post");
        context.takeScreenshot("daedalus-spawning-guard-woken");

        server.runCommand("gamemode spectator @a");
        server.runCommand("tp @a " + (GUARD_X + 12.5) + " " + (FEET + 2) + " 4.5 120 10");
        boolean back = false;
        for (int waited = 0; waited < RETURN_TICKS && !back; waited += 10)
        {
            context.waitTicks(10);
            back = server.computeOnServer(minecraft ->
            {
                var dragoon = guard(minecraft);
                return dragoon.getTarget() == null && dragoon.position().distanceTo(Vec3.atBottomCenterOf(dragoon.post())) <= AT_POST
                       && Math.abs(net.minecraft.util.Mth.wrapDegrees(dragoon.getYRot())) < 1;
            });
        }
        expect(back, "The dragoon never went back to its post facing south");
        context.waitTicks(20);
        context.takeScreenshot("daedalus-spawning-guard-back");
        server.runOnServer(minecraft ->
        {
            guard(minecraft).discard();
        });
    }

    // ---------------------------------------------------------------------------------------------
    // The fence at the bridges
    // ---------------------------------------------------------------------------------------------

    /**
     * A skeleton in the doorway of a landing goes after a player standing a few blocks out on the bridge, and is held at the edge.
     */
    private static void checkFence(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server)
    {
        var pos = closestDistrict(DaedalusTheme.ARCHIVE);
        long seed = server.computeOnServer(minecraft -> VoidDaedalus.level(minecraft).getSeed());
        var bridge = DaedalusBridges.between(seed, pos, Compass.EAST).getFirst();
        int edge = pos.originX() + DistrictPos.SIZE;
        double laneZ = bridge.worldZ(0, LANE_CENTRE) + 0.5;
        int floor = DistrictPos.floorY(bridge.fromTier()) + 1;

        server.runCommand("gamemode survival @a");
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            VoidDaedalus.arriveOn(player, bridge);
            player.teleportTo(VoidDaedalus.level(minecraft), bridge.worldX(4, LANE_CENTRE) + 0.5, bridge.walkY(4), laneZ, java.util.Set.of(), 90, 15, true);
        });
        context.waitTicks(ARRIVAL_TICKS);
        world.getConnection().waitForChunksRender(false, CHUNK_TIMEOUT_TICKS);

        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            var skeleton = VoidHeartEntities.VOID_SKELETON.create(level, EntitySpawnReason.COMMAND);
            skeleton.snapTo(edge - 4 + 0.5, floor, laneZ, -90, 0);
            expect(level.noCollision(skeleton), "No room for the skeleton in the landing at " + skeleton.blockPosition());
            skeleton.setPersistenceRequired();
            skeleton.addTag("fence");
            level.addFreshEntity(skeleton);
        });

        double furthest = Double.NEGATIVE_INFINITY;
        boolean chased = false;
        for (int waited = 0; waited < FENCE_TICKS; waited += 5)
        {
            context.waitTicks(5);
            var state = server.computeOnServer(minecraft ->
            {
                var skeleton = fenced(VoidDaedalus.level(minecraft));
                return new double[] { skeleton.getBoundingBox().maxX, skeleton.getTarget() == player(minecraft) ? 1 : 0 };
            });
            furthest = Math.max(furthest, state[0]);
            chased |= state[1] > 0;
        }
        System.out.printf("Daedalus spawning: the fenced skeleton reached x %.2f, the district ends at %d%n", furthest, edge);
        expect(chased, "The skeleton never went after the player on the bridge");
        expect(furthest <= edge + 1e-6, "The skeleton stepped onto the bridge, reaching " + furthest + " past " + edge);
        expect(furthest > edge - 1.5, "The skeleton stopped short of the edge at " + furthest);
        context.takeScreenshot("daedalus-spawning-fence");

        server.runOnServer(minecraft ->
        {
            fenced(VoidDaedalus.level(minecraft)).discard();
        });
        server.runCommand("effect clear @a");
        server.runCommand("gamemode spectator @a");
    }

    private static Mob fenced(ServerLevel level)
    {
        for (var entity : level.getAllEntities())
        {
            if (entity instanceof Mob mob && mob.entityTags().contains("fence"))
                return mob;
        }
        throw new AssertionError("The fenced skeleton is gone");
    }
}
