package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.voxelindustry.voidheart.common.content.mob.BarnacleInfection;
import net.voxelindustry.voidheart.common.content.mob.BarnacleNestBlock;
import net.voxelindustry.voidheart.common.content.mob.CorruptedBarnacle;
import net.voxelindustry.voidheart.common.content.mob.CrawlerMonster;
import net.voxelindustry.voidheart.common.content.mob.FloatingEye;
import net.voxelindustry.voidheart.common.content.mob.VoidOrb;
import net.voxelindustry.voidheart.common.content.mob.VoidSkeletonHeadBlock;
import net.voxelindustry.voidheart.common.content.mob.WraithStain;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.List;
import java.util.function.Consumer;

/**
 * How every mob and what comes with them look from around, standing still.
 */
public class VoidMobLooksClientTest implements FabricClientGameTest
{
    private static final int GROUND = -61;
    private static final int FEET = GROUND + 1;
    private static final int ROW_Z = 4;
    private static final int SPACING = 5;
    private static final int CLOSE_FOV = 45;
    private static final int DEFAULT_FOV = 70;

    private record Pose(String name, EntityType<? extends Mob> type, double height, Consumer<Mob> setup)
    {
    }

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
            server.runCommand("difficulty hard");
            server.runCommand("gamemode creative @a");
            context.waitTicks(20);
            context.runOnClient(minecraft ->
            {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                minecraft.options.fov().set(CLOSE_FOV);
            });
            context.getInput().pressKey(InputConstants.KEY_F1);

            var poses = List.of(
                    new Pose("void-skeleton", VoidHeartEntities.VOID_SKELETON, 1, mob -> {}),
                    new Pose("void-skeleton-aggressive", VoidHeartEntities.VOID_SKELETON, 1, mob -> mob.setAggressive(true)),
                    new Pose("void-ghoul", VoidHeartEntities.VOID_GHOUL, 0.9, mob -> {}),
                    new Pose("wraith", VoidHeartEntities.WRAITH, 1, mob -> {}),
                    new Pose("crab-small", VoidHeartEntities.CORRUPTED_CRAB_SMALL, 0.2, mob -> {}),
                    new Pose("crab-medium", VoidHeartEntities.CORRUPTED_CRAB_MEDIUM, 0.4, mob -> {}),
                    new Pose("crab-large", VoidHeartEntities.CORRUPTED_CRAB_LARGE, 0.6, mob -> {}),
                    new Pose("barnacle-small", VoidHeartEntities.CORRUPTED_BARNACLE, 0.2, mob -> ((CorruptedBarnacle) mob).setStage(0)),
                    new Pose("barnacle-medium", VoidHeartEntities.CORRUPTED_BARNACLE, 0.3, mob -> ((CorruptedBarnacle) mob).setStage(1)),
                    new Pose("barnacle-grown", VoidHeartEntities.CORRUPTED_BARNACLE, 0.4, mob -> ((CorruptedBarnacle) mob).setStage(2)),
                    new Pose("fallen-novice", VoidHeartEntities.FALLEN_NOVICE, 0.8, mob -> {}),
                    new Pose("fallen-novice-aggressive", VoidHeartEntities.FALLEN_NOVICE, 0.8, mob -> mob.setAggressive(true)),
                    new Pose("fallen-adept", VoidHeartEntities.FALLEN_ADEPT, 0.8, mob -> {}),
                    new Pose("fallen-elder", VoidHeartEntities.FALLEN_ELDER, 1.4, mob -> {}),
                    new Pose("eye-maw", VoidHeartEntities.EYE_MAW, 0.3, mob -> {}),
                    new Pose("eye-maw-open", VoidHeartEntities.EYE_MAW, 0.3, mob -> ((FloatingEye) mob).setJawsOpen(true)),
                    new Pose("eye-maw-large", VoidHeartEntities.LARGE_EYE_MAW, 1.1, mob -> {}),
                    new Pose("eye-maw-large-open", VoidHeartEntities.LARGE_EYE_MAW, 1.1, mob -> ((FloatingEye) mob).setJawsOpen(true)),
                    new Pose("arcane-eye", VoidHeartEntities.ARCANE_EYE, 0.4, mob -> {}));

            server.runCommand("fill -40 " + GROUND + " -6 60 " + GROUND + " 14 voidheart:voidstone_tile");
            for (int i = 0; i < poses.size(); i++)
            {
                var pose = poses.get(i);
                int x = i * SPACING;
                server.runOnServer(minecraft -> spawn(minecraft, pose, x));
            }
            context.waitTicks(10);
            server.runCommand("tick freeze");

            for (int i = 0; i < poses.size(); i++)
            {
                var pose = poses.get(i);
                double x = i * SPACING + 0.5;
                double y = FEET + pose.height();
                double reach = 2.2 + pose.height() * 1.3;
                aimFrom(context, server, x + reach * 0.6, y - 0.4, ROW_Z + 0.5 + reach, x, y, ROW_Z + 0.5);
                context.takeScreenshot("mob-" + pose.name() + "-front");
                aimFrom(context, server, x - reach * 0.7, y + 0.3, ROW_Z + 0.5 - reach, x, y, ROW_Z + 0.5);
                context.takeScreenshot("mob-" + pose.name() + "-back");
            }
            server.runCommand("tick unfreeze");
            server.runCommand("kill @e[type=!player]");

            checkClinging(context, server);
            checkStain(context, server);
            checkOrb(context, server);
            checkNestAndHead(context, server);
            checkGrowths(context, server);
            checkItems(context, server);

            context.runOnClient(minecraft ->
            {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                minecraft.options.fov().set(DEFAULT_FOV);
            });
        }
    }

    private static void spawn(MinecraftServer minecraft, Pose pose, int x)
    {
        var level = minecraft.overworld();
        var mob = pose.type().create(level, EntitySpawnReason.COMMAND);
        mob.snapTo(x + 0.5, FEET, ROW_Z + 0.5, 0, 0);
        mob.setYHeadRot(0);
        mob.setYBodyRot(0);
        mob.setNoAi(true);
        pose.setup().accept(mob);
        level.addFreshEntity(mob);
    }

    /**
     * A small room with a crab held to the wall, the ceiling and the floor, and a barnacle on the wall.
     */
    private static void checkClinging(ClientGameTestContext context, TestServerContext server)
    {
        int x = -30;
        int z = 0;
        server.runCommand("fill " + (x - 6) + " " + GROUND + " " + (z - 6) + " " + (x + 6) + " " + (GROUND + 7) + " " + (z + 6) + " voidheart:voidstone_bricks hollow");
        server.runCommand("effect give @a night_vision 60 0 true");
        server.runOnServer(minecraft ->
        {
            cling(minecraft, VoidHeartEntities.CORRUPTED_CRAB_MEDIUM, new BlockPos(x - 5, FEET + 2, z), Direction.EAST, 0);
            cling(minecraft, VoidHeartEntities.CORRUPTED_CRAB_MEDIUM, new BlockPos(x + 5, FEET + 2, z), Direction.WEST, 0);
            cling(minecraft, VoidHeartEntities.CORRUPTED_CRAB_MEDIUM, new BlockPos(x, FEET + 2, z - 5), Direction.SOUTH, 0);
            cling(minecraft, VoidHeartEntities.CORRUPTED_CRAB_MEDIUM, new BlockPos(x, FEET + 5, z), Direction.DOWN, 0);
            cling(minecraft, VoidHeartEntities.CORRUPTED_CRAB_LARGE, new BlockPos(x - 3, FEET, z - 3), Direction.UP, 0);
            cling(minecraft, VoidHeartEntities.CORRUPTED_BARNACLE, new BlockPos(x - 4, FEET + 2, z - 5), Direction.SOUTH, 2);
            cling(minecraft, VoidHeartEntities.CORRUPTED_BARNACLE, new BlockPos(x + 3, FEET + 4, z - 5), Direction.SOUTH, 1);
            cling(minecraft, VoidHeartEntities.CORRUPTED_BARNACLE, new BlockPos(x + 3, FEET + 5, z - 2), Direction.DOWN, 2);
        });
        context.waitTicks(20);
        aimFrom(context, server, x, FEET + 2, z + 5, x, FEET + 3, z - 4);
        context.takeScreenshot("mob-clinging-room");
        aimFrom(context, server, x - 1, FEET, z + 1, x + 3, FEET + 5, z - 2);
        context.takeScreenshot("mob-clinging-ceiling");
        aimFrom(context, server, x - 1, FEET + 2, z + 2, x - 5.5, FEET + 2.5, z);
        context.takeScreenshot("mob-clinging-wall-west");
        aimFrom(context, server, x + 1, FEET + 2, z + 2, x + 5.5, FEET + 2.5, z);
        context.takeScreenshot("mob-clinging-wall-east");
        server.runCommand("kill @e[type=!player]");
        server.runCommand("effect clear @a night_vision");
    }

    private static void cling(MinecraftServer minecraft, EntityType<? extends CrawlerMonster> type, BlockPos cell, Direction surface, int stage)
    {
        var level = minecraft.overworld();
        var mob = type.create(level, EntitySpawnReason.COMMAND);
        if (mob instanceof CorruptedBarnacle barnacle)
            barnacle.setStage(stage);
        mob.snapTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, 30, 0);
        mob.settleOn(cell, surface);
        mob.setYBodyRot(30);
        mob.setYHeadRot(30);
        mob.setNoAi(true);
        level.addFreshEntity(mob);
    }

    private static void checkStain(ClientGameTestContext context, TestServerContext server)
    {
        int x = 30;
        int z = -20;
        server.runCommand("fill " + (x - 5) + " " + FEET + " " + z + " " + (x + 5) + " " + (FEET + 6) + " " + z + " voidheart:voidstone_bricks");
        server.runCommand("fill " + (x - 5) + " " + GROUND + " " + z + " " + (x + 5) + " " + GROUND + " " + (z + 9) + " voidheart:voidstone_tile");
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            WraithStain.on(level, new BlockPos(x, FEET + 2, z), Direction.SOUTH);
            WraithStain.on(level, new BlockPos(x + 3, GROUND, z + 4), Direction.UP);
        });
        aimFrom(context, server, x - 1.5, FEET + 1, z + 5, x + 1, FEET + 1, z + 1);
        context.waitTicks(3);
        context.takeScreenshot("mob-stain-start");
        context.waitTicks(25);
        context.takeScreenshot("mob-stain-growing");
        context.waitTicks(25);
        context.takeScreenshot("mob-stain-full");
        server.runCommand("tick freeze");
        for (int step = 0; step < 6; step++)
        {
            aimFrom(context, server, x - 4 + step * 0.05, FEET + 0.5, z + 1.2 + step * 0.04, x, FEET + 2.5, z + 1);
            context.takeScreenshot("mob-stain-grazing-sweep-" + step);
        }
        server.runCommand("tick unfreeze");
        context.waitTicks(10);
        aimFrom(context, server, x - 1.5, FEET + 1, z + 5, x + 1, FEET + 1, z + 1);
        context.waitTicks(30);
        context.takeScreenshot("mob-stain-fading");
        context.waitTicks(40);
        if (server.computeOnServer(minecraft -> !minecraft.overworld().getEntities(VoidHeartEntities.WRAITH_STAIN, stain -> true).isEmpty()))
            throw new AssertionError("Expected the stains to have faded away");
    }

    private static void checkOrb(ClientGameTestContext context, TestServerContext server)
    {
        int x = 45;
        int z = -20;
        server.runCommand("tick freeze");
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var player = player(minecraft);
            var orb = new VoidOrb(level, player, player, 8);
            orb.setPos(x + 0.5, FEET + 1.5, z + 0.5);
            level.addFreshEntity(orb);
        });
        aimFrom(context, server, x + 0.5, FEET + 1, z + 2.5, x + 0.5, FEET + 1.75, z + 0.5);
        context.takeScreenshot("mob-orb-north");
        aimFrom(context, server, x + 0.5, FEET + 1, z - 1.5, x + 0.5, FEET + 1.75, z + 0.5);
        context.takeScreenshot("mob-orb-south");
        server.runCommand("kill @e[type=voidheart:void_orb]");
        server.runCommand("tick unfreeze");
    }

    private static void checkNestAndHead(ClientGameTestContext context, TestServerContext server)
    {
        int x = 0;
        int z = -20;
        server.runCommand("fill " + (x - 6) + " " + FEET + " " + z + " " + (x + 6) + " " + (FEET + 5) + " " + z + " voidheart:voidstone_bricks");
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var nest = VoidHeartBlocks.BARNACLE_NEST.defaultBlockState();
            level.setBlockAndUpdate(new BlockPos(x - 3, FEET + 1, z + 1), nest.setValue(BarnacleNestBlock.FACING, Direction.SOUTH));
            level.setBlockAndUpdate(new BlockPos(x - 1, FEET, z + 2), nest.setValue(BarnacleNestBlock.FACING, Direction.UP));

            var head = VoidHeartBlocks.VOID_SKELETON_HEAD.defaultBlockState();
            level.setBlockAndUpdate(new BlockPos(x + 1, FEET, z + 2), head.setValue(VoidSkeletonHeadBlock.FACING, Direction.SOUTH));
            level.setBlockAndUpdate(new BlockPos(x + 3, FEET + 1, z + 1), head.setValue(VoidSkeletonHeadBlock.FACING, Direction.SOUTH).setValue(VoidSkeletonHeadBlock.WALL, true));
        });
        context.waitTicks(5);
        aimFrom(context, server, x, FEET + 1, z + 7, x, FEET + 0.8, z + 1);
        context.takeScreenshot("mob-nest-and-head");

        server.runOnServer(minecraft -> minecraft.overworld().setBlockAndUpdate(new BlockPos(x - 3, FEET + 1, z), Blocks.AIR.defaultBlockState()));
        context.waitTicks(2);
        if (server.computeOnServer(minecraft -> !minecraft.overworld().getBlockState(new BlockPos(x - 3, FEET + 1, z + 1)).isAir()))
            throw new AssertionError("Expected a nest with nothing behind it to fall away");
    }

    private static void checkGrowths(ClientGameTestContext context, TestServerContext server)
    {
        int x = 10;
        int z = -30;
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setAttached(BarnacleInfection.AMOUNT, 5);
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(VoidHeartBlocks.VOID_SKELETON_HEAD));
        });
        aimFrom(context, server, x, FEET, z, x, FEET + 1.5, z + 5);
        context.runOnClient(minecraft ->
        {
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            minecraft.options.fov().set(CLOSE_FOV - 15);
        });
        context.waitTicks(5);
        context.takeScreenshot("mob-growths-back");
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(5);
        context.takeScreenshot("mob-growths-front");
        context.runOnClient(minecraft ->
        {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            minecraft.options.fov().set(DEFAULT_FOV);
        });
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.removeAttached(BarnacleInfection.AMOUNT);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        });
    }

    private static void checkItems(ClientGameTestContext context, TestServerContext server)
    {
        var firstRow = List.of(VoidHeartBlocks.VOID_SKELETON_HEAD.asItem(), VoidHeartBlocks.BARNACLE_NEST.asItem(), VoidHeartItems.WRIGGLING_BRAIN);
        context.getInput().pressKey(InputConstants.KEY_F1);
        server.runOnServer(minecraft ->
        {
            var inventory = player(minecraft).getInventory();
            int slot = 0;
            for (Item item : firstRow)
                inventory.setItem(slot++, new ItemStack(item));
            for (int i = 0; i < 6; i++)
                inventory.setItem(slot++, new ItemStack(VoidHeartItems.SPAWN_EGGS.get(i)));
            inventory.setSelectedSlot(0);
        });
        context.waitTicks(20);
        context.takeScreenshot("mob-items-head-in-hand");
        server.runOnServer(minecraft ->
        {
            var inventory = player(minecraft).getInventory();
            for (int i = 0; i < 9; i++)
                inventory.setItem(i, i < 4 ? new ItemStack(VoidHeartItems.SPAWN_EGGS.get(6 + i)) : ItemStack.EMPTY);
        });
        context.waitTicks(5);
        context.takeScreenshot("mob-items-eggs");
    }

    private static void aimFrom(ClientGameTestContext context, TestServerContext server, double feetX, double feetY, double feetZ, double x, double y, double z)
    {
        double dx = x - feetX;
        double dy = y - (feetY + 1.62);
        double dz = z - feetZ;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        server.runCommand("tp @a " + feetX + " " + feetY + " " + feetZ + " " + yaw + " " + pitch);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
        context.waitTicks(3);
    }

    private static ServerPlayer player(MinecraftServer minecraft)
    {
        return minecraft.getPlayerList().getPlayers().getFirst();
    }
}
