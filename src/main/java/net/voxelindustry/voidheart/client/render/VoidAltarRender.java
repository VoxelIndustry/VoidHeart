package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.state.VoidAltarRenderState;
import net.voxelindustry.voidheart.client.util.MathUtil;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.jspecify.annotations.Nullable;

public class VoidAltarRender implements BlockEntityRenderer<VoidAltarTile, VoidAltarRenderState>
{
    public static final BlockDisplayContext BLOCK_DISPLAY_CONTEXT = BlockDisplayContext.create();

    public static        long lastTouchedMillis;
    private static final long HINT_DURATION_MILLIS = 10_000;

    private final ItemModelResolver  itemModelResolver;
    private final BlockModelResolver blockModelResolver;

    public VoidAltarRender(BlockEntityRendererProvider.Context context)
    {
        itemModelResolver = context.itemModelResolver();
        blockModelResolver = context.blockModelResolver();
    }

    @Override
    public VoidAltarRenderState createRenderState()
    {
        return new VoidAltarRenderState();
    }

    @Override
    public void extractRenderState(VoidAltarTile altar, VoidAltarRenderState state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(altar, state, partialTicks, cameraPosition, breakProgress);

        var level = altar.getLevel();
        int seed = (int) altar.getBlockPos().asLong();
        state.worldTime = level.getGameTime() + partialTicks;
        state.cameraDistanceSqr = altar.getBlockPos().distToCenterSqr(cameraPosition);
        itemModelResolver.updateForTopItem(state.item, altar.getStack(), ItemDisplayContext.GROUND, level, null, seed);

        state.missingPillarOffsets.clear();
        if (!altar.getStack().isEmpty() || System.currentTimeMillis() - lastTouchedMillis < HINT_DURATION_MILLIS)
        {
            PillarPlacementRender.collectMissingPillars(altar, state.missingPillarOffsets);
            if (!state.missingPillarOffsets.isEmpty())
                blockModelResolver.update(state.pillarModel, VoidHeartBlocks.VOID_PILLAR.defaultBlockState(), BLOCK_DISPLAY_CONTEXT);
        }

        state.warmProgress = altar.getWarmProgress();
        state.coolProgress = altar.getCoolProgress();
        state.renderVoidCube = altar.isCrafting() && altar.getWarmProgress() > 0 || altar.getCoolProgress() > 0;

        var output = altar.getClientRecipeOutput();
        state.outputCount = output.getCount();
        itemModelResolver.updateForTopItem(state.output, output, ItemDisplayContext.GUI, level, null, seed);

        state.toConsume.clear();
        var toConsume = altar.getClientRecipeToConsume();
        // The first stack is the catalyst, already floating above the altar
        for (int i = 1; i < toConsume.size(); i++)
        {
            if (toConsume.get(i).isEmpty())
                continue;
            var stackState = new ItemStackRenderState();
            itemModelResolver.updateForTopItem(stackState, toConsume.get(i), ItemDisplayContext.GUI, level, null, seed + i);
            state.toConsume.add(stackState);
        }
    }

    @Override
    public void submit(VoidAltarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        PillarPlacementRender.submitPillarsPreview(state, poseStack, collector);

        CraftingOverlayRender.submitCraftingOverlay(state, poseStack, collector, camera);

        if (state.renderVoidCube)
            CraftingVoidRender.submitVoidCube(state, poseStack, collector);

        if (state.item.isEmpty())
            return;

        poseStack.pushPose();

        double offset = Math.abs(Math.sin(state.worldTime / 8.0) / 24.0) + 2 / 16D;

        poseStack.translate(0.5, 1 + offset, 0.5);
        poseStack.rotate(MathUtil.quatFromAngleDegrees(state.worldTime, MathUtil.NEGATIVE_Y));

        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);

        poseStack.popPose();
    }
}
