package net.voxelindustry.voidheart.client.render;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.voxelindustry.steamlayer.math.interpolator.Interpolators;
import net.voxelindustry.voidheart.client.CustomRenderLayers;
import net.voxelindustry.voidheart.client.util.ClientConstants;
import net.voxelindustry.voidheart.client.util.MathUtil;
import net.voxelindustry.voidheart.common.block.StateProperties;
import net.voxelindustry.voidheart.common.content.repair.ExperienceSkullTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.util.ExperienceUtil;
import net.voxelindustry.voidheart.compat.immportal.ImmersivePortalCompat;
import org.joml.Math;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

public class ExperienceSkullRender implements BlockEntityRenderer<ExperienceSkullTile>
{
    private final Map<Direction, BlockState> experienceSkullStateCache = new HashMap<>();

    private static BlockState createSkullState(Direction direction)
    {
        return VoidHeartBlocks.EXPERIENCE_SKULL.getDefaultState()
                .with(StateProperties.MODEL, true)
                .with(Properties.HORIZONTAL_FACING, direction);
    }

    private BlockState getSkullState(Direction direction)
    {
        return experienceSkullStateCache.computeIfAbsent(direction, ExperienceSkullRender::createSkullState);
    }

    @Override
    public void render(ExperienceSkullTile skull, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)
    {
        matrices.push();

        matrices.translate(0.5, 0.5, 0.5);
        float rotationVerticalDelta = getAnimationDelta(skull, 160, 80, tickDelta);
        float rotationHorizontalDelta = getAnimationDelta(skull, 100, 0, tickDelta);
        matrices.multiply(new Quaternionf().rotationXYZ(Math.toRadians(15 * rotationVerticalDelta - 7.5F), Math.toRadians(10 * rotationHorizontalDelta - 5), 0));
        matrices.translate(-0.5, -0.5, -0.5);

        float translateDelta = getAnimationDelta(skull, 160, 0, tickDelta);
        matrices.translate(0, Interpolators.QUAD_BOTH.apply(translateDelta) * 0.5, 0);

        var buffer = vertexConsumers.getBuffer(RenderLayer.getSolid());
        MinecraftClient.getInstance().getBlockRenderManager().renderBlock(
                getSkullState(skull.getCachedState().get(Properties.HORIZONTAL_FACING)),
                skull.getPos(),
                skull.getWorld(),
                matrices,
                buffer,
                true,
                skull.getWorld().getRandom());
        matrices.pop();

        if (ImmersivePortalCompat.areWeRenderedByPortal())
            return;

        matrices.push();
        matrices.translate(0.5, 0.8, 0.5);
        matrices.translate(0, Interpolators.QUAD_BOTH.apply(translateDelta) * 0.5, 0);
        matrices.scale(1.25F / 64F, 1.25F / 64F, 1.25F / 64F);

        matrices.multiply(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation());
        matrices.multiply(MathUtil.quatFromAngleDegrees(180, MathUtil.POSITIVE_Z));

        var textRenderer = MinecraftClient.getInstance().textRenderer;

        var levelText = String.valueOf(ExperienceUtil.getExperienceLevel(skull.getExperience()));
        var levelTextSize = textRenderer.getWidth(levelText) / 2F;

        var positionMatrix = matrices.peek().getPositionMatrix();

        MinecraftClient.getInstance().textRenderer.draw(levelText, -levelTextSize - 1, -3, 0, false, positionMatrix, vertexConsumers, TextLayerType.NORMAL, 0, light);
        MinecraftClient.getInstance().textRenderer.draw(levelText, -levelTextSize + 1, -3, 0, false, positionMatrix, vertexConsumers, TextLayerType.NORMAL, 0, light);
        MinecraftClient.getInstance().textRenderer.draw(levelText, -levelTextSize, -4, 0, false, positionMatrix, vertexConsumers, TextLayerType.NORMAL, 0, light);
        MinecraftClient.getInstance().textRenderer.draw(levelText, -levelTextSize, -2, 0, false, positionMatrix, vertexConsumers, TextLayerType.NORMAL, 0, light);


        matrices.translate(0, 0, -0.1F);
        positionMatrix = matrices.peek().getPositionMatrix();
        MinecraftClient.getInstance().textRenderer.draw(levelText, -levelTextSize, -3, 8453920, false, positionMatrix, vertexConsumers, TextLayerType.NORMAL, 0, light);

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

        matrices.pop();
    }

    private float getAnimationDelta(ExperienceSkullTile skull, float period, float offset, float tickDelta)
    {
        var halfPeriod = period / 2;
        var delta = (skull.getWorld().getTime() + offset) % period;

        delta = MathHelper.lerp(tickDelta, delta, delta + 1);

        if (delta >= halfPeriod)
            delta = (delta - halfPeriod) / halfPeriod;
        else
            delta = 1 - delta / halfPeriod;
        return delta;
    }

    private void renderExpBar(MatrixStack matrixStack,
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
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY, posZ)
                .color(1, 1, 1, 0.95F)
                .texture(minU, minV)
                .light(240, 240)
                .normal(-1, 0, 0)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY + height, posZ)
                .color(1, 1, 1, 0.95F)
                .texture(minU, maxV)
                .light(240, 240)
                .normal(-1, 0, 0)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY + height, posZ)
                .color(1, 1, 1, 0.95F)
                .texture(maxU, maxV)
                .light(240, 240)
                .normal(-1, 0, 0)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY, posZ)
                .color(1, 1, 1, 0.95F)
                .texture(maxU, minV)
                .light(240, 240)
                .normal(-1, 0, 0)
                .next();
    }
}