package net.voxelindustry.voidheart.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3f;
import net.voxelindustry.voidheart.client.CustomRenderLayers;
import net.voxelindustry.voidheart.client.util.ImmersivePortalUtil;
import net.voxelindustry.voidheart.common.content.repair.ExperienceSkullTile;
import net.voxelindustry.voidheart.common.util.ExperienceUtil;

public class ExperienceSkullRender implements BlockEntityRenderer<ExperienceSkullTile>
{
    @Override
    public void render(ExperienceSkullTile skull, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)
    {
        if (ImmersivePortalUtil.areWeRenderedByPortal())
            return;

        matrices.push();
        matrices.translate(0.5, 0.8, 0.5);
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