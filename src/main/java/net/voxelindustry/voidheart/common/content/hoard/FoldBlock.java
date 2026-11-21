package net.voxelindustry.voidheart.common.content.hoard;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Shown an item, the Fold gives that item back as well as taking anything. Sneaking with an empty hand makes it only take again, and an empty hand
 * opens the hoard it reaches.
 */
public class FoldBlock extends Block implements EntityBlock
{
    public FoldBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player && level.getBlockEntity(pos) instanceof FoldTile fold)
            fold.setOwner(player.getUUID());
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (stack.isEmpty() || player.isShiftKeyDown())
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level.getBlockEntity(pos) instanceof FoldTile fold))
            return InteractionResult.PASS;

        if (!level.isClientSide())
        {
            fold.attune(ItemVariant.of(stack));
            player.sendOverlayMessage(Component.translatable(MODID + ".fold.attuned", stack.getHoverName()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof FoldTile fold))
            return InteractionResult.PASS;

        if (player.isShiftKeyDown())
        {
            if (!level.isClientSide())
            {
                fold.attune(null);
                player.sendOverlayMessage(Component.translatable(MODID + ".fold.only_takes"));
            }
            return InteractionResult.SUCCESS;
        }

        if (player instanceof ServerPlayer serverPlayer)
            HoardMenu.open(serverPlayer, fold.getOwner(), pos);
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new FoldTile(pos, state);
    }
}
