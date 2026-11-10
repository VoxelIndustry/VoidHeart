package net.voxelindustry.voidheart.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassages;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Nobody suffocates in their own Wraith Veil tunnel, though its blocks are still there.
 */
@Mixin(Entity.class)
public abstract class MixinEntityWallPassage
{
    @Inject(method = "isInWall", at = @At("HEAD"), cancellable = true)
    private void voidheart$notInWall(CallbackInfoReturnable<Boolean> info)
    {
        if ((Object) this instanceof Player player && WallPassages.isInside(player))
            info.setReturnValue(false);
    }
}
