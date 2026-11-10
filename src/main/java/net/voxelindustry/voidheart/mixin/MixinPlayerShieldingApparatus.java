package net.voxelindustry.voidheart.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldingApparatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// The apparatus shield takes what armor and enchantments leave of a hit, before absorption hearts and health
@Mixin(Player.class)
public abstract class MixinPlayerShieldingApparatus
{
    @WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float voidheart$shield(Player player, DamageSource source, float damage, Operation<Float> original)
    {
        return ShieldingApparatus.absorb(player, source, original.call(player, source, damage));
    }
}
