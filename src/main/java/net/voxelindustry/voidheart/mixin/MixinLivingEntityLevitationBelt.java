package net.voxelindustry.voidheart.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.voxelindustry.voidheart.common.content.levitation.LevitationBelts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Levitation Belt's fading lift lowers the gravity its wearer feels while they sink.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityLevitationBelt
{
    @Inject(method = "getEffectiveGravity", at = @At("RETURN"), cancellable = true)
    private void voidheart$fadingLift(CallbackInfoReturnable<Double> info)
    {
        if ((Object) this instanceof Player player)
            info.setReturnValue(LevitationBelts.gravity(player, info.getReturnValue()));
    }
}
