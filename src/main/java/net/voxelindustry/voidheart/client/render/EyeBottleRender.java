package net.voxelindustry.voidheart.client.render;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.model.EyeBottleModel;
import net.voxelindustry.voidheart.client.util.MathUtil;
import net.voxelindustry.voidheart.common.content.eyebottle.EyeBottleTile;
import net.voxelindustry.voidheart.common.util.ProfileUtil;

public class EyeBottleRender extends GeoBlockRenderer<EyeBottleTile, BlockEntityRenderState>
{
    private static final DataTicket<String> OWNER_NAME = DataTicket.create("voidheart_eye_bottle_owner", String.class);

    public EyeBottleRender(BlockEntityRendererProvider.Context context)
    {
        super(context, new EyeBottleModel());
        withRenderLayer(new EyeBottleLabelLayer(this));
    }

    public static class EyeBottleLabelLayer extends GeoRenderLayer<EyeBottleTile, Void, BlockEntityRenderState>
    {
        public EyeBottleLabelLayer(GeoRenderer<EyeBottleTile, Void, BlockEntityRenderState> renderer)
        {
            super(renderer);
        }

        @Override
        public void addRenderData(EyeBottleTile eyeBottle, Void relatedObject, BlockEntityRenderState renderState, float partialTick)
        {
            var playerProfile = eyeBottle.getPlayerProfile();
            var player = Minecraft.getInstance().player;

            if (playerProfile != null && player != null && player.distanceToSqr(Vec3.atLowerCornerOf(eyeBottle.getBlockPos())) < 64)
                renderState.addGeckolibData(OWNER_NAME, ProfileUtil.getName(playerProfile));
        }

        @Override
        public void submitRenderTask(RenderPassInfo<BlockEntityRenderState> renderPassInfo, SubmitNodeCollector collector)
        {
            var ownerName = renderPassInfo.getOrDefaultGeckolibData(OWNER_NAME, null);

            if (ownerName == null)
                return;

            var matrices = renderPassInfo.poseStack();
            matrices.pushPose();
            matrices.translate(0, 0.8, 0);
            matrices.scale(1F / 64F, 1F / 64F, 1F / 64F);

            matrices.rotate(renderPassInfo.cameraState().orientation);
            matrices.rotate(MathUtil.quatFromAngleDegrees(180, MathUtil.POSITIVE_Z));

            var name = Component.literal(ownerName).getVisualOrderText();
            var nameSize = Minecraft.getInstance().font.width(name) / 2F;
            collector.submitText(matrices, -nameSize + 1, 1, name, false, DisplayMode.NORMAL, 15728880, 0xFF3C3C3C, 0, 0);
            collector.submitText(matrices, -nameSize, 0, name, false, DisplayMode.SEE_THROUGH, 15728880, 0xFFFFFFFF, 0, 0);

            matrices.popPose();
        }
    }
}
