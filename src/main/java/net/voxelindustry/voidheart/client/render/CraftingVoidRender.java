package net.voxelindustry.voidheart.client.render;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.voxelindustry.steamlayer.math.interpolator.Interpolators;
import net.voxelindustry.voidheart.client.CustomRenderLayers;
import net.voxelindustry.voidheart.client.util.MathUtil;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import net.voxelindustry.voidheart.compat.iris.IrisCompat;
import net.voxelindustry.voidheart.compat.iris.IrisCraftingVoidRender;

import java.util.Random;

import static java.lang.Math.*;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class CraftingVoidRender
{
    private static final Random RANDOM = new Random(31100L);

    private static final Identifier ALTAR_OVERLAY_TEXTURE = new Identifier(MODID, "textures/block/altar_overlay_cube.png");

    static void renderVoidCube(VoidAltarTile altar, BlockEntityRenderDispatcher dispatcher, float worldTimeInterp, MatrixStack matrices, VertexConsumerProvider vertexConsumers)
    {
        RANDOM.setSeed(31100L);

        matrices.push();

        if (altar.getCoolProgress() > 0)
            matricesForCooling(altar, worldTimeInterp, matrices);
        else
            matricesForWarmingAndCrafting(altar, worldTimeInterp, matrices);
        double playerDistance = altar.getPos().getSquaredDistance(dispatcher.camera.getPos());
        int draws = getDrawFromPlayerDistance(playerDistance);

        float size = 12 / 16F;

        var outlineThickness = 1.015F;
        renderOutlineCubeFaces(matrices, vertexConsumers.getBuffer(CustomRenderLayers.getColorTextureTranslucent(ALTAR_OVERLAY_TEXTURE)), -size / 2 * outlineThickness, -size / 2 * outlineThickness, -size / 2 * outlineThickness, size * outlineThickness, size * outlineThickness, size * outlineThickness);

        if (IrisCompat.useIris())
            IrisCraftingVoidRender.irisEndPortalRender(matrices, vertexConsumers, OverlayTexture.DEFAULT_UV, 240);
        else
        {
            renderVoidCubeFaces(matrices, vertexConsumers.getBuffer(RenderLayer.getEndPortal()), -size / 2, -size / 2, -size / 2, size, size, size, 0.15F);

            for (int l = 1; l < draws; ++l)
                renderVoidCubeFaces(matrices, vertexConsumers.getBuffer(RenderLayer.getEndPortal()), -size / 2, -size / 2, -size / 2, size, size, size, 2.0F / (float) (18 - l));
        }

        matrices.pop();
    }

    private static void matricesForCooling(VoidAltarTile altar, float worldTimeInterp, MatrixStack matrices)
    {
        float coolingDelta = 1 - altar.getCoolProgress() / (float) VoidAltarTile.COOLING_TIME;
        float angle = 360 * (worldTimeInterp / 25);

        matrices.translate(0.5, 3, 0.5);

        matrices.multiply(MathUtil.quatFromAngleDegrees(angle, MathUtil.NEGATIVE_Y));
        matrices.multiply(MathUtil.quatFromAngleDegrees(angle, MathUtil.NEGATIVE_X));
        matrices.multiply(MathUtil.quatFromAngleDegrees(angle, MathUtil.NEGATIVE_Z));

        float scale = 1 - Interpolators.SINE_BOTH.apply(coolingDelta);
        matrices.scale(scale, scale, scale);
    }

    private static void matricesForWarmingAndCrafting(VoidAltarTile altar, float worldTimeInterp, MatrixStack matrices)
    {
        matrices.translate(0.5, 3, 0.5);

        float angle = 360 * (worldTimeInterp / 100);

        matrices.multiply(MathUtil.quatFromAngleDegrees(angle, MathUtil.NEGATIVE_Y));
        matrices.multiply(MathUtil.quatFromAngleDegrees(angle, MathUtil.NEGATIVE_X));
        matrices.multiply(MathUtil.quatFromAngleDegrees(angle, MathUtil.NEGATIVE_Z));

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

    private static void renderVoidCubeFaces(MatrixStack matrixStack, VertexConsumer buffer, float posX, float posY, float posZ, float width, float height, float length,
                                            float colorAlteration)
    {
        float red = (RANDOM.nextFloat() * 0.5F + 0.1F) * colorAlteration;
        float green = (RANDOM.nextFloat() * 0.5F + 0.4F) * colorAlteration;
        float blue = (RANDOM.nextFloat() * 0.5F + 0.5F) * colorAlteration;

        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY, posZ)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY + height, posZ)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY + height, posZ)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY, posZ)
                .color(red, green, blue, 1)
                .next();

        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY, posZ + length)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY + height, posZ + length)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY + height, posZ + length)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY, posZ + length)
                .color(red, green, blue, 1)
                .next();

        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY, posZ + length)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY + height, posZ + length)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY + height, posZ)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY, posZ)
                .color(red, green, blue, 1)
                .next();

        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY, posZ)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY + height, posZ)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY + height, posZ + length)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY, posZ + length)
                .color(red, green, blue, 1)
                .next();

        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY + height, posZ + length)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY + height, posZ + length)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY + height, posZ)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY + height, posZ)
                .color(red, green, blue, 1)
                .next();

        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY, posZ + length)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY, posZ)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY, posZ)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY, posZ + length)
                .color(red, green, blue, 1)
                .next();
    }

    private static void renderOutlineCubeFaces(MatrixStack matrixStack, VertexConsumer buffer, float posX, float posY, float posZ, float width, float height, float length)
    {
        float red = 86 / 255F;
        float green = 223 / 255F;
        float blue = 166 / 255F;
        float alpha = 0.35F;

        int light = 15728880;

        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY, posZ)
                .color(red, green, blue, alpha)
                .texture(1, 0)
                .light(light)
                .normal(0, 1, 0)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY + height, posZ)
                .color(red, green, blue, alpha)
                .texture(1, 1)
                .light(light)
                .normal(0, 1, 0)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY + height, posZ)
                .color(red, green, blue, alpha)
                .texture(0, 1)
                .light(light)
                .normal(0, 1, 0)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY, posZ)
                .color(red, green, blue, alpha)
                .texture(0, 0)
                .light(light)
                .normal(0, 1, 0)
                .next();

        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY, posZ + length)
                .color(red, green, blue, alpha)
                .texture(0, 0)
                .light(light)
                .normal(0, 0, 1)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY + height, posZ + length)
                .color(red, green, blue, alpha)
                .texture(0, 1)
                .light(light)
                .normal(0, 0, 1)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY + height, posZ + length)
                .color(red, green, blue, alpha)
                .texture(1, 1)
                .light(light)
                .normal(0, 0, 1)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY, posZ + length)
                .color(red, green, blue, alpha)
                .texture(1, 0)
                .light(light)
                .normal(0, 0, 1)
                .next();

        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY, posZ)
                .color(red, green, blue, alpha)
                .texture(0, 0)
                .light(light)
                .normal(0, 0, -1)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY + height, posZ)
                .color(red, green, blue, alpha)
                .texture(1, 0)
                .light(light)
                .normal(0, 0, -1)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY + height, posZ + length)
                .color(red, green, blue, alpha)
                .texture(1, 1)
                .light(light)
                .normal(0, 0, -1)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY, posZ + length)
                .color(red, green, blue, alpha)
                .texture(0, 1)
                .light(light)
                .normal(0, 0, -1)
                .next();

        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY, posZ + length)
                .color(red, green, blue, alpha)
                .texture(0, 1)
                .light(light)
                .normal(0, -1, 0)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY + height, posZ + length)
                .color(red, green, blue, alpha)
                .texture(1, 1)
                .light(light)
                .normal(0, -1, 0)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY + height, posZ)
                .color(red, green, blue, alpha)
                .texture(1, 0)
                .light(light)
                .normal(0, -1, 0)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY, posZ)
                .color(red, green, blue, alpha)
                .texture(0, 0)
                .light(light)
                .normal(0, -1, 0)
                .next();

        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY + height, posZ)
                .color(red, green, blue, alpha)
                .texture(0, 0)
                .light(light)
                .normal(0, 1, 0)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY + height, posZ)
                .color(red, green, blue, alpha)
                .texture(1, 0)
                .light(light)
                .normal(0, 1, 0)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY + height, posZ + length)
                .color(red, green, blue, alpha)
                .texture(1, 1)
                .light(light)
                .normal(0, 1, 0)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY + height, posZ + length)
                .color(red, green, blue, alpha)
                .texture(0, 1)
                .light(light)
                .normal(0, 1, 0)
                .next();

        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY, posZ + length)
                .color(red, green, blue, alpha)
                .texture(1, 1)
                .light(light)
                .normal(-1, 0, 0)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY, posZ)
                .color(red, green, blue, alpha)
                .texture(1, 0)
                .light(light)
                .normal(-1, 0, 0)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY, posZ)
                .color(red, green, blue, alpha)
                .texture(0, 0)
                .light(light)
                .normal(-1, 0, 0)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY, posZ + length)
                .color(red, green, blue, alpha)
                .texture(0, 1)
                .light(light)
                .normal(-1, 0, 0)
                .next();
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
