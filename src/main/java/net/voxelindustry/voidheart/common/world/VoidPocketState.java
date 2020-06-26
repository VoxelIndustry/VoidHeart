package net.voxelindustry.voidheart.common.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Direction;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidPocketState extends PersistentState
{
    private final Map<UUID, BlockPos> posByPlayerID = new HashMap<>();

    public VoidPocketState()
    {
        super(MODID + ":pocket_storage");
    }

    public BlockPos getNextAvailable()
    {
        Direction startDir = Direction.EAST;
        int segmentLength = 1;
        int segmentIndex = 0;
        int currentIndex = 0;

        Mutable currentPos = new Mutable(0, 64, 0);

        while (posByPlayerID.containsValue(currentPos))
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
        return posByPlayerID.computeIfAbsent(uuid, id -> getNextAvailable());
    }

    @Override
    public void fromTag(CompoundTag tag)
    {
        int count = tag.getInt("count");

        for (int index = 0; index < count; index++)
        {
            posByPlayerID.put(tag.getUuid("uuid" + index), BlockPos.fromLong(tag.getLong("pos" + index)));
        }
    }

    @Override
    public CompoundTag toTag(CompoundTag tag)
    {
        tag.putInt("count", posByPlayerID.size());

        int count = 0;
        for (Entry<UUID, BlockPos> posByID : posByPlayerID.entrySet())
        {
            tag.putUuid("uuid" + count, posByID.getKey());
            tag.putLong("pos" + count, posByID.getValue().asLong());
            count++;
        }

        return tag;
    }

    public static VoidPocketState getVoidPocketState(ServerWorld world)
    {
        return world.getPersistentStateManager().getOrCreate(VoidPocketState::new, MODID + ":pocket_storage");
    }

    public void createPocket(ServerWorld voidWorld, UUID player)
    {
        BlockPos pos = getPosForPlayer(player);
        markDirty();

        voidWorld.setBlockState(pos, Blocks.END_STONE.getDefaultState());

        placeBlockEmptyVolume(voidWorld, pos.add(0, 6, 0), 16, 16, 16, Blocks.GLASS.getDefaultState());
        placeBlockEmptyVolume(voidWorld, pos.add(0, 6, 0), 18, 18, 18, Blocks.BEDROCK.getDefaultState());
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
}
