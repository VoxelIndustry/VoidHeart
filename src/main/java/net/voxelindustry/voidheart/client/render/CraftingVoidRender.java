package net.voxelindustry.voidheart.client.render;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.voxelindustry.steamlayer.math.interpolator.Interpolators;
import net.voxelindustry.voidheart.client.CustomRenderLayers;
import net.voxelindustry.voidheart.client.util.MathUtil;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import net.voxelindustry.voidheart.compat.iris.IrisCompat;
import net.voxelindustry.voidheart.compat.iris.IrisCraftingVoidRender;

import java.util.Random;

import static java.lang.Math.*;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public class CraftingVoidRender
{
    private static final Random RANDOM = new Random(31100L);

    private static final ResourceLocation ALTAR_OVERLAY_TEXTURE = new ResourceLocation(MODID, "textures/block/altar_overlay_cube.png");

    static void renderVoidCube(VoidAltarTile altar, BlockEntityRenderDispatcher dispatcher, float worldTimeInterp, PoseStack matrices, MultiBufferSource vertexConsumers)
    {
        RANDOM.setSeed(31100L);

        matrices.pushPose();

        if (altar.getCoolProgress() > 0)
            matricesForCooling(altar, worldTimeInterp, matrices);
        else
            matricesForWarmingAndCrafting(altar, worldTimeInterp, matrices);
        double playerDistance = altar.getBlockPos().distToCenterSqr(dispatcher.camera.getPosition());
        int draws = getDrawFromPlayerDistance(playerDistance);

        float size = 12 / 16F;

        var outlineThickness = 1.015F;
        renderOutlineCubeFaces(matrices, vertexConsumers.getBuffer(CustomRenderLayers.getColorTextureTranslucent(ALTAR_OVERLAY_TEXTURE)), -size / 2 * outlineThickness, -size / 2 * outlineThickness, -size / 2 * outlineThickness, size * outlineThickness, size * outlineThickness, size * outlineThickness);

        if (IrisCompat.useIris())
            IrisCraftingVoidRender.irisEndPortalRender(matrices, vertexConsumers, OverlayTexture.NO_OVERLAY, 240);
        else
        {
            renderVoidCubeFaces(matrices, vertexConsumers.getBuffer(RenderType.endPortal()), -size / 2, -size / 2, -size / 2, size, size, size, 0.15F);

            for (int l = 1; l < draws; ++l)
                renderVoidCubeFaces(matrices, vertexConsumers.getBuffer(RenderType.endPortal()), -size / 2, -size / 2, -size / 2, size, size, size, 2.0F / (float) (18 - l));
        }

        matrices.popPose();
    }

    private static void matricesForCooling(VoidAltarTile altar, float worldTimeInterp, PoseStack matrices)
    {
        float coolingDelta = 1 - altar.getCoolProgress() / (float) VoidAltarTile.COOLING_TIME;
        float angle = 360 * (worldTimeInterp / 25);

        matrices.translate(0.5, 3, 0.5);

        matrices.mulPose(MathUtil.quatFromAngleDegrees(angle, MathUtil.NEGATIVE_Y));
        matrices.mulPose(MathUtil.quatFromAngleDegrees(angle, MathUtil.NEGATIVE_X));
        matrices.mulPose(MathUtil.quatFromAngleDegrees(angle, MathUtil.NEGATIVE_Z));

        float scale = 1 - Interpolators.SINE_BOTH.apply(coolingDelta);
        matrices.scale(scale, scale, scale);
    }

    private static void matricesForWarmingAndCrafting(VoidAltarTile altar, float worldTimeInterp, PoseStack matrices)
    {
        matrices.translate(0.5, 3, 0.5);

        float angle = 360 * (worldTimeInterp / 100);

        matrices.mulPose(MathUtil.quatFromAngleDegrees(angle, MathUtil.NEGATIVE_Y));
        matrices.mulPose(MathUtil.quatFromAngleDegrees(angle, MathUtil.NEGATIVE_X));
        matrices.mulPose(MathUtil.quatFromAngleDegrees(angle, MathUtil.NEGATIVE_Z));

        float scale;
        if (altar.getWarmProgress() < VoidAltarTile.WARMING_TIME)
            scale = max(1 / 16F, MathUtil.interpolateBounce(altar.getWarmProgress() / (float) VoidAltarTile.WARMING_TIME));
        else
        {
            float sinHeight = 8;
            float amplitude = 5;
            scale = (float) (1 - abs(sin(worldTimeInterp / amplitude)) / sinHeight);
        }
        matrices.scale(scale, scale, scale);
    }

    private static void renderVoidCubeFaces(PoseStack matrixStack, VertexConsumer buffer, float posX, float posY, float posZ, float width, float height, float length,
                                            float colorAlteration)
    {
        float red = (RANDOM.nextFloat() * 0.5F + 0.1F) * colorAlteration;
        float green = (RANDOM.nextFloat() * 0.5F + 0.4F) * colorAlteration;
        float blue = (RANDOM.nextFloat() * 0.5F + 0.5F) * colorAlteration;

        buffer.vertex(matrixStack.last().pose(), posX, posY, posZ)
                .color(red, green, blue, 1)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX, posY + height, posZ)
                .color(red, green, blue, 1)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY + height, posZ)
                .color(red, green, blue, 1)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY, posZ)
                .color(red, green, blue, 1)
                .endVertex();

        buffer.vertex(matrixStack.last().pose(), posX + width, posY, posZ + length)
                .color(red, green, blue, 1)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY + height, posZ + length)
                .color(red, green, blue, 1)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX, posY + height, posZ + length)
                .color(red, green, blue, 1)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX, posY, posZ + length)
                .color(red, green, blue, 1)
                .endVertex();

        buffer.vertex(matrixStack.last().pose(), posX, posY, posZ + length)
                .color(red, green, blue, 1)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX, posY + height, posZ + length)
                .color(red, green, blue, 1)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX, posY + height, posZ)
                .color(red, green, blue, 1)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX, posY, posZ)
                .color(red, green, blue, 1)
                .endVertex();

        buffer.vertex(matrixStack.last().pose(), posX + width, posY, posZ)
                .color(red, green, blue, 1)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY + height, posZ)
                .color(red, green, blue, 1)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY + height, posZ + length)
                .color(red, green, blue, 1)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY, posZ + length)
                .color(red, green, blue, 1)
                .endVertex();

        buffer.vertex(matrixStack.last().pose(), posX, posY + height, posZ + length)
                .color(red, green, blue, 1)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY + height, posZ + length)
                .color(red, green, blue, 1)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY + height, posZ)
                .color(red, green, blue, 1)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX, posY + height, posZ)
                .color(red, green, blue, 1)
                .endVertex();

        buffer.vertex(matrixStack.last().pose(), posX, posY, posZ + length)
                .color(red, green, blue, 1)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX, posY, posZ)
                .color(red, green, blue, 1)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY, posZ)
                .color(red, green, blue, 1)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY, posZ + length)
                .color(red, green, blue, 1)
                .endVertex();
    }

    private static void renderOutlineCubeFaces(PoseStack matrixStack, VertexConsumer buffer, float posX, float posY, float posZ, float width, float height, float length)
    {
        float red = 86 / 255F;
        float green = 223 / 255F;
        float blue = 166 / 255F;
        float alpha = 0.35F;

        int light = 15728880;

        buffer.vertex(matrixStack.last().pose(), posX + width, posY, posZ)
                .color(red, green, blue, alpha)
                .uv(1, 0)
                .uv2(light)
                .normal(0, 1, 0)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY + height, posZ)
                .color(red, green, blue, alpha)
                .uv(1, 1)
                .uv2(light)
                .normal(0, 1, 0)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX, posY + height, posZ)
                .color(red, green, blue, alpha)
                .uv(0, 1)
                .uv2(light)
                .normal(0, 1, 0)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX, posY, posZ)
                .color(red, green, blue, alpha)
                .uv(0, 0)
                .uv2(light)
                .normal(0, 1, 0)
                .endVertex();

        buffer.vertex(matrixStack.last().pose(), posX, posY, posZ + length)
                .color(red, green, blue, alpha)
                .uv(0, 0)
                .uv2(light)
                .normal(0, 0, 1)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX, posY + height, posZ + length)
                .color(red, green, blue, alpha)
                .uv(0, 1)
                .uv2(light)
                .normal(0, 0, 1)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY + height, posZ + length)
                .color(red, green, blue, alpha)
                .uv(1, 1)
                .uv2(light)
                .normal(0, 0, 1)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY, posZ + length)
                .color(red, green, blue, alpha)
                .uv(1, 0)
                .uv2(light)
                .normal(0, 0, 1)
                .endVertex();

        buffer.vertex(matrixStack.last().pose(), posX, posY, posZ)
                .color(red, green, blue, alpha)
                .uv(0, 0)
                .uv2(light)
                .normal(0, 0, -1)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX, posY + height, posZ)
                .color(red, green, blue, alpha)
                .uv(1, 0)
                .uv2(light)
                .normal(0, 0, -1)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX, posY + height, posZ + length)
                .color(red, green, blue, alpha)
                .uv(1, 1)
                .uv2(light)
                .normal(0, 0, -1)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX, posY, posZ + length)
                .color(red, green, blue, alpha)
                .uv(0, 1)
                .uv2(light)
                .normal(0, 0, -1)
                .endVertex();

        buffer.vertex(matrixStack.last().pose(), posX + width, posY, posZ + length)
                .color(red, green, blue, alpha)
                .uv(0, 1)
                .uv2(light)
                .normal(0, -1, 0)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY + height, posZ + length)
                .color(red, green, blue, alpha)
                .uv(1, 1)
                .uv2(light)
                .normal(0, -1, 0)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY + height, posZ)
                .color(red, green, blue, alpha)
                .uv(1, 0)
                .uv2(light)
                .normal(0, -1, 0)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY, posZ)
                .color(red, green, blue, alpha)
                .uv(0, 0)
                .uv2(light)
                .normal(0, -1, 0)
                .endVertex();

        buffer.vertex(matrixStack.last().pose(), posX, posY + height, posZ)
                .color(red, green, blue, alpha)
                .uv(0, 0)
                .uv2(light)
                .normal(0, 1, 0)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY + height, posZ)
                .color(red, green, blue, alpha)
                .uv(1, 0)
                .uv2(light)
                .normal(0, 1, 0)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY + height, posZ + length)
                .color(red, green, blue, alpha)
                .uv(1, 1)
                .uv2(light)
                .normal(0, 1, 0)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX, posY + height, posZ + length)
                .color(red, green, blue, alpha)
                .uv(0, 1)
                .uv2(light)
                .normal(0, 1, 0)
                .endVertex();

        buffer.vertex(matrixStack.last().pose(), posX + width, posY, posZ + length)
                .color(red, green, blue, alpha)
                .uv(1, 1)
                .uv2(light)
                .normal(-1, 0, 0)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY, posZ)
                .color(red, green, blue, alpha)
                .uv(1, 0)
                .uv2(light)
                .normal(-1, 0, 0)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX, posY, posZ)
                .color(red, green, blue, alpha)
                .uv(0, 0)
                .uv2(light)
                .normal(-1, 0, 0)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX, posY, posZ + length)
                .color(red, green, blue, alpha)
                .uv(0, 1)
                .uv2(light)
                .normal(-1, 0, 0)
                .endVertex();
    }

    private static int getDrawFromPlayerDistance(double playerDistance)
    {
        if (playerDistance > 36864)
            return 1;
        else if (playerDistance > 25600)
            return 3;
        else if (playerDistance > 16384)
            return 5;
        else if (playerDistance > 9216)
            return 7;
        else if (playerDistance > 4096)
            return 9;
        else if (playerDistance > 1024)
            return 11;
        else if (playerDistance > 576)
            return 13;
        else
            return playerDistance > 256 ? 14 : 15;
    }
}
