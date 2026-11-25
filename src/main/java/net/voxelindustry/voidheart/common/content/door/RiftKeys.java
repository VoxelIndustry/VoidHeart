package net.voxelindustry.voidheart.common.content.door;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents.RiftKeyLink;
import net.voxelindustry.voidheart.common.world.VoidPocketState;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTeleportLock;

import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Binding Rift Keys to their owner's pocket doors, opening rifts out of thin air and closing them.
 * A rift closes when its pocket door is shut or broken, when its entry is gone, or when its key opens a new one.
 */
public final class RiftKeys
{
    public static final int COOLDOWN_TICKS = 600;
    private static final int CHECK_TICKS = 5;
    private static final float OPEN_VOLUME = 1;
    private static final float OPEN_PITCH = 0.6F;

    private RiftKeys()
    {
    }

    /**
     * Binds a blank key to a void door of the player's own pocket, which takes one of their flexions unless another key already opens onto that door.
     */
    public static void bind(ServerPlayer player, ItemStack key, ServerLevel voidLevel, BlockPos door)
    {
        var pocketState = VoidPocketState.getVoidPocketState(voidLevel);
        var owner = pocketState.ownerAt(door);
        if (owner.isEmpty() || !owner.get().equals(player.getUUID()) || !(voidLevel.getBlockEntity(door) instanceof VoidDoorTile tile))
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".rift_key.not_your_pocket"));
            return;
        }

        var heartData = pocketState.getHeartData(player.getUUID());
        if (!heartData.holdsRiftDoor(door))
        {
            if (heartData.noFlexionLeft())
            {
                player.sendOverlayMessage(Component.translatable(MODID + ".no_flexion_left"));
                return;
            }
            heartData.addRiftDoor(door);
        }

        key.set(VoidHeartDataComponents.RIFT_KEY_LINK, new RiftKeyLink(door.immutable(), tile.getId(), player.getUUID(), player.getGameProfile().name()));
        voidLevel.playSound(null, door, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, OPEN_VOLUME, OPEN_PITCH);
        player.sendOverlayMessage(Component.translatable(MODID + ".rift_key.bound"));
    }

    /**
     * Raises an entry in front of the player onto the key's pocket door, closing the rift the key had open.
     *
     * @return whether a rift was opened, which starts the key's cooldown
     */
    public static boolean open(ServerPlayer player, ItemStack key, RiftKeyLink link)
    {
        var server = player.level().getServer();
        var level = player.level();
        var voidLevel = server.getLevel(VoidHeart.VOID_WORLD_KEY);

        if (level == voidLevel)
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".rift_key.in_pocket"));
            return false;
        }
        if (DungeonTeleportLock.refuse(player))
            return false;

        var heartData = VoidPocketState.getVoidPocketState(voidLevel).getHeartData(link.owner());
        var door = link.doorPos();
        var tile = voidLevel.getBlockEntity(door) instanceof VoidDoorTile doorTile ? doorTile : null;
        if (tile == null)
        {
            // The door went without its tile noticing, its flexion is free again
            if (heartData != null)
                heartData.removeRiftDoor(door);
            close(server, door, false);
        }
        // A door placed since in the same spot is another door: a stale key leaves its flexion and rift alone
        if (tile == null || heartData == null || !heartData.holdsRiftDoor(door) || !tile.getId().equals(link.doorId()))
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".rift_key.door_missing"));
            return false;
        }

        var facing = player.getDirection();
        var entry = player.blockPosition().relative(facing);
        var pairs = RiftPairs.get(server);
        var previous = pairs.entryOf(door).filter(previousEntry -> previousEntry.dimension().equals(level.dimension())).map(GlobalPos::pos);
        if (!hasRoom(level, entry, previous.orElse(null)))
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".rift_key.no_room"));
            return false;
        }

        // The rift the key had open closes first, its entry may stand where the new one goes
        pairs.close(door).ifPresent(previousEntry -> collapseEntry(server, previousEntry));

        var lower = RiftEntryBlock.lower(VoidHeartBlocks.RIFT_ENTRY, facing);
        level.setBlock(entry, lower, Block.UPDATE_ALL);
        level.setBlock(entry.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        pairs.open(door, GlobalPos.of(level.dimension(), entry));

        var doorState = voidLevel.getBlockState(door);
        if (doorState.getBlock() instanceof VoidDoorBlock voidDoor)
            voidDoor.setOpen(null, voidLevel, doorState, door, true);

        level.playSound(null, entry, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, OPEN_VOLUME, OPEN_PITCH);
        return true;
    }

    /**
     * @param previous lower half of the entry the key has open in this level, whose place counts as free since it collapses first
     */
    private static boolean hasRoom(ServerLevel level, BlockPos entry, @Nullable BlockPos previous)
    {
        if (!level.isInWorldBounds(entry) || !level.isInWorldBounds(entry.above()))
            return false;

        for (var pos : new BlockPos[]{entry, entry.above()})
        {
            if (previous != null && (pos.equals(previous) || pos.equals(previous.above())))
                continue;

            var state = level.getBlockState(pos);
            if (!state.canBeReplaced() || !state.getFluidState().isEmpty())
                return false;
        }
        return true;
    }

    /**
     * Closes the rift of a pocket door, if it has one, collapsing its entry.
     *
     * @param shutDoor also shut the pocket door, when the rift closes from the entry's side
     */
    public static void close(MinecraftServer server, BlockPos door, boolean shutDoor)
    {
        RiftPairs.get(server).close(door).ifPresent(entry -> collapseEntry(server, entry));
        if (!shutDoor)
            return;

        var voidLevel = server.getLevel(VoidHeart.VOID_WORLD_KEY);
        if (!voidLevel.isLoaded(door))
            return;
        var doorState = voidLevel.getBlockState(door);
        if (doorState.getBlock() instanceof VoidDoorBlock voidDoor)
            voidDoor.setOpen(null, voidLevel, doorState, door, false);
    }

    /**
     * An entry in an unloaded chunk is left for its own check to collapse once it loads.
     */
    private static void collapseEntry(MinecraftServer server, GlobalPos entry)
    {
        var level = server.getLevel(entry.dimension());
        if (level != null && level.isLoaded(entry.pos()))
            RiftEntryBlock.collapse(level, entry.pos());
    }

    /**
     * Collapses the rifts whose pocket door was shut or broken, and shuts the doors whose entry is gone.
     */
    public static void tickServer(MinecraftServer server)
    {
        if (server.getTickCount() % CHECK_TICKS != 0)
            return;

        var voidLevel = server.getLevel(VoidHeart.VOID_WORLD_KEY);
        if (voidLevel == null)
            return;

        var pairs = RiftPairs.get(server);
        if (pairs.isEmpty())
            return;

        for (var pair : pairs.pairs())
        {
            if (voidLevel.isLoaded(pair.door()))
            {
                var doorState = voidLevel.getBlockState(pair.door());
                if (!(doorState.getBlock() instanceof VoidDoorBlock) || !doorState.getValue(DoorBlock.OPEN))
                {
                    close(server, pair.door(), false);
                    continue;
                }
            }

            var entryLevel = server.getLevel(pair.entry().dimension());
            if (entryLevel == null)
                close(server, pair.door(), true);
            else if (entryLevel.isLoaded(pair.entry().pos()) && !isEntry(entryLevel, pair.entry().pos()))
                close(server, pair.door(), true);
        }
    }

    public static boolean isEntry(ServerLevel level, BlockPos pos)
    {
        var state = level.getBlockState(pos);
        return state.getBlock() instanceof RiftEntryBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    /**
     * A pocket void door was removed: its flexion is free again and its rift collapses.
     */
    public static void doorRemoved(ServerLevel voidLevel, BlockPos door)
    {
        var pocketState = VoidPocketState.getVoidPocketState(voidLevel);
        pocketState.ownerAt(door).map(pocketState::getHeartData).ifPresent(heartData -> heartData.removeRiftDoor(door));
        close(voidLevel.getServer(), door, false);
    }
}
