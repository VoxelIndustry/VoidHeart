package net.voxelindustry.voidheart.common.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.block.enums.Instrument;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.List;

import static java.util.Collections.singletonList;
import static net.voxelindustry.voidheart.common.block.StateProperties.*;

public class VoidMonolithBlock extends Block
{
    public VoidMonolithBlock()
    {
        super(Settings.create()
                .mapColor(MapColor.BLACK)
                .instrument(Instrument.BASEDRUM)
                .requiresTool()
                .strength(3F)
                .sounds(BlockSoundGroup.STONE));

        setDefaultState(getStateManager().getDefaultState()
                .with(UP, false)
                .with(DOWN, false)
                .with(BROKEN, false)
                .with(Properties.LIT, false));
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved)
    {
        if (!state.isOf(newState.getBlock()) && state.get(BROKEN))
        {
            if (world.getRandom().nextInt(5) == 0)
                world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_ENDERMAN_SCREAM, SoundCategory.BLOCKS, 1, 1);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder)
    {
        if (state.get(BROKEN))
            return singletonList(new ItemStack(VoidHeartBlocks.VOIDSTONE));
        return singletonList(new ItemStack(VoidHeartBlocks.VOID_MONOLITH));
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState newState, WorldAccess world, BlockPos pos, BlockPos posFrom)
    {
        switch (direction)
        {
            case DOWN ->
            {
                Boolean down = state.get(DOWN);
                if (down && !newState.isOf(VoidHeartBlocks.VOID_MONOLITH))
                    state = state.with(DOWN, false);
                else if (!down && newState.isOf(VoidHeartBlocks.VOID_MONOLITH))
                    state = state.with(DOWN, true);
            }
            case UP ->
            {
                Boolean up = state.get(UP);
                if (up && !newState.isOf(VoidHeartBlocks.VOID_MONOLITH))
                    state = state.with(UP, false);
                else if (!up && newState.isOf(VoidHeartBlocks.VOID_MONOLITH))
                    state = state.with(UP, true);
            }
        }
        return state;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(UP, DOWN, Properties.LIT, BROKEN);
    }
}
