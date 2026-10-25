package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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
        public void render(PoseStack matrices, EyeBottleTile eyeBottle, BakedGeoModel bakedModel, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay)
        {
            var playerProfile = eyeBottle.getPlayerProfile();

            if (playerProfile == null)
                return;

            if (Minecraft.getInstance().player.distanceToSqr(eyeBottle.getBlockPos().getX(), eyeBottle.getBlockPos().getY(), eyeBottle.getBlockPos().getZ()) < 64)
            {
                matrices.pushPose();
                matrices.translate(0, 0.8, 0);
                matrices.scale(1F / 64F, 1F / 64F, 1F / 64F);

                matrices.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
                matrices.mulPose(MathUtil.quatFromAngleDegrees(180, MathUtil.POSITIVE_Z));
                var textRenderer = Minecraft.getInstance().font;
                var nameSize = textRenderer.width(playerProfile.getName()) / 2F;
                textRenderer.drawInBatch(playerProfile.getName(), -nameSize + 1, 1, 0x3c3c3c, false, matrices.last().pose(), bufferSource, DisplayMode.NORMAL, 0, 15728880);
                textRenderer.drawInBatch(playerProfile.getName(), -nameSize, 0, 0xFFFFFF, false, matrices.last().pose(), bufferSource, DisplayMode.SEE_THROUGH, 0, 15728880);

                matrices.popPose();
            }
        }
    }
}
