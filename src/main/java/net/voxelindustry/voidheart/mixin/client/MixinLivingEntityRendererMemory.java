package net.voxelindustry.voidheart.mixin.client;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.voxelindustry.voidheart.client.InsightMemory;
import net.voxelindustry.voidheart.client.TunnelVision;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;
import net.voxelindustry.voidheart.client.armor.ArmorSight;
import net.voxelindustry.voidheart.client.armor.ArmorWings;
import net.voxelindustry.voidheart.client.armor.VoidArmorLook;
import net.voxelindustry.voidheart.client.render.KleinStarRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands each mob's remembered and spectral silhouettes to its render state, since only the entity knows whether it was seen, and whether it holds out
 * a staff with a Klein Star growing on it.
 */
@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRendererMemory
{
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void voidheart$memory(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo callback)
    {
        // A Wraith Veil tunnel reveals what is near through the same silhouette
        state.setData(InsightMemory.ALPHA, Math.max(InsightMemory.alpha(entity), TunnelVision.silhouette(entity, partialTicks)));
        // Whoever grows a Klein Star on a staff holds the staff out
        state.setData(KleinStarRender.RAISED_ARM, KleinStarRender.raisedArm(entity));
        // What a Void Armor wearer's spectral lens shows of it through walls
        state.setData(ArmorSight.SPECTRAL, ArmorSight.silhouette(entity, partialTicks));
        // The Void Armor a player has on, if any, drawn in place of their body
        if (state instanceof AvatarRenderState)
        {
            state.setData(VoidArmorLook.KEY, entity instanceof Player player ? VoidArmorLook.of(player, partialTicks) : VoidArmorLook.NONE);
            // And the wings it spreads in flight
            state.setData(ArmorWings.KEY, entity instanceof Player player ? ArmorWings.of(player, partialTicks) : ArmorWings.Wings.NONE);
        }
    }
}
