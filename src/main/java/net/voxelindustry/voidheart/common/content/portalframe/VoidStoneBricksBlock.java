package net.voxelindustry.voidheart.common.content.portalframe;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

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
        if (stack.getItem() == VoidHeartItems.VOID_PEARL)
        {
            if (world.isClient())
                return ActionResult.SUCCESS;

            boolean isInPocket = PortalFormer.isInPocket(world, pos, player.getUuid());
            if (!PortalFormer.canUsePearlHere(stack, isInPocket))
            {
                player.sendMessage(new TranslatableText(MODID + ".must_be_inside_outside"), true);
                return ActionResult.PASS;
            }

            if (PortalLinker.voidPieceInteract(null, world, pos, hit.getSide(), player, stack, isInPocket))
                return ActionResult.SUCCESS;
        }

        return super.onUse(state, world, pos, player, hand, hit);
    }
}
