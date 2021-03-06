package net.voxelindustry.voidheart.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformation.Mode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector3f;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile;

public class VoidPillarRender extends BlockEntityRenderer<VoidPillarTile>
{
    public VoidPillarRender(BlockEntityRenderDispatcher dispatcher)
    {
        super(dispatcher);
    }

    @Override
    public void render(VoidPillarTile pillar, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)
    {
        matrices.push();

        double offset = Math.abs(Math.sin((pillar.getWorld().getTime() + tickDelta) / 8.0) / 24.0);

        matrices.translate(0.5, 1.1 + offset, 0.5);
        matrices.multiply(Vector3f.NEGATIVE_Y.getDegreesQuaternion(pillar.getWorld().getTime() + tickDelta));

        MinecraftClient.getInstance().getItemRenderer().renderItem(pillar.getStack(), Mode.GROUND, 15728880, OverlayTexture.DEFAULT_UV, matrices, vertexConsumers);

        matrices.pop();
    }
}
