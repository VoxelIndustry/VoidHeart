package net.voxelindustry.voidheart.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.voxelindustry.voidheart.client.util.MathUtil;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeTile;

public class ShatterForgeRender implements BlockEntityRenderer<ShatterForgeTile>
{
    @Override
    public void render(ShatterForgeTile forge, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)
    {
        matrices.push();

        double offset = Math.abs(Math.sin((forge.getWorld().getTime() + tickDelta) / 8.0) / 24.0);

        matrices.translate(0.5, 1 + offset, 0.5);
        matrices.multiply(MathUtil.quatFromAngleDegrees(forge.getWorld().getTime() + tickDelta, MathUtil.NEGATIVE_Y));

        var model = MinecraftClient.getInstance().getItemRenderer().getModel(forge.getStack(), null, null, 0);
        MinecraftClient.getInstance().getItemRenderer().renderItem(forge.getStack(), ModelTransformationMode.GROUND, false, matrices, vertexConsumers, light, overlay, model);

        matrices.pop();
    }
}
