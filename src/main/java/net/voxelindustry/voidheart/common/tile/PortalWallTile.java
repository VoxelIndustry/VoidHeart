package net.voxelindustry.voidheart.common.tile;

import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

public class PortalWallTile extends BlockEntity
{
    private final List<BlockPos> adjacentPos = new ArrayList<>();

    private List<BlockPos> linkedCores = new ArrayList<>();

    @Getter
    private boolean isCore;

    private Identifier         linkedWorld;
    private BlockPos           linkedPos;
    private Direction          linkedFacing;
    private RegistryKey<World> linkedWorldKey;

    public PortalWallTile()
    {
        super(VoidHeartTiles.PORTAL_WALL);
    }

    public void tryForm(Direction direction)
    {
        Pair<BlockPos, BlockPos> portalPoints = PortalFormer.tryFloodFill(
                getPos(),
                16,
                pos -> canUseBlock(pos, getWorld().getBlockState(pos)),
                pos -> getWorld().isAir(pos),
                direction,
                getAdjacentDirection(direction));

        System.out.println(portalPoints.getLeft() + " << >> " + portalPoints.getRight());

        BlockPos.stream(portalPoints.getLeft(), portalPoints.getRight()).forEach(pos -> world.setBlockState(pos, Blocks.LAPIS_BLOCK.getDefaultState()));
    }

    private Direction[] getAdjacentDirection(Direction facing)
    {
        if (facing.getAxis() == Axis.X)
            return new Direction[]{Direction.NORTH, Direction.UP, Direction.SOUTH, Direction.DOWN};
        else if (facing.getAxis() == Axis.Z)
            return new Direction[]{Direction.WEST, Direction.UP, Direction.EAST, Direction.DOWN};
        return new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    }

    private boolean canUseBlock(BlockPos pos, BlockState state)
    {
        if (pos.equals(getPos()))
            return true;

        if (state.getBlock() != VoidHeartBlocks.PORTAL_WALL)
            return false;

        PortalWallTile tile = (PortalWallTile) getWorld().getBlockEntity(pos);

        return tile != null && !tile.isCore();
    }

    public boolean isCore()
    {
        return isCore;
    }

    @Override
    public void fromTag(BlockState state, CompoundTag tag)
    {
        super.fromTag(state, tag);

        int adjacentCount = tag.getInt("adjacentCount");
        for (int index = 0; index < adjacentCount; index++)
        {
            adjacentPos.add(BlockPos.fromLong(tag.getLong("adjacent" + index)));
        }
    }

    @Override
    public CompoundTag toTag(CompoundTag tag)
    {
        tag.putInt("adjacentPos", adjacentPos.size());

        int index = 0;
        for (BlockPos adjacent : adjacentPos)
        {
            tag.putLong("adjacent" + index, adjacent.asLong());
            index++;
        }

        return super.toTag(tag);
    }
}
