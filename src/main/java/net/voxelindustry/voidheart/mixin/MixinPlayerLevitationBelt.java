package net.voxelindustry.voidheart.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.levitation.LevitationBelts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A Levitation Belt's fading lift caps how fast its wearer sinks, once the tick's movement has been worked out.
 */
@Mixin(Player.class)
public abstract class MixinPlayerLevitationBelt
{
    @Inject(method = "travel", at = @At("TAIL"))
    private void voidheart$capDescent(Vec3 input, CallbackInfo info)
    {
        LevitationBelts.afterTravel((Player) (Object) this);
    }
}
