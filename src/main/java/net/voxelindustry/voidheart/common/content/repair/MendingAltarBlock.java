package net.voxelindustry.voidheart.common.content.repair;

import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

public class MendingAltarBlock extends Block implements EntityBlock
{
    public MendingAltarBlock(Properties settings)
    {
        super(settings);

        this.registerDefaultState(getStateDefinition().any()
                .setValue(BlockStateProperties.LIT, false));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack heldStack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!player.getItemInHand(hand).isDamageableItem())
            return InteractionResult.PASS;

        var altarOpt = world.getBlockEntity(pos, VoidHeartTiles.MENDING_ALTAR);

        if (altarOpt.isEmpty())
            return InteractionResult.SUCCESS;

        var altar = altarOpt.get();
        if (altar.getTool().isEmpty())
        {
            altar.setTool(player, player.getItemInHand(hand).copyWithCount(1));

            if (!player.isCreative())
                player.getItemInHand(hand).shrink(1);
        }
        else
        {
            player.getInventory().placeItemBackInInventory(altar.getTool());
            altar.setTool(player, ItemStack.EMPTY);
        }

        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new MendingAltarTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type)
    {
        return createTickerHelper(type, VoidHeartTiles.MENDING_ALTAR, MendingAltarTile::tick);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(BlockStateProperties.LIT);
    }
}
