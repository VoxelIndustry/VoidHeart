package net.voxelindustry.voidheart.client.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector3f;
import net.voxelindustry.steamlayer.math.interpolator.Interpolators;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;

import java.util.Random;

import static java.lang.Math.max;
import static java.lang.Math.pow;

public class CraftingVoidRender
{
    private static final Random RANDOM = new Random(31100L);

    static void renderVoidCube(VoidAltarTile altar, BlockEntityRenderDispatcher dispatcher, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers)
    {
        RANDOM.setSeed(31100L);

        matrices.push();

        if (altar.getCoolProgress() > 0)
            matricesForCooling(altar, tickDelta, matrices);
        else
            matricesForWarmingAndCrafting(altar, tickDelta, matrices);
        double playerDistance = altar.getPos().getSquaredDistance(dispatcher.camera.getPos(), true);
        int draws = getDrawFromPlayerDistance(playerDistance);

        float size = 12 / 16F;

        renderVoidCubeFaces(matrices, vertexConsumers.getBuffer(RenderLayer.getEndPortal(1)), -size / 2, -size / 2, -size / 2, size, size, size, 0.15F);

        for (int l = 1; l < draws; ++l)
            renderVoidCubeFaces(matrices, vertexConsumers.getBuffer(RenderLayer.getEndPortal(l + 1)), -size / 2, -size / 2, -size / 2, size, size, size, 2.0F / (float) (18 - l));

        matrices.pop();
    }

    private static void matricesForCooling(VoidAltarTile altar, float tickDelta, MatrixStack matrices)
    {
        float coolingDelta = 1 - altar.getCoolProgress() / (float) VoidAltarTile.COOLING_TIME;
        float angle = 360 * ((altar.getWorld().getTime() + tickDelta) / 25);

        matrices.translate(0.5, 3, 0.5);
        matrices.multiply(Vector3f.NEGATIVE_Y.getDegreesQuaternion(angle));
        matrices.multiply(Vector3f.NEGATIVE_X.getDegreesQuaternion(angle));
        matrices.multiply(Vector3f.NEGATIVE_Z.getDegreesQuaternion(angle));

        float scale = 1 - Interpolators.SINE_BOTH.apply(coolingDelta);
        matrices.scale(scale, scale, scale);
    }

    private static void matricesForWarmingAndCrafting(VoidAltarTile altar, float tickDelta, MatrixStack matrices)
    {
        matrices.translate(0.5, 3, 0.5);

        float angle = 360 * ((altar.getWorld().getTime() + tickDelta) / 100);

        matrices.multiply(Vector3f.NEGATIVE_Y.getDegreesQuaternion(angle));
        matrices.multiply(Vector3f.NEGATIVE_X.getDegreesQuaternion(angle));
        matrices.multiply(Vector3f.NEGATIVE_Z.getDegreesQuaternion(angle));

        float scale = max(1 / 16F, interpolateBounce(altar.getWarmProgress() / (float) VoidAltarTile.WARMING_TIME));
        matrices.scale(scale, scale, scale);
    }

    private static float interpolateBounce(float delta)
    {
        if (delta > 1)
            return 1;

        float a = 0;
        float b = 1;
        while (!(delta >= (7 - 4 * a) / 11D))
        {
            a += b;
            b /= 2;
        }
        return (float) (-pow((11 - 6 * a - 11 * delta) / 4, 2) + pow(b, 2));
    }

    private static void renderVoidCubeFaces(MatrixStack matrixStack, VertexConsumer buffer, float posX, float posY, float posZ, float width, float height, float length,
                                            float colorAlteration)
    {
        float red = (RANDOM.nextFloat() * 0.5F + 0.1F) * colorAlteration;
        float green = (RANDOM.nextFloat() * 0.5F + 0.4F) * colorAlteration;
        float blue = (RANDOM.nextFloat() * 0.5F + 0.5F) * colorAlteration;

        buffer.vertex(matrixStack.peek().getModel(), posX, posY, posZ)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getModel(), posX, posY + height, posZ)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getModel(), posX + width, posY + height, posZ)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getModel(), posX + width, posY, posZ)
                .color(red, green, blue, 1)
                .next();

        buffer.vertex(matrixStack.peek().getModel(), posX + width, posY, posZ + length)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getModel(), posX + width, posY + height, posZ + length)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getModel(), posX, posY + height, posZ + length)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getModel(), posX, posY, posZ + length)
                .color(red, green, blue, 1)
                .next();

        buffer.vertex(matrixStack.peek().getModel(), posX, posY, posZ + length)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getModel(), posX, posY + height, posZ + length)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getModel(), posX, posY + height, posZ)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getModel(), posX, posY, posZ)
                .color(red, green, blue, 1)
                .next();

        buffer.vertex(matrixStack.peek().getModel(), posX + width, posY, posZ)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getModel(), posX + width, posY + height, posZ)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getModel(), posX + width, posY + height, posZ + length)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getModel(), posX + width, posY, posZ + length)
                .color(red, green, blue, 1)
                .next();

        buffer.vertex(matrixStack.peek().getModel(), posX, posY + height, posZ + length)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getModel(), posX + width, posY + height, posZ + length)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getModel(), posX + width, posY + height, posZ)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getModel(), posX, posY + height, posZ)
                .color(red, green, blue, 1)
                .next();

        buffer.vertex(matrixStack.peek().getModel(), posX, posY, posZ + length)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getModel(), posX, posY, posZ)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getModel(), posX + width, posY, posZ)
                .color(red, green, blue, 1)
                .next();
        buffer.vertex(matrixStack.peek().getModel(), posX + width, posY, posZ + length)
                .color(red, green, blue, 1)
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
