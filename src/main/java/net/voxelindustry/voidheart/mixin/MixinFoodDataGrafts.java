package net.voxelindustry.voidheart.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import net.voxelindustry.voidheart.common.content.research.VoidGrafts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// The Void Marrow slows natural regeneration away from the void and quickens it inside, whether it comes from saturation or a full bar
@Mixin(FoodData.class)
public abstract class MixinFoodDataGrafts
{
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V"))
    private void voidheart$graftedHeal(ServerPlayer player, float amount, Operation<Void> original)
    {
        original.call(player, amount * VoidGrafts.healFactor(VoidGrafts.attunementOf(player), VoidGrafts.isVoid(player.level().dimension())));
    }
}
