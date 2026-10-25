package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.state.ShatterForgeRenderState;
import net.voxelindustry.voidheart.client.render.state.ShatterForgeRenderState.BeamDraw;
import net.voxelindustry.voidheart.common.content.beam.BeamComposition;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeTile;
import org.jspecify.annotations.Nullable;

public class ShatterForgeRender implements BlockEntityRenderer<ShatterForgeTile, ShatterForgeRenderState>
{
    private static final Identifier BEAM_TEXTURE = Identifier.withDefaultNamespace("textures/entity/beacon/beacon_beam.png");

    private static final int FULL_BRIGHT = 15728880;

    private static final int UNATTUNED_COLOR = 0x9B59FF;
    private static final int STILLNESS_COLOR = 0x7FB8FF;
    private static final int FIERY_COLOR     = 0xFF8A3D;

    private final FloatingItemRender<ShatterForgeTile> itemRender;

    public ShatterForgeRender(BlockEntityRendererProvider.Context context)
    {
        itemRender = new FloatingItemRender<>(context, ShatterForgeTile::getStack, 1);
    }

    @Override
    public ShatterForgeRenderState createRenderState()
    {
        return new ShatterForgeRenderState();
    }

    @Override
    public void extractRenderState(ShatterForgeTile forge, ShatterForgeRenderState state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        itemRender.extractRenderState(forge, state, partialTicks, cameraPosition, breakProgress);

        state.beams.clear();
        BlockPos origin = forge.getBlockPos();
        for (var segment : forge.getBeamSegments())
        {
            var composition = segment.composition();
            int color = colorOf(composition);
            float radius = 0.075F + Math.min(Math.max(composition.total() - 2, 0) * 0.02F, 0.1F);

            state.beams.add(new BeamDraw(
                    segment.from().getX() - origin.getX() + 0.5F,
                    segment.from().getY() - origin.getY() + 0.5F,
                    segment.from().getZ() - origin.getZ() + 0.5F,
                    segment.to().getX() - origin.getX() + 0.5F,
                    segment.to().getY() - origin.getY() + 0.5F,
                    segment.to().getZ() - origin.getZ() + 0.5F,
                    (color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F,
                    radius));
        }
    }

    @Override
    public void submit(ShatterForgeRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        itemRender.submit(state, poseStack, collector, camera);

        if (state.beams.isEmpty())
            return;

        float time = state.worldTime;
        collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BEAM_TEXTURE, true), (pose, buffer) ->
        {
            for (var beam : state.beams)
                renderBeam(pose, buffer, beam, time);
        });
    }

    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }

    @Override
    public int getViewDistance()
    {
        return 256;
    }

    private static int colorOf(BeamComposition composition)
    {
        double red = 0;
        double green = 0;
        double blue = 0;
        double weight = 0;

        if (composition.unattuned() > 0)
        {
            red += (UNATTUNED_COLOR >> 16 & 0xFF) * (double) composition.unattuned();
            green += (UNATTUNED_COLOR >> 8 & 0xFF) * (double) composition.unattuned();
            blue += (UNATTUNED_COLOR & 0xFF) * (double) composition.unattuned();
            weight += composition.unattuned();
        }

        for (var entry : composition.attuned().entrySet())
        {
            int amount = entry.getValue();
            if (amount <= 0)
                continue;

            int color = switch (entry.getKey().identifier().getPath())
            {
                case "stillness" -> STILLNESS_COLOR;
                case "fiery" -> FIERY_COLOR;
                default -> UNATTUNED_COLOR;
            };
            red += (color >> 16 & 0xFF) * (double) amount;
            green += (color >> 8 & 0xFF) * (double) amount;
            blue += (color & 0xFF) * (double) amount;
            weight += amount;
        }

        if (weight <= 0)
            return UNATTUNED_COLOR;
        return (int) (red / weight) << 16 | (int) (green / weight) << 8 | (int) (blue / weight);
    }

    private static void renderBeam(PoseStack.Pose pose, VertexConsumer buffer, BeamDraw beam, float time)
    {
        float dx = beam.toX() - beam.fromX();
        float dy = beam.toY() - beam.fromY();
        float dz = beam.toZ() - beam.fromZ();
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-3F)
            return;
        dx /= length;
        dy /= length;
        dz /= length;

        // side = dir x up (dir x X when the beam is vertical), other = dir x side
        float upX = 0;
        float upY = 1;
        float upZ = 0;
        if (Math.abs(dy) > 0.99F)
        {
            upX = 1;
            upY = 0;
        }
        float sideX = dy * upZ - dz * upY;
        float sideY = dz * upX - dx * upZ;
        float sideZ = dx * upY - dy * upX;
        float sideLength = (float) Math.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
        sideX /= sideLength;
        sideY /= sideLength;
        sideZ /= sideLength;

        float otherX = dy * sideZ - dz * sideY;
        float otherY = dz * sideX - dx * sideZ;
        float otherZ = dx * sideY - dy * sideX;

        float scroll = -time * 0.04F;
        float pulse = 0.8F + 0.2F * (float) Math.sin(time * 0.15F + length);

        // Hot core, then a wider faint halo
        renderCross(pose, buffer, beam, sideX, sideY, sideZ, otherX, otherY, otherZ, beam.radius(), length, scroll, 0.85F * pulse);
        renderCross(pose, buffer, beam, sideX, sideY, sideZ, otherX, otherY, otherZ, beam.radius() * 2.2F, length, scroll * 0.5F, 0.22F * pulse);
    }

    private static void renderCross(PoseStack.Pose pose, VertexConsumer buffer, BeamDraw beam,
                                    float sideX, float sideY, float sideZ, float otherX, float otherY, float otherZ,
                                    float radius, float length, float scroll, float alpha)
    {
        renderQuad(pose, buffer, beam, sideX * radius, sideY * radius, sideZ * radius, otherX, otherY, otherZ, length, scroll, alpha);
        renderQuad(pose, buffer, beam, otherX * radius, otherY * radius, otherZ * radius, sideX, sideY, sideZ, length, scroll, alpha);
    }

    // Quad spanned by +-offset around the beam axis, drawn on both faces
    private static void renderQuad(PoseStack.Pose pose, VertexConsumer buffer, BeamDraw beam,
                                   float offsetX, float offsetY, float offsetZ,
                                   float normalX, float normalY, float normalZ,
                                   float length, float scroll, float alpha)
    {
        float v0 = scroll;
        float v1 = scroll + length * 0.5F;

        float ax = beam.fromX() - offsetX;
        float ay = beam.fromY() - offsetY;
        float az = beam.fromZ() - offsetZ;
        float bx = beam.fromX() + offsetX;
        float by = beam.fromY() + offsetY;
        float bz = beam.fromZ() + offsetZ;
        float cx = beam.toX() + offsetX;
        float cy = beam.toY() + offsetY;
        float cz = beam.toZ() + offsetZ;
        float ex = beam.toX() - offsetX;
        float ey = beam.toY() - offsetY;
        float ez = beam.toZ() - offsetZ;

        vertex(pose, buffer, beam, ax, ay, az, 0, v0, alpha, normalX, normalY, normalZ);
        vertex(pose, buffer, beam, bx, by, bz, 1, v0, alpha, normalX, normalY, normalZ);
        vertex(pose, buffer, beam, cx, cy, cz, 1, v1, alpha, normalX, normalY, normalZ);
        vertex(pose, buffer, beam, ex, ey, ez, 0, v1, alpha, normalX, normalY, normalZ);

        vertex(pose, buffer, beam, ex, ey, ez, 0, v1, alpha, -normalX, -normalY, -normalZ);
        vertex(pose, buffer, beam, cx, cy, cz, 1, v1, alpha, -normalX, -normalY, -normalZ);
        vertex(pose, buffer, beam, bx, by, bz, 1, v0, alpha, -normalX, -normalY, -normalZ);
        vertex(pose, buffer, beam, ax, ay, az, 0, v0, alpha, -normalX, -normalY, -normalZ);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, BeamDraw beam,
                               float x, float y, float z, float u, float v, float alpha,
                               float normalX, float normalY, float normalZ)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(beam.red(), beam.green(), beam.blue(), alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(normalX, normalY, normalZ);
    }
}
