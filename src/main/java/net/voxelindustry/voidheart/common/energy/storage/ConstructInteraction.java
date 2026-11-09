package net.voxelindustry.voidheart.common.energy.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What using a construct does, from its core or any of its frame blocks: a bundle in hand goes into the free socket
 * nearest the click, sneaking with an empty hand takes the nearest one out, and an empty hand reads the charge.
 */
final class ConstructInteraction
{
    private ConstructInteraction()
    {
    }

    static InteractionResult useBundle(Level level, ItemStack stack, Player player, Vec3 hit, @Nullable RubrumConstructTile core)
    {
        var bundle = ConstructBundles.bundleOf(stack);
        if (bundle == null || core == null || !core.isFormed())
            return InteractionResult.PASS;
        if (level.isClientSide())
            return InteractionResult.SUCCESS;

        if (core.insertBundle(bundle, hit))
            stack.consume(1, player);
        else
            player.sendOverlayMessage(Component.translatable("voidheart.rubrum_construct.no_socket"));
        return InteractionResult.SUCCESS;
    }

    /**
     * Returns PASS when the click means nothing here, so the core can show its preview instead.
     */
    static InteractionResult useEmptyHand(Level level, Player player, Vec3 hit, @Nullable RubrumConstructTile core)
    {
        if (core == null)
            return InteractionResult.PASS;

        if (player.isShiftKeyDown())
        {
            if (core.layout() == null || core.sockets().isEmpty())
                return InteractionResult.PASS;
            if (level.isClientSide())
                return InteractionResult.SUCCESS;

            switch (core.removeBundle(hit))
            {
                case RubrumConstructTile.Removal.Taken taken ->
                        player.getInventory().placeItemBackInInventory(new ItemStack(ConstructBundles.blockOf(taken.bundle())), Prediction.SERVER_ONLY);
                case RubrumConstructTile.Removal.Held held ->
                        player.sendOverlayMessage(Component.translatable("voidheart.rubrum_construct.rubrum_held"));
                case RubrumConstructTile.Removal.Empty empty ->
                {
                }
            }
            return InteractionResult.SUCCESS;
        }

        if (!core.isFormed())
        {
            if (core.problem() == RubrumConstructTile.Problem.TOO_MANY_BUNDLES)
            {
                if (!level.isClientSide())
                    player.sendOverlayMessage(Component.translatable("voidheart.rubrum_construct.too_many_bundles"));
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        if (!level.isClientSide())
            player.sendOverlayMessage(core.status().getFirst());
        return InteractionResult.SUCCESS;
    }

    /**
     * A core used on a whole frame standing empty goes into its middle, which nothing else touches to place against.
     * The large frame wins when both stand around the same spot. Returns PASS to place it the usual way otherwise.
     */
    static InteractionResult placeCore(Level level, BlockPos framePos, Player player, InteractionHand hand, ItemStack stack)
    {
        var center = emptyFrameCenter(level, framePos);
        if (center == null || !(stack.getItem() instanceof BlockItem blockItem))
            return InteractionResult.PASS;
        var context = new BlockPlaceContext(level, player, hand, stack, new BlockHitResult(Vec3.atCenterOf(center), Direction.UP, center, false));
        return blockItem.place(context);
    }

    private static @Nullable BlockPos emptyFrameCenter(Level level, BlockPos framePos)
    {
        for (var size : new ConstructLayout.Size[]{ConstructLayout.Size.LARGE, ConstructLayout.Size.SMALL})
        {
            for (var offset : ConstructLayout.frame(size))
            {
                var center = framePos.subtract(offset);
                if (!level.getBlockState(center).canBeReplaced())
                    continue;
                var whole = true;
                for (var other : ConstructLayout.frame(size))
                {
                    if (!level.getBlockState(center.offset(other)).is(RubrumContent.RUBRUM_CONSTRUCT_FRAME))
                    {
                        whole = false;
                        break;
                    }
                }
                if (whole)
                    return center;
            }
        }
        return null;
    }

    /**
     * The formed or loose core whose frame holds this position, searching as far as the largest construct reaches.
     */
    static @Nullable RubrumConstructTile coreOf(Level level, BlockPos framePos)
    {
        var reach = ConstructLayout.Size.LARGE.half() * 2;
        RubrumConstructTile loose = null;
        for (var pos : BlockPos.betweenClosed(framePos.offset(-reach, -reach, -reach), framePos.offset(reach, reach, reach)))
        {
            if (!(level.getBlockEntity(pos) instanceof RubrumConstructTile core))
                continue;
            var offset = framePos.subtract(pos);
            if (core.isFormed() && core.layout() != null && ConstructLayout.partAt(core.layout(), offset) != null)
                return core;
            if (loose == null && (ConstructLayout.partAt(ConstructLayout.Size.SMALL, offset) != null || ConstructLayout.partAt(ConstructLayout.Size.LARGE, offset) != null))
                loose = core;
        }
        return loose;
    }
}
