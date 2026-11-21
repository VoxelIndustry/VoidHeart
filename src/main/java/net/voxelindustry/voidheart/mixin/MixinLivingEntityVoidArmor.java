package net.voxelindustry.voidheart.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.armor.ArmorBalance;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The Void Armor is too heavy to swim: it sinks and walks the bottom. Its legs take falls out of its battery, and it can't sprint while stepping, limp or wading
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityVoidArmor
{
    @Inject(method = "jumpInLiquid", at = @At("HEAD"), cancellable = true)
    private void voidheart$noSwimUp(TagKey<Fluid> type, CallbackInfo info)
    {
        if ((Object) this instanceof Player player && ArmorWear.wearing(player))
            info.cancel();
    }

    @Inject(method = "travelInWater", at = @At("TAIL"))
    private void voidheart$sink(Vec3 input, double baseGravity, boolean isFalling, double oldY, CallbackInfo info)
    {
        var self = (LivingEntity) (Object) this;
        if (self instanceof Player player && ArmorWear.wearing(player))
            self.setDeltaMovement(self.getDeltaMovement().add(0, -ArmorBalance.WATER_SINK, 0));
    }

    @ModifyVariable(method = "setSprinting", at = @At("HEAD"), argsOnly = true)
    private boolean voidheart$noSprint(boolean sprinting)
    {
        return sprinting && !ArmorWear.holdsBackSprint((LivingEntity) (Object) this);
    }

    @WrapOperation(method = "causeFallDamage", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;calculateFallDamage(DF)I"))
    private int voidheart$legsTakeFall(LivingEntity self, double fallDistance, float damageModifier, Operation<Integer> original)
    {
        return ArmorWear.takeFall(self, original.call(self, fallDistance, damageModifier));
    }
}
