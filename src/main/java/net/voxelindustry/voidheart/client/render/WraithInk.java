package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassages;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Wraith ink, shared by the wraith's stain, the Wraith Veil's marks and its tunnel mouths. A mark is one quad whose look comes from a timing
 * mask, so it spreads from several seeds with a glowing front instead of scaling up, and its hole opens along an edge the mouth from inside
 * shares exactly. Which mask, and how it is flipped, follows from where the mark is, so the mark and its mouth always agree.
 * <p>
 * The quad is opaque and writes depth, a hair off its surface, and nothing else draws on that plane. It is cut along block edges to the faces
 * that can hold ink, at most a part per block face it covers.
 */
public final class WraithInk
{
    public static final Identifier SMOKE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/wall_passage_smoke.png");

    private static final int          MASKS  = 3;
    private static final Identifier[] TALL   = masks("tall");
    private static final Identifier[] SQUARE = masks("square");
    // A mark this many times taller than wide takes the tall masks
    private static final float        TALL_RATIO = 1.5F;

    // Spread at which every pixel the mask inks is inked, tendril tips included, with no front left glowing
    public static final float SPREAD_FULL = 0.96F;

    private WraithInk()
    {
    }

    private static Identifier[] masks(String shape)
    {
        var masks = new Identifier[MASKS];
        for (int i = 0; i < MASKS; i++)
            masks[i] = Identifier.fromNamespaceAndPath(MODID, "textures/entity/wraith_ink/wraith_ink_" + shape + "_" + i + ".png");
        return masks;
    }

    public record Variant(Identifier mask, boolean flipU, boolean flipV)
    {
        public RenderType ink()
        {
            return VoidHeartRenderTypes.wraithInk(SMOKE, mask);
        }

        public RenderType mouth()
        {
            return VoidHeartRenderTypes.wraithMouth(SMOKE, mask);
        }

        /**
         * @return where a point offset from a mark's center by u and v lies on its mask
         */
        public float[] uv(float u, float v, float halfU, float halfV)
        {
            float x = 0.5F + u / (2 * halfU), y = 0.5F - v / (2 * halfV);
            return new float[]{flipU ? 1 - x : x, flipV ? 1 - y : y};
        }
    }

    /**
     * Picks from the two coordinates along the surface only, since a mark and its mouth sit a hair apart across it.
     */
    public static Variant variant(Direction face, Vec3 center, float halfU, float halfV)
    {
        int a, b;
        switch (face.getAxis())
        {
            case X ->
            {
                a = (int) Math.round(center.z * 2);
                b = (int) Math.round(center.y * 2);
            }
            case Y ->
            {
                a = (int) Math.round(center.x * 2);
                b = (int) Math.round(center.z * 2);
            }
            default ->
            {
                a = (int) Math.round(center.x * 2);
                b = (int) Math.round(center.y * 2);
            }
        }
        int h = a * 73856093 ^ b * 19349663 ^ face.ordinal() * 83492791;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        var masks = halfV > halfU * TALL_RATIO ? TALL : SQUARE;
        return new Variant(masks[Math.floorMod(h, MASKS)], (h & 0x100) != 0, (h & 0x200) != 0);
    }

    public enum Mode
    {
        // The hole shows the realm, as it does to anyone but the tunnel's bearer
        MARK(255),
        // The hole is left open, onto the tunnel its bearer's client has opened behind it
        WINDOW(128),
        // Only the glowing front, where the ink would be
        PREVIEW(0);

        final int alpha;

        Mode(int alpha)
        {
            this.alpha = alpha;
        }
    }

    /**
     * @param spread how far the ink has spread, 0 to {@link #SPREAD_FULL}
     * @param open   how far its hole has opened, 0 to 1
     * @param glow   how brightly its fronts and the hole's edge glow, 0 to 1
     * @return the vertex color the shader reads its timeline from
     */
    public static int color(float spread, float open, float glow, Mode mode)
    {
        return ARGB.color(mode.alpha, channel(spread), channel(open), channel(glow));
    }

    private static int channel(float value)
    {
        return Mth.clamp(Math.round(value * 255), 0, 255);
    }

    /**
     * @param center the mark's center, on or a hair off the surface
     * @return the parts of a mark lying on block faces that can hold ink, as {u0, v0, u1, v1} offsets from its center, one per block face, so
     * no ink hangs in the air past an edge or over a gap. A mark wholly on faces is one part. Blocks a Wraith Veil tunnel erased from sight
     * count as what they really are.
     */
    public static List<float[]> holding(Level level, Direction face, Vec3 center, float halfU, float halfV)
    {
        var axis = face.getAxis();
        int plane = (int) Math.round(axis.choose(center.x, center.y, center.z));
        int behind = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? plane - 1 : plane;
        double cu = axis == Direction.Axis.X ? center.z : center.x;
        double cv = axis == Direction.Axis.Y ? center.z : center.y;

        var parts = new ArrayList<float[]>();
        boolean all = true;
        for (int a = Mth.floor(cu - halfU); a < cu + halfU; a++)
        {
            for (int b = Mth.floor(cv - halfV); b < cv + halfV; b++)
            {
                var pos = switch (axis)
                {
                    case X -> new BlockPos(behind, b, a);
                    case Y -> new BlockPos(a, behind, b);
                    case Z -> new BlockPos(a, b, behind);
                };
                if (!WallPassages.trueState(level, pos).isFaceSturdy(level, pos, face))
                {
                    all = false;
                    continue;
                }
                parts.add(new float[]{(float) Math.max(a - cu, -halfU), (float) Math.max(b - cv, -halfV),
                        (float) Math.min(a + 1 - cu, halfU), (float) Math.min(b + 1 - cv, halfV)});
            }
        }
        return all ? List.of(new float[]{-halfU, -halfV, halfU, halfV}) : parts;
    }

    /**
     * Draws the given parts of a mark flat on its surface, centered on the pose's origin, see {@link #holding}.
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Direction face, float halfU, float halfV, Variant variant, int color, int light,
                              List<float[]> parts)
    {
        if (parts.isEmpty())
            return;
        collector.submitCustomGeometry(poseStack, variant.ink(), (pose, buffer) ->
        {
            var normal = face.getUnitVec3f();
            for (var part : parts)
            {
                for (int corner = 0; corner < 4; corner++)
                {
                    float du = (corner == 1 || corner == 2) ? part[2] : part[0];
                    float dv = (corner >= 2) ? part[3] : part[1];
                    var at = onSurface(face, du, dv);
                    var uv = variant.uv(du, dv, halfU, halfV);
                    buffer.addVertex(pose, at[0], at[1], at[2])
                            .setColor(color)
                            .setUv(uv[0], uv[1])
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(light)
                            .setNormal(pose, normal.x(), normal.y(), normal.z());
                }
            }
        });
    }

    /**
     * @return the offset from a mark's center to a point on its surface: u along x, or z on a face toward east or west; v along y on a wall, z on
     * a floor or ceiling
     */
    public static float[] onSurface(Direction face, float u, float v)
    {
        return switch (face.getAxis())
        {
            case X -> new float[]{0, v, u};
            case Y -> new float[]{u, 0, v};
            case Z -> new float[]{u, v, 0};
        };
    }
}
