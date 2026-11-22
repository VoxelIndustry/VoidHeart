package net.voxelindustry.voidheart.common.content.hoard;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Takes one rubrum bundle, set in with it in hand and taken out with an empty hand while sneaking.
 */
public class HoardCofferBlock extends Block implements EntityBlock
{
    public HoardCofferBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (stack.isEmpty())
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level.getBlockEntity(pos) instanceof HoardCofferTile coffer) || !coffer.getBundle().isEmpty())
            return InteractionResult.PASS;
        if (!HoardCofferTile.fits(stack))
        {
            if (!level.isClientSide())
                player.sendOverlayMessage(Component.translatable(MODID + ".hoard_coffer.rubrum_only"));
            return InteractionResult.PASS;
        }

        if (!level.isClientSide())
        {
            coffer.setBundle(stack.consumeAndReturn(1, player));
            if (coffer.pocketOwner().isEmpty())
                player.sendOverlayMessage(Component.translatable(MODID + ".hoard_coffer.outside"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!player.isShiftKeyDown() || !(level.getBlockEntity(pos) instanceof HoardCofferTile coffer) || coffer.getBundle().isEmpty())
            return InteractionResult.PASS;

        if (!level.isClientSide())
        {
            player.getInventory().placeItemBackInInventory(coffer.getBundle(), Prediction.SERVER_ONLY);
            coffer.setBundle(ItemStack.EMPTY);
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new HoardCofferTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide() ? null : createTickerHelper(type, HoardContent.HOARD_COFFER_TILE, HoardCofferTile::tick);
    }
}
