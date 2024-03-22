package net.voxelindustry.voidheart.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.voxelindustry.voidheart.client.model.EyeBottleModel;
import net.voxelindustry.voidheart.client.util.MathUtil;
import net.voxelindustry.voidheart.common.content.eyebottle.EyeBottleTile;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public class EyeBottleRender extends GeoBlockRenderer<EyeBottleTile>
{
    public EyeBottleRender()
    {
        super(new EyeBottleModel());
        this.addRenderLayer(new EyeBottleLabelLayer(this));
    }

    public static class EyeBottleLabelLayer extends GeoRenderLayer<EyeBottleTile>
    {
        public EyeBottleLabelLayer(GeoRenderer<EyeBottleTile> renderer)
        {
            super(renderer);
        }

        @Override
        public void render(MatrixStack matrices, EyeBottleTile eyeBottle, BakedGeoModel bakedModel, RenderLayer renderType, VertexConsumerProvider bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay)
        {
            var playerProfile = eyeBottle.getPlayerProfile();

            if (playerProfile == null)
                return;

            if (MinecraftClient.getInstance().player.squaredDistanceTo(eyeBottle.getPos().getX(), eyeBottle.getPos().getY(), eyeBottle.getPos().getZ()) < 64)
            {
                matrices.push();
                matrices.translate(0, 0.8, 0);
                matrices.scale(1F / 64F, 1F / 64F, 1F / 64F);

                matrices.multiply(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation());
                matrices.multiply(MathUtil.quatFromAngleDegrees(180, MathUtil.POSITIVE_Z));
                var textRenderer = MinecraftClient.getInstance().textRenderer;
                var nameSize = textRenderer.getWidth(playerProfile.getName()) / 2F;
                textRenderer.draw(playerProfile.getName(), -nameSize + 1, 1, 0x3c3c3c, false, matrices.peek().getPositionMatrix(), bufferSource, TextLayerType.NORMAL, 0, 15728880);
                textRenderer.draw(playerProfile.getName(), -nameSize, 0, 0xFFFFFF, false, matrices.peek().getPositionMatrix(), bufferSource, TextLayerType.SEE_THROUGH, 0, 15728880);

                matrices.pop();
            }
        }
    }
}
