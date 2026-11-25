package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.door.RiftEntryBlock;
import net.voxelindustry.voidheart.common.content.door.RiftKeys;
import net.voxelindustry.voidheart.common.content.door.RiftPairs;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.VoidPocketState;
import net.voxelindustry.voidheart.common.world.pocket.VoidHeartData;

/**
 * A Rift Key binds to a void door of its owner's pocket with real clicks, holding one flexion, raises a diaphane door out of thin air that leads both ways,
 * moves it on a later use, and collapses it when the pocket door is shut or broken.
 */
public class RiftKeyClientTest implements FabricClientGameTest
{
    // A platform in the sky of the overworld, clear of terrain
    private static final BlockPos PLATFORM = new BlockPos(0, 120, 0);
    private static final int COOLDOWN_CHECK_TICKS = 20;

    private BlockPos door;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        // All tests share one client, so the camera and HUD go back as they were, even when a check fails
        var camera = context.computeOnClient(minecraft -> minecraft.options.getCameraType());
        var hudHidden = context.computeOnClient(minecraft -> minecraft.gui.hud.isHidden());
        try
        {
            run(context);
        }
        finally
        {
            context.runOnClient(minecraft ->
            {
                minecraft.options.setCameraType(camera);
                if (minecraft.gui.hud.isHidden() != hudHidden)
                    minecraft.gui.hud.toggle();
            });
        }
    }

    private void run(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode survival @a");
            context.waitTicks(10);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                player.getAbilities().invulnerable = true;
                VoidPocketState.getVoidPocketState(voidLevel(minecraft)).createPocket(voidLevel(minecraft), player.getUUID());
                heartData(minecraft).maxFlexion(1);
                buildPlatform(minecraft.overworld());
                player.getInventory().setItem(0, new ItemStack(VoidHeartItems.RIFT_KEY));
                player.getInventory().setItem(1, new ItemStack(VoidHeartItems.RIFT_KEY));
            });

            // The pocket's terrain is laid once its chunks load, so the room is cleared after
            var heart = server.computeOnServer(minecraft -> heartData(minecraft).pocketPos());
            server.runCommand("execute in voidheart:void run tp @a " + (heart.getX() + 0.5) + " " + (heart.getY() + 8) + " " + (heart.getZ() + 0.5));
            context.waitTicks(40);
            world.getConnection().waitForChunksRender();
            server.runOnServer(this::buildPocketDoors);
            context.waitTicks(10);

            checkBinding(context, server);
            checkOpening(context, server);
            checkTravel(context, server);
            checkReuse(context, server);
            checkShutDoor(context, server);
            checkBrokenDoor(context, server);
            checkInPocket(context, server);
        }
    }

    private static ServerPlayer player(MinecraftServer minecraft)
    {
        return minecraft.getPlayerList().getPlayers().getFirst();
    }

    private static ServerLevel voidLevel(MinecraftServer minecraft)
    {
        return minecraft.getLevel(VoidHeart.VOID_WORLD_KEY);
    }

    private static VoidHeartData heartData(MinecraftServer minecraft)
    {
        return VoidPocketState.getVoidPocketState(voidLevel(minecraft)).getHeartData(player(minecraft).getUUID());
    }

    /**
     * Two east-facing void doors side by side in a cleared room of the pocket, the first at {@link #door}.
     */
    private void buildPocketDoors(MinecraftServer minecraft)
    {
        var level = voidLevel(minecraft);
        var heart = heartData(minecraft).pocketPos();
        door = heart.offset(3, 3, -1);
        for (int x = -3; x <= 3; x++)
        {
            for (int z = -2; z <= 4; z++)
            {
                level.setBlockAndUpdate(door.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                for (int y = 0; y < 3; y++)
                    level.setBlockAndUpdate(door.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
        }
        for (var pos : new BlockPos[]{door, door.south(2)})
        {
            var lower = VoidHeartBlocks.VOID_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            level.setBlockAndUpdate(pos, lower);
            level.setBlockAndUpdate(pos.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        }
    }

    private static void buildPlatform(ServerLevel level)
    {
        for (int x = -6; x <= 6; x++)
        {
            for (int z = -6; z <= 6; z++)
            {
                level.setBlockAndUpdate(PLATFORM.offset(x, -1, z), Blocks.POLISHED_ANDESITE.defaultBlockState());
                for (int y = 0; y < 4; y++)
                    level.setBlockAndUpdate(PLATFORM.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static void select(ClientGameTestContext context, int slot)
    {
        context.getInput().pressKey(options -> options.keyHotbarSlots[slot]);
        context.waitTicks(2);
    }

    private static void use(ClientGameTestContext context)
    {
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(3);
    }

    /**
     * The first key binds to the first door by a plain use, which would otherwise open it, and takes the one flexion. The second binds to the same door for free,
     * but can't bind to the other door, the heart having no flexion left.
     */
    private void checkBinding(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("execute in voidheart:void run tp @a " + (door.getX() - 2.5) + " " + door.getY() + " " + (door.getZ() + 0.5)
                + " facing " + (door.getX() + 0.9) + " " + (door.getY() + 0.5) + " " + (door.getZ() + 0.5));
        context.waitTicks(40);
        world(context);

        select(context, 0);
        use(context);
        server.runOnServer(minecraft ->
        {
            var link = player(minecraft).getInventory().getItem(0).get(VoidHeartDataComponents.RIFT_KEY_LINK);
            if (link == null || !link.doorPos().equals(door))
                throw new AssertionError("A plain use on a pocket void door should bind the key to it, got " + link);
            if (voidLevel(minecraft).getBlockState(door).getValue(DoorBlock.OPEN))
                throw new AssertionError("Binding a key should not open the door");
            if (heartData(minecraft).currentFlexionCount() != 1 || !heartData(minecraft).holdsRiftDoor(door))
                throw new AssertionError("The bound door should hold one flexion, got " + heartData(minecraft).currentFlexionCount());
        });

        select(context, 1);
        use(context);
        server.runCommand("execute in voidheart:void run tp @a " + (door.getX() - 2.5) + " " + door.getY() + " " + (door.getZ() + 2.5)
                + " facing " + (door.getX() + 0.9) + " " + (door.getY() + 0.5) + " " + (door.getZ() + 2.5));
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var stack = player(minecraft).getInventory().getItem(1);
            if (!stack.has(VoidHeartDataComponents.RIFT_KEY_LINK) || heartData(minecraft).currentFlexionCount() != 1)
                throw new AssertionError("A second key on the same door should share its flexion, got " + heartData(minecraft).currentFlexionCount());
            player(minecraft).getInventory().setItem(1, new ItemStack(VoidHeartItems.RIFT_KEY));
        });
        use(context);
        server.runOnServer(minecraft ->
        {
            if (player(minecraft).getInventory().getItem(1).has(VoidHeartDataComponents.RIFT_KEY_LINK))
                throw new AssertionError("A key should not bind to another door with no flexion left");
            if (voidLevel(minecraft).getBlockState(door.south(2)).getValue(DoorBlock.OPEN))
                throw new AssertionError("A refused key should not open the door either");
        });
        select(context, 0);
    }

    private static void world(ClientGameTestContext context)
    {
        context.waitFor(minecraft -> minecraft.level != null && minecraft.player != null, 200);
    }

    private static BlockPos entryAt(BlockPos feet)
    {
        return feet.north();
    }

    /**
     * Used in the overworld facing north, it raises an open entry one block ahead with nothing under it, and opens the pocket door.
     */
    private void checkOpening(ClientGameTestContext context, TestServerContext server)
    {
        var feet = PLATFORM.offset(0, 0, 3);
        server.runCommand("execute in minecraft:overworld run tp @a " + (feet.getX() + 0.5) + " " + feet.getY() + " " + (feet.getZ() + 0.5) + " 180 0");
        context.waitTicks(10);
        world(context);
        // Take away the floor under where the entry goes, it hangs in thin air
        server.runOnServer(minecraft -> minecraft.overworld().setBlockAndUpdate(entryAt(feet).below(), Blocks.AIR.defaultBlockState()));
        context.waitTicks(2);

        use(context);
        server.runOnServer(minecraft ->
        {
            var entry = entryAt(feet);
            var state = minecraft.overworld().getBlockState(entry);
            if (!(state.getBlock() instanceof RiftEntryBlock) || !state.getValue(DoorBlock.OPEN) || state.getValue(DoorBlock.FACING) != Direction.NORTH)
                throw new AssertionError("The key should raise an open entry facing north one block ahead, got " + state);
            if (!(minecraft.overworld().getBlockState(entry.above()).getBlock() instanceof RiftEntryBlock))
                throw new AssertionError("The entry should stand two blocks tall");
            if (!voidLevel(minecraft).getBlockState(door).getValue(DoorBlock.OPEN))
                throw new AssertionError("Opening a rift should open its pocket door");
            if (!RiftPairs.get(minecraft).entryOf(door).equals(java.util.Optional.of(GlobalPos.of(Level.OVERWORLD, entry))))
                throw new AssertionError("The rift should pair the door with its entry, got " + RiftPairs.get(minecraft).entryOf(door));
            if (!player(minecraft).getCooldowns().isOnCooldown(player(minecraft).getMainHandItem()))
                throw new AssertionError("Opening a rift should start the key's cooldown");
        });

        // Several ticks later the entry still hangs there, without support
        context.waitTicks(30);
        if (!server.computeOnServer(minecraft -> minecraft.overworld().getBlockState(entryAt(feet)).getBlock() instanceof RiftEntryBlock))
            throw new AssertionError("The entry should stay up without a floor under it");

        context.getInput().pressKey(InputConstants.KEY_F1);
        server.runCommand("execute in minecraft:overworld run tp @a " + (feet.getX() + 2.5) + " " + (feet.getY() + 0.2) + " " + (feet.getZ() + 1.5)
                + " facing " + (feet.getX() + 0.5) + " " + (feet.getY() + 1) + " " + (feet.getZ() - 0.5));
        context.waitTicks(10);
        context.takeScreenshot("rift-key-entry-front");
        server.runCommand("execute in minecraft:overworld run tp @a " + (feet.getX() - 1.5) + " " + (feet.getY() + 0.2) + " " + (feet.getZ() - 3.5)
                + " facing " + (feet.getX() + 0.5) + " " + (feet.getY() + 1) + " " + (feet.getZ() - 0.5));
        context.waitTicks(10);
        context.takeScreenshot("rift-key-entry-back");

        server.runCommand("tick freeze");
        for (int step = 0; step < 6; step++)
        {
            server.runCommand("execute in minecraft:overworld run tp @a " + (feet.getX() + 1.8 - step * 0.15) + " " + (feet.getY() + 0.3) + " " + (feet.getZ() + 1.2)
                    + " facing " + (feet.getX() + 0.5) + " " + (feet.getY() + 1) + " " + (feet.getZ() - 0.5));
            context.waitTicks(2);
            context.takeScreenshot("rift-key-entry-sweep-" + step);
        }
        server.runCommand("tick unfreeze");
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    /**
     * Walking into the entry leads out of the pocket door into the pocket, and walking back into the pocket door leads out of the entry.
     */
    private void checkTravel(ClientGameTestContext context, TestServerContext server)
    {
        var entry = entryAt(PLATFORM.offset(0, 0, 3));
        server.runCommand("execute in minecraft:overworld run tp @a " + (entry.getX() + 0.5) + " " + entry.getY() + " " + (entry.getZ() + 0.5) + " 180 0");
        context.waitTicks(10);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            if (player.level() != voidLevel(minecraft) || player.blockPosition().distManhattan(door) > 2)
                throw new AssertionError("Walking into the entry should lead out of the pocket door, got " + player.level().dimension() + " " + player.blockPosition());
        });
        world(context);
        context.waitTicks(10);
        context.takeScreenshot("rift-key-pocket-arrival");

        context.waitTicks(20);
        server.runCommand("execute in voidheart:void run tp @a " + (door.getX() + 0.5) + " " + door.getY() + " " + (door.getZ() + 0.5) + " -90 0");
        context.waitTicks(10);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            if (player.level() != minecraft.overworld() || player.blockPosition().distManhattan(entry) > 2)
                throw new AssertionError("Walking into the open pocket door should lead out of the entry, got " + player.level().dimension() + " " + player.blockPosition());
        });
        world(context);
        context.waitTicks(30);
    }

    /**
     * While cooling down the key does nothing. Used again afterwards elsewhere, it moves the rift: the first entry collapses and the pocket door stays open.
     */
    private void checkReuse(ClientGameTestContext context, TestServerContext server)
    {
        var firstEntry = entryAt(PLATFORM.offset(0, 0, 3));
        var feet = PLATFORM.offset(4, 0, 3);
        server.runCommand("execute in minecraft:overworld run tp @a " + (feet.getX() + 0.5) + " " + feet.getY() + " " + (feet.getZ() + 0.5) + " 180 0");
        context.waitTicks(COOLDOWN_CHECK_TICKS);
        use(context);
        server.runOnServer(minecraft ->
        {
            if (!minecraft.overworld().getBlockState(entryAt(feet)).isAir())
                throw new AssertionError("A key cooling down should raise nothing");
            var player = player(minecraft);
            player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(player.getMainHandItem()));
        });
        context.waitTicks(5);

        use(context);
        server.runOnServer(minecraft ->
        {
            if (!(minecraft.overworld().getBlockState(entryAt(feet)).getBlock() instanceof RiftEntryBlock))
                throw new AssertionError("The key should raise a new entry once cooled down");
            if (!minecraft.overworld().getBlockState(firstEntry).isAir() || !minecraft.overworld().getBlockState(firstEntry.above()).isAir())
                throw new AssertionError("The first entry should collapse when the key opens a new one, got " + minecraft.overworld().getBlockState(firstEntry));
            if (!RiftPairs.get(minecraft).entryOf(door).equals(java.util.Optional.of(GlobalPos.of(Level.OVERWORLD, entryAt(feet)))))
                throw new AssertionError("The rift should now pair the door with the new entry");
            if (!voidLevel(minecraft).getBlockState(door).getValue(DoorBlock.OPEN))
                throw new AssertionError("The pocket door should stay open while the rift moves");
        });
    }

    /**
     * Gone through the rift, the player shuts the pocket door behind them, which closes the rift. Its entry, now far from anyone, has collapsed when they come back.
     */
    private void checkShutDoor(ClientGameTestContext context, TestServerContext server)
    {
        var entry = entryAt(PLATFORM.offset(4, 0, 3));
        context.waitTicks(30);
        server.runCommand("execute in minecraft:overworld run tp @a " + (entry.getX() + 0.5) + " " + entry.getY() + " " + (entry.getZ() + 0.5) + " 180 0");
        context.waitTicks(10);
        world(context);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            if (player.level() != voidLevel(minecraft))
                throw new AssertionError("The moved rift should lead into the pocket");
            var level = voidLevel(minecraft);
            level.getBlockState(door).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(door), Direction.EAST, door, false));
        });
        context.waitTicks(10);
        server.runOnServer(minecraft ->
        {
            if (voidLevel(minecraft).getBlockState(door).getValue(DoorBlock.OPEN))
                throw new AssertionError("The pocket door should be shut");
            if (!RiftPairs.get(minecraft).isEmpty())
                throw new AssertionError("Shutting the pocket door should close the rift");
        });

        server.runCommand("execute in minecraft:overworld run tp @a " + (entry.getX() + 0.5) + " " + entry.getY() + " " + (entry.getZ() + 2.5) + " 180 0");
        context.waitTicks(40);
        world(context);
        server.runOnServer(minecraft ->
        {
            if (!minecraft.overworld().getBlockState(entry).isAir() || !minecraft.overworld().getBlockState(entry.above()).isAir())
                throw new AssertionError("Shutting the pocket door should collapse the entry, got " + minecraft.overworld().getBlockState(entry));
            if (heartData(minecraft).currentFlexionCount() != 1)
                throw new AssertionError("A closed rift should keep its door's flexion");
        });
    }

    /**
     * Breaking the pocket door frees its flexion and collapses its open entry, and the key then refuses.
     */
    private void checkBrokenDoor(ClientGameTestContext context, TestServerContext server)
    {
        var feet = PLATFORM.offset(-4, 0, 3);
        server.runCommand("execute in minecraft:overworld run tp @a " + (feet.getX() + 0.5) + " " + feet.getY() + " " + (feet.getZ() + 0.5) + " 180 0");
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(player.getMainHandItem()));
        });
        context.waitTicks(2);
        use(context);
        if (!server.computeOnServer(minecraft -> minecraft.overworld().getBlockState(entryAt(feet)).getBlock() instanceof RiftEntryBlock))
            throw new AssertionError("The key should open a rift again once its door was shut");

        server.runOnServer(minecraft -> voidLevel(minecraft).destroyBlock(door, true));
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            if (!minecraft.overworld().getBlockState(entryAt(feet)).isAir())
                throw new AssertionError("Breaking the pocket door should collapse its entry");
            if (heartData(minecraft).currentFlexionCount() != 0)
                throw new AssertionError("Breaking the pocket door should free its flexion, got " + heartData(minecraft).currentFlexionCount());
            var player = player(minecraft);
            player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(player.getMainHandItem()));
        });
        context.waitTicks(2);
        use(context);
        server.runOnServer(minecraft ->
        {
            if (!minecraft.overworld().getBlockState(entryAt(feet)).isAir())
                throw new AssertionError("A key whose door is gone should raise nothing");

            // A new door in the same spot, bound to a fresh key that opens a rift: the stale key must leave both alone
            var level = voidLevel(minecraft);
            var lower = VoidHeartBlocks.VOID_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            level.setBlockAndUpdate(door, lower);
            level.setBlockAndUpdate(door.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            var player = player(minecraft);
            var fresh = new ItemStack(VoidHeartItems.RIFT_KEY);
            RiftKeys.bind(player, fresh, level, door);
            if (!RiftKeys.open(player, fresh, fresh.get(VoidHeartDataComponents.RIFT_KEY_LINK)))
                throw new AssertionError("A fresh key on the replacement door should open a rift");
            player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(player.getMainHandItem()));
        });
        context.waitTicks(2);
        use(context);
        server.runOnServer(minecraft ->
        {
            if (!heartData(minecraft).holdsRiftDoor(door) || RiftPairs.get(minecraft).entryOf(door).isEmpty())
                throw new AssertionError("A stale key should leave the replacement door's flexion and rift alone");
            voidLevel(minecraft).destroyBlock(door, false);
        });
        context.waitTicks(5);
    }

    /**
     * Rebound to the second door, the key raises nothing inside the pockets. Ends with the key and its door shown in hand and in the inventory.
     */
    private void checkInPocket(ClientGameTestContext context, TestServerContext server)
    {
        var second = door.south(2);
        server.runOnServer(minecraft -> player(minecraft).getInventory().setItem(0, new ItemStack(VoidHeartItems.RIFT_KEY)));
        server.runCommand("execute in voidheart:void run tp @a " + (second.getX() - 2.5) + " " + second.getY() + " " + (second.getZ() + 0.5)
                + " facing " + (second.getX() + 0.9) + " " + (second.getY() + 0.5) + " " + (second.getZ() + 0.5));
        context.waitTicks(10);
        world(context);
        use(context);
        server.runOnServer(minecraft ->
        {
            if (!player(minecraft).getInventory().getItem(0).has(VoidHeartDataComponents.RIFT_KEY_LINK))
                throw new AssertionError("With its flexion freed, the heart should let a key bind to the other door");
        });

        server.runCommand("execute in voidheart:void run tp @a " + (second.getX() - 2.5) + " " + second.getY() + " " + (second.getZ() + 0.5) + " -90 0");
        context.waitTicks(5);
        use(context);
        server.runOnServer(minecraft ->
        {
            if (!RiftPairs.get(minecraft).isEmpty() || voidLevel(minecraft).getBlockState(second.west(2)).getBlock() instanceof RiftEntryBlock)
                throw new AssertionError("The key should open no rift inside the pockets");
        });

        context.waitTicks(10);
        context.takeScreenshot("rift-key-in-hand");
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitTicks(5);
        context.takeScreenshot("rift-key-inventory");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(2);
    }
}
