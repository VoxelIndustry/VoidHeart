package net.voxelindustry.voidheart.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.voxelindustry.voidheart.common.content.secondheart.SecondHeart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player dissolved by their second heart can't be picked as a target or pushed until the reform is done.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntitySecondHeart
{
    @Inject(method = "canBeSeenAsEnemy", at = @At("HEAD"), cancellable = true)
    private void voidheart$unseenWhileDissolved(CallbackInfoReturnable<Boolean> info)
    {
        if ((Object) this instanceof Player player && SecondHeart.held(player))
            info.setReturnValue(false);
    }

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void voidheart$unpushableWhileDissolved(CallbackInfoReturnable<Boolean> info)
    {
        if ((Object) this instanceof Player player && SecondHeart.held(player))
            info.setReturnValue(false);
    }
}
