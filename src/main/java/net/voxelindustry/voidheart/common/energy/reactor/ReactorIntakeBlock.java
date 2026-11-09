package net.voxelindustry.voidheart.common.energy.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One of the two intakes at the ends of a reactor, where relays deliver its fuel. Broken, it keeps its fuel on the dropped item.
 */
public class ReactorIntakeBlock extends Block implements EntityBlock
{
    public ReactorIntakeBlock(Properties properties)
    {
        super(properties);
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
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);
        var carried = stack.get(VoidHeartDataComponents.ESSENCE_STORAGE);
        if (carried != null && level.getBlockEntity(pos) instanceof ReactorIntakeTile intake)
            intake.restore(carried);
    }

    /**
     * Keeps the fuel on the item. An empty intake drops a plain item so it stacks.
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder)
    {
        var stack = new ItemStack(this);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof ReactorIntakeTile intake && intake.amount() > 0)
            stack.set(VoidHeartDataComponents.ESSENCE_STORAGE, intake.tank().copy());
        return List.of(stack);
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new ReactorIntakeTile(pos, state);
    }
}
