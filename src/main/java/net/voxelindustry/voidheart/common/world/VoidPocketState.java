package net.voxelindustry.voidheart.common.world;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Direction;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.heart.VoidHeartTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.world.pocket.VoidHeartData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidPocketState extends PersistentState
{
    private final Map<UUID, VoidHeartData> heartDataByUUID = new HashMap<>();

    public VoidPocketState()
    {
    }

    public VoidPocketState(NbtCompound tag)
    {
        readNbt(tag);
    }

    @Override
    public boolean isDirty()
    {
        for (var heartData : heartDataByUUID.values())
        {
            if(heartData.isDirty())
                return true;
        }
        return false;
    }

    public BlockPos getNextAvailable()
    {
        Direction startDir = Direction.EAST;
        int segmentLength = 1;
        int segmentIndex = 0;
        int currentIndex = 0;

        Mutable currentPos = new Mutable(0, 64, 0);

        var pocketPositions = heartDataByUUID.values().stream().map(VoidHeartData::pocketPos).toList();
        while (pocketPositions.contains(currentPos))
        {
            if (currentIndex == segmentLength)
            {
                startDir = startDir.rotateYCounterclockwise();
                segmentIndex++;

                if (segmentIndex % 2 == 0)
                    segmentLength++;

                currentIndex = 0;
            }
            currentPos.move(startDir, 256);
            currentIndex++;
        }

        markDirty();
        return currentPos.toImmutable();
    }

    public BlockPos getPosForPlayer(UUID uuid)
    {
        return heartDataByUUID.computeIfAbsent(uuid, id -> VoidHeartData.create(getNextAvailable())).pocketPos();
    }

    public VoidHeartData getHeartData(UUID uuid)
    {
        return heartDataByUUID.get(uuid);
    }

    public void readNbt(NbtCompound tag)
    {
        var heartsData = tag.getCompound("hearts");

        for (var playerUUID : heartsData.getKeys())
            this.heartDataByUUID.put(UUID.fromString(playerUUID), VoidHeartData.fromNbt(heartsData.getCompound(playerUUID)));
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag)
    {
        var heartsTag = new NbtCompound();

        for (var heartDataByPlayerUUID : this.heartDataByUUID.entrySet())
        {
            heartsTag.put(heartDataByPlayerUUID.getKey().toString(), heartDataByPlayerUUID.getValue().toNbt());
        }

        tag.put("hearts", heartsTag);
        return tag;
    }

    public static VoidPocketState getVoidPocketState(World world)
    {
        ServerWorld serverWorld;

        if (!world.getRegistryKey().equals(VoidHeart.VOID_WORLD_KEY))
            serverWorld = world.getServer().getWorld(VoidHeart.VOID_WORLD_KEY);
        else if (world instanceof ServerWorld s)
            serverWorld = s;
        else
            throw new UnsupportedOperationException("Cannot access VoidPocketState from a ClientWorld");

        return serverWorld.getPersistentStateManager().getOrCreate(VoidPocketState::new, VoidPocketState::new, MODID + ":pocket_storage");
    }

    public void createPocket(ServerWorld voidWorld, UUID player)
    {
        BlockPos pos = getPosForPlayer(player);
        markDirty();

        voidWorld.setBlockState(pos, VoidHeartBlocks.VOID_HEART.getDefaultState());
        VoidHeartTile voidHeart = (VoidHeartTile) voidWorld.getBlockEntity(pos);
        voidHeart.setPlayerID(player);
        voidHeart.markDirty();

        placeBlockEmptyVolume(voidWorld, pos.add(0, 6, 0), 16, 16, 16, VoidHeartBlocks.VOIDSTONE.getDefaultState());
        placeBlockEmptyVolume(voidWorld, pos.add(0, 6, 0), 18, 18, 18, VoidHeartBlocks.POCKET_WALL.getDefaultState());
    }

    private void placeBlockEmptyVolume(ServerWorld voidWorld, BlockPos center, int width, int height, int length, BlockState state)
    {
        placeBlockArea(voidWorld, center.add(-width / 2, -height / 2, -length / 2), center.add(width / 2, -height / 2, length / 2), state);
        placeBlockArea(voidWorld, center.add(-width / 2, height / 2, -length / 2), center.add(width / 2, height / 2, length / 2), state);

        placeBlockArea(voidWorld, center.add(-width / 2, -height / 2 + 1, -length / 2), center.add(-width / 2, height / 2 - 1, length / 2), state);
        placeBlockArea(voidWorld, center.add(width / 2, -height / 2 + 1, -length / 2), center.add(width / 2, height / 2 - 1, length / 2), state);

        placeBlockArea(voidWorld, center.add(-width / 2 + 1, -height / 2 + 1, -length / 2), center.add(width / 2 - 1, height / 2 - 1, -length / 2), state);
        placeBlockArea(voidWorld, center.add(-width / 2 + 1, -height / 2 + 1, length / 2), center.add(width / 2 - 1, height / 2 - 1, length / 2), state);
    }

    private void placeBlockArea(World world, BlockPos from, BlockPos to, BlockState state)
    {
        BlockPos.iterate(from, to).forEach(pos -> world.setBlockState(pos, state));
    }

    public boolean hasPocket(UUID player)
    {
        return heartDataByUUID.containsKey(player);
    }
}
