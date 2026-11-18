package net.voxelindustry.voidheart.client.secondheart;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidBeamRender;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.HeartCradleTile;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleCeremony.Phase;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleRhythm;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The Twin Ventricle's chamber, drawn from its cradle so both halves keep one clock. Each basin's essence lies as a surface at its fill and swells
 * and brightens on its pulse. During the rite a strand runs from each basin over its valve into the cradle, a bright pulse running along it toward
 * the cradle on each beat, and the cradle's heart glows on the dub. Out of step while kindling, in step while beating, see {@link VentricleRhythm}.
 * <p>
 * Render types: the surfaces and the heart in vanilla's emissive translucency, flat and alone in their planes; the strands as relay beams are,
 * through the translucent beacon beam with the void veil texture. Per frame: two surfaces, the
 * heart and two strands of {@link #STRAND_SEGMENTS} segments each.
 */
public class HeartCradleRender implements BlockEntityRenderer<HeartCradleTile, HeartCradleRender.State>
{
    private static final Identifier ESSENCE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/second_heart/ventricle_essence.png");
    private static final Identifier HEART   = Identifier.fromNamespaceAndPath(MODID, "textures/entity/second_heart/heart_cradle_glow.png");
    private static final RenderType SURFACE_TYPE = RenderTypes.entityTranslucentEmissive(ESSENCE);
    private static final RenderType HEART_TYPE   = RenderTypes.entityTranslucentEmissive(HEART);
    private static final RenderType STRAND_TYPE  = RenderTypes.beaconBeam(VoidBeamRender.VEIL_TEXTURE, true);

    private static final float BASIN_FLOOR  = 4 / 16F;
    private static final float BASIN_DEPTH  = 11.5F / 16;
    private static final float BASIN_INSET  = 2 / 16F;
    private static final float SWELL        = 0.05F;
    private static final float SURFACE_DIM  = 0.55F;
    private static final float SURFACE_ALPHA = 0.95F;

    private static final float HEART_LIFT   = 6.05F / 16;
    private static final int   HEART_RGB    = 0xA45CFF;
    private static final float HEART_IDLE   = 0.3F;
    /** While kindling the heart catches each basin's pulse, dimmer than its own glow once beating. */
    private static final float HEART_KINDLE = 0.5F;

    private static final float STRAND_HEIGHT = 0.95F;
    private static final float STRAND_CRADLE = 0.7F;
    private static final int   STRAND_SEGMENTS = 24;
    private static final float STRAND_CORE   = 0.05F;
    private static final float STRAND_GLOW   = 0.15F;
    private static final float STRAND_BASE   = 0.5F;
    private static final float STRAND_SWELL  = 1.8F;
    private static final float STRAND_TAPER  = 0.15F;
    private static final float STRAND_SCROLL = 0.08F;
    /** Length of the swelling stretch, in blocks. */
    private static final float PULSE_LENGTH  = 0.6F;

    public static class State extends BlockEntityRenderState
    {
        @Nullable Vec3 essenceBasin;
        @Nullable Vec3 contravoidBasin;
        float essenceFill;
        float contravoidFill;
        int   essenceRgb;
        int   contravoidRgb;
        boolean rite;
        float time;
        VentricleRhythm.Pulses pulses = VentricleRhythm.Pulses.NONE;
    }

    public HeartCradleRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(HeartCradleTile cradle, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(cradle, state, partialTicks, cameraPosition, breakProgress);
        var level = cradle.getLevel();
        state.essenceBasin = null;
        state.contravoidBasin = null;
        state.rite = false;
        state.pulses = VentricleRhythm.Pulses.NONE;
        if (level == null)
            return;
        var basins = cradle.basins(level);
        if (basins == null)
            return;
        var origin = cradle.getBlockPos();
        state.essenceBasin = Vec3.atLowerCornerOf(basins[0].getBlockPos().subtract(origin));
        state.contravoidBasin = Vec3.atLowerCornerOf(basins[1].getBlockPos().subtract(origin));
        state.essenceFill = basins[0].fill();
        state.contravoidFill = basins[1].fill();
        state.essenceRgb = VoidBeamRender.aspectColor(EssenceRegistry.SUFFERING);
        state.contravoidRgb = VoidBeamRender.aspectColor(EssenceRegistry.CONTRAVOID);

        var phase = cradle.ceremony().phase();
        if (!cradle.isComplete() || phase != Phase.KINDLING && phase != Phase.BEATING)
            return;
        state.rite = true;
        state.time = level.getGameTime() + partialTicks;
        float ticks = cradle.clientProgress() + (cradle.isRunning() ? partialTicks : 0);
        state.pulses = phase == Phase.KINDLING ? VentricleRhythm.kindling(ticks) : VentricleRhythm.beating(ticks);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.essenceBasin == null || state.contravoidBasin == null)
            return;
        var pulses = state.pulses;

        collector.submitCustomGeometry(poseStack, SURFACE_TYPE, (pose, buffer) ->
        {
            surface(buffer, pose, state.essenceBasin, state.essenceFill, state.essenceRgb, pulses.essence());
            surface(buffer, pose, state.contravoidBasin, state.contravoidFill, state.contravoidRgb, pulses.contravoid());
        });

        if (!state.rite)
            return;

        collector.submitCustomGeometry(poseStack, HEART_TYPE, (pose, buffer) ->
        {
            float glow = Math.max(HEART_IDLE, Math.max(pulses.cradle(), HEART_KINDLE * Math.max(pulses.essence(), pulses.contravoid())));
            int color = ARGB.color(glow, HEART_RGB);
            quad(buffer, pose, 0, HEART_LIFT, 0, 1, HEART_LIFT, 1, 0, 0, 1, 1, color);
        });

        collector.submitCustomGeometry(poseStack, STRAND_TYPE, (pose, buffer) ->
        {
            strand(buffer, pose, state.essenceBasin, state.essenceRgb, pulses.essenceRun(), state.time);
            strand(buffer, pose, state.contravoidBasin, state.contravoidRgb, pulses.contravoidRun(), state.time);
        });
    }

    private static void surface(VertexConsumer buffer, PoseStack.Pose pose, Vec3 basin, float fill, int rgb, float pulse)
    {
        if (fill <= 0)
            return;
        float y = (float) basin.y + BASIN_FLOOR + BASIN_DEPTH * fill + SWELL * pulse;
        float x0 = (float) basin.x + BASIN_INSET, x1 = (float) basin.x + 1 - BASIN_INSET;
        float z0 = (float) basin.z + BASIN_INSET, z1 = (float) basin.z + 1 - BASIN_INSET;
        float bright = SURFACE_DIM + (1 - SURFACE_DIM) * pulse;
        int color = ARGB.color(SURFACE_ALPHA, ARGB.scaleRGB(rgb, bright));
        quad(buffer, pose, x0, y, z0, x1, y, z1, 0, 0, 1, 1, color);
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1,
                             float u0, float v0, float u1, float v1, int color)
    {
        vertex(buffer, pose, x0, y0, z0, u0, v0, color);
        vertex(buffer, pose, x0, y0, z1, u0, v1, color);
        vertex(buffer, pose, x1, y1, z1, u1, v1, color);
        vertex(buffer, pose, x1, y1, z0, u1, v0, color);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int color)
    {
        buffer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0, 1, 0);
    }

    /**
     * A strand from above the basin, over its valve, down into the cradle, drawn like a relay's beam: a thin core in a soft glow, faint between
     * beats, swelling where the pulse runs toward the cradle.
     */
    private static void strand(VertexConsumer buffer, PoseStack.Pose pose, Vec3 basin, int rgb, float run, float time)
    {
        var from = basin.add(0.5, STRAND_HEIGHT, 0.5);
        var to = new Vec3(0.5, STRAND_CRADLE, 0.5);
        float length = (float) from.distanceTo(to);
        float[][] previousCore = null, previousGlow = null;
        for (int i = 0; i <= STRAND_SEGMENTS; i++)
        {
            float u = i / (float) STRAND_SEGMENTS;
            var point = along(from, to, u);
            var tangent = along(from, to, Math.min(1, u + 0.01F)).subtract(along(from, to, Math.max(0, u - 0.01F)));
            float swell = 0;
            if (run >= 0)
            {
                float offset = (u - run) * length / PULSE_LENGTH;
                swell = (float) Math.exp(-offset * offset);
            }
            float taper = Math.min(1, Math.min(u, 1 - u) * length / STRAND_TAPER);
            float v = u * length - time * STRAND_SCROLL;
            var core = VoidBeamRender.crossedSection(point, tangent, STRAND_CORE * (1 + STRAND_SWELL * swell) * taper,
                    Math.min(1, STRAND_BASE + (1 - STRAND_BASE) * swell) * taper, v);
            var glow = VoidBeamRender.crossedSection(point, tangent, STRAND_GLOW * (1 + STRAND_SWELL * swell) * taper,
                    (STRAND_BASE * 0.5F + 0.45F * swell) * taper, v * 0.5F);
            if (previousCore != null)
            {
                VoidBeamRender.crossedStrip(pose, buffer, rgb, previousGlow, glow);
                VoidBeamRender.crossedStrip(pose, buffer, rgb, previousCore, core);
            }
            previousCore = core;
            previousGlow = glow;
        }
    }

    /**
     * A shallow arc, so the strand hangs over the valve rather than cutting through the dais.
     */
    private static Vec3 along(Vec3 from, Vec3 to, float t)
    {
        var straight = from.lerp(to, t);
        return straight.add(0, 0.25 * Math.sin(Math.PI * t), 0);
    }

    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }
}
