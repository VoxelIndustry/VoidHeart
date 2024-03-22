package net.voxelindustry.voidheart.common.content.portalframe;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.common.item.PortalCoreItem;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.VoidPocketState;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidStoneBricksBlock extends Block
{
    public VoidStoneBricksBlock(Settings settings)
    {
        super(settings);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit)
    {
        ItemStack stack = player.getStackInHand(hand);
        if (stack.getItem() == VoidHeartItems.PORTAL_CORE)
        {
            if (world.isClient())
                return ActionResult.SUCCESS;

            var voidPocketState = VoidPocketState.getVoidPocketState(world);

            if (!voidPocketState.hasPocket(player.getUuid()))
            {
                player.sendMessage(Text.translatable(MODID + ".no_pocket_for_player"), true);
                return ActionResult.PASS;
            }
            if (voidPocketState.getHeartData(player.getUuid()).noFlexionLeft())
            {
                player.sendMessage(Text.translatable(MODID + ".no_flexion_left"), true);
                return ActionResult.PASS;
            }

            boolean isInPocket = PortalFormer.isInPocket(world, pos, player.getUuid());
            if (!PortalCoreItem.checkPearlUseHereAndWarn(stack, isInPocket, player))
                return ActionResult.PASS;

            if (PortalLinker.voidIronEyeInteract(null, world, pos, hit.getSide(), player, stack))
            {
                PortalCoreItem.sendSuccessMessage(player, stack, false);
                return ActionResult.SUCCESS;
            }
        }

        return super.onUse(state, world, pos, player, hand, hit);
    }
}
