package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.steamlayer.math.interpolator.Interpolators;
import net.voxelindustry.voidheart.client.util.GuiSprites;
import net.voxelindustry.voidheart.client.util.MathUtil;
import net.voxelindustry.voidheart.common.block.StateProperties;
import net.voxelindustry.voidheart.common.content.repair.ExperienceSkullTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.util.ExperienceUtil;
import org.joml.Math;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public class ExperienceSkullRender implements BlockEntityRenderer<ExperienceSkullTile, ExperienceSkullRender.ExperienceSkullRenderState>
{
    private static final BlockDisplayContext BLOCK_DISPLAY_CONTEXT = BlockDisplayContext.create();

    public static class ExperienceSkullRenderState extends BlockEntityRenderState
    {
        public final BlockModelRenderState skull = new BlockModelRenderState();

        public float rotationVerticalDelta;
        public float rotationHorizontalDelta;
        public float translateDelta;

        public int   level;
        public float nextLevelProgress;
    }

    private final Map<Direction, BlockState> experienceSkullStateCache = new EnumMap<>(Direction.class);

    private final BlockModelResolver blockModelResolver;

    public ExperienceSkullRender(BlockEntityRendererProvider.Context context)
    {
        blockModelResolver = context.blockModelResolver();
    }

    private static BlockState createSkullState(Direction direction)
    {
        return VoidHeartBlocks.EXPERIENCE_SKULL.defaultBlockState()
                .setValue(StateProperties.MODEL, true)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, direction);
    }

    private BlockState getSkullState(Direction direction)
    {
        return experienceSkullStateCache.computeIfAbsent(direction, ExperienceSkullRender::createSkullState);
    }

    @Override
    public ExperienceSkullRenderState createRenderState()
    {
        return new ExperienceSkullRenderState();
    }

    @Override
    public void extractRenderState(ExperienceSkullTile skull, ExperienceSkullRenderState state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(skull, state, partialTicks, cameraPosition, breakProgress);

        long gameTime = skull.getLevel().getGameTime();
        state.rotationVerticalDelta = getAnimationDelta(gameTime, 160, 80, partialTicks);
        state.rotationHorizontalDelta = getAnimationDelta(gameTime, 100, 0, partialTicks);
        state.translateDelta = getAnimationDelta(gameTime, 160, 0, partialTicks);

        blockModelResolver.update(state.skull, getSkullState(skull.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING)), BLOCK_DISPLAY_CONTEXT);

        state.level = ExperienceUtil.getExperienceLevel(skull.getExperience());
        state.nextLevelProgress = ExperienceUtil.nextLevelProgress(skull.getExperience());
    }

    @Override
    public void submit(ExperienceSkullRenderState state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera)
    {
        matrices.pushPose();

        matrices.translate(0.5, 0.5, 0.5);
        matrices.rotate(new Quaternionf().rotationXYZ(Math.toRadians(15 * state.rotationVerticalDelta - 7.5F), Math.toRadians(10 * state.rotationHorizontalDelta - 5), 0));
        matrices.translate(-0.5, -0.5, -0.5);

        matrices.translate(0, Interpolators.QUAD_BOTH.apply(state.translateDelta) * 0.5, 0);

        state.skull.submit(matrices, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        matrices.popPose();

        matrices.pushPose();
        matrices.translate(0.5, 0.8, 0.5);
        matrices.translate(0, Interpolators.QUAD_BOTH.apply(state.translateDelta) * 0.5, 0);
        matrices.scale(1.25F / 64F, 1.25F / 64F, 1.25F / 64F);

        // Since 26.1 the camera orientation faces the other way, only the Y axis needs flipping for text and sprites
        matrices.rotate(camera.orientation);
        matrices.rotate(MathUtil.quatFromAngleDegrees(180, MathUtil.POSITIVE_X));

        var levelText = Component.literal(String.valueOf(state.level)).getVisualOrderText();
        var levelTextSize = Minecraft.getInstance().font.width(levelText) / 2F;
        int light = state.lightCoords;

        collector.submitText(matrices, -levelTextSize - 1, -3, levelText, false, DisplayMode.NORMAL, light, 0xFF000000, 0, 0);
        collector.submitText(matrices, -levelTextSize + 1, -3, levelText, false, DisplayMode.NORMAL, light, 0xFF000000, 0, 0);
        collector.submitText(matrices, -levelTextSize, -4, levelText, false, DisplayMode.NORMAL, light, 0xFF000000, 0, 0);
        collector.submitText(matrices, -levelTextSize, -2, levelText, false, DisplayMode.NORMAL, light, 0xFF000000, 0, 0);

        matrices.translate(0, 0, -0.1F);
        collector.submitText(matrices, -levelTextSize, -3, levelText, false, DisplayMode.NORMAL, light, 0xFF80FF20, 0, 0);

        matrices.translate(0, 0, 0.15F);
        float nextLevelProgress = state.nextLevelProgress;
        collector.submitCustomGeometry(matrices, GuiSprites.renderType(), (pose, buffer) ->
        {
            var background = GuiSprites.get(GuiSprites.EXPERIENCE_BAR_BACKGROUND);
            renderExpBar(pose, buffer, -182 / 5F, 6, 0, 182 / 2.5F, 7 / 2.5F,
                    background.getU0(), background.getV0(), background.getU1(), background.getV1());

            var progress = GuiSprites.get(GuiSprites.EXPERIENCE_BAR_PROGRESS);
            renderExpBar(pose, buffer, -182 / 5F, 6, -0.04F, 182 / 2.5F * nextLevelProgress, 7 / 2.5F,
                    progress.getU0(), progress.getV0(), progress.getU0() + (progress.getU1() - progress.getU0()) * nextLevelProgress, progress.getV1());
        });

        matrices.popPose();
    }

    private float getAnimationDelta(long gameTime, float period, float offset, float tickDelta)
    {
        var halfPeriod = period / 2;
        var delta = (gameTime + offset) % period;

        delta = delta + tickDelta;

        if (delta >= halfPeriod)
            delta = (delta - halfPeriod) / halfPeriod;
        else
            delta = 1 - delta / halfPeriod;
        return delta;
    }

    private void renderExpBar(PoseStack.Pose pose,
                              VertexConsumer buffer,
                              float posX,
                              float posY,
                              float posZ,
                              float width,
                              float height,
                              float minU,
                              float minV,
                              float maxU,
                              float maxV)
    {
        buffer.addVertex(pose, posX, posY, posZ)
                .setColor(1, 1, 1, 0.95F)
                .setUv(minU, minV)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(-1, 0, 0);
        buffer.addVertex(pose, posX, posY + height, posZ)
                .setColor(1, 1, 1, 0.95F)
                .setUv(minU, maxV)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(-1, 0, 0);
        buffer.addVertex(pose, posX + width, posY + height, posZ)
                .setColor(1, 1, 1, 0.95F)
                .setUv(maxU, maxV)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(-1, 0, 0);
        buffer.addVertex(pose, posX + width, posY, posZ)
                .setColor(1, 1, 1, 0.95F)
                .setUv(maxU, minV)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(-1, 0, 0);
    }
}
