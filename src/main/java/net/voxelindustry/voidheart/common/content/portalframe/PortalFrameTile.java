package net.voxelindustry.voidheart.common.content.portalframe;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.voxelindustry.steamlayer.tile.ILoadable;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import org.jetbrains.annotations.Nullable;

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
     *
     * @param eventSource the frame that was removed, or null when this tile itself is removed
     */
    public void breakTile(@Nullable BlockPos eventSource)
    {
        if (level == null || level.isClientSide())
            return;

        setChanged();

        for (BlockPos pos : List.copyOf(linkedCores))
        {
            if (pos.equals(eventSource))
                continue;

            getLevel().getBlockEntity(pos, VoidHeartTiles.PORTAL_FRAME_CORE).ifPresent(core -> core.removeFrame(getBlockPos()));
        }
        linkedCores.clear();
    }

    void addCore(PortalFrameTile core)
    {
        if (!linkedCores.contains(core.getBlockPos()))
            linkedCores.add(core.getBlockPos().immutable());
        setChanged();
    }

    void removeCore(PortalFrameTile core)
    {
        linkedCores.remove(core.getBlockPos());
        setChanged();
    }

    /**
     * Drop references to cores that do not exist anymore or do not list this frame
     *
     * @return true if this frame still belongs to at least one portal
     */
    boolean pruneLinkedCores()
    {
        boolean changed = linkedCores.removeIf(pos -> getLevel().getBlockEntity(pos, VoidHeartTiles.PORTAL_FRAME_CORE)
                .map(core -> !core.getLinkedFrames().contains(getBlockPos()))
                .orElse(true));
        if (changed)
            setChanged();
        return !linkedCores.isEmpty();
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
        var existingState = level.getBlockState(getBlockPos());

        if (existingState.getBlock() != VoidHeartBlocks.PORTAL_FRAME)
            return;

        var isAnyCoreLit = linkedCores.stream().anyMatch(this::isCoreLit);
        if (existingState.getValue(BlockStateProperties.LIT) != isAnyCoreLit)
            level.setBlockAndUpdate(getBlockPos(), existingState.setValue(BlockStateProperties.LIT, isAnyCoreLit));
    }

    private boolean isCoreLit(BlockPos corePos)
    {
        var coreState = level.getBlockState(corePos);

        if (coreState.getBlock() != VoidHeartBlocks.PORTAL_FRAME_CORE)
            return false;
        return coreState.getValue(BlockStateProperties.LIT);
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);

        linkedCores.clear();
        var count = tag.getIntOr("linkedCoreCount", 0);
        for (int index = 0; index < count; index++)
        {
            var core = tag.getLong("linkedCore" + index).map(BlockPos::of);
            if (core.isPresent() && !linkedCores.contains(core.get()))
                linkedCores.add(core.get());
        }
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        tag.putInt("linkedCoreCount", linkedCores.size());

        var index = 0;
        for (BlockPos core : linkedCores)
        {
            tag.putLong("linkedCore" + index, core.asLong());
            index++;
        }

        super.saveAdditional(tag);
    }

    public boolean isCore()
    {
        return false;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        breakTile(null);
        super.preRemoveSideEffects(pos, state);
    }
}
