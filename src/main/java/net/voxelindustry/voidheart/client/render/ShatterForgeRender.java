package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.voxelindustry.voidheart.client.util.MathUtil;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeTile;

public class ShatterForgeRender implements BlockEntityRenderer<ShatterForgeTile>
{
    @Override
    public void render(ShatterForgeTile forge, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay)
    {
        matrices.pushPose();

        double offset = Math.abs(Math.sin((forge.getLevel().getGameTime() + tickDelta) / 8.0) / 24.0);

        matrices.translate(0.5, 1 + offset, 0.5);
        matrices.mulPose(MathUtil.quatFromAngleDegrees(forge.getLevel().getGameTime() + tickDelta, MathUtil.NEGATIVE_Y));

        var model = Minecraft.getInstance().getItemRenderer().getModel(forge.getStack(), null, null, 0);
        Minecraft.getInstance().getItemRenderer().render(forge.getStack(), ItemDisplayContext.GROUND, false, matrices, vertexConsumers, light, overlay, model);

        matrices.popPose();
    }
}
