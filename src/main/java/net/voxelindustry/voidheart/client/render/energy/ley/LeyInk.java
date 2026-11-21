package net.voxelindustry.voidheart.client.render.energy.ley;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.crystal.CrystalCradleTile;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneTile;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The ink thread of a scouring cradle: a violet-black thread from the cradle's foot along the ground to its stone, then down the spur's seams to the line,
 * with dark beads drifting toward the node that takes the foulness. Blended over what lies beneath rather than added, so the teal seam darkens where it
 * passes. While a breached line holds it back the beads stop and the thread dims.
 * <p>
 * Each thread is laid on the ground when it starts and every few seconds after, so a frame only places cached points: about 3 quads per half block of
 * thread plus a fan per bead.
 */
final class LeyInk
{
    private static final float  STEP          = 0.5F;
    private static final float  CRADLE_EDGE   = 0.45F;
    private static final float  STONE_FOOT    = 0.45F;
    private static final double LIFT          = 0.045;
    private static final int    REBUILD_TICKS = 100;
    private static final int    MAX_STONES    = 8;
    private static final double DRAW_DISTANCE = 128;

    private static final int   INK_RGB      = 0x1A0F24;
    private static final float INK_ALPHA    = 0.8F;
    private static final float INK_STALLED  = 0.5F;
    private static final float CORE_HALF    = 0.06F;
    private static final float SOFT_HALF    = 0.15F;
    private static final float START_FADE   = 0.6F;
    // What is left of a seam's teal where a thread runs along it, since the seam's glow is added over anything blended beneath it
    private static final float SEAM_UNDER_INK = 0.2F;

    private static final int   BEAD_RGB     = 0x050208;
    private static final int   BEAD_RIM_RGB = 0x3A1F52;
    private static final float BEAD_ALPHA   = 0.9F;
    private static final float BEAD_RADIUS  = 0.09F;
    private static final float BEAD_SPACING = 1.6F;
    private static final float BEAD_SPEED   = 0.05F;
    private static final float BEAD_HEIGHT  = 0.06F;

    private static final class Thread
    {
        BlockPos stone;
        double[] xs, ys, zs;
        float[]  along;
        float    length;
        long     builtAt;
        List<LeyStoneTile> stones = new ArrayList<>();
    }

    private static final Map<CrystalCradleTile, Thread> THREADS = new WeakHashMap<>();

    private LeyInk()
    {
    }

    static void clear()
    {
        THREADS.clear();
    }

    static void tick(Level level, long time)
    {
        THREADS.keySet().removeIf(cradle -> !scouring(cradle) || !CrystalCradleTile.CLIENT_LOADED.contains(cradle));
        for (var cradle : CrystalCradleTile.CLIENT_LOADED)
        {
            if (!scouring(cradle))
                continue;
            var thread = THREADS.get(cradle);
            if (thread == null || !thread.stone.equals(cradle.scourStone()) || time - thread.builtAt > REBUILD_TICKS)
            {
                var built = build(level, cradle, time);
                if (built == null)
                    THREADS.remove(cradle);
                else
                    THREADS.put(cradle, built);
            }
        }
    }

    private static boolean scouring(CrystalCradleTile cradle)
    {
        var source = cradle.source();
        return cradle.scourStone() != null && (source == CrystalCradleTile.Source.SCOURING || source == CrystalCradleTile.Source.BREACHED);
    }

    /**
     * From the cradle's foot over the ground to its stone, then down each stone's seam toward the line, stone to stone along the chain.
     */
    private static Thread build(Level level, CrystalCradleTile cradle, long time)
    {
        var stonePos = cradle.scourStone();
        var points = new ArrayList<double[]>();
        var cradlePos = cradle.getBlockPos();
        double cx = cradlePos.getX() + 0.5, cz = cradlePos.getZ() + 0.5;
        double sx = stonePos.getX() + 0.5, sz = stonePos.getZ() + 0.5;
        double dx = sx - cx, dz = sz - cz;
        double full = Math.sqrt(dx * dx + dz * dz);
        double from = CRADLE_EDGE, to = full - STONE_FOOT;
        if (to > from)
        {
            int samples = Math.max(2, Mth.ceil((to - from) / STEP) + 1);
            int reference = cradlePos.getY();
            for (int i = 0; i < samples; i++)
            {
                double t = (from + (to - from) * i / (samples - 1)) / full;
                double x = cx + dx * t, z = cz + dz * t;
                double y = LeyGround.surface(level, x, z, reference);
                if (Double.isNaN(y))
                    y = reference;
                reference = Mth.floor(y);
                points.add(new double[] { x, y + LIFT, z });
            }
        }

        var stones = new ArrayList<LeyStoneTile>();
        var stonePosition = stonePos;
        for (int hop = 0; hop < MAX_STONES && stonePosition != null; hop++)
        {
            if (!(level.getBlockEntity(stonePosition) instanceof LeyStoneTile stone) || !stone.isConnected())
                break;
            stones.add(stone);
            var seam = LeySeams.points(stone);
            if (seam != null)
            {
                // Seam points run from the upstream end at index 0 to the stone's foot
                for (int i = seam[0].length - 1; i >= 0; i--)
                    points.add(new double[] { seam[0][i], seam[1][i] - 0.03 + LIFT, seam[2][i] });
            }
            stonePosition = stone.isRoot() ? null : BlockPos.containing(stone.upstreamX(), stone.upstreamY(), stone.upstreamZ());
        }
        if (points.size() < 2)
            return null;

        var thread = new Thread();
        thread.stone = stonePos;
        thread.stones = stones;
        thread.builtAt = time;
        int count = points.size();
        thread.xs = new double[count];
        thread.ys = new double[count];
        thread.zs = new double[count];
        thread.along = new float[count];
        for (int i = 0; i < count; i++)
        {
            thread.xs[i] = points.get(i)[0];
            thread.ys[i] = points.get(i)[1];
            thread.zs[i] = points.get(i)[2];
            if (i > 0)
            {
                double ax = thread.xs[i] - thread.xs[i - 1], ay = thread.ys[i] - thread.ys[i - 1], az = thread.zs[i] - thread.zs[i - 1];
                thread.along[i] = thread.along[i - 1] + (float) Math.sqrt(ax * ax + ay * ay + az * az);
            }
        }
        thread.length = thread.along[count - 1];
        return thread;
    }

    /**
     * How much of a stone's seam glow shows: dimmed where a scouring thread runs along it, so the seam darkens there.
     */
    static float seamShown(LeyStoneTile stone)
    {
        for (var thread : THREADS.values())
        {
            if (thread.stones.contains(stone))
                return SEAM_UNDER_INK;
        }
        return 1;
    }

    static void draw(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, float time)
    {
        THREADS.forEach((cradle, thread) ->
        {
            if (Vec3.atCenterOf(cradle.getBlockPos()).distanceToSqr(camera) > DRAW_DISTANCE * DRAW_DISTANCE || thread.length <= 0)
                return;
            boolean stalled = cradle.source() == CrystalCradleTile.Source.BREACHED;
            thread(pose, buffer, camera, thread, stalled ? INK_STALLED : INK_ALPHA);
            beads(pose, buffer, camera, thread, stalled ? 0 : time, stalled ? INK_STALLED : 1);
        });
    }

    private static void thread(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, Thread thread, float alpha)
    {
        int none = LeyLook.color(INK_RGB, 0);
        for (int i = 0; i < thread.xs.length - 1; i++)
        {
            var a = LeyLook.relative(thread.xs[i], thread.ys[i], thread.zs[i], camera);
            var b = LeyLook.relative(thread.xs[i + 1], thread.ys[i + 1], thread.zs[i + 1], camera);
            if (a.distanceSquared(b) < 1.0E-8F)
                continue;
            var side = flatSide(a, b);
            // It seeps out of the cradle's foot rather than starting sharp
            int coreA = LeyLook.color(INK_RGB, alpha * Mth.clamp(thread.along[i] / START_FADE, 0, 1));
            int coreB = LeyLook.color(INK_RGB, alpha * Mth.clamp(thread.along[i + 1] / START_FADE, 0, 1));
            strip(pose, buffer, a, b, side, -SOFT_HALF, -CORE_HALF, none, coreA, none, coreB);
            strip(pose, buffer, a, b, side, -CORE_HALF, CORE_HALF, coreA, coreA, coreB, coreB);
            strip(pose, buffer, a, b, side, CORE_HALF, SOFT_HALF, coreA, none, coreB, none);
        }
    }

    /**
     * Dark beads drifting from the cradle toward the node, fading in at the cradle and out at the line.
     */
    private static void beads(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, Thread thread, float time, float dim)
    {
        float offset = (time * BEAD_SPEED) % BEAD_SPACING;
        for (float along = offset; along < thread.length; along += BEAD_SPACING)
        {
            float ends = Mth.clamp(Math.min(along, thread.length - along) / 0.8F, 0, 1);
            var at = at(thread, along, camera).add(0, BEAD_HEIGHT, 0);
            LeyLook.softDot(buffer, pose, at, BEAD_RADIUS * 1.6F, BEAD_RIM_RGB, BEAD_ALPHA * 0.5F * ends * dim);
            LeyLook.softDot(buffer, pose, at, BEAD_RADIUS, BEAD_RGB, BEAD_ALPHA * ends * dim);
        }
    }

    private static Vector3f at(Thread thread, float along, Vec3 camera)
    {
        int i = 0;
        while (i < thread.along.length - 2 && thread.along[i + 1] < along)
            i++;
        float span = thread.along[i + 1] - thread.along[i];
        float t = span <= 0 ? 0 : Mth.clamp((along - thread.along[i]) / span, 0, 1);
        return LeyLook.relative(Mth.lerp(t, thread.xs[i], thread.xs[i + 1]), Mth.lerp(t, thread.ys[i], thread.ys[i + 1]),
                Mth.lerp(t, thread.zs[i], thread.zs[i + 1]), camera);
    }

    private static Vector3f flatSide(Vector3f a, Vector3f b)
    {
        float dx = b.x - a.x, dz = b.z - a.z;
        float length = Mth.sqrt(dx * dx + dz * dz);
        return length < 1.0E-5F ? new Vector3f(1, 0, 0) : new Vector3f(-dz / length, 0, dx / length);
    }

    private static void strip(PoseStack.Pose pose, VertexConsumer buffer, Vector3f a, Vector3f b, Vector3f side, float from, float to,
                              int aFrom, int aTo, int bFrom, int bTo)
    {
        LeyLook.vertex(buffer, pose, new Vector3f(a).add(side.x * from, 0, side.z * from), aFrom);
        LeyLook.vertex(buffer, pose, new Vector3f(a).add(side.x * to, 0, side.z * to), aTo);
        LeyLook.vertex(buffer, pose, new Vector3f(b).add(side.x * to, 0, side.z * to), bTo);
        LeyLook.vertex(buffer, pose, new Vector3f(b).add(side.x * from, 0, side.z * from), bFrom);
    }
}
