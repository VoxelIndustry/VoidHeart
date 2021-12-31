package net.voxelindustry.voidheart.client.render;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Quaternion;
import net.minecraft.util.math.Vec3f;
import net.voxelindustry.steamlayer.math.interpolator.Interpolators;
import net.voxelindustry.voidheart.client.CustomRenderLayers;
import net.voxelindustry.voidheart.client.util.ImmersivePortalUtil;
import net.voxelindustry.voidheart.common.block.StateProperties;
import net.voxelindustry.voidheart.common.content.repair.ExperienceSkullTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.util.ExperienceUtil;

import java.util.HashMap;
import java.util.Map;

public class ExperienceSkullRender implements BlockEntityRenderer<ExperienceSkullTile>
{
    private Map<Direction, BlockState> experienceSkullStateCache = new HashMap<>();

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
        matrices.multiply(Quaternion.fromEulerXyz((float) Math.toRadians(15 * rotationVerticalDelta - 7.5), (float) Math.toRadians(10 * rotationHorizontalDelta - 5), 0));
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

        if (ImmersivePortalUtil.areWeRenderedByPortal())
            return;

        matrices.push();
        matrices.translate(0.5, 0.8, 0.5);
        matrices.translate(0, Interpolators.QUAD_BOTH.apply(translateDelta) * 0.5, 0);
        matrices.scale(1.25F / 64F, 1.25F / 64F, 1.25F / 64F);

        matrices.multiply(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation());
        matrices.multiply(Vec3f.POSITIVE_Z.getDegreesQuaternion(180));

        var textRenderer = MinecraftClient.getInstance().textRenderer;

        var levelText = String.valueOf(ExperienceUtil.getExperienceLevel(skull.getExperience()));
        var levelTextSize = textRenderer.getWidth(levelText) / 2F;
        textRenderer.draw(matrices, levelText, -levelTextSize - 1, 0, 0);
        textRenderer.draw(matrices, levelText, -levelTextSize + 1, 0, 0);
        textRenderer.draw(matrices, levelText, -levelTextSize, -1, 0);
        textRenderer.draw(matrices, levelText, -levelTextSize, 1, 0);

        matrices.translate(0, 0, -0.1F);
        textRenderer.draw(matrices, levelText, -levelTextSize, 0, 8453920);

        matrices.translate(0, 0, 0.15F);
        var expRenderBuffer = vertexConsumers.getBuffer(CustomRenderLayers.getColorTextureTranslucent(DrawableHelper.GUI_ICONS_TEXTURE));
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