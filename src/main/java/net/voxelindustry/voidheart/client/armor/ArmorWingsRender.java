package net.voxelindustry.voidheart.client.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import org.joml.Vector3f;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The Void Armor's wings: two fans of light filaments opening from the vents between its shoulder blades while it flies. Where each filament hangs comes
 * from {@link ArmorWings}, which weighs it down and swings it; this draws it as a textured ribbon lying across the wing, a thinner one crossed through it so
 * it holds from the side, and a wide soft halo, all added to what is behind them, over a faint violet tint so they keep their hue against a bright sky.
 * <p>
 * Drawn in the frame model's space: one unit a block before the wearer's scale, y down, the back toward +z, the body's pivot at the neck.
 */
public final class ArmorWingsRender
{
    private static final Identifier CORE_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/misc/armor_wing_core.png");
    private static final Identifier HALO_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/misc/armor_wing_halo.png");

    private static final RenderType CORE_TYPE = VoidHeartRenderTypes.radiant(CORE_TEXTURE);
    private static final RenderType HALO_TYPE = VoidHeartRenderTypes.radiant(HALO_TEXTURE);
    private static final RenderType TINT_TYPE = VoidHeartRenderTypes.tint(HALO_TEXTURE);

    // Colors: the lacing's pale violet, whiter at the thread
    private static final int   CORE_RGB        = 0xE6DAFF;
    private static final int   HALO_RGB        = 0x9C78FF;
    private static final int   TINT_RGB        = 0x3C2178;
    private static final float CORE_ALPHA      = 0.85F;
    private static final float CROSS_ALPHA     = 0.35F;
    private static final float HALO_ALPHA      = 0.5F;
    private static final float TINT_ALPHA      = 0.35F;

    // Widths, as half widths in model units, tapering to the tip
    private static final float CORE_HALF_ROOT  = 0.07F;
    private static final float CORE_HALF_TIP   = 0.015F;
    private static final float HALO_HALF_ROOT  = 0.22F;
    private static final float HALO_HALF_TIP   = 0.06F;
    /** The filaments brighten over the first part of their length, so their roots don't pile into one hot spot, and fade over the last half. */
    private static final float FADE_IN         = 0.18F;
    private static final float FADE_OUT_FROM   = 0.45F;
    /** How long a fresh tip takes to brighten as it grows, as a share of the filament. */
    private static final float TIP_SOFTNESS    = 0.15F;

    // The texture: how many repeats a unit, and how fast its sheen flows out toward the tips
    private static final float TEXTURE_PER_UNIT = 2.2F;
    private static final float FLOW_SPEED      = 0.06F;

    // Fraying as the lift fades: filaments flicker out, thin and dim
    private static final float FRAY_DROPOUT    = 0.6F;
    private static final float FRAY_THIN       = 0.5F;
    private static final float FRAY_FLICKER_TICKS = 3;

    /** Pieces drawn between two weights of a filament, along a smooth curve through them. */
    private static final int   PIECES_PER_LINK = 2;

    private ArmorWingsRender()
    {
    }

    /**
     * Submits the wings at the pose stack, which stands in the frame's model space.
     */
    public static void submit(ArmorWings.Wings wings, PoseStack poseStack, SubmitNodeCollector collector)
    {
        if (!wings.shown())
            return;
        var tint = new Quads();
        var halo = new Quads();
        var core = new Quads();
        for (var filament : wings.filaments())
            filament(wings, filament, tint, halo, core);
        tint.submit(collector, poseStack, TINT_TYPE);
        halo.submit(collector, poseStack, HALO_TYPE);
        core.submit(collector, poseStack, CORE_TYPE);
    }

    private static void filament(ArmorWings.Wings wings, ArmorWings.Filament filament, Quads tint, Quads halo, Quads core)
    {
        int seed = filament.seed();
        float time = wings.time();
        float alpha = 1;
        float thin = 1;
        if (wings.fray() > 0)
        {
            float flicker = WingShape.noise(seed, (int) (time / FRAY_FLICKER_TICKS));
            if (flicker < wings.fray() * FRAY_DROPOUT)
                return;
            alpha = 1 - wings.fray() * (0.35F + 0.65F * flicker);
            thin = 1 - wings.fray() * FRAY_THIN;
        }

        var nodes = filament.points();
        int steps = (nodes.length - 1) * PIECES_PER_LINK;
        var points = new Vector3f[steps + 1];
        for (int j = 0; j <= steps; j++)
            points[j] = curve(nodes, j / (float) PIECES_PER_LINK, new Vector3f());

        // Each point's edges are shared by the two pieces that meet there, so the ribbon doesn't notch where it bends
        var sides = new Vector3f[steps + 1];
        var crosses = new Vector3f[steps + 1];
        for (int j = 0; j <= steps; j++)
        {
            var tangent = new Vector3f(points[Math.min(steps, j + 1)]).sub(points[Math.max(0, j - 1)]);
            if (tangent.lengthSquared() < 1.0E-10F)
                tangent.set(0, -1, 0);
            tangent.normalize();
            var flat = new Vector3f(filament.facing()).cross(tangent);
            if (flat.lengthSquared() < 1.0E-6F)
                flat.set(1, 0, 0);
            sides[j] = flat.normalize();
            crosses[j] = new Vector3f(tangent).cross(sides[j]).normalize();
        }

        float growth = filament.growth();
        float length = filament.length();
        float pace = 0.8F + 0.4F * WingShape.noise(seed, 1);
        float flow = time * FLOW_SPEED * pace + WingShape.noise(seed, 5);
        for (int j = 0; j < steps; j++)
        {
            float u0 = growth * j / steps;
            float u1 = growth * (j + 1) / steps;
            float fade0 = fade(u0, growth) * alpha;
            float fade1 = fade(u1, growth) * alpha;
            float tex0 = length * u0 * TEXTURE_PER_UNIT - flow;
            float tex1 = length * u1 * TEXTURE_PER_UNIT - flow;

            float core0 = Mth.lerp(u0, CORE_HALF_ROOT, CORE_HALF_TIP) * thin;
            float core1 = Mth.lerp(u1, CORE_HALF_ROOT, CORE_HALF_TIP) * thin;
            float halo0 = Mth.lerp(u0, HALO_HALF_ROOT, HALO_HALF_TIP) * thin;
            float halo1 = Mth.lerp(u1, HALO_HALF_ROOT, HALO_HALF_TIP) * thin;

            tint.ribbon(points[j], points[j + 1], sides[j], sides[j + 1], halo0, halo1, tex0, tex1, color(TINT_RGB, TINT_ALPHA * fade0), color(TINT_RGB, TINT_ALPHA * fade1));
            halo.ribbon(points[j], points[j + 1], sides[j], sides[j + 1], halo0, halo1, tex0, tex1, color(HALO_RGB, HALO_ALPHA * fade0), color(HALO_RGB, HALO_ALPHA * fade1));
            core.ribbon(points[j], points[j + 1], sides[j], sides[j + 1], core0, core1, tex0, tex1, color(CORE_RGB, CORE_ALPHA * fade0), color(CORE_RGB, CORE_ALPHA * fade1));
            core.ribbon(points[j], points[j + 1], crosses[j], crosses[j + 1], core0 * 0.6F, core1 * 0.6F, tex0, tex1, color(CORE_RGB, CROSS_ALPHA * fade0),
                    color(CORE_RGB, CROSS_ALPHA * fade1));
        }
    }

    /**
     * A point on the smooth curve through the weights, at a place counted in links from the root.
     */
    private static Vector3f curve(Vector3f[] nodes, float at, Vector3f out)
    {
        int last = nodes.length - 1;
        int i = Math.min(last - 1, (int) at);
        float t = at - i;
        var p0 = nodes[Math.max(0, i - 1)];
        var p1 = nodes[i];
        var p2 = nodes[i + 1];
        var p3 = nodes[Math.min(last, i + 2)];
        float t2 = t * t;
        float t3 = t2 * t;
        // Catmull-Rom, which passes through every weight
        float a = -0.5F * t3 + t2 - 0.5F * t;
        float b = 1.5F * t3 - 2.5F * t2 + 1;
        float c = -1.5F * t3 + 2 * t2 + 0.5F * t;
        float d = 0.5F * t3 - 0.5F * t2;
        return out.set(p0.x * a + p1.x * b + p2.x * c + p3.x * d, p0.y * a + p1.y * b + p2.y * c + p3.y * d, p0.z * a + p1.z * b + p2.z * c + p3.z * d);
    }

    /**
     * Bright along the first half, gone at the tip, and soft at the front of a filament still growing.
     */
    private static float fade(float u, float growth)
    {
        float along = smooth(u / FADE_IN) * (1 - smooth((u - FADE_OUT_FROM) / (1 - FADE_OUT_FROM)));
        if (growth < 1)
            along *= Mth.clamp((growth - u) / TIP_SOFTNESS, 0, 1);
        return along;
    }

    private static float smooth(float t)
    {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static int color(int rgb, float alpha)
    {
        return ARGB.color(Mth.clamp((int) (alpha * 255), 0, 255), rgb);
    }

    /**
     * Quads gathered while submitting and written out later in the frame, when the geometry is built. A fresh one per submit, since that comes after.
     */
    private static final class Quads
    {
        private final FloatArrayList vertices = new FloatArrayList();
        private final IntArrayList   colors   = new IntArrayList();

        void ribbon(Vector3f start, Vector3f end, Vector3f startSide, Vector3f endSide, float startHalf, float endHalf, float u0, float u1, int startColor,
                    int endColor)
        {
            if (ARGB.alpha(startColor) == 0 && ARGB.alpha(endColor) == 0)
                return;
            vertex(start.x - startSide.x * startHalf, start.y - startSide.y * startHalf, start.z - startSide.z * startHalf, u0, 0, startColor);
            vertex(start.x + startSide.x * startHalf, start.y + startSide.y * startHalf, start.z + startSide.z * startHalf, u0, 1, startColor);
            vertex(end.x + endSide.x * endHalf, end.y + endSide.y * endHalf, end.z + endSide.z * endHalf, u1, 1, endColor);
            vertex(end.x - endSide.x * endHalf, end.y - endSide.y * endHalf, end.z - endSide.z * endHalf, u1, 0, endColor);
        }

        private void vertex(float x, float y, float z, float u, float v, int color)
        {
            vertices.add(x);
            vertices.add(y);
            vertices.add(z);
            vertices.add(u);
            vertices.add(v);
            colors.add(color);
        }

        void submit(SubmitNodeCollector collector, PoseStack poseStack, RenderType type)
        {
            if (!colors.isEmpty())
                collector.submitCustomGeometry(poseStack, type, this::emit);
        }

        private void emit(PoseStack.Pose pose, VertexConsumer buffer)
        {
            for (int i = 0; i < colors.size(); i++)
            {
                int at = i * 5;
                buffer.addVertex(pose, vertices.getFloat(at), vertices.getFloat(at + 1), vertices.getFloat(at + 2))
                        .setColor(colors.getInt(i))
                        .setUv(vertices.getFloat(at + 3), vertices.getFloat(at + 4))
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightCoordsUtil.FULL_BRIGHT)
                        .setNormal(pose, 0, 1, 0);
            }
        }
    }
}
