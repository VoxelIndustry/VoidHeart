package net.voxelindustry.voidheart.compat.jade;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.voxelindustry.voidheart.common.content.essence.StorageParts;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.callback.JadeRayTraceCallback;

/**
 * Looking at a storage's pillars, or a matrix's columns, shows the block that holds the essence.
 */
public class RavenousStoragePartRedirect implements JadeRayTraceCallback
{
    private final IWailaClientRegistration registration;

    public RavenousStoragePartRedirect(IWailaClientRegistration registration)
    {
        this.registration = registration;
    }

    @Override
    public @Nullable Accessor<?> onRayTrace(HitResult hitResult, @Nullable Accessor<?> accessor, @Nullable Accessor<?> originalAccessor)
    {
        if (!(accessor instanceof BlockAccessor blockAccessor))
            return accessor;
        var holder = StorageParts.holderOf(blockAccessor.getBlockState(), blockAccessor.getPosition());
        if (holder == null || holder.equals(blockAccessor.getPosition()))
            return accessor;

        var level = blockAccessor.getLevel();
        var state = level.getBlockState(holder);
        var tile = level.getBlockEntity(holder);
        if (!state.is(blockAccessor.getBlock()) || tile == null)
            return accessor;

        return registration.blockAccessor()
                .from(blockAccessor)
                .hit(new BlockHitResult(blockAccessor.getHitResult().getLocation(), blockAccessor.getSide(), holder, false))
                .blockState(state)
                .blockEntity(tile)
                .serversideRep(new ItemStack(state.getBlock()))
                .build();
    }
}
