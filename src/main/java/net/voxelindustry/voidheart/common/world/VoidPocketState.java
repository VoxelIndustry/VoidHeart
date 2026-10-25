package net.voxelindustry.voidheart.common.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.heart.VoidHeartTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.world.pocket.VoidHeartData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidPocketState extends SavedData
{
    private final Map<UUID, VoidHeartData> heartDataByUUID = new HashMap<>();

    public VoidPocketState()
    {
    }

    public VoidPocketState(CompoundTag tag)
    {
        readNbt(tag);
    }

    @Override
    public boolean isDirty()
    {
        for (var heartData : heartDataByUUID.values())
        {
            if (heartData.isDirty())
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

        MutableBlockPos currentPos = new MutableBlockPos(0, 64, 0);

        var pocketPositions = heartDataByUUID.values().stream().map(VoidHeartData::pocketPos).toList();
        while (pocketPositions.contains(currentPos))
        {
            if (currentIndex == segmentLength)
            {
                startDir = startDir.getCounterClockWise();
                segmentIndex++;

                if (segmentIndex % 2 == 0)
                    segmentLength++;

                currentIndex = 0;
            }
            currentPos.move(startDir, 256);
            currentIndex++;
        }

        setDirty();
        return currentPos.immutable();
    }

    public BlockPos getPosForPlayer(UUID uuid)
    {
        return heartDataByUUID.computeIfAbsent(uuid, id -> VoidHeartData.create(getNextAvailable())).pocketPos();
    }

    public VoidHeartData getHeartData(UUID uuid)
    {
        return heartDataByUUID.get(uuid);
    }

    public void readNbt(CompoundTag tag)
    {
        var heartsData = tag.getCompound("hearts");

        for (var playerUUID : heartsData.getAllKeys())
            this.heartDataByUUID.put(UUID.fromString(playerUUID), VoidHeartData.fromNbt(heartsData.getCompound(playerUUID)));
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        var heartsTag = new CompoundTag();

        for (var heartDataByPlayerUUID : this.heartDataByUUID.entrySet())
        {
            heartsTag.put(heartDataByPlayerUUID.getKey().toString(), heartDataByPlayerUUID.getValue().toNbt());
        }

        tag.put("hearts", heartsTag);
        return tag;
    }

    public static VoidPocketState getVoidPocketState(Level world)
    {
        ServerLevel serverWorld;

        if (!world.dimension().equals(VoidHeart.VOID_WORLD_KEY))
            serverWorld = world.getServer().getLevel(VoidHeart.VOID_WORLD_KEY);
        else if (world instanceof ServerLevel s)
            serverWorld = s;
        else
            throw new UnsupportedOperationException("Cannot access VoidPocketState from a ClientWorld");

        return getOrCreateWithDeprecatedId(serverWorld);
    }

    private static VoidPocketState getOrCreateWithDeprecatedId(ServerLevel serverWorld)
    {
        var existingDeprecated = serverWorld.getDataStorage().get(VoidPocketState::new, MODID + ":pocket_storage");
        if (existingDeprecated != null)
            return existingDeprecated;

        return serverWorld.getDataStorage().computeIfAbsent(VoidPocketState::new, VoidPocketState::new, MODID + "_pocket_storage");
    }

    public void createPocket(ServerLevel voidWorld, UUID player)
    {
        BlockPos pos = getPosForPlayer(player);
        setDirty();

        voidWorld.setBlockAndUpdate(pos, VoidHeartBlocks.VOID_HEART.defaultBlockState());
        VoidHeartTile voidHeart = (VoidHeartTile) voidWorld.getBlockEntity(pos);
        voidHeart.setPlayerID(player);
        voidHeart.setChanged();

        placeBlockEmptyVolume(voidWorld, pos.offset(0, 6, 0), 16, 16, 16, VoidHeartBlocks.VOIDSTONE.defaultBlockState());
        placeBlockEmptyVolume(voidWorld, pos.offset(0, 6, 0), 18, 18, 18, VoidHeartBlocks.POCKET_WALL.defaultBlockState());
    }

    private void placeBlockEmptyVolume(ServerLevel voidWorld, BlockPos center, int width, int height, int length, BlockState state)
    {
        placeBlockArea(voidWorld, center.offset(-width / 2, -height / 2, -length / 2), center.offset(width / 2, -height / 2, length / 2), state);
        placeBlockArea(voidWorld, center.offset(-width / 2, height / 2, -length / 2), center.offset(width / 2, height / 2, length / 2), state);

        placeBlockArea(voidWorld, center.offset(-width / 2, -height / 2 + 1, -length / 2), center.offset(-width / 2, height / 2 - 1, length / 2), state);
        placeBlockArea(voidWorld, center.offset(width / 2, -height / 2 + 1, -length / 2), center.offset(width / 2, height / 2 - 1, length / 2), state);

        placeBlockArea(voidWorld, center.offset(-width / 2 + 1, -height / 2 + 1, -length / 2), center.offset(width / 2 - 1, height / 2 - 1, -length / 2), state);
        placeBlockArea(voidWorld, center.offset(-width / 2 + 1, -height / 2 + 1, length / 2), center.offset(width / 2 - 1, height / 2 - 1, length / 2), state);
    }

    private void placeBlockArea(Level world, BlockPos from, BlockPos to, BlockState state)
    {
        BlockPos.betweenClosed(from, to).forEach(pos -> world.setBlockAndUpdate(pos, state));
    }

    public boolean hasPocket(UUID player)
    {
        return heartDataByUUID.containsKey(player);
    }
}
