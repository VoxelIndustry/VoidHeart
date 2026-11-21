package net.voxelindustry.voidheart.mixin.client;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.voxelindustry.voidheart.client.armor.VoidArmorLook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// What a Void Armor wearer wears over their body, its armor, cape, wings and head item, would poke out of the frame, which hides it all
@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRendererVoidArmor
{
    @WrapWithCondition(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/EntityRenderState;FF)V"))
    private boolean voidheart$hiddenByFrame(RenderLayer<?, ?> layer, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, EntityRenderState state,
                                            float yRot, float xRot)
    {
        if (!(state instanceof AvatarRenderState) || !((FabricRenderState) state).getDataOrDefault(VoidArmorLook.KEY, VoidArmorLook.NONE).shown())
            return true;
        return !(layer instanceof HumanoidArmorLayer<?, ?, ?> || layer instanceof CapeLayer || layer instanceof WingsLayer<?, ?>
                 || layer instanceof CustomHeadLayer<?, ?>);
    }
}
