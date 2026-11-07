package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.voxelindustry.voidheart.common.content.mob.CorruptedBarnacle;
import net.voxelindustry.voidheart.common.content.mob.VoidGhoul;
import net.voxelindustry.voidheart.common.content.salt.SaltPatch;
import net.voxelindustry.voidheart.common.content.salt.SaltWard;
import net.voxelindustry.voidheart.common.content.salt.SaltWardBlock;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.lang.reflect.Field;
import java.util.function.Predicate;

/**
 * Salt is laid from the hand as a 3x3, a second use grows it by a ring, and each salt covers nine blocks. Wraiths stay off it, the Fallen burn on it and wear
 * it away, ghouls balk at its edge, barnacles won't root on it, and water washes it off. A wraith won't come out of a wall beside salt, a crab drags through it
 * and can't start up a wall from it.
 */
public class SaltClientTest implements FabricClientGameTest
{
    private static final int FEET = -60;
    private static final int PATCH_X = 0;
    private static final int PATCH_Z = 6;
    private static final int WATCH_TICKS = 200;
    private static final int ROOM_X = 40;
    private static final int LANE_X = 70;
    private static final int LANE_Z = 0;
    private static final int SALT_LANE = 6;
    private static final int LANE_LENGTH = 12;
    private static final int LANE_TICKS = 40;
    private static final int PILLAR_X = 100;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamemode survival @a");
            server.runCommand("clear @a");
            context.waitTicks(10);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            lay(context, server);
            wraith(context, server);
            ghoul(context, server);
            fallen(context, server);
            barnacle(context, server);
            water(context, server);
            wraithThroughWall(context, server);
            crabDrag(context, server);
            crabClimb(context, server);
        }
    }

    private static void lay(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft -> player(minecraft).getInventory().setItem(0, new ItemStack(VoidHeartItems.SALT, 3)));
        aimAt(context, server, PATCH_X + 0.5, FEET, PATCH_Z - 3.5, PATCH_X + 0.5, FEET, PATCH_Z + 0.5);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            expect(count(minecraft) == 9, "One salt should lay a 3x3, found " + count(minecraft));
            expect(player(minecraft).getInventory().getItem(0).getCount() == 2, "Laying a 3x3 should take one salt");
            var middle = state(minecraft, PATCH_X, PATCH_Z);
            expect(!middle.getValue(SaltWardBlock.NORTH) && !middle.getValue(SaltWardBlock.EAST), "The middle of a patch should show no edge");
            var corner = state(minecraft, PATCH_X - 1, PATCH_Z - 1);
            expect(corner.getValue(SaltWardBlock.NORTH) && corner.getValue(SaltWardBlock.WEST) && !corner.getValue(SaltWardBlock.SOUTH),
                    "A corner should show its two outer edges");
        });

        // On the salt, it grows the whole patch by a ring of 16, two salt's worth
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            expect(count(minecraft) == 25, "A second use should grow the patch to 5x5, found " + count(minecraft));
            expect(player(minecraft).getInventory().getItem(0).isEmpty(), "A ring of 16 should take the last two salt");
        });

        server.runCommand("tp @a " + (PATCH_X + 0.5) + " " + (FEET + 6) + " " + (PATCH_Z - 5.5) + " 0 50");
        context.waitTicks(10);
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.takeScreenshot("salt-patch");
        server.runCommand("tp @a " + (PATCH_X + 3.5) + " " + (FEET + 1.2) + " " + (PATCH_Z - 3.5) + " 30 25");
        context.waitTicks(5);
        context.takeScreenshot("salt-patch-low");
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    /**
     * A player in the middle of the 5x5 is two and a half blocks in, which a wraith should never get past.
     */
    private static void wraith(ClientGameTestContext context, TestServerContext server)
    {
        standInPatch(server);
        server.runOnServer(minecraft -> spawn(minecraft, VoidHeartEntities.WRAITH, PATCH_X + 0.5, PATCH_Z + 9.5));
        double closest = watch(context, server, VoidHeartEntities.WRAITH, mob -> expect(!SaltWard.isOn(mob), "A wraith should never stand on salt"));
        System.out.println("[Salt] the wraith came within " + closest + " of the player");
        expect(closest > 2, "The wraith should stay off the patch, came within " + closest);
        frame(context, server, "salt-wraith");
        server.runCommand("kill @e[type=voidheart:wraith]");
    }

    /**
     * Sent walking into the middle of the patch with no one to hunt, so that it walks up to the edge rather than lunging over it, and has no way around.
     */
    private static void ghoul(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("gamemode creative @a");
        server.runCommand("tp @a " + (PATCH_X + 6.5) + " " + FEET + " " + (PATCH_Z + 0.5));
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var ghoul = VoidHeartEntities.VOID_GHOUL.create(level, EntitySpawnReason.COMMAND);
            ghoul.snapTo(PATCH_X + 0.5, FEET, PATCH_Z + 6.5, 180, 0);
            // A stroll of its own would take it somewhere else, so it only walks where it is sent
            goals(ghoul, "goalSelector").removeAllGoals(goal -> true);
            level.addFreshEntity(ghoul);
        });
        boolean[] hesitated = { false };
        var trace = new StringBuilder();
        watch(context, server, VoidHeartEntities.VOID_GHOUL, mob ->
        {
            var ghoul = (VoidGhoul) mob;
            hesitated[0] |= ghoul.isHesitating();
            if (ghoul.tickCount % 10 == 0)
            {
                var path = ghoul.getNavigation().getPath();
                trace.append(String.format("%n  %d at %.2f %.2f %.2f ground %s on salt %s next %s", ghoul.tickCount, ghoul.getX(), ghoul.getY(), ghoul.getZ(), ghoul.onGround(),
                        SaltWard.isOn(ghoul), path == null ? "none" : path.isDone() ? "done" : path.getNextNodePos().toShortString()));
            }
            if (!ghoul.isHesitating() && ghoul.getNavigation().isDone())
                ghoul.getNavigation().moveTo(PATCH_X + 0.5, FEET, PATCH_Z + 0.5, 1);
        });
        expect(hesitated[0], "The ghoul should balk at the edge of the salt:" + trace);
        server.runCommand("kill @e[type=voidheart:void_ghoul]");
        server.runCommand("gamemode survival @a");
    }

    private static void fallen(ClientGameTestContext context, TestServerContext server)
    {
        standInPatch(server);
        int before = server.computeOnServer(SaltClientTest::count);
        server.runOnServer(minecraft -> spawn(minecraft, VoidHeartEntities.FALLEN_NOVICE, PATCH_X + 0.5, PATCH_Z + 7.5));
        waitFor(context, server, WATCH_TICKS * 2, "The novice should burn on the salt and wear some away", minecraft ->
        {
            var novice = first(minecraft, VoidHeartEntities.FALLEN_NOVICE);
            return count(minecraft) < before && (novice == null || novice.getHealth() < novice.getMaxHealth());
        });
        frame(context, server, "salt-fallen");
        server.runCommand("kill @e[type=voidheart:fallen_novice]");
    }

    private static void barnacle(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var pos = firstSalt(minecraft);
            var barnacle = VoidHeartEntities.CORRUPTED_BARNACLE.create(level, EntitySpawnReason.COMMAND);
            barnacle.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            barnacle.setStage(CorruptedBarnacle.GROWN - 1);
            setInt(barnacle, "growthTicks", CorruptedBarnacle.GROWN * CorruptedBarnacle.GROWTH_TICKS - 1);
            barnacle.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100000, 10));
            level.addFreshEntity(barnacle);
        });
        context.waitTicks(40);
        server.runOnServer(minecraft ->
        {
            var barnacle = first(minecraft, VoidHeartEntities.CORRUPTED_BARNACLE);
            expect(barnacle != null && SaltWard.isOn(barnacle), "The barnacle should still be on the salt");
            expect(!barnacle.isRooted(), "A barnacle should not root on salt");
            barnacle.discard();
        });
    }

    private static void water(ClientGameTestContext context, TestServerContext server)
    {
        var edge = server.computeOnServer(SaltClientTest::firstSalt);
        int before = server.computeOnServer(SaltClientTest::count);
        server.runCommand("setblock " + edge.getX() + " " + edge.getY() + " " + (edge.getZ() - 1) + " water");
        context.waitTicks(40);
        int after = server.computeOnServer(SaltClientTest::count);
        expect(after < before, "Water should wash salt away, still " + after + " of " + before);
        server.runCommand("fill " + (PATCH_X - 8) + " " + FEET + " " + (PATCH_Z - 8) + " " + (PATCH_X + 8) + " " + FEET + " " + (PATCH_Z + 8) + " air");
    }

    private static int crackleDelay(net.voxelindustry.voidheart.common.content.mob.Wraith wraith)
    {
        try
        {
            var field = net.voxelindustry.voidheart.common.content.mob.Wraith.class.getDeclaredField("saltCrackleDelay");
            field.setAccessible(true);
            return field.getInt(wraith);
        } catch (ReflectiveOperationException e)
        {
            throw new AssertionError(e);
        }
    }

    /**
     * A walled room the player waits in and a wraith outside it. Bare, the wraith comes through the wall. With salt along the inner foot of every wall there
     * is nowhere it can come out, so it never starts, wherever it walks around to.
     */
    private static void wraithThroughWall(ClientGameTestContext context, TestServerContext server)
    {
        int x = ROOM_X;
        int z = 0;
        server.runCommand("fill " + x + " " + (FEET - 1) + " " + z + " " + (x + 8) + " " + (FEET + 5) + " " + (z + 8) + " stone hollow");
        server.runCommand("fill " + (x + 8) + " " + FEET + " " + z + " " + (x + 10) + " " + (FEET + 5) + " " + (z + 8) + " stone");
        server.runCommand("setblock " + (x + 4) + " " + (FEET + 5) + " " + (z + 4) + " sea_lantern");
        server.runCommand("tp @a " + (x + 2.5) + " " + FEET + " " + (z + 4.5) + " -90 0");
        protect(server);

        // Bare, it comes through
        server.runOnServer(minecraft -> spawn(minecraft, VoidHeartEntities.WRAITH, x + 14.5, z + 4.5));
        waitFor(context, server, WATCH_TICKS, "Without salt the wraith should start through the wall", minecraft ->
                first(minecraft, VoidHeartEntities.WRAITH).isCrossing());
        server.runCommand("kill @e[type=voidheart:wraith]");
        server.runCommand("kill @e[type=voidheart:wraith_stain]");
        context.waitTicks(5);

        // With salt along the inner foot of every wall, nowhere it could come out is free
        server.runOnServer(minecraft ->
        {
            saltSquare(minecraft, x + 1, z + 1, x + 7, z + 1);
            saltSquare(minecraft, x + 1, z + 7, x + 7, z + 7);
            saltSquare(minecraft, x + 1, z + 1, x + 1, z + 7);
            saltSquare(minecraft, x + 7, z + 1, x + 7, z + 7);
            spawn(minecraft, VoidHeartEntities.WRAITH, x + 14.5, z + 4.5);
        });
        boolean crackled = false;
        for (int tick = 0; tick < WATCH_TICKS; tick++)
        {
            server.runOnServer(minecraft ->
            {
                var wraith = first(minecraft, VoidHeartEntities.WRAITH);
                expect(!wraith.isCrossing(), "A wraith should not cross a wall into salt");
                expect(minecraft.overworld().getEntities(VoidHeartEntities.WRAITH_STAIN, stain -> true).isEmpty(), "A wraith should not mark a wall it can't come out of");
                boolean inside = wraith.getX() > x && wraith.getX() < x + 8 && wraith.getZ() > z && wraith.getZ() < z + 8;
                expect(!inside, "The wraith should stay outside the room, at " + wraith.position());
            });
            // It never sets foot on salt out there, so only a refused route can have crackled
            if (!crackled && server.computeOnServer(minecraft -> crackleDelay(first(minecraft, VoidHeartEntities.WRAITH))) > 0)
            {
                crackled = true;
                context.getInput().pressKey(InputConstants.KEY_F1);
                context.takeScreenshot("salt-route-crackle");
                context.getInput().pressKey(InputConstants.KEY_F1);
            }
            context.waitTick();
        }
        expect(crackled, "The salt should crackle when it turns a wraith's route away");
        server.runCommand("tp @a " + (x + 3.5) + " " + (FEET + 2) + " " + (z + 4.5) + " 90 30");
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.waitTicks(3);
        context.takeScreenshot("salt-wall-ward");
        context.getInput().pressKey(InputConstants.KEY_F1);
        server.runOnServer(minecraft -> expect(minecraft.overworld().getEntities(VoidHeartEntities.WRAITH_STAIN, stain -> true).isEmpty(),
                "No wall should be marked by the end either"));
        server.runCommand("kill @e[type=voidheart:wraith]");
        server.runCommand("fill " + x + " " + (FEET - 1) + " " + z + " " + (x + 10) + " " + (FEET + 5) + " " + (z + 8) + " air");
        server.runCommand("fill " + x + " " + (FEET - 1) + " " + z + " " + (x + 10) + " " + (FEET - 1) + " " + (z + 8) + " grass_block");
    }

    /**
     * Steered straight along a lane with no path to follow, the same crab covers far less ground in the same time on salt than on grass.
     */
    private static void crabDrag(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("gamemode creative @a");
        server.runCommand("tp @a " + (LANE_X - 6) + " " + FEET + " " + (LANE_Z + 4) + " -90 20");
        double onGrass = laneRun(context, server, LANE_Z);
        server.runOnServer(minecraft -> saltSquare(minecraft, LANE_X - 1, LANE_Z + SALT_LANE - 1, LANE_X + LANE_LENGTH + 1, LANE_Z + SALT_LANE + 1));
        double onSalt = laneRun(context, server, LANE_Z + SALT_LANE);
        System.out.println("[Salt] a crab covered " + onGrass + " on grass and " + onSalt + " on salt in " + LANE_TICKS + " ticks");
        expect(onGrass > 1, "The crab should have walked on the grass, covered " + onGrass);
        expect(onSalt < onGrass * 0.75, "Salt should drag at the crab, covered " + onSalt + " against " + onGrass + " on grass");
        expect(onSalt > onGrass * 0.25, "Salt should slow the crab, not hold it, covered " + onSalt + " against " + onGrass + " on grass");
        server.runCommand("fill " + (LANE_X - 2) + " " + FEET + " " + (LANE_Z - 2) + " " + (LANE_X + LANE_LENGTH + 2) + " " + FEET + " " + (LANE_Z + SALT_LANE + 2) + " air");
        server.runCommand("gamemode survival @a");
    }

    /**
     * @return how far a crab steered along the lane got
     */
    private static double laneRun(ClientGameTestContext context, TestServerContext server, int laneZ)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var crab = VoidHeartEntities.CORRUPTED_CRAB_MEDIUM.create(level, EntitySpawnReason.COMMAND);
            crab.snapTo(LANE_X + 0.5, FEET, laneZ + 0.5, -90, 0);
            // Its own goals would steer it too, so only the test does
            goals(crab, "goalSelector").removeAllGoals(goal -> true);
            goals(crab, "targetSelector").removeAllGoals(goal -> true);
            level.addFreshEntity(crab);
        });
        context.waitTicks(10);
        double start = server.computeOnServer(minecraft -> first(minecraft, VoidHeartEntities.CORRUPTED_CRAB_MEDIUM).getX());
        for (int tick = 0; tick < LANE_TICKS; tick++)
        {
            server.runOnServer(minecraft -> first(minecraft, VoidHeartEntities.CORRUPTED_CRAB_MEDIUM).getMoveControl()
                    .setWantedPosition(LANE_X + LANE_LENGTH + 0.5, FEET, laneZ + 0.5, 1));
            context.waitTick();
        }
        double end = server.computeOnServer(minecraft -> first(minecraft, VoidHeartEntities.CORRUPTED_CRAB_MEDIUM).getX());
        server.runCommand("kill @e[type=voidheart:corrupted_crab_medium]");
        context.waitTicks(5);
        return end - start;
    }

    /**
     * A player up on a pillar it can't path to: on salted ground at its foot the crab walks up to it but never takes hold. Swept clear, it climbs.
     */
    private static void crabClimb(ClientGameTestContext context, TestServerContext server)
    {
        int x = PILLAR_X;
        int z = 0;
        server.runCommand("fill " + x + " " + FEET + " " + z + " " + (x + 2) + " " + (FEET + 5) + " " + (z + 2) + " stone");
        server.runCommand("tp @a " + (x + 1.5) + " " + (FEET + 6) + " " + (z + 1.5));
        protect(server);
        server.runOnServer(minecraft ->
        {
            saltSquare(minecraft, x - 4, z - 4, x + 6, z + 6);
            spawn(minecraft, VoidHeartEntities.CORRUPTED_CRAB_MEDIUM, x + 1.5, z + 5.5);
        });

        double closest = Double.MAX_VALUE;
        for (int tick = 0; tick < WATCH_TICKS * 2; tick++)
        {
            double distance = server.computeOnServer(minecraft ->
            {
                var crab = first(minecraft, VoidHeartEntities.CORRUPTED_CRAB_MEDIUM);
                expect(!crab.isClinging(), "A crab on salt should not take hold of the wall, at " + crab.position());
                return (double) Math.abs(crab.getZ() - (z + 3));
            });
            closest = Math.min(closest, distance);
            context.waitTick();
        }
        expect(closest < 0.8, "The crab should have walked up against the pillar, came within " + closest);

        server.runCommand("fill " + (x - 4) + " " + FEET + " " + (z - 4) + " " + (x + 6) + " " + FEET + " " + (z + 6) + " air replace voidheart:salt_ward");
        waitFor(context, server, WATCH_TICKS * 2, "Swept clear, the crab should climb the pillar", minecraft ->
                first(minecraft, VoidHeartEntities.CORRUPTED_CRAB_MEDIUM).isClinging());
        server.runCommand("kill @e[type=voidheart:corrupted_crab_medium]");
        server.runCommand("fill " + x + " " + FEET + " " + z + " " + (x + 2) + " " + (FEET + 5) + " " + (z + 2) + " air");
    }

    private static void protect(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setHealth(player.getMaxHealth());
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100000, 4));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100000, 4));
        });
    }

    /**
     * Salt over every bare floor block in the square, as laying it would.
     */
    private static void saltSquare(MinecraftServer minecraft, int x0, int z0, int x1, int z1)
    {
        var level = minecraft.overworld();
        var block = (SaltWardBlock) VoidHeartBlocks.SALT_WARD;
        for (var pos : BlockPos.betweenClosed(x0, FEET, z0, x1, FEET, z1))
        {
            if (level.getBlockState(pos).isAir() && SaltWardBlock.canLayOn(level, pos))
                level.setBlockAndUpdate(pos, block.stateAt(level, pos));
        }
    }

    private static void standInPatch(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.teleportTo(PATCH_X + 0.5, FEET, PATCH_Z + 0.5);
            player.setYRot(0);
            player.setXRot(10);
            player.setHealth(player.getMaxHealth());
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100000, 4));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100000, 4));
        });
    }

    /**
     * @return how close the mob came to the player over the watch
     */
    private static double watch(ClientGameTestContext context, TestServerContext server, EntityType<? extends Mob> type, java.util.function.Consumer<Mob> check)
    {
        double closest = Double.MAX_VALUE;
        for (int tick = 0; tick < WATCH_TICKS; tick++)
        {
            double distance = server.computeOnServer(minecraft ->
            {
                var mob = first(minecraft, type);
                if (mob == null)
                    return Double.MAX_VALUE;
                check.accept(mob);
                return (double) mob.distanceTo(player(minecraft));
            });
            closest = Math.min(closest, distance);
            context.waitTick();
        }
        return closest;
    }

    private static void frame(ClientGameTestContext context, TestServerContext server, String name)
    {
        server.runCommand("tp @a " + (PATCH_X + 0.5) + " " + FEET + " " + (PATCH_Z + 0.5) + " 0 15");
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.waitTicks(3);
        context.takeScreenshot(name);
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    private static void aimAt(ClientGameTestContext context, TestServerContext server, double feetX, double feetY, double feetZ, double x, double y, double z)
    {
        double dx = x - feetX;
        double dy = y - (feetY + 1.62);
        double dz = z - feetZ;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        server.runCommand("tp @a " + feetX + " " + feetY + " " + feetZ + " " + yaw + " " + pitch);
        context.waitTicks(5);
    }

    private static <T extends Mob> void spawn(MinecraftServer minecraft, EntityType<T> type, double x, double z)
    {
        var level = minecraft.overworld();
        var mob = type.create(level, EntitySpawnReason.COMMAND);
        mob.snapTo(x, FEET, z, 180, 0);
        mob.setTarget(player(minecraft));
        level.addFreshEntity(mob);
    }

    private static <T extends Mob> T first(MinecraftServer minecraft, EntityType<T> type)
    {
        var found = minecraft.overworld().getEntities(type, Mob::isAlive);
        return found.isEmpty() ? null : found.getFirst();
    }

    private static int count(MinecraftServer minecraft)
    {
        var level = minecraft.overworld();
        int count = 0;
        for (var pos : BlockPos.betweenClosed(PATCH_X - 8, FEET, PATCH_Z - 8, PATCH_X + 8, FEET, PATCH_Z + 8))
        {
            if (SaltWard.isSalt(level, pos))
                count++;
        }
        return count;
    }

    private static BlockPos firstSalt(MinecraftServer minecraft)
    {
        ServerLevel level = minecraft.overworld();
        for (var pos : BlockPos.betweenClosed(PATCH_X - 8, FEET, PATCH_Z - 8, PATCH_X + 8, FEET, PATCH_Z + 8))
        {
            if (SaltWard.isSalt(level, pos) && level.getBlockState(pos.north()).is(Blocks.AIR))
                return pos.immutable();
        }
        throw new AssertionError("No salt left on the patch's edge");
    }

    private static net.minecraft.world.level.block.state.BlockState state(MinecraftServer minecraft, int x, int z)
    {
        var state = minecraft.overworld().getBlockState(new BlockPos(x, FEET, z));
        expect(state.is(VoidHeartBlocks.SALT_WARD), "Expected salt at " + x + ", " + z);
        return state;
    }

    private static GoalSelector goals(Mob mob, String name)
    {
        try
        {
            Field field = Mob.class.getDeclaredField(name);
            field.setAccessible(true);
            return (GoalSelector) field.get(mob);
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError(e);
        }
    }

    private static void setInt(Object target, String name, int value)
    {
        try
        {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.setInt(target, value);
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError(e);
        }
    }

    private static void waitFor(ClientGameTestContext context, TestServerContext server, int maxTicks, String message, Predicate<MinecraftServer> condition)
    {
        for (int tick = 0; tick < maxTicks; tick++)
        {
            if (server.computeOnServer(condition::test))
                return;
            context.waitTick();
        }
        throw new AssertionError(message + " within " + maxTicks + " ticks");
    }

    private static ServerPlayer player(MinecraftServer minecraft)
    {
        return minecraft.getPlayerList().getPlayers().getFirst();
    }

    private static void expect(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
