package net.voxelindustry.voidheart.compat.jade;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.voxelindustry.voidheart.common.block.TwoHighBlock;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.callback.JadeRayTraceCallback;

/**
 * Shows another block in place of the one looked at, for parts of a structure that hold nothing themselves.
 */
public final class JadeRedirects
{
    private JadeRedirects()
    {
    }

    public static Accessor<?> to(IWailaClientRegistration registration, BlockAccessor from, BlockPos pos, BlockState state, @Nullable BlockEntity tile)
    {
        return to(registration, from, pos, state, tile, new ItemStack(state.getBlock()));
    }

    public static Accessor<?> to(IWailaClientRegistration registration, BlockAccessor from, BlockPos pos, BlockState state, @Nullable BlockEntity tile,
                                 ItemStack shown)
    {
        return registration.blockAccessor()
                .from(from)
                .hit(new BlockHitResult(from.getHitResult().getLocation(), from.getSide(), pos, false))
                .blockState(state)
                .blockEntity(tile)
                .serversideRep(shown)
                .build();
    }

    /**
     * The upper half of a {@link TwoHighBlock} holds nothing itself, so looking at it shows the lower half.
     */
    public static JadeRayTraceCallback upperHalves(IWailaClientRegistration registration)
    {
        return (hitResult, accessor, originalAccessor) ->
        {
            if (!(accessor instanceof BlockAccessor blockAccessor) || !(blockAccessor.getBlock() instanceof TwoHighBlock<?> block)
                    || !block.isUpper(blockAccessor.getBlockState()))
                return accessor;

            var below = blockAccessor.getPosition().below();
            var level = blockAccessor.getLevel();
            var state = level.getBlockState(below);
            var tile = level.getBlockEntity(below);
            if (!block.isLower(state) || tile == null)
                return accessor;
            return to(registration, blockAccessor, below, state, tile);
        };
    }
}
