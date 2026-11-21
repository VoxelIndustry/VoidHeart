package net.voxelindustry.voidheart.common.content.crystal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;

/**
 * A crystal bundle that remembers how it grew, so a habit survives being placed, set in a pylon and broken again.
 */
public class CrystalBundleBlock extends AmethystClusterBlock
{
    public static final EnumProperty<CrystalHabit> HABIT = EnumProperty.create("habit", CrystalHabit.class);

    public CrystalBundleBlock(float height, float width, Properties properties)
    {
        super(height, width, properties);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context)
    {
        var state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(HABIT, CrystalBundles.habitOf(context.getItemInHand()));
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData)
    {
        return CrystalBundles.itemOf(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        super.createBlockStateDefinition(builder);
        builder.add(HABIT);
    }
}
