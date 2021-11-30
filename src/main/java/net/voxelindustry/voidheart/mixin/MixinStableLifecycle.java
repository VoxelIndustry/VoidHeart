package net.voxelindustry.voidheart.mixin;

import com.mojang.serialization.Lifecycle;
import net.minecraft.world.level.LevelProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelProperties.class)
public class MixinStableLifecycle
{
    @Inject(method = "getLifecycle()Lcom/mojang/serialization/Lifecycle;", at = @At("HEAD"), cancellable = true)
    public void allDimensionsAreStable(CallbackInfoReturnable<Lifecycle> callbackInfoReturnable) {
        callbackInfoReturnable.setReturnValue(Lifecycle.stable());
    }
}