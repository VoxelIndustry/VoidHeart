package net.voxelindustry.voidheart.common.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContext.Builder;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.List;

import static java.util.Collections.singletonList;
import static net.voxelindustry.voidheart.common.block.StateProperties.DOWN;
import static net.voxelindustry.voidheart.common.block.StateProperties.UP;

public class VoidMonolithBlock extends Block
{
    public VoidMonolithBlock()
    {
        super(Settings.of(Material.STONE)
                .strength(3F)
                .sounds(BlockSoundGroup.STONE));

        setDefaultState(getStateManager().getDefaultState()
                .with(UP, false)
                .with(DOWN, false)
                .with(Properties.LIT, false));
    }

    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, Builder builder)
    {
        return singletonList(new ItemStack(VoidHeartBlocks.VOIDSTONE));
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState newState, WorldAccess world, BlockPos pos, BlockPos posFrom)
    {
        switch (direction)
        {
            case DOWN:
                Boolean down = state.get(DOWN);
                if (down && !newState.isOf(VoidHeartBlocks.VOID_MONOLITH))
                    state = state.with(DOWN, false);
                else if (!down && newState.isOf(VoidHeartBlocks.VOID_MONOLITH))
                    state = state.with(DOWN, true);
                break;
            case UP:
                Boolean up = state.get(UP);
                if (up && !newState.isOf(VoidHeartBlocks.VOID_MONOLITH))
                    state = state.with(UP, false);
                else if (!up && newState.isOf(VoidHeartBlocks.VOID_MONOLITH))
                    state = state.with(UP, true);
                break;
        }
        return state;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(UP, DOWN, Properties.LIT);
    }
}
