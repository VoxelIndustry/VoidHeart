package net.voxelindustry.voidheart.common.content.portalframe;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;
import net.voxelindustry.steamlayer.tile.ILoadable;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.ArrayList;
import java.util.List;

@Log4j2
public class PortalFrameTile extends TileBase implements ILoadable
{
    @Getter
    private final List<BlockPos> linkedCores = new ArrayList<>();

    public PortalFrameTile(BlockPos pos, BlockState state)
    {
        this(VoidHeartTiles.PORTAL_FRAME, pos, state);
    }

    public PortalFrameTile(BlockEntityType<?> type, BlockPos pos, BlockState state)
    {
        super(type, pos, state);
    }

    /**
     * Cut link and notify potential linked portal
     */
    public void breakTile(BlockPos eventSource)
    {
        markDirty();

        linkedCores.forEach(pos ->
        {
            if (pos.equals(eventSource))
                return;

            var coreFrameOpt = getWorld().getBlockEntity(pos, VoidHeartTiles.PORTAL_FRAME_CORE);
            if (!coreFrameOpt.isEmpty())
                coreFrameOpt.get().removeFrame(getPos());
        });
    }

    void addCore(PortalFrameTile wall)
    {
        linkedCores.add(wall.getPos());
        markDirty();
    }

    void removeCore(PortalFrameTile wall)
    {
        linkedCores.remove(wall.getPos());
        markDirty();
    }

    public static Direction[] getAdjacentDirection(Direction facing)
    {
        if (facing.getAxis() == Axis.X)
            return new Direction[]{Direction.NORTH, Direction.UP, Direction.SOUTH, Direction.DOWN};
        else if (facing.getAxis() == Axis.Z)
            return new Direction[]{Direction.WEST, Direction.UP, Direction.EAST, Direction.DOWN};
        return new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    }

    public void refreshLitStatus()
    {
        var existingState = world.getBlockState(getPos());

        if (existingState.getBlock() != VoidHeartBlocks.PORTAL_FRAME)
            return;

        if (linkedCores.isEmpty())
            world.setBlockState(getPos(), existingState.with(Properties.LIT, false));

        var isAnyCoreLit = linkedCores.stream().anyMatch(this::isCoreLit);
        world.setBlockState(getPos(), existingState.with(Properties.LIT, isAnyCoreLit));
    }

    private boolean isCoreLit(BlockPos corePos)
    {
        var coreState = world.getBlockState(corePos);

        if (coreState.getBlock() != VoidHeartBlocks.PORTAL_FRAME_CORE)
            return false;
        return coreState.get(Properties.LIT);
    }

    @Override
    public void readNbt(NbtCompound tag)
    {
        super.readNbt(tag);

        var count = tag.getInt("linkedCoreCount");
        for (int index = 0; index < count; index++)
            linkedCores.add(BlockPos.fromLong(tag.getLong("linkedCore" + index)));
    }

    @Override
    public void writeNbt(NbtCompound tag)
    {
        tag.putInt("linkedCoreCount", linkedCores.size());

        var index = 0;
        for (BlockPos core : linkedCores)
        {
            tag.putLong("linkedCore" + index, core.asLong());
            index++;
        }

        super.writeNbt(tag);
    }

    public boolean isCore()
    {
        return false;
    }
}
