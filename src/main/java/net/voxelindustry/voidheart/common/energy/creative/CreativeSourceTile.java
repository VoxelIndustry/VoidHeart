package net.voxelindustry.voidheart.common.energy.creative;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.energy.EnergyFields;
import net.voxelindustry.voidheart.common.energy.FoundationBalance;

/**
 * Keeps its block's field in {@link CreativeSources} while the chunk is loaded. It saves nothing, since the field comes back with the block.
 */
public class CreativeSourceTile extends TileBase
{
    public CreativeSourceTile(BlockPos pos, BlockState state)
    {
        super(CreativeEnergyContent.CREATIVE_SOURCE, pos, state);
    }

    @Override
    public void setLevel(Level level)
    {
        super.setLevel(level);
        if (level instanceof ServerLevel serverLevel)
            join(serverLevel);
    }

    @Override
    public void clearRemoved()
    {
        super.clearRemoved();
        if (getLevel() instanceof ServerLevel serverLevel)
            join(serverLevel);
    }

    @Override
    public void setRemoved()
    {
        super.setRemoved();
        if (getLevel() instanceof ServerLevel serverLevel)
            fields(serverLevel).remove(getBlockPos());
    }

    private void join(ServerLevel level)
    {
        fields(level).add(getBlockPos(), radius());
    }

    private EnergyFields fields(ServerLevel level)
    {
        return kind() == CreativeSourceBlock.Kind.VOID_WELL ? CreativeSources.wells(level) : CreativeSources.stones(level);
    }

    private int radius()
    {
        return kind() == CreativeSourceBlock.Kind.VOID_WELL ? FoundationBalance.CREATIVE_WELL_RADIUS : FoundationBalance.CREATIVE_LEY_STONE_RADIUS;
    }

    private CreativeSourceBlock.Kind kind()
    {
        return getBlockState().getBlock() instanceof CreativeSourceBlock block ? block.kind() : CreativeSourceBlock.Kind.VOID_WELL;
    }
}
