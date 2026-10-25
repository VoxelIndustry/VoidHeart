package net.voxelindustry.voidheart.client.render;

import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.voxelindustry.voidheart.client.render.state.VoidAltarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.voxelindustry.steamlayer.math.interpolator.Interpolators;
import net.voxelindustry.voidheart.client.util.MathUtil;
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

    private static final Identifier ALTAR_OVERLAY_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/block/altar_overlay_cube.png");

    static void submitVoidCube(VoidAltarRenderState state, PoseStack matrices, SubmitNodeCollector collector)
    {
        matrices.pushPose();

        if (state.coolProgress > 0)
            matricesForCooling(state, matrices);
        else
            matricesForWarmingAndCrafting(state, matrices);
        int draws = getDrawFromPlayerDistance(state.cameraDistanceSqr);

        float size = 12 / 16F;

        var outlineThickness = 1.015F;
        collector.submitCustomGeometry(matrices, RenderTypes.entityTranslucent(ALTAR_OVERLAY_TEXTURE), (pose, buffer) ->
                renderOutlineCubeFaces(pose, buffer, -size / 2 * outlineThickness, -size / 2 * outlineThickness, -size / 2 * outlineThickness, size * outlineThickness, size * outlineThickness, size * outlineThickness));

        if (IrisCompat.useIris())
            IrisCraftingVoidRender.irisEndPortalRender(matrices, collector, OverlayTexture.NO_OVERLAY, 240);
        else
        {
            collector.submitCustomGeometry(matrices, RenderTypes.endPortal(), (pose, buffer) ->
            {
                RANDOM.setSeed(31100L);
                renderVoidCubeFaces(pose, buffer, -size / 2, -size / 2, -size / 2, size, size, size, 0.15F);

                for (int l = 1; l < draws; ++l)
                    renderVoidCubeFaces(pose, buffer, -size / 2, -size / 2, -size / 2, size, size, size, 2.0F / (float) (18 - l));
            });
        }

        matrices.popPose();
    }

    private static void matricesForCooling(VoidAltarRenderState state, PoseStack matrices)
    {
        float coolingDelta = 1 - state.coolProgress / (float) VoidAltarTile.COOLING_TIME;
        float angle = 360 * (state.worldTime / 25);

        matrices.translate(0.5, 3, 0.5);

        matrices.rotate(MathUtil.quatFromAngleDegrees(angle, MathUtil.NEGATIVE_Y));
        matrices.rotate(MathUtil.quatFromAngleDegrees(angle, MathUtil.NEGATIVE_X));
        matrices.rotate(MathUtil.quatFromAngleDegrees(angle, MathUtil.NEGATIVE_Z));

        float scale = 1 - Interpolators.SINE_BOTH.apply(coolingDelta);
        matrices.scale(scale, scale, scale);
    }

    private static void matricesForWarmingAndCrafting(VoidAltarRenderState state, PoseStack matrices)
    {
        matrices.translate(0.5, 3, 0.5);

        float angle = 360 * (state.worldTime / 100);

        matrices.rotate(MathUtil.quatFromAngleDegrees(angle, MathUtil.NEGATIVE_Y));
        matrices.rotate(MathUtil.quatFromAngleDegrees(angle, MathUtil.NEGATIVE_X));
        matrices.rotate(MathUtil.quatFromAngleDegrees(angle, MathUtil.NEGATIVE_Z));

        float scale;
        if (state.warmProgress < VoidAltarTile.WARMING_TIME)
            scale = max(1 / 16F, MathUtil.interpolateBounce(state.warmProgress / (float) VoidAltarTile.WARMING_TIME));
        else
        {
            float sinHeight = 8;
            float amplitude = 5;
            scale = (float) (1 - abs(sin(state.worldTime / amplitude)) / sinHeight);
        }
        matrices.scale(scale, scale, scale);
    }

    private static void renderVoidCubeFaces(PoseStack.Pose pose, VertexConsumer buffer, float posX, float posY, float posZ, float width, float height, float length,
                                            float colorAlteration)
    {
        float red = (RANDOM.nextFloat() * 0.5F + 0.1F) * colorAlteration;
        float green = (RANDOM.nextFloat() * 0.5F + 0.4F) * colorAlteration;
        float blue = (RANDOM.nextFloat() * 0.5F + 0.5F) * colorAlteration;

        buffer.addVertex(pose, posX, posY, posZ)
                .setColor(red, green, blue, 1);
        buffer.addVertex(pose, posX, posY + height, posZ)
                .setColor(red, green, blue, 1);
        buffer.addVertex(pose, posX + width, posY + height, posZ)
                .setColor(red, green, blue, 1);
        buffer.addVertex(pose, posX + width, posY, posZ)
                .setColor(red, green, blue, 1);

        buffer.addVertex(pose, posX + width, posY, posZ + length)
                .setColor(red, green, blue, 1);
        buffer.addVertex(pose, posX + width, posY + height, posZ + length)
                .setColor(red, green, blue, 1);
        buffer.addVertex(pose, posX, posY + height, posZ + length)
                .setColor(red, green, blue, 1);
        buffer.addVertex(pose, posX, posY, posZ + length)
                .setColor(red, green, blue, 1);

        buffer.addVertex(pose, posX, posY, posZ + length)
                .setColor(red, green, blue, 1);
        buffer.addVertex(pose, posX, posY + height, posZ + length)
                .setColor(red, green, blue, 1);
        buffer.addVertex(pose, posX, posY + height, posZ)
                .setColor(red, green, blue, 1);
        buffer.addVertex(pose, posX, posY, posZ)
                .setColor(red, green, blue, 1);

        buffer.addVertex(pose, posX + width, posY, posZ)
                .setColor(red, green, blue, 1);
        buffer.addVertex(pose, posX + width, posY + height, posZ)
                .setColor(red, green, blue, 1);
        buffer.addVertex(pose, posX + width, posY + height, posZ + length)
                .setColor(red, green, blue, 1);
        buffer.addVertex(pose, posX + width, posY, posZ + length)
                .setColor(red, green, blue, 1);

        buffer.addVertex(pose, posX, posY + height, posZ + length)
                .setColor(red, green, blue, 1);
        buffer.addVertex(pose, posX + width, posY + height, posZ + length)
                .setColor(red, green, blue, 1);
        buffer.addVertex(pose, posX + width, posY + height, posZ)
                .setColor(red, green, blue, 1);
        buffer.addVertex(pose, posX, posY + height, posZ)
                .setColor(red, green, blue, 1);

        buffer.addVertex(pose, posX, posY, posZ + length)
                .setColor(red, green, blue, 1);
        buffer.addVertex(pose, posX, posY, posZ)
                .setColor(red, green, blue, 1);
        buffer.addVertex(pose, posX + width, posY, posZ)
                .setColor(red, green, blue, 1);
        buffer.addVertex(pose, posX + width, posY, posZ + length)
                .setColor(red, green, blue, 1);
    }

    private static void renderOutlineCubeFaces(PoseStack.Pose pose, VertexConsumer buffer, float posX, float posY, float posZ, float width, float height, float length)
    {
        float red = 86 / 255F;
        float green = 223 / 255F;
        float blue = 166 / 255F;
        float alpha = 0.35F;

        int light = 15728880;

        buffer.addVertex(pose, posX + width, posY, posZ)
                .setColor(red, green, blue, alpha)
                .setUv(1, 0)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(0, 1, 0);
        buffer.addVertex(pose, posX + width, posY + height, posZ)
                .setColor(red, green, blue, alpha)
                .setUv(1, 1)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(0, 1, 0);
        buffer.addVertex(pose, posX, posY + height, posZ)
                .setColor(red, green, blue, alpha)
                .setUv(0, 1)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(0, 1, 0);
        buffer.addVertex(pose, posX, posY, posZ)
                .setColor(red, green, blue, alpha)
                .setUv(0, 0)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(0, 1, 0);

        buffer.addVertex(pose, posX, posY, posZ + length)
                .setColor(red, green, blue, alpha)
                .setUv(0, 0)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(0, 0, 1);
        buffer.addVertex(pose, posX, posY + height, posZ + length)
                .setColor(red, green, blue, alpha)
                .setUv(0, 1)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(0, 0, 1);
        buffer.addVertex(pose, posX + width, posY + height, posZ + length)
                .setColor(red, green, blue, alpha)
                .setUv(1, 1)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(0, 0, 1);
        buffer.addVertex(pose, posX + width, posY, posZ + length)
                .setColor(red, green, blue, alpha)
                .setUv(1, 0)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(0, 0, 1);

        buffer.addVertex(pose, posX, posY, posZ)
                .setColor(red, green, blue, alpha)
                .setUv(0, 0)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(0, 0, -1);
        buffer.addVertex(pose, posX, posY + height, posZ)
                .setColor(red, green, blue, alpha)
                .setUv(1, 0)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(0, 0, -1);
        buffer.addVertex(pose, posX, posY + height, posZ + length)
                .setColor(red, green, blue, alpha)
                .setUv(1, 1)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(0, 0, -1);
        buffer.addVertex(pose, posX, posY, posZ + length)
                .setColor(red, green, blue, alpha)
                .setUv(0, 1)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(0, 0, -1);

        buffer.addVertex(pose, posX + width, posY, posZ + length)
                .setColor(red, green, blue, alpha)
                .setUv(0, 1)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(0, -1, 0);
        buffer.addVertex(pose, posX + width, posY + height, posZ + length)
                .setColor(red, green, blue, alpha)
                .setUv(1, 1)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(0, -1, 0);
        buffer.addVertex(pose, posX + width, posY + height, posZ)
                .setColor(red, green, blue, alpha)
                .setUv(1, 0)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(0, -1, 0);
        buffer.addVertex(pose, posX + width, posY, posZ)
                .setColor(red, green, blue, alpha)
                .setUv(0, 0)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(0, -1, 0);

        buffer.addVertex(pose, posX, posY + height, posZ)
                .setColor(red, green, blue, alpha)
                .setUv(0, 0)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(0, 1, 0);
        buffer.addVertex(pose, posX + width, posY + height, posZ)
                .setColor(red, green, blue, alpha)
                .setUv(1, 0)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(0, 1, 0);
        buffer.addVertex(pose, posX + width, posY + height, posZ + length)
                .setColor(red, green, blue, alpha)
                .setUv(1, 1)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(0, 1, 0);
        buffer.addVertex(pose, posX, posY + height, posZ + length)
                .setColor(red, green, blue, alpha)
                .setUv(0, 1)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(0, 1, 0);

        buffer.addVertex(pose, posX + width, posY, posZ + length)
                .setColor(red, green, blue, alpha)
                .setUv(1, 1)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(-1, 0, 0);
        buffer.addVertex(pose, posX + width, posY, posZ)
                .setColor(red, green, blue, alpha)
                .setUv(1, 0)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(-1, 0, 0);
        buffer.addVertex(pose, posX, posY, posZ)
                .setColor(red, green, blue, alpha)
                .setUv(0, 0)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(-1, 0, 0);
        buffer.addVertex(pose, posX, posY, posZ + length)
                .setColor(red, green, blue, alpha)
                .setUv(0, 1)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(-1, 0, 0);
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
