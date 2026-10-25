package net.voxelindustry.voidheart.mixin;

import com.mojang.serialization.Lifecycle;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PrimaryLevelData.class)
public class MixinStableLifecycle
{
    @Inject(method = "worldGenSettingsLifecycle()Lcom/mojang/serialization/Lifecycle;", at = @At("HEAD"), cancellable = true)
    public void allDimensionsAreStable(CallbackInfoReturnable<Lifecycle> callbackInfoReturnable) {
        callbackInfoReturnable.setReturnValue(Lifecycle.stable());
    }
}