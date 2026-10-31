package net.voxelindustry.voidheart.compat.jade;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.HitResult;
import net.voxelindustry.voidheart.common.content.permeablebarrier.PermeableBarrierStructure;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.callback.JadeRayTraceCallback;

/**
 * Non-owners looking at a barrier are told it is the block it pretends to be.
 */
public class PermeableBarrierDisguise implements JadeRayTraceCallback
{
    private final IWailaClientRegistration registration;

    public PermeableBarrierDisguise(IWailaClientRegistration registration)
    {
        this.registration = registration;
    }

    @Override
    public @Nullable Accessor<?> onRayTrace(HitResult hitResult, @Nullable Accessor<?> accessor, @Nullable Accessor<?> originalAccessor)
    {
        if (!(accessor instanceof BlockAccessor blockAccessor) || !blockAccessor.getBlockState().is(VoidHeartBlocks.PERMEABLE_BARRIER))
            return accessor;

        var tile = PermeableBarrierStructure.getTile(blockAccessor.getLevel(), blockAccessor.getPosition());
        if (tile == null || tile.getConfig().isAlly(blockAccessor.getPlayer()))
            return accessor;

        // An unconfigured sigil has nothing to pass itself off as, so it stays unnamed
        var appearance = tile.getConfig().appearance();
        if (appearance.isEmpty())
            return null;

        return registration.blockAccessor()
                .from(blockAccessor)
                .blockState(appearance.get())
                .blockEntity((BlockEntity) null)
                .serversideRep(new ItemStack(appearance.get().getBlock()))
                .build();
    }
}
