package net.voxelindustry.voidheart.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.voxelindustry.steamlayer.math.interpolator.Interpolators;
import net.voxelindustry.voidheart.client.CustomRenderLayers;
import net.voxelindustry.voidheart.client.util.ClientConstants;
import net.voxelindustry.voidheart.client.util.MathUtil;
import net.voxelindustry.voidheart.common.block.StateProperties;
import net.voxelindustry.voidheart.common.content.repair.ExperienceSkullTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.util.ExperienceUtil;
import org.joml.Math;
import org.joml.Quaternionf;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;

public class ExperienceSkullRender implements BlockEntityRenderer<ExperienceSkullTile>
{
    private final Map<Direction, BlockState> experienceSkullStateCache = new HashMap<>();

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
    public void render(ExperienceSkullTile skull, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay)
    {
        matrices.pushPose();

        matrices.translate(0.5, 0.5, 0.5);
        float rotationVerticalDelta = getAnimationDelta(skull, 160, 80, tickDelta);
        float rotationHorizontalDelta = getAnimationDelta(skull, 100, 0, tickDelta);
        matrices.mulPose(new Quaternionf().rotationXYZ(Math.toRadians(15 * rotationVerticalDelta - 7.5F), Math.toRadians(10 * rotationHorizontalDelta - 5), 0));
        matrices.translate(-0.5, -0.5, -0.5);

        float translateDelta = getAnimationDelta(skull, 160, 0, tickDelta);
        matrices.translate(0, Interpolators.QUAD_BOTH.apply(translateDelta) * 0.5, 0);

        var buffer = vertexConsumers.getBuffer(RenderType.solid());
        Minecraft.getInstance().getBlockRenderer().renderBatched(
                getSkullState(skull.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING)),
                skull.getBlockPos(),
                skull.getLevel(),
                matrices,
                buffer,
                true,
                skull.getLevel().getRandom());
        matrices.popPose();

        matrices.pushPose();
        matrices.translate(0.5, 0.8, 0.5);
        matrices.translate(0, Interpolators.QUAD_BOTH.apply(translateDelta) * 0.5, 0);
        matrices.scale(1.25F / 64F, 1.25F / 64F, 1.25F / 64F);

        matrices.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        matrices.mulPose(MathUtil.quatFromAngleDegrees(180, MathUtil.POSITIVE_Z));

        var textRenderer = Minecraft.getInstance().font;

        var levelText = String.valueOf(ExperienceUtil.getExperienceLevel(skull.getExperience()));
        var levelTextSize = textRenderer.width(levelText) / 2F;

        var positionMatrix = matrices.last().pose();

        Minecraft.getInstance().font.drawInBatch(levelText, -levelTextSize - 1, -3, 0, false, positionMatrix, vertexConsumers, DisplayMode.NORMAL, 0, light);
        Minecraft.getInstance().font.drawInBatch(levelText, -levelTextSize + 1, -3, 0, false, positionMatrix, vertexConsumers, DisplayMode.NORMAL, 0, light);
        Minecraft.getInstance().font.drawInBatch(levelText, -levelTextSize, -4, 0, false, positionMatrix, vertexConsumers, DisplayMode.NORMAL, 0, light);
        Minecraft.getInstance().font.drawInBatch(levelText, -levelTextSize, -2, 0, false, positionMatrix, vertexConsumers, DisplayMode.NORMAL, 0, light);


        matrices.translate(0, 0, -0.1F);
        positionMatrix = matrices.last().pose();
        Minecraft.getInstance().font.drawInBatch(levelText, -levelTextSize, -3, 8453920, false, positionMatrix, vertexConsumers, DisplayMode.NORMAL, 0, light);

        matrices.translate(0, 0, 0.15F);
        var expRenderBuffer = vertexConsumers.getBuffer(CustomRenderLayers.getColorTextureTranslucent(ClientConstants.GUI_ICONS_TEXTURE));
        renderExpBar(matrices,
                expRenderBuffer,
                -182 / 5F,
                6,
                0,
                182 / 2.5F,
                7 / 2.5F,
                0,
                0.25F,
                0.7109375F,
                0.25F + 0.01953125F);

        float nextLevelProgress = ExperienceUtil.nextLevelProgress(skull.getExperience());
        renderExpBar(matrices,
                expRenderBuffer,
                -182 / 5F,
                6,
                -0.04F,
                182 / 2.5F * nextLevelProgress,
                7 / 2.5F,
                0,
                (69 / 256F),
                0.7109375F * nextLevelProgress,
                (69 / 256F) + 0.01953125F);

        matrices.popPose();
    }

    private float getAnimationDelta(ExperienceSkullTile skull, float period, float offset, float tickDelta)
    {
        var halfPeriod = period / 2;
        var delta = (skull.getLevel().getGameTime() + offset) % period;

        delta = Mth.lerp(tickDelta, delta, delta + 1);

        if (delta >= halfPeriod)
            delta = (delta - halfPeriod) / halfPeriod;
        else
            delta = 1 - delta / halfPeriod;
        return delta;
    }

    private void renderExpBar(PoseStack matrixStack,
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
        buffer.vertex(matrixStack.last().pose(), posX, posY, posZ)
                .color(1, 1, 1, 0.95F)
                .uv(minU, minV)
                .uv2(240, 240)
                .normal(-1, 0, 0)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX, posY + height, posZ)
                .color(1, 1, 1, 0.95F)
                .uv(minU, maxV)
                .uv2(240, 240)
                .normal(-1, 0, 0)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY + height, posZ)
                .color(1, 1, 1, 0.95F)
                .uv(maxU, maxV)
                .uv2(240, 240)
                .normal(-1, 0, 0)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY, posZ)
                .color(1, 1, 1, 0.95F)
                .uv(maxU, minV)
                .uv2(240, 240)
                .normal(-1, 0, 0)
                .endVertex();
    }
}