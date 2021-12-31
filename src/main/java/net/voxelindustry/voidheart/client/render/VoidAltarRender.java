package net.voxelindustry.voidheart.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformation.Mode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3f;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;

public class VoidAltarRender implements BlockEntityRenderer<VoidAltarTile>
{
    @Override
    public void render(VoidAltarTile altar, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)
    {
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getTranslucent());

        if (!altar.getStack().isEmpty())
            PillarPlacementRender.renderPillarsPreview(this, altar, matrices, buffer);

        CraftingOverlayRender.renderCraftingOverlay(altar, matrices, vertexConsumers, light);

        var worldTimeInterp = MathHelper.lerp(tickDelta, altar.getWorld().getTime(), altar.getWorld().getTime() + 1);

        if (altar.isCrafting() && altar.getWarmProgress() > 0 || altar.getCoolProgress() > 0)
            CraftingVoidRender.renderVoidCube(altar, MinecraftClient.getInstance().getBlockEntityRenderDispatcher(), worldTimeInterp, matrices, vertexConsumers);

        matrices.push();

        double offset = Math.abs(Math.sin(worldTimeInterp / 8.0) / 24.0) + 2 / 16D;

        matrices.translate(0.5, 1 + offset, 0.5);
        matrices.multiply(Vec3f.NEGATIVE_Y.getDegreesQuaternion(worldTimeInterp));

        MinecraftClient.getInstance().getItemRenderer().renderItem(altar.getStack(), Mode.GROUND, 15728880, OverlayTexture.DEFAULT_UV, matrices, vertexConsumers, 0);

        matrices.pop();
    }
}
