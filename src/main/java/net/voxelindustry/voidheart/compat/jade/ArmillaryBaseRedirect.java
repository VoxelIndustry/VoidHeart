package net.voxelindustry.voidheart.compat.jade;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryBaseBlock;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryTile;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.callback.JadeRayTraceCallback;

/**
 * The base of a formed armillary holds nothing itself, so looking at it shows the armillary it carries.
 */
public class ArmillaryBaseRedirect implements JadeRayTraceCallback
{
    private final IWailaClientRegistration registration;

    public ArmillaryBaseRedirect(IWailaClientRegistration registration)
    {
        this.registration = registration;
    }

    @Override
    public @Nullable Accessor<?> onRayTrace(HitResult hitResult, @Nullable Accessor<?> accessor, @Nullable Accessor<?> originalAccessor)
    {
        if (!(accessor instanceof BlockAccessor blockAccessor) || !(blockAccessor.getBlock() instanceof ArmillaryBaseBlock base))
            return accessor;

        var level = blockAccessor.getLevel();
        var corePos = base.corePos(blockAccessor.getBlockState(), blockAccessor.getPosition());
        if (!(level.getBlockEntity(corePos) instanceof ArmillaryTile armillary))
            return accessor;

        var state = level.getBlockState(corePos);
        return registration.blockAccessor()
                .from(blockAccessor)
                .hit(new BlockHitResult(blockAccessor.getHitResult().getLocation(), blockAccessor.getSide(), corePos, false))
                .blockState(state)
                .blockEntity(armillary)
                .serversideRep(new ItemStack(state.getBlock()))
                .build();
    }
}
