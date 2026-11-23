package net.voxelindustry.voidheart.client.render.energy.superfluid;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.common.energy.superfluid.SuperfluidRings;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Superfluid azurium wound about a mature pearl: up to four rings of plasma, a ribbon each, turning about the pearl, the one still forming an arc that
 * lengthens to its bright head as cradles pour in, and closes into a loop once it holds a large bundle's worth. Drawn off into a tesseract, the rings
 * unspool outermost first, along a stream to the hand that holds it.
 * <p>
 * Render types. Each ring is a flat band in its own plane and a narrower band across it, so it reads as a line edge-on; both
 * {@link VoidHeartRenderTypes#radiant}, added and never writing depth, so the rings cross each other in any order without flicker while the pearl and
 * terrain still hide them. A wider radiant halo lies in the plane, and under it a dark {@link VoidHeartRenderTypes#tint} sheath blended over the
 * sky, so the rings keep their blue by day. The textures are grey and take their colour from the vertices.
 * <p>
 * Per frame, at worst: four closed rings of {@value #SEGMENTS} segments in four layers (768 quads), and a head bead.
 */
public final class SuperfluidRingRender
{
    static final Identifier RIBBON_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/misc/superfluid_ribbon.png");
    static final Identifier HALO_TEXTURE   = Identifier.fromNamespaceAndPath(MODID, "textures/misc/superfluid_halo.png");
    static final Identifier SHEATH_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/misc/superfluid_sheath.png");
    static final Identifier BEAD_TEXTURE   = Identifier.fromNamespaceAndPath(MODID, "textures/misc/superfluid_bead.png");

    static final RenderType RIBBON_TYPE = VoidHeartRenderTypes.radiant(RIBBON_TEXTURE);
    static final RenderType HALO_TYPE   = VoidHeartRenderTypes.radiant(HALO_TEXTURE);
    static final RenderType SHEATH_TYPE = VoidHeartRenderTypes.tint(SHEATH_TEXTURE);
    static final RenderType BEAD_TYPE   = VoidHeartRenderTypes.radiant(BEAD_TEXTURE);

    // Shape
    /** Segments in a closed ring; an open one gets its share, never fewer than three. */
    private static final int   SEGMENTS    = 48;
    /** Half widths, in blocks: the band in the ring's plane, the band across it, the halo and the sheath. */
    private static final float BAND_HALF   = 0.9F / 16;
    private static final float CROSS_HALF  = 0.45F / 16;
    private static final float HALO_HALF   = 2.2F / 16;
    private static final float SHEATH_HALF = 1.2F / 16;
    /** Share of an open ring's length its tail takes to fade in from nothing. */
    private static final float TAIL        = 0.18F;
    /** How long one repeat of the ribbon texture runs round a ring, roughly, in blocks; each ring rounds it to a whole number of repeats. */
    private static final float TEXTURE_RUN = 5F / 16;
    /** Repeats a tick the plasma flows along its ring, ahead of the ring's own turning. */
    private static final float FLOW        = 0.035F;
    private static final float BEAD_RADIUS = 1.1F / 16;

    // Color and light
    static final int   CORE_COLOR   = 0x9EECFF;
    static final int   CROSS_COLOR  = 0x68BBF7;
    static final int   HALO_COLOR   = 0x3D7ED9;
    /** Azurium's own mid blue, blended rather than added, so the rings stay blue against a bright sky. */
    static final int   SHEATH_COLOR = 0x2C56B8;
    static final int   BEAD_COLOR   = 0xFFFFFF;
    private static final int BAND_ALPHA   = 235;
    private static final int CROSS_ALPHA  = 170;
    private static final int HALO_ALPHA   = 95;
    private static final int SHEATH_ALPHA = 170;
    /** Light added at the height of a burst. */
    private static final int TREMOR_FLARE = 20;

    /**
     * What the rings need from their pearl at one moment, in the pearl block's space.
     *
     * @param units  superfluid shown, eased
     * @param tremor the pearl's shudder, held a little after each burst so the rings settle rather than snap true
     * @param drawTo where the rings unspool to while drawn off, or null
     * @param draw   how far the unspooling has gone, from 0 to 1, or negative when it isn't
     */
    public record Input(long seed, double time, Vector3f center, Vector3f camera, double units, float tremor, @Nullable Vector3f drawTo, float draw)
    {
    }

    private SuperfluidRingRender()
    {
    }

    public static void submit(Input input, PoseStack poseStack, SubmitNodeCollector collector)
    {
        var ribbon = new SuperfluidQuads();
        var halo = new SuperfluidQuads();
        var sheath = new SuperfluidQuads();
        var beads = new SuperfluidQuads();
        build(input, ribbon, halo, sheath, beads);
        SuperfluidStreamRender.submitLayers(poseStack, collector, ribbon, halo, sheath, beads);
    }

    static void build(Input input, SuperfluidQuads ribbon, SuperfluidQuads halo, SuperfluidQuads sheath, SuperfluidQuads beads)
    {
        var begun = SuperfluidRings.begun(input.units());
        var flare = TREMOR_FLARE * input.tremor();
        for (var ring = 0; ring < begun; ring++)
        {
            var closed = SuperfluidRings.closed(input.units(), ring);
            if (closed <= 0)
                continue;
            var frame = SuperfluidRingShape.frame(input.seed(), ring, input.time(), input.center());
            ring(input, frame, closed, flare, ribbon, halo, sheath, beads);
        }

        // Drawn off, the stream runs from the head of the outermost ring left to the hand holding the tesseract
        if (input.drawTo() != null && input.draw() >= 0 && input.draw() < 1)
        {
            var head = SuperfluidRingShape.tail(input.seed(), Math.max(1, input.units()), input.time(), input.tremor(), input.center());
            var strength = 1 - SuperfluidQuads.smooth((input.draw() - 0.6F) / 0.4F);
            SuperfluidStreamRender.drawOff(head.point(), new Vector3f(head.tangent()).negate(), input.drawTo(), input.camera(), input.time(), strength,
                    ribbon, halo, sheath, beads);
        }
    }

    private static void ring(Input input, SuperfluidRingShape.Frame frame, float closed, float flare, SuperfluidQuads ribbon, SuperfluidQuads halo,
                             SuperfluidQuads sheath, SuperfluidQuads beads)
    {
        var open = closed < 1;
        var segments = Math.max(3, Mth.ceil(SEGMENTS * closed));
        var repeats = Math.max(1, Math.round(Mth.TWO_PI * frame.radius() / TEXTURE_RUN));
        var flow = (float) (input.time() * FLOW % repeats);

        var points = new Vector3f[segments + 1];
        var radials = new Vector3f[segments + 1];
        var alphas = new float[segments + 1];
        var us = new float[segments + 1];
        for (var i = 0; i <= segments; i++)
        {
            var along = (float) i / segments;
            var angle = frame.start() + Mth.TWO_PI * closed * along;
            points[i] = SuperfluidRingShape.point(frame, angle, input.tremor());
            radials[i] = SuperfluidRingShape.radial(frame, angle);
            alphas[i] = open ? SuperfluidQuads.smooth(along * closed / TAIL) : 1;
            us[i] = repeats * closed * along - flow;
        }

        var bandHalf = BAND_HALF * frame.thick();
        var crossHalf = CROSS_HALF * frame.thick();
        for (var i = 0; i < segments; i++)
        {
            var a0 = alphas[i];
            var a1 = alphas[i + 1];
            ribbon.strip(points[i], radials[i], bandHalf, points[i + 1], radials[i + 1], bandHalf, us[i], us[i + 1],
                    color(BAND_ALPHA + flare, a0, CORE_COLOR), color(BAND_ALPHA + flare, a1, CORE_COLOR));
            ribbon.strip(points[i], frame.normal(), crossHalf, points[i + 1], frame.normal(), crossHalf, us[i], us[i + 1],
                    color(CROSS_ALPHA + flare, a0, CROSS_COLOR), color(CROSS_ALPHA + flare, a1, CROSS_COLOR));
            halo.strip(points[i], radials[i], HALO_HALF, points[i + 1], radials[i + 1], HALO_HALF, us[i], us[i + 1],
                    color(HALO_ALPHA + flare, a0, HALO_COLOR), color(HALO_ALPHA + flare, a1, HALO_COLOR));
            sheath.strip(points[i], radials[i], SHEATH_HALF, points[i + 1], radials[i + 1], SHEATH_HALF, 0, 1,
                    color(SHEATH_ALPHA, a0, SHEATH_COLOR), color(SHEATH_ALPHA, a1, SHEATH_COLOR));
        }

        // The forming ring's head is its brightest point, where the poured azurium is laid down
        if (open)
            beads.sprite(points[segments], input.camera(), BEAD_RADIUS * frame.thick(), ARGB.color(255, BEAD_COLOR));
    }

    static int color(float alpha, float share, int rgb)
    {
        return ARGB.color(Mth.clamp(Math.round(alpha * share), 0, 255), rgb);
    }

    static int light()
    {
        return LightCoordsUtil.FULL_BRIGHT;
    }
}
