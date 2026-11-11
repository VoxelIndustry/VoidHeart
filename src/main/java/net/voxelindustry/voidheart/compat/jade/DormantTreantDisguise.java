package net.voxelindustry.voidheart.compat.jade;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.HitResult;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.callback.JadeRayTraceCallback;

/**
 * A trunk hiding a Treant is named a dark oak log to anyone but a creative player, so looking at it gives the ambush away no sooner than coming near does.
 */
public class DormantTreantDisguise implements JadeRayTraceCallback
{
    private final IWailaClientRegistration registration;

    public DormantTreantDisguise(IWailaClientRegistration registration)
    {
        this.registration = registration;
    }

    @Override
    public @Nullable Accessor<?> onRayTrace(HitResult hitResult, @Nullable Accessor<?> accessor, @Nullable Accessor<?> originalAccessor)
    {
        if (!(accessor instanceof BlockAccessor blockAccessor) || !blockAccessor.getBlockState().is(VoidHeartBlocks.DORMANT_TREANT)
            || blockAccessor.getPlayer().isCreative())
            return accessor;

        return registration.blockAccessor()
                .from(blockAccessor)
                .blockState(Blocks.DARK_OAK_LOG.defaultBlockState())
                .blockEntity((BlockEntity) null)
                .serversideRep(new ItemStack(Blocks.DARK_OAK_LOG))
                .build();
    }
}
