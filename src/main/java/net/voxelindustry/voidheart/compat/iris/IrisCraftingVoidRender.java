package net.voxelindustry.voidheart.compat.iris;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.coderbot.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class IrisCraftingVoidRender
{
    private static final float RED = 0.075f;
    private static final float GREEN = 0.15f;
    private static final float BLUE = 0.2f;

    public static void irisEndPortalRender(PoseStack matrixStack, MultiBufferSource vertexConsumers, int overlay, int light)
    {
        var vertexConsumer =
                vertexConsumers.getBuffer(RenderType.entitySolid(TheEndPortalRenderer.END_PORTAL_LOCATION));

        var pose = matrixStack.last().pose();
        var normal = matrixStack.last().normal();

        var progress = (SystemTimeUniforms.TIMER.getFrameTimeCounter() * 0.01f) % 1f;
        var topHeight = 6 / 16F;
        var bottomHeight = -6 / 16F;

        quad(vertexConsumer, pose, normal, Direction.UP, progress, overlay, light,
                bottomHeight, topHeight, topHeight,
                topHeight, topHeight, topHeight,
                topHeight, topHeight, bottomHeight,
                bottomHeight, topHeight, bottomHeight);

        quad(vertexConsumer, pose, normal, Direction.DOWN, progress, overlay, light,
                bottomHeight, bottomHeight, topHeight,
                bottomHeight, bottomHeight, bottomHeight,
                topHeight, bottomHeight, bottomHeight,
                topHeight, bottomHeight, topHeight);

        quad(vertexConsumer, pose, normal, Direction.NORTH, progress, overlay, light,
                bottomHeight, topHeight, bottomHeight,
                topHeight, topHeight, bottomHeight,
                topHeight, bottomHeight, bottomHeight,
                bottomHeight, bottomHeight, bottomHeight);

        quad(vertexConsumer, pose, normal, Direction.WEST, progress, overlay, light,
                bottomHeight, topHeight, topHeight,
                bottomHeight, topHeight, bottomHeight,
                bottomHeight, bottomHeight, bottomHeight,
                bottomHeight, bottomHeight, topHeight);

        quad(vertexConsumer, pose, normal, Direction.SOUTH, progress, overlay, light,
                bottomHeight, topHeight, topHeight,
                bottomHeight, bottomHeight, topHeight,
                topHeight, bottomHeight, topHeight,
                topHeight, topHeight, topHeight);

        quad(vertexConsumer, pose, normal, Direction.EAST, progress, overlay, light,
                topHeight, topHeight, topHeight,
                topHeight, bottomHeight, topHeight,
                topHeight, bottomHeight, bottomHeight,
                topHeight, topHeight, bottomHeight);
    }

    private static void quad(VertexConsumer vertexConsumer, Matrix4f pose, Matrix3f normal,
                             Direction direction, float progress, int overlay, int light,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4)
    {
        float nx = direction.getStepX();
        float ny = direction.getStepY();
        float nz = direction.getStepZ();

        vertexConsumer.vertex(pose, x1, y1, z1).color(RED, GREEN, BLUE, 1.0f)
                .uv(0.0F + progress, 0.0F + progress).overlayCoords(overlay).uv2(light)
                .normal(normal, nx, ny, nz).endVertex();

        vertexConsumer.vertex(pose, x2, y2, z2).color(RED, GREEN, BLUE, 1.0f)
                .uv(0.0F + progress, 0.2F + progress).overlayCoords(overlay).uv2(light)
                .normal(normal, nx, ny, nz).endVertex();

        vertexConsumer.vertex(pose, x3, y3, z3).color(RED, GREEN, BLUE, 1.0f)
                .uv(0.2F + progress, 0.2F + progress).overlayCoords(overlay).uv2(light)
                .normal(normal, nx, ny, nz).endVertex();

        vertexConsumer.vertex(pose, x4, y4, z4).color(RED, GREEN, BLUE, 1.0f)
                .uv(0.2F + progress, 0.0F + progress).overlayCoords(overlay).uv2(light)
                .normal(normal, nx, ny, nz).endVertex();
    }
}
