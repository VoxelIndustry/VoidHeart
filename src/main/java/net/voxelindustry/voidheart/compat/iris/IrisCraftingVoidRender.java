package net.voxelindustry.voidheart.compat.iris;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.blockentity.AbstractEndPortalRenderer;
import net.minecraft.core.Direction;

public class IrisCraftingVoidRender
{
    private static final float RED = 0.075f;
    private static final float GREEN = 0.15f;
    private static final float BLUE = 0.2f;

    public static void irisEndPortalRender(PoseStack matrixStack, SubmitNodeCollector collector, int overlay, int light)
    {
        collector.submitCustomGeometry(matrixStack, RenderTypes.entitySolid(AbstractEndPortalRenderer.END_PORTAL_LOCATION),
                (pose, vertexConsumer) -> renderCube(vertexConsumer, pose, overlay, light));
    }

    private static void renderCube(VertexConsumer vertexConsumer, PoseStack.Pose pose, int overlay, int light)
    {
        var progress = (SystemTimeUniforms.TIMER.getFrameTimeCounter() * 0.01f) % 1f;
        var topHeight = 6 / 16F;
        var bottomHeight = -6 / 16F;

        quad(vertexConsumer, pose, Direction.UP, progress, overlay, light,
                bottomHeight, topHeight, topHeight,
                topHeight, topHeight, topHeight,
                topHeight, topHeight, bottomHeight,
                bottomHeight, topHeight, bottomHeight);

        quad(vertexConsumer, pose, Direction.DOWN, progress, overlay, light,
                bottomHeight, bottomHeight, topHeight,
                bottomHeight, bottomHeight, bottomHeight,
                topHeight, bottomHeight, bottomHeight,
                topHeight, bottomHeight, topHeight);

        quad(vertexConsumer, pose, Direction.NORTH, progress, overlay, light,
                bottomHeight, topHeight, bottomHeight,
                topHeight, topHeight, bottomHeight,
                topHeight, bottomHeight, bottomHeight,
                bottomHeight, bottomHeight, bottomHeight);

        quad(vertexConsumer, pose, Direction.WEST, progress, overlay, light,
                bottomHeight, topHeight, topHeight,
                bottomHeight, topHeight, bottomHeight,
                bottomHeight, bottomHeight, bottomHeight,
                bottomHeight, bottomHeight, topHeight);

        quad(vertexConsumer, pose, Direction.SOUTH, progress, overlay, light,
                bottomHeight, topHeight, topHeight,
                bottomHeight, bottomHeight, topHeight,
                topHeight, bottomHeight, topHeight,
                topHeight, topHeight, topHeight);

        quad(vertexConsumer, pose, Direction.EAST, progress, overlay, light,
                topHeight, topHeight, topHeight,
                topHeight, bottomHeight, topHeight,
                topHeight, bottomHeight, bottomHeight,
                topHeight, topHeight, bottomHeight);
    }

    private static void quad(VertexConsumer vertexConsumer, PoseStack.Pose pose,
                             Direction direction, float progress, int overlay, int light,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4)
    {
        float nx = direction.getStepX();
        float ny = direction.getStepY();
        float nz = direction.getStepZ();

        vertexConsumer.addVertex(pose, x1, y1, z1).setColor(RED, GREEN, BLUE, 1.0f)
                .setUv(0.0F + progress, 0.0F + progress).setOverlay(overlay).setLight(light)
                .setNormal(pose, nx, ny, nz);

        vertexConsumer.addVertex(pose, x2, y2, z2).setColor(RED, GREEN, BLUE, 1.0f)
                .setUv(0.0F + progress, 0.2F + progress).setOverlay(overlay).setLight(light)
                .setNormal(pose, nx, ny, nz);

        vertexConsumer.addVertex(pose, x3, y3, z3).setColor(RED, GREEN, BLUE, 1.0f)
                .setUv(0.2F + progress, 0.2F + progress).setOverlay(overlay).setLight(light)
                .setNormal(pose, nx, ny, nz);

        vertexConsumer.addVertex(pose, x4, y4, z4).setColor(RED, GREEN, BLUE, 1.0f)
                .setUv(0.2F + progress, 0.0F + progress).setOverlay(overlay).setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
