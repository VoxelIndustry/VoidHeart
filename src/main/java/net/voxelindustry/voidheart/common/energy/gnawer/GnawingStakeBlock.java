package net.voxelindustry.voidheart.common.energy.gnawer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;

import static net.voxelindustry.voidheart.common.energy.gnawer.GnawerBlock.message;

/**
 * A thin azurium stake marking a corner of a Gnawer's box. It needs nothing under it, so the upper corner can be set in the air. A stake that remembers a
 * Gnawer, used on two placed stakes, gives that Gnawer the box between them.
 */
public class GnawingStakeBlock extends Block
{
    private static final VoxelShape SHAPE = Block.box(6.5, 0, 6.5, 9.5, 14, 9.5);

    public GnawingStakeBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        var link = stack.get(VoidHeartDataComponents.GNAWER_LINK);
        if (!stack.is(asItem()) || link == null)
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide())
            return InteractionResult.SUCCESS;

        if (link.first().isEmpty())
        {
            stack.set(VoidHeartDataComponents.GNAWER_LINK, link.withFirst(pos));
            player.sendOverlayMessage(message("first_corner"));
            return InteractionResult.SUCCESS;
        }

        var first = link.first().get();
        if (first.equals(pos))
            return InteractionResult.SUCCESS;

        if (!(level.getBlockEntity(link.gnawer()) instanceof GnawerTile gnawer))
        {
            stack.remove(VoidHeartDataComponents.GNAWER_LINK);
            player.sendOverlayMessage(message("lost"));
            return InteractionResult.SUCCESS;
        }
        if (!level.getBlockState(first).is(this))
        {
            stack.set(VoidHeartDataComponents.GNAWER_LINK, link.withFirst(pos));
            player.sendOverlayMessage(message("first_corner"));
            return InteractionResult.SUCCESS;
        }

        var refusal = GnawerBox.check(link.gnawer(), first, pos);
        if (refusal != null)
        {
            player.sendOverlayMessage(message(refusal == GnawerBox.Refusal.TOO_WIDE ? "too_wide" : "too_far", GnawerBalance.MAX_WIDTH, GnawerBalance.RANGE));
            return InteractionResult.SUCCESS;
        }
        if (!gnawer.link(first, pos))
        {
            player.sendOverlayMessage(message("busy"));
            return InteractionResult.SUCCESS;
        }

        stack.remove(VoidHeartDataComponents.GNAWER_LINK);
        player.sendOverlayMessage(message("linked"));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }
}
