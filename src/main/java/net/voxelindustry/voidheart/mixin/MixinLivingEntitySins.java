package net.voxelindustry.voidheart.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.voxelindustry.voidheart.common.content.sin.SinCombat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// A sin weapon raises the damage of its hits and answers once they land, and a wrath blade cuts its wielder's healing
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntitySins
{
    @Unique
    private float voidheart$lifeBeforeHit;

    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float voidheart$sinDamage(float amount, ServerLevel level, DamageSource source)
    {
        var self = (LivingEntity) (Object) this;
        voidheart$lifeBeforeHit = self.getHealth() + self.getAbsorptionAmount();
        return SinCombat.modifyOutgoing(self, source, amount);
    }

    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void voidheart$sinHitLanded(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> info)
    {
        if (!info.getReturnValueZ())
            return;

        var self = (LivingEntity) (Object) this;
        var dealt = voidheart$lifeBeforeHit - (Math.max(0, self.getHealth()) + self.getAbsorptionAmount());
        SinCombat.onHitLanded(self, source, dealt);

        if (self instanceof ServerPlayer player && amount > 0)
            SinCombat.onPlayerHurt(player);
    }

    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
    private float voidheart$wrathHealing(float amount)
    {
        return SinCombat.modifyHealing((LivingEntity) (Object) this, amount);
    }
}
