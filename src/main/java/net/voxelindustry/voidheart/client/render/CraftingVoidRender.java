package net.voxelindustry.voidheart.client.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector3f;
import net.voxelindustry.voidheart.common.tile.VoidAltarTile;

import java.util.Random;

public class CraftingVoidRender
{
    private static final Random RANDOM = new Random(31100L);

    static void renderVoidCube(VoidAltarTile altar, BlockEntityRenderDispatcher dispatcher, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers)
    {
        RANDOM.setSeed(31100L);

        matrices.push();

        matrices.translate(0.5, 3, 0.5);
        matrices.multiply(Vector3f.NEGATIVE_Y.getDegreesQuaternion(altar.getWorld().getTime() * 2 + tickDelta));
        matrices.multiply(Vector3f.NEGATIVE_X.getDegreesQuaternion(altar.getWorld().getTime() * 2 + tickDelta));
        matrices.multiply(Vector3f.NEGATIVE_Z.getDegreesQuaternion(altar.getWorld().getTime() * 2 + tickDelta));

        double playerDistance = altar.getPos().getSquaredDistance(dispatcher.camera.getPos(), true);
        int draws = getDrawFromPlayerDistance(playerDistance);

        float size = 12 / 16F;

        renderVoidCube(matrices, vertexConsumers.getBuffer(RenderLayer.getEndPortal(1)), -size / 2, -size / 2, -size / 2, size, size, size, 0.15F);

        for (int l = 1; l < draws; ++l)
            renderVoidCube(matrices, vertexConsumers.getBuffer(RenderLayer.getEndPortal(l + 1)), -size / 2, -size / 2, -size / 2, size, size, size, 2.0F / (float) (18 - l));

        matrices.pop();
    }


    private static void renderVoidCube(MatrixStack matrixStack, VertexConsumer buffer, float posX, float posY, float posZ, float width, float height, float length,
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
