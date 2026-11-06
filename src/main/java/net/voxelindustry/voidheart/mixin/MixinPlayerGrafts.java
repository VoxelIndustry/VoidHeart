package net.voxelindustry.voidheart.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.voxelindustry.voidheart.common.content.research.VoidGrafts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// The Void Vein makes hunger drain faster away from the void and slower inside
@Mixin(Player.class)
public abstract class MixinPlayerGrafts
{
    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float voidheart$graftedHunger(float exhaustion)
    {
        if (!((Object) this instanceof ServerPlayer player))
            return exhaustion;
        return exhaustion * VoidGrafts.hungerFactor(VoidGrafts.attunementOf(player), VoidGrafts.isVoid(player.level().dimension()));
    }
}
