package net.voxelindustry.voidheart.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassages;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Wraith Veil tunnel up or down is climbed like a ladder: jump to rise, sneak to hold, let go to sink.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityWallPassage
{
    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void voidheart$climbTunnel(CallbackInfoReturnable<Boolean> info)
    {
        if ((Object) this instanceof Player player && WallPassages.isClimbing(player))
            info.setReturnValue(true);
    }
}
