package net.voxelindustry.voidheart.common.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.tile.PortalFormer;
import net.voxelindustry.voidheart.common.tile.PortalFrameTile;

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
        if (world.isClient())
            return ActionResult.SUCCESS;

        ItemStack stack = player.getStackInHand(hand);
        if (stack.getItem() == VoidHeartItems.VOID_HEART_PIECE)
        {
            CompoundTag tag = stack.getOrCreateTag();
            if (!tag.containsUuid("player"))
                return ActionResult.PASS;

            if (PortalFormer.tryForm(world, state, pos, hit.getSide()))
            {
                PortalFrameTile frame = (PortalFrameTile) world.getBlockEntity(pos);
                frame.voidPieceInteract(hit.getSide(), player, stack);
                return ActionResult.SUCCESS;
            }
            else
                player.sendMessage(new TranslatableText(MODID + ".portal_form_error"), true);
        }

        return super.onUse(state, world, pos, player, hand, hit);
    }
}
