package net.voxelindustry.voidheart.compat.jade;

import net.minecraft.world.phys.HitResult;
import net.voxelindustry.voidheart.common.content.tormenter.MalignantTormenterBlock;
import net.voxelindustry.voidheart.common.content.tormenter.MalignantTormenterPartBlock;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.callback.JadeRayTraceCallback;

/**
 * Looking at any part of a droplet shows the tormenter it belongs to.
 */
public class MalignantTormenterPartRedirect implements JadeRayTraceCallback
{
    private final IWailaClientRegistration registration;

    public MalignantTormenterPartRedirect(IWailaClientRegistration registration)
    {
        this.registration = registration;
    }

    @Override
    public @Nullable Accessor<?> onRayTrace(HitResult hitResult, @Nullable Accessor<?> accessor, @Nullable Accessor<?> originalAccessor)
    {
        if (!(accessor instanceof BlockAccessor blockAccessor) || !blockAccessor.getBlockState().is(VoidHeartBlocks.MALIGNANT_TORMENTER_PART))
            return accessor;

        var level = blockAccessor.getLevel();
        var tormenter = MalignantTormenterPartBlock.tormenterOf(blockAccessor.getPosition(), blockAccessor.getBlockState());
        var state = level.getBlockState(tormenter);
        if (!state.is(VoidHeartBlocks.MALIGNANT_TORMENTER))
            return accessor;

        var tile = level.getBlockEntity(tormenter);
        return JadeRedirects.to(registration, blockAccessor, tormenter, state, tile, MalignantTormenterBlock.itemOf(tile));
    }
}
