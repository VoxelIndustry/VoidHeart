package net.voxelindustry.voidheart.common.energy.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorLayout.Slit;

/**
 * A block of a reactor's shell: voidstone with cut ravenous gold bands. The core cuts a narrow slit through the four face centers once the shell stands
 * whole, which is all that shows of it. A slit block still holds as a whole one, so nothing reaches the core through it.
 */
public class ReactorContainmentBlock extends Block
{
    public static final EnumProperty<Slit> SLIT = EnumProperty.create("slit", Slit.class);

    public ReactorContainmentBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(SLIT, Slit.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(SLIT);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        return ReactorShellBlocks.useItemOn(stack);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        return ReactorShellBlocks.useWithoutItem(level, pos, player);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston)
    {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this))
            ReactorShellBlocks.changed(level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston)
    {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        ReactorShellBlocks.changed(level, pos);
    }
}
