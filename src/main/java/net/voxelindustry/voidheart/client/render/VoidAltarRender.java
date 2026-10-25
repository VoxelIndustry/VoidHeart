package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.voxelindustry.voidheart.client.util.MathUtil;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;

public class VoidAltarRender implements BlockEntityRenderer<VoidAltarTile>
{
    public static long lastTouchedMillis;
    private static final long HINT_DURATION_MILLIS = 10_000;

    @Override
    public void render(VoidAltarTile altar, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay)
    {
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderType.translucent());

        if (!altar.getStack().isEmpty() || System.currentTimeMillis() - lastTouchedMillis < HINT_DURATION_MILLIS)
            PillarPlacementRender.renderPillarsPreview(this, altar, matrices, buffer);

        CraftingOverlayRender.renderCraftingOverlay(altar, matrices, vertexConsumers, light);

        var worldTimeInterp = Mth.lerp(tickDelta, altar.getLevel().getGameTime(), altar.getLevel().getGameTime() + 1);

        if (altar.isCrafting() && altar.getWarmProgress() > 0 || altar.getCoolProgress() > 0)
            CraftingVoidRender.renderVoidCube(altar, Minecraft.getInstance().getBlockEntityRenderDispatcher(), worldTimeInterp, matrices, vertexConsumers);

        matrices.pushPose();

        double offset = Math.abs(Math.sin(worldTimeInterp / 8.0) / 24.0) + 2 / 16D;

        matrices.translate(0.5, 1 + offset, 0.5);
        matrices.mulPose(MathUtil.quatFromAngleDegrees(worldTimeInterp, MathUtil.NEGATIVE_Y));

        var model = Minecraft.getInstance().getItemRenderer().getModel(altar.getStack(), null, null, 0);
        Minecraft.getInstance().getItemRenderer().render(altar.getStack(), ItemDisplayContext.GROUND, false, matrices, vertexConsumers, light, overlay, model);

        matrices.popPose();
    }
}
