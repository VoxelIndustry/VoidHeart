package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.common.content.mob.DormantTreantTile;
import net.voxelindustry.voidheart.common.content.mob.Treant;
import net.voxelindustry.voidheart.common.content.mob.TreantMoves;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.dungeon.VoidDungeons;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * A Treant waiting in its trunk, coming out when a player comes near or breaks the bark, sweeping a player away, dropping its wood, and hiding in a forest
 * dungeon's trunks.
 */
public class TreantClientTest implements FabricClientGameTest
{
    private static final int FEET = FLAT_FEET;
    private static final int TRUNK_HEIGHT = 5;
    private static final int CHUNK_TIMEOUT_TICKS = 1200;
    private static final int LOOT_ROLLS = 200;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamerule immediate_respawn true");
            server.runCommand("difficulty normal");
            server.runCommand("gamemode survival @a");
            context.waitTicks(20);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().pressKey(InputConstants.KEY_F1);

            checkStaysHidden(context, server);
            checkEmerges(context, server);
            checkSweep(context, server);
            checkBreakingWakes(context, server);
            checkLoot(context, server);
            checkForestDungeon(context, server, world);

            server.runCommand("gamemode creative @a");
            context.getInput().pressKey(InputConstants.KEY_F1);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        }
    }

    private static void trunk(TestServerContext server, int x, int z)
    {
        server.runCommand("fill " + (x - 6) + " " + FEET + " " + (z - 6) + " " + (x + 6) + " " + (FEET + 8) + " " + (z + 6) + " air");
        server.runCommand("setblock " + x + " " + FEET + " " + z + " voidheart:dormant_treant");
        server.runCommand("fill " + x + " " + (FEET + 1) + " " + z + " " + x + " " + (FEET + TRUNK_HEIGHT) + " " + z + " dark_oak_log");
    }

    private static List<? extends Treant> treants(MinecraftServer minecraft)
    {
        return minecraft.overworld().getEntities(VoidHeartEntities.TREANT, Treant::isAlive);
    }

    /**
     * A creative player, a far player and a player in Peaceful all leave it be.
     */
    private static void checkStaysHidden(ClientGameTestContext context, TestServerContext server)
    {
        int x = 0;
        int z = 0;
        trunk(server, x, z);

        tp(server, x + 12.5, FEET, z + 0.5);
        context.waitTicks(40);
        server.runOnServer(minecraft -> expect(treants(minecraft).isEmpty(), "A far player should not wake a Treant"));

        server.runCommand("gamemode creative @a");
        tp(server, x + 2.5, FEET, z + 0.5);
        context.waitTicks(40);
        server.runOnServer(minecraft -> expect(treants(minecraft).isEmpty(), "A creative player should not wake a Treant"));

        server.runCommand("difficulty peaceful");
        server.runCommand("gamemode survival @a");
        context.waitTicks(40);
        server.runOnServer(minecraft ->
        {
            expect(treants(minecraft).isEmpty(), "Nothing should wake a Treant in Peaceful");
            expect(minecraft.overworld().getBlockState(new BlockPos(x, FEET, z)).is(VoidHeartBlocks.DORMANT_TREANT), "The trunk should still hide its Treant");
        });
        server.runCommand("difficulty normal");
        tp(server, x + 12.5, FEET, z + 0.5);
        context.waitTicks(5);
    }

    /**
     * Walking up to the trunk strips its foot bare and the Treant pulls itself out of it toward the player over a few seconds.
     */
    private static void checkEmerges(ClientGameTestContext context, TestServerContext server)
    {
        int x = 0;
        int z = 0;
        aimCamera(server, x + 4.5, FEET, z + 0.5, x + 0.5, FEET + 1.6, z + 0.5);
        waitFor(context, server, 40, "A player coming near should wake the Treant", minecraft -> !treants(minecraft).isEmpty());

        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var treant = treants(minecraft).getFirst();
            expect(treant.isEmerging(), "A woken Treant should start inside its trunk");
            expect(treant.getTarget() == player(minecraft), "A woken Treant should go for the player who woke it");
            for (int up = 0; up < 3; up++)
                expect(level.getBlockState(new BlockPos(x, FEET + up, z)).is(Blocks.STRIPPED_DARK_OAK_LOG), "The trunk it filled should be stripped at " + up);
            expect(level.getBlockState(new BlockPos(x, FEET + 3, z)).is(Blocks.DARK_OAK_LOG), "The trunk above it should keep its bark");
        });

        // Watch from the side and out of its reach, as a spectator it will not go for
        server.runCommand("gamemode spectator @a");
        aimCamera(server, x + 4.5, FEET + 1, z + 5.5, x + 1, FEET + 1.5, z + 0.5);
        waitFor(context, server, 20, "Emerging", minecraft -> treants(minecraft).getFirst().emergeTicks() >= 6);
        context.takeScreenshot("treant-emerge-start");
        waitFor(context, server, 40, "Emerging", minecraft -> treants(minecraft).getFirst().emergeTicks() >= 35);
        context.takeScreenshot("treant-emerge-middle");
        aimCamera(server, x + 5.5, FEET + 1, z + 0.5, x + 1, FEET + 1.5, z + 0.5);
        waitFor(context, server, 40, "Emerging", minecraft -> treants(minecraft).getFirst().emergeTicks() >= 55);
        context.takeScreenshot("treant-emerge-late");
        waitFor(context, server, 40, "The Treant should stand free", minecraft -> !treants(minecraft).getFirst().isEmerging());
        context.waitTicks(5);
        context.takeScreenshot("treant-out");

        server.runOnServer(minecraft ->
        {
            var treant = treants(minecraft).getFirst();
            double out = treant.getX() - (x + 0.5);
            expect(Math.abs(out - TreantMoves.EMERGE_DISTANCE) < 0.3 && Math.abs(treant.getZ() - (z + 0.5)) < 0.3,
                    "The Treant should step out toward the player, got " + treant.position());
            expect(!treant.noPhysics, "A Treant out of its trunk should collide again");
        });
    }

    /**
     * Within reach of its claws a player is hit hard, through diamond, and thrown back.
     */
    private static void checkSweep(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("gamemode survival @a");
        for (var slot : new String[] { "head", "chest", "legs", "feet" })
        {
            String piece = switch (slot)
            {
                case "head" -> "helmet";
                case "chest" -> "chestplate";
                case "legs" -> "leggings";
                default -> "boots";
            };
            server.runCommand("item replace entity @a armor." + slot + " with diamond_" + piece);
        }
        double[] start = new double[4];
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            var treant = treants(minecraft).getFirst();
            var player = player(minecraft);
            treant.setTarget(player);
        });
        aimCamera(server, 4.5, FEET, 0.5, 1.6, FEET + 2, 0.5);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            start[0] = player.getHealth();
            start[1] = player.getX();
            start[2] = player.getZ();
        });

        waitFor(context, server, 120, "The Treant should rear back for a sweep", minecraft -> treants(minecraft).getFirst().sweepTicks() >= 14);
        context.takeScreenshot("treant-sweep-windup");
        waitFor(context, server, 30, "The sweep should land", minecraft -> player(minecraft).getHealth() < start[0]);
        context.takeScreenshot("treant-sweep-strike");
        context.waitTicks(10);

        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            float lost = (float) start[0] - player.getHealth();
            var treant = treants(minecraft).getFirst();
            double before = Math.hypot(start[1] - treant.getX(), start[2] - treant.getZ());
            double after = Math.hypot(player.getX() - treant.getX(), player.getZ() - treant.getZ());
            System.out.println("[Treant] sweep took " + lost + " health through diamond, thrown from " + before + " to " + after);
            expect(lost >= 4, "A sweep should hurt through diamond, took " + lost);
            expect(after >= before + 1.5, "A sweep should throw the player back, from " + before + " to " + after);
        });

        // Its look in the open, a little off its front and held still
        server.runCommand("gamemode spectator @a");
        var at = server.computeOnServer(minecraft ->
        {
            var treant = treants(minecraft).getFirst();
            treant.setNoAi(true);
            treant.snapTo(10.5, FEET, 0.5, 90, 0);
            treant.setYHeadRot(90);
            treant.setYBodyRot(90);
            return treant.position();
        });
        aimCamera(server, at.x - 5, FEET + 0.5, at.z + 2.5, at.x, FEET + 1.6, at.z);
        context.waitTicks(10);
        context.takeScreenshot("treant-front");
        clearScene(server);
    }

    /**
     * Breaking the bark brings it out at once, and the trunk drops no log.
     */
    private static void checkBreakingWakes(ClientGameTestContext context, TestServerContext server)
    {
        int x = 40;
        int z = 0;
        server.runCommand("difficulty peaceful");
        trunk(server, x, z);
        server.runCommand("gamemode survival @a");
        tp(server, x + 2.5, FEET, z + 0.5);
        context.waitTicks(5);
        server.runCommand("difficulty normal");
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.gameMode.destroyBlock(new BlockPos(x, FEET, z)), "The player should break the trunk's foot");
            var level = minecraft.overworld();
            expect(level.getBlockState(new BlockPos(x, FEET, z)).isAir(), "The broken foot should be gone");
            expect(level.getBlockState(new BlockPos(x, FEET + 1, z)).is(Blocks.STRIPPED_DARK_OAK_LOG), "The rest of what it filled should be stripped");
            var treant = treants(minecraft);
            expect(treant.size() == 1 && treant.getFirst().isEmerging(), "Breaking the bark should bring the Treant out");
        });
        context.waitTicks(2);
        server.runOnServer(minecraft ->
        {
            var logs = minecraft.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(x - 3, FEET - 1, z - 3, x + 4, FEET + 4, z + 4));
            expect(logs.isEmpty(), "The trunk's foot should drop nothing, dropped " + logs);
        });
        clearScene(server);
    }

    private static void checkLoot(ClientGameTestContext context, TestServerContext server)
    {
        tp(server, 700.5, FEET, 0.5);
        context.waitTicks(10);
        server.runOnServer(minecraft ->
        {
            int[] logs = { Integer.MAX_VALUE, 0 };
            int[] sticks = { Integer.MAX_VALUE, 0 };
            int knots = 0;
            for (int roll = 0; roll < LOOT_ROLLS; roll++)
            {
                var drops = drops(minecraft);
                int log = count(drops, Items.DARK_OAK_LOG);
                int stick = count(drops, Items.STICK);
                logs[0] = Math.min(logs[0], log);
                logs[1] = Math.max(logs[1], log);
                sticks[0] = Math.min(sticks[0], stick);
                sticks[1] = Math.max(sticks[1], stick);
                knots += count(drops, VoidHeartItems.HEARTWOOD_KNOT);
            }
            System.out.println("[Treant] logs " + logs[0] + "-" + logs[1] + ", sticks " + sticks[0] + "-" + sticks[1] + ", knots " + knots + " in " + LOOT_ROLLS);
            expect(logs[0] == 1 && logs[1] == 3, "Logs should drop 1 to 3, got " + logs[0] + "-" + logs[1]);
            expect(sticks[0] == 2 && sticks[1] == 5, "Sticks should drop 2 to 5, got " + sticks[0] + "-" + sticks[1]);
            expect(knots >= 4 && knots <= 32, "About 8% of kills should drop a Heartwood Knot, got " + knots + " in " + LOOT_ROLLS);
        });
    }

    private static List<ItemEntity> drops(MinecraftServer minecraft)
    {
        var level = minecraft.overworld();
        var player = player(minecraft);
        var treant = spawn(minecraft, VoidHeartEntities.TREANT, 700.5, FEET + 30, 30.5);
        treant.setNoAi(true);
        treant.hurtServer(level, player.damageSources().playerAttack(player), 100000);
        var area = new AABB(690, FEET + 20, 20, 710, FEET + 40, 40);
        var drops = new ArrayList<>(level.getEntitiesOfClass(ItemEntity.class, area));
        drops.forEach(ItemEntity::discard);
        level.getEntitiesOfClass(Treant.class, area).forEach(Treant::discard);
        return drops;
    }

    private static int count(List<ItemEntity> drops, Item item)
    {
        return drops.stream().filter(drop -> drop.getItem().is(item)).mapToInt(drop -> drop.getItem().getCount()).sum();
    }

    /**
     * A forest dungeon hides Treants in trunks standing on its floors, that pass for dark oak until a player comes near.
     */
    private static void checkForestDungeon(ClientGameTestContext context, TestServerContext server, TestSingleplayerContext world)
    {
        server.runCommand("gamemode spectator @a");
        server.runCommand("execute as @a run voidheart dungeon open medium_forest 3");
        context.waitTicks(100);
        world.getConnection().waitForChunksRender(false, CHUNK_TIMEOUT_TICKS);
        context.waitTicks(20);

        BlockPos trunk = server.computeOnServer(minecraft ->
        {
            var level = minecraft.getLevel(VoidDungeons.WORLD_KEY);
            var player = player(minecraft);
            var found = nearestTrunk(level, player.blockPosition(), 96);
            System.out.println("[Treant] nearest dungeon trunk " + found + " from " + player.blockPosition());
            return found;
        });
        expect(trunk != null, "A forest dungeon should hide Treants in its trunks");

        server.runOnServer(minecraft ->
        {
            var level = minecraft.getLevel(VoidDungeons.WORLD_KEY);
            expect(level.getBlockState(trunk.above()).is(Blocks.DARK_OAK_LOG) && level.getBlockState(trunk.above(2)).is(Blocks.DARK_OAK_LOG),
                    "A dungeon trunk should rise in dark oak over its Treant");
            expect(!level.getBlockState(trunk.below()).isAir(), "A dungeon trunk should stand on the floor");
        });

        var side = server.computeOnServer(minecraft ->
        {
            var level = minecraft.getLevel(VoidDungeons.WORLD_KEY);
            for (var dir : net.minecraft.core.Direction.Plane.HORIZONTAL)
            {
                var spot = trunk.relative(dir, 4);
                if (level.getBlockState(spot).isAir() && level.getBlockState(spot.above()).isAir() && level.getBlockState(trunk.relative(dir, 2)).isAir())
                    return dir;
            }
            return net.minecraft.core.Direction.EAST;
        });
        var view = trunk.relative(side, 5);
        server.runCommand("execute as @a in voidheart:dungeons run tp @s " + aimedPose(view.getX() + 0.5, view.getY() + 1, view.getZ() + 0.5,
                trunk.getX() + 0.5, trunk.getY() + 2, trunk.getZ() + 0.5));
        context.waitTicks(20);
        context.takeScreenshot("treant-dungeon-trunk");

        // Coming near, with something to survive whatever else lives there
        server.runCommand("effect give @a resistance 60 4 true");
        server.runCommand("gamemode survival @a");
        var near = trunk.relative(side, 3);
        server.runCommand("execute as @a in voidheart:dungeons run tp @s " + aimedPose(near.getX() + 0.5, near.getY(), near.getZ() + 0.5,
                trunk.getX() + 0.5, trunk.getY() + 1.5, trunk.getZ() + 0.5));
        waitFor(context, server, 60, "Coming near a dungeon trunk should wake its Treant",
                minecraft -> !minecraft.getLevel(VoidDungeons.WORLD_KEY).getEntities(VoidHeartEntities.TREANT, Treant::isAlive).isEmpty());
        server.runCommand("gamemode spectator @a");
        server.runCommand("execute as @a in voidheart:dungeons run tp @s " + aimedPose(view.getX() + 0.5, view.getY() + 1, view.getZ() + 0.5,
                trunk.getX() + 0.5, trunk.getY() + 1.5, trunk.getZ() + 0.5));
        context.waitTicks(35);
        context.takeScreenshot("treant-dungeon-emerge");
        server.runCommand("effect clear @a");
    }

    private static BlockPos nearestTrunk(ServerLevel level, BlockPos center, int range)
    {
        BlockPos best = null;
        for (int chunkX = (center.getX() - range) >> 4; chunkX <= (center.getX() + range) >> 4; chunkX++)
        {
            for (int chunkZ = (center.getZ() - range) >> 4; chunkZ <= (center.getZ() + range) >> 4; chunkZ++)
            {
                var chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null)
                    continue;
                for (var tile : chunk.getBlockEntities().values())
                {
                    if (tile instanceof DormantTreantTile && (best == null || tile.getBlockPos().distSqr(center) < best.distSqr(center)))
                        best = tile.getBlockPos();
                }
            }
        }
        return best;
    }
}
