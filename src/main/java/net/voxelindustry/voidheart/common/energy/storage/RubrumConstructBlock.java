package net.voxelindustry.voidheart.common.energy.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

/**
 * Core of a rubrum construct. Broken, it keeps its energy and its bundles on the dropped item.
 */
public class RubrumConstructBlock extends Block implements EntityBlock
{
    public RubrumConstructBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        var core = level.getBlockEntity(pos) instanceof RubrumConstructTile tile ? tile : null;
        var result = ConstructInteraction.useBundle(level, stack, player, hit.getLocation(), core);
        if (result == InteractionResult.PASS && stack.isEmpty())
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        return result;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof RubrumConstructTile core))
            return InteractionResult.PASS;

        var result = ConstructInteraction.useEmptyHand(level, player, hit.getLocation(), core);
        if (result != InteractionResult.PASS || player.isShiftKeyDown())
            return result;

        // An empty hand on a loose core shows where its frame goes
        if (!level.isClientSide())
            core.stepPreview(player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);
        var contents = stack.get(RubrumContent.CONSTRUCT_CONTENTS);
        if (contents != null && level.getBlockEntity(pos) instanceof RubrumConstructTile core)
            core.restore(contents);
    }

    /**
     * Keeps the energy and bundles on the item. An empty core drops a plain item so it stacks.
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder)
    {
        var stack = new ItemStack(this);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof RubrumConstructTile core)
        {
            var contents = core.contents();
            if (contents != null)
                stack.set(RubrumContent.CONSTRUCT_CONTENTS, contents);
        }
        return List.of(stack);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state)
    {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction)
    {
        if (!(level.getBlockEntity(pos) instanceof RubrumConstructTile core) || core.capacity() <= 0 || core.stored() <= 0)
            return 0;
        return Math.max(1, Math.round(core.charge().fill() * 15));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new RubrumConstructTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return createTickerHelper(type, RubrumContent.RUBRUM_CONSTRUCT, RubrumConstructTile::tick);
    }
}
