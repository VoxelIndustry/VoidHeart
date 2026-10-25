package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.voxelindustry.voidheart.client.render.state.VoidAltarRenderState;

public class CraftingOverlayRender
{
    private static final int FULL_BRIGHT = 15728880;

    static void submitCraftingOverlay(VoidAltarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        // Render count text label
        if (!state.output.isEmpty() && state.outputCount > 1)
        {
            poseStack.pushPose();
            poseStack.translate(0.5, 1.85, 0.5);
            poseStack.scale(1 / 64F, 1 / 64F, 1 / 64F);
            poseStack.rotate(camera.orientation);
            collector.submitText(poseStack, 6, -6, Component.literal(String.valueOf(state.outputCount)).getVisualOrderText(),
                    true, DisplayMode.NORMAL, state.lightCoords, 0xFFFFFFFF, 0, 0);
            poseStack.popPose();
        }

        poseStack.pushPose();
        poseStack.translate(0.5, 1.75, 0.5);
        poseStack.scale(2 / 16F, 2 / 16F, 2 / 16F);
        poseStack.rotate(camera.orientation);

        if (!state.output.isEmpty())
        {
            poseStack.translate(0, 2, 0);
            poseStack.scale(2.5F, 2.5F, 2.5F);

            state.output.submit(poseStack, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            poseStack.scale(0.6F, 0.6F, 0.6F);
            poseStack.translate(0, -2, 0);
        }

        poseStack.translate(-(state.toConsume.size() - 1) / 2F - 0.5F, 0, 0);

        for (var stack : state.toConsume)
        {
            poseStack.translate(1, 0, 0);
            stack.submit(poseStack, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        }

        poseStack.popPose();
    }
}
