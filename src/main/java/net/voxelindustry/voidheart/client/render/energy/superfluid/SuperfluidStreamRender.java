package net.voxelindustry.voidheart.client.render.energy.superfluid;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.common.content.crystal.CrystalCradleBlock;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Azurium pouring out of a cradle into a pearl's forming ring. It creeps as a thin film from the crystal across the basin floor, up the inner wall
 * and over the rim on the pearl's side, the way a superfluid climbs out of its vessel, then lifts off as one slow ribbon that curves up to the ring's
 * head and runs on into it, bright beads travelling along it. It forms from the cradle out over a second, and once the pour stops
 * it fades from the cradle toward the pearl.
 * <p>
 * The film lies flat on the stone it climbs, lifted {@value #FILM_LIFT} off it so the depth test never ties. The ribbon in the air is two bands
 * crossed, so it reads as a line from any side. Same render types as the rings, see {@link SuperfluidRingRender}.
 * <p>
 * Per frame, per pouring cradle: 4 film quads, {@value #FREE_STEPS} ribbon steps in three layers (72), and {@value #BEADS} beads.
 */
public final class SuperfluidStreamRender
{
    // Path, in blocks, in the cradle's space
    private static final float FLOOR      = CrystalCradleBlock.FLOOR / 16;
    private static final float RIM_TOP    = 6F / 16;
    private static final float INNER_HALF = 5F / 16;
    private static final float OUTER_HALF = 7F / 16;
    /** Where the film leaves the crystal, out from the basin's middle and up its flank. */
    private static final float CRYSTAL_OUT = 1.5F / 16;
    private static final float CRYSTAL_UP  = 1.5F / 16;
    private static final float FILM_LIFT   = 0.004F;
    /** Share of the way to its end the ribbon rises before it bends toward the ring, and how far back from the head it starts to follow the ring. */
    private static final float RISE        = 0.45F;
    private static final float MERGE       = 0.35F;
    private static final int   FREE_STEPS  = 24;
    /** How far the ribbon wavers off its curve at its middle, and how fast the waver runs along it. */
    private static final float WAVER       = 0.6F / 16;
    private static final float WAVER_RUN   = 0.15F;

    // Widths
    private static final float FILM_HALF   = 0.9F / 16;
    private static final float STREAM_HALF = 0.55F / 16;
    private static final float HALO_HALF   = 1.5F / 16;
    private static final float SHEATH_HALF = 0.9F / 16;
    private static final float BEAD_RADIUS = 0.8F / 16;

    // Motion
    /** Blocks a tick the beads travel, one a second. */
    private static final float BEAD_SPEED = 0.05F;
    private static final int   BEADS      = 4;
    /** Repeats of the ribbon texture a block, and how fast it flows toward the pearl. */
    private static final float TEXTURE_PER_BLOCK = 3;
    private static final float FLOW              = 0.08F;

    // Light
    private static final int FILM_ALPHA   = 235;
    private static final int STREAM_ALPHA = 225;
    private static final int HALO_ALPHA   = 80;
    private static final int SHEATH_ALPHA = 150;

    /**
     * A point on the path, and the normal of the stone under it, or null in the air.
     */
    private record Step(Vector3f at, Vector3f ground)
    {
    }

    private SuperfluidStreamRender()
    {
    }

    /**
     * Draws the stream from a cradle at the origin of this pose to the head of a ring.
     *
     * @param head        the head, in the cradle block's space
     * @param headTangent which way the ring runs on from its head
     * @param shown       how far it has formed or faded, from 0 to 1
     * @param forming     true while it pours, so it shows from the cradle out; false once it stopped, so it shows from the pearl back
     */
    public static void submitFromCradle(Vector3f head, Vector3f headTangent, Vector3f camera, double time, float shown, boolean forming,
                                        PoseStack poseStack, SubmitNodeCollector collector)
    {
        var ribbon = new SuperfluidQuads();
        var halo = new SuperfluidQuads();
        var sheath = new SuperfluidQuads();
        var beads = new SuperfluidQuads();
        var path = cradlePath(head, headTangent, time);
        var length = length(path);
        if (length <= 1e-4F)
            return;
        var from = forming ? 0 : (1 - shown) * length;
        var to = forming ? shown * length : length;
        draw(path, from, to, camera, time, 1, ribbon, halo, sheath, beads);
        submitLayers(poseStack, collector, ribbon, halo, sheath, beads);
    }

    /**
     * The rings unspooling from a head into a tesseract held at drawTo, all in the pearl block's space.
     */
    static void drawOff(Vector3f head, Vector3f leaving, Vector3f drawTo, Vector3f camera, double time, float strength, SuperfluidQuads ribbon,
                        SuperfluidQuads halo, SuperfluidQuads sheath, SuperfluidQuads beads)
    {
        if (strength <= 0.01F)
            return;
        var path = new ArrayList<Step>();
        path.add(new Step(head, null));
        curve(path, head, leaving, drawTo, new Vector3f(drawTo).sub(head).normalize(), time);
        draw(path, 0, length(path), camera, time, strength, ribbon, halo, sheath, beads);
    }

    static void submitLayers(PoseStack poseStack, SubmitNodeCollector collector, SuperfluidQuads ribbon, SuperfluidQuads halo, SuperfluidQuads sheath,
                             SuperfluidQuads beads)
    {
        var light = LightCoordsUtil.FULL_BRIGHT;
        if (!sheath.isEmpty())
            collector.submitCustomGeometry(poseStack, SuperfluidRingRender.SHEATH_TYPE, (pose, buffer) -> sheath.emit(pose, buffer, light));
        if (!halo.isEmpty())
            collector.submitCustomGeometry(poseStack, SuperfluidRingRender.HALO_TYPE, (pose, buffer) -> halo.emit(pose, buffer, light));
        if (!ribbon.isEmpty())
            collector.submitCustomGeometry(poseStack, SuperfluidRingRender.RIBBON_TYPE, (pose, buffer) -> ribbon.emit(pose, buffer, light));
        if (!beads.isEmpty())
            collector.submitCustomGeometry(poseStack, SuperfluidRingRender.BEAD_TYPE, (pose, buffer) -> beads.emit(pose, buffer, light));
    }

    /**
     * Crystal, floor, inner wall, rim, then the air: the film's corners on the side facing the head, and the ribbon's curve from the rim's lip.
     */
    private static List<Step> cradlePath(Vector3f head, Vector3f headTangent, double time)
    {
        var toward = new Vector3f(head.x - 0.5F, 0, head.z - 0.5F);
        if (toward.lengthSquared() < 1e-6F)
            toward.set(1, 0, 0);
        toward.normalize();
        // The basin is square, so the walls stand farther off along a diagonal
        var square = Math.max(Math.abs(toward.x), Math.abs(toward.z));
        var inner = INNER_HALF / square;
        var outer = OUTER_HALF / square;
        var wallNormal = Math.abs(toward.x) >= Math.abs(toward.z) ? new Vector3f(-Math.signum(toward.x), 0, 0) : new Vector3f(0, 0, -Math.signum(toward.z));
        var up = new Vector3f(0, 1, 0);

        var path = new ArrayList<Step>();
        path.add(new Step(at(toward, CRYSTAL_OUT, FLOOR + CRYSTAL_UP), up));
        path.add(new Step(at(toward, CRYSTAL_OUT + 1F / 16, FLOOR), up));
        path.add(new Step(at(toward, inner, FLOOR), wallNormal));
        path.add(new Step(at(toward, inner, RIM_TOP), up));
        var lip = at(toward, outer, RIM_TOP);
        path.add(new Step(lip, null));

        var rising = new Vector3f(toward).mul(0.4F).add(up).normalize();
        curve(path, lip, rising, head, headTangent, time);
        return path;
    }

    private static Vector3f at(Vector3f toward, float out, float y)
    {
        return new Vector3f(0.5F + toward.x * out, y, 0.5F + toward.z * out);
    }

    /**
     * A slow curve from a point leaving one way to a point arriving another, wavering off true in the middle, the first point left out.
     */
    private static void curve(List<Step> path, Vector3f from, Vector3f leaving, Vector3f to, Vector3f arriving, double time)
    {
        var span = from.distance(to);
        var c1 = new Vector3f(leaving).mul(span * RISE).add(from);
        var c2 = new Vector3f(arriving).mul(-span * MERGE).add(to);
        var up = new Vector3f(0, 1, 0);
        for (var i = 1; i <= FREE_STEPS; i++)
        {
            var t = (float) i / FREE_STEPS;
            var point = bezier(from, c1, c2, to, t);
            var tangent = bezier(from, c1, c2, to, Math.min(1, t + 0.01F)).sub(bezier(from, c1, c2, to, Math.max(0, t - 0.01F)));
            var side = new Vector3f(tangent).cross(up);
            if (side.lengthSquared() > 1e-8F)
            {
                side.normalize();
                var waver = (float) (Math.sin(Math.PI * t) * WAVER * Math.sin(t * Mth.TWO_PI * 1.5 - time * WAVER_RUN));
                point.add(side.mul(waver));
            }
            path.add(new Step(point, null));
        }
    }

    private static Vector3f bezier(Vector3f a, Vector3f b, Vector3f c, Vector3f d, float t)
    {
        var s = 1 - t;
        return new Vector3f(a).mul(s * s * s).add(new Vector3f(b).mul(3 * s * s * t)).add(new Vector3f(c).mul(3 * s * t * t)).add(new Vector3f(d).mul(t * t * t));
    }

    private static float length(List<Step> path)
    {
        var length = 0F;
        for (var i = 1; i < path.size(); i++)
            length += path.get(i - 1).at().distance(path.get(i).at());
        return length;
    }

    /**
     * Draws the part of the path between two distances along it, the film flat on its stone and the rest crossed in the air.
     */
    private static void draw(List<Step> path, float from, float to, Vector3f camera, double time, float strength, SuperfluidQuads ribbon,
                             SuperfluidQuads halo, SuperfluidQuads sheath, SuperfluidQuads beads)
    {
        if (to - from <= 1e-4F)
            return;
        var flow = (float) (time * FLOW);
        var walked = 0F;
        for (var i = 1; i < path.size(); i++)
        {
            var a = path.get(i - 1);
            var b = path.get(i);
            var span = a.at().distance(b.at());
            var start = walked;
            walked += span;
            if (span <= 1e-6F || walked <= from || start >= to)
                continue;
            var t0 = Math.max(0, (from - start) / span);
            var t1 = Math.min(1, (to - start) / span);
            var p0 = new Vector3f(a.at()).lerp(b.at(), t0);
            var p1 = new Vector3f(a.at()).lerp(b.at(), t1);
            var u0 = (start + span * t0) * TEXTURE_PER_BLOCK - flow;
            var u1 = (start + span * t1) * TEXTURE_PER_BLOCK - flow;
            var along = new Vector3f(b.at()).sub(a.at()).normalize();

            if (a.ground() != null)
            {
                // Flat on the stone it climbs
                var side = new Vector3f(along).cross(a.ground()).normalize();
                var lift = new Vector3f(a.ground()).mul(FILM_LIFT);
                var color = SuperfluidRingRender.color(FILM_ALPHA * strength, 1, SuperfluidRingRender.CORE_COLOR);
                ribbon.strip(new Vector3f(p0).add(lift), side, FILM_HALF, new Vector3f(p1).add(lift), side, FILM_HALF, u0, u1, color, color);
                continue;
            }

            var helper = Math.abs(along.y) > 0.9F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
            var first = new Vector3f(along).cross(helper).normalize();
            var second = new Vector3f(along).cross(first).normalize();
            var core = SuperfluidRingRender.color(STREAM_ALPHA * strength, 1, SuperfluidRingRender.CORE_COLOR);
            var cross = SuperfluidRingRender.color(STREAM_ALPHA * strength, 0.75F, SuperfluidRingRender.CROSS_COLOR);
            ribbon.strip(p0, first, STREAM_HALF, p1, first, STREAM_HALF, u0, u1, core, core);
            ribbon.strip(p0, second, STREAM_HALF, p1, second, STREAM_HALF, u0, u1, cross, cross);

            var toCamera = new Vector3f(camera).sub(p0);
            var facing = new Vector3f(along).cross(toCamera);
            if (facing.lengthSquared() > 1e-8F)
            {
                facing.normalize();
                var glow = SuperfluidRingRender.color(HALO_ALPHA * strength, 1, SuperfluidRingRender.HALO_COLOR);
                halo.strip(p0, facing, HALO_HALF, p1, facing, HALO_HALF, u0, u1, glow, glow);
                var dark = SuperfluidRingRender.color(SHEATH_ALPHA * strength, 1, SuperfluidRingRender.SHEATH_COLOR);
                sheath.strip(p0, facing, SHEATH_HALF, p1, facing, SHEATH_HALF, 0, 1, dark, dark);
            }
        }

        // Beads travel the whole way at one pace, unevenly spaced, shown only where the stream is
        var total = walked;
        for (var bead = 0; bead < BEADS; bead++)
        {
            var offset = (bead + 0.37F * Mth.sin(bead * 2.3F)) / BEADS;
            var distance = (float) (((time * BEAD_SPEED / total + offset) % 1 + 1) % 1) * total;
            if (distance < from || distance > to)
                continue;
            var point = pointAt(path, distance);
            beads.sprite(point, camera, BEAD_RADIUS, ARGB.color(Mth.clamp(Math.round(255 * strength), 0, 255), SuperfluidRingRender.BEAD_COLOR));
        }
    }

    private static Vector3f pointAt(List<Step> path, float distance)
    {
        var walked = 0F;
        for (var i = 1; i < path.size(); i++)
        {
            var a = path.get(i - 1).at();
            var b = path.get(i).at();
            var span = a.distance(b);
            if (walked + span >= distance && span > 1e-6F)
            {
                var lift = path.get(i - 1).ground() == null ? new Vector3f() : new Vector3f(path.get(i - 1).ground()).mul(BEAD_RADIUS * 0.5F);
                return new Vector3f(a).lerp(b, (distance - walked) / span).add(lift);
            }
            walked += span;
        }
        return new Vector3f(path.getLast().at());
    }
}
