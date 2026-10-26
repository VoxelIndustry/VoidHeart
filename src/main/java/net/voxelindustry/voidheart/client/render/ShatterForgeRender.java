package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.state.ShatterForgeRenderState;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeTile;
import org.jspecify.annotations.Nullable;

public class ShatterForgeRender implements BlockEntityRenderer<ShatterForgeTile, ShatterForgeRenderState>
{
    private final FloatingItemRender<ShatterForgeTile> itemRender;

    public ShatterForgeRender(BlockEntityRendererProvider.Context context)
    {
        itemRender = new FloatingItemRender<>(context, ShatterForgeTile::getStack, 1.2);
    }

    @Override
    public ShatterForgeRenderState createRenderState()
    {
        return new ShatterForgeRenderState();
    }

    @Override
    public void extractRenderState(ShatterForgeTile forge, ShatterForgeRenderState state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        itemRender.extractRenderState(forge, state, partialTicks, cameraPosition, breakProgress);

        state.hints.clear();
        ForgePreviewRender.collectHints(forge, state.hints);

        VoidBeamRender.extract(forge, state, partialTicks);
    }

    @Override
    public void submit(ShatterForgeRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        itemRender.submit(state, poseStack, collector, camera);
        ForgePreviewRender.submitHints(state, poseStack, collector, camera);
        VoidBeamRender.submit(state, poseStack, collector);
    }

    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }

    @Override
    public int getViewDistance()
    {
        return 256;
    }
}
