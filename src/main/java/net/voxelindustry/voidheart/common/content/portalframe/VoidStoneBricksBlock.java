package net.voxelindustry.voidheart.common.content.portalframe;

import net.voxelindustry.voidheart.common.item.PortalCoreItem;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.VoidPocketState;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class VoidStoneBricksBlock extends Block
{
    public VoidStoneBricksBlock(Properties settings)
    {
        super(settings);
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() == VoidHeartItems.PORTAL_CORE)
        {
            if (world.isClientSide())
                return InteractionResult.SUCCESS;

            var voidPocketState = VoidPocketState.getVoidPocketState(world);

            if (!voidPocketState.hasPocket(player.getUUID()))
            {
                player.displayClientMessage(Component.translatable(MODID + ".no_pocket_for_player"), true);
                return InteractionResult.PASS;
            }
            if (voidPocketState.getHeartData(player.getUUID()).noFlexionLeft())
            {
                player.displayClientMessage(Component.translatable(MODID + ".no_flexion_left"), true);
                return InteractionResult.PASS;
            }

            boolean isInPocket = PortalFormer.isInPocket(world, pos, player.getUUID());
            if (!PortalCoreItem.checkPearlUseHereAndWarn(stack, isInPocket, player))
                return InteractionResult.PASS;

            if (PortalLinker.voidIronEyeInteract(null, world, pos, hit.getDirection(), player, stack))
            {
                PortalCoreItem.sendSuccessMessage(player, stack, false);
                return InteractionResult.SUCCESS;
            }
        }

        return super.use(state, world, pos, player, hand, hit);
    }
}
