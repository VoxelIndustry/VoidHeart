package net.voxelindustry.voidheart.client.render.energy.ley;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneTile;
import net.voxelindustry.voidheart.common.ley.TaintStage;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Spurs as seams of light along the ground, from where they tap their line, or from the stone before, to each stone. Motes travel along them toward the base.
 * A seam's ground is found once when it changes and again every few seconds, so a frame only recolors cached points: about 3 quads per half block, plus a
 * handful per mote.
 */
final class LeySeams
{
    private static final float  STEP          = 0.5F;
    private static final float  STONE_FOOT    = 0.45F;
    private static final double LIFT          = 0.03;
    private static final int    REBUILD_TICKS = 100;
    private static final double DRAW_DISTANCE = 160;

    private static final float SEAM_ALPHA   = 0.3F;
    private static final float FAR_END_FADE = 0.3F;
    private static final float CORE_HALF    = 0.05F;
    private static final float SOFT_HALF    = 0.13F;
    private static final float GAP_CHANCE   = 0.18F;

    private static final float SWIRL_RADIUS = 0.55F;
    private static final float SWIRL_CURL   = 1.7F;
    private static final float SWIRL_TURN   = 0.03F;
    private static final float SWIRL_ALPHA  = 0.5F;
    private static final int   SWIRL_ARMS   = 3;
    private static final int   SWIRL_STEPS  = 8;

    private static final float MOTE_SPEED  = 0.05F;
    private static final float MOTE_ALPHA  = 0.65F;
    private static final float MOTE_RADIUS = 0.08F;
    private static final float MOTE_TAIL   = 0.5F;
    private static final float MOTE_HEIGHT = 0.12F;

    private static final class Path
    {
        double ux, uz;
        int    uy;
        double[] xs, ys, zs;
        float  length;
        float  step;
        float  period;
        float[] motes;
        long   builtAt;
    }

    private static final Map<LeyStoneTile, Path> PATHS = new WeakHashMap<>();

    private LeySeams()
    {
    }

    static void clear()
    {
        PATHS.clear();
    }

    static void tick(Level level, long time)
    {
        PATHS.keySet().removeIf(stone -> !stone.isConnected() || !LeyStoneTile.CLIENT_LOADED.contains(stone));
        for (var stone : LeyStoneTile.CLIENT_LOADED)
        {
            if (!stone.isConnected())
                continue;
            var path = PATHS.get(stone);
            if (path == null || path.ux != stone.upstreamX() || path.uz != stone.upstreamZ() || path.uy != stone.upstreamY() || time - path.builtAt > REBUILD_TICKS)
                PATHS.put(stone, build(level, stone, time));
        }
    }

    /**
     * Samples the ground from the stone back toward its upstream end, each sample searching near the last, so the seam climbs and falls with the land.
     */
    private static Path build(Level level, LeyStoneTile stone, long time)
    {
        var pos = stone.getBlockPos();
        double sx = pos.getX() + 0.5, sz = pos.getZ() + 0.5;
        double ux = stone.upstreamX(), uz = stone.upstreamZ();
        double dx = ux - sx, dz = uz - sz;
        double full = Math.sqrt(dx * dx + dz * dz);

        var path = new Path();
        path.ux = ux;
        path.uz = uz;
        path.uy = stone.upstreamY();
        path.builtAt = time;

        // A seam leaves a stone at its foot, and from the line itself at the tap
        double start = STONE_FOOT;
        double end = stone.isRoot() ? full : full - STONE_FOOT;
        float length = (float) Math.max(0, end - start);
        int samples = Math.max(2, Mth.ceil(length / STEP) + 1);
        path.length = length;
        path.step = length / (samples - 1);
        path.xs = new double[samples];
        path.ys = new double[samples];
        path.zs = new double[samples];

        int reference = pos.getY();
        for (int i = 0; i < samples; i++)
        {
            // Index 0 is the upstream end, so walk from the last index back
            int index = samples - 1 - i;
            double t = full == 0 ? 0 : (start + i * (double) path.step) / full;
            double x = sx + dx * t;
            double z = sz + dz * t;
            double y = LeyGround.surface(level, x, z, reference);
            if (Double.isNaN(y))
                y = reference;
            reference = Mth.floor(y);
            path.xs[index] = x;
            path.ys[index] = y + LIFT;
            path.zs[index] = z;
        }

        int count = Math.max(1, (int) (length / 1.5F) + 1);
        path.motes = new float[count];
        float along = 0;
        for (int i = 0; i < count; i++)
        {
            path.motes[i] = along;
            along += 1 + LeyLook.hash(pos.asLong(), i, 11);
        }
        path.period = Math.max(along, length + MOTE_TAIL);
        return path;
    }

    static void draw(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, float time, float partialTicks)
    {
        PATHS.forEach((stone, path) ->
        {
            if (path.length <= 0 || Vec3.atCenterOf(stone.getBlockPos()).distanceToSqr(camera) > DRAW_DISTANCE * DRAW_DISTANCE)
                return;
            float level = LeyLook.flowLevel(stone.glow(partialTicks));
            if (level < 0.01F)
                return;

            float taint = stone.taint();
            int rgb = LeyLook.lerp(LeyLook.TEAL, LeyLook.MAUVE, taint / 100);
            int light = LeyLook.lerp(LeyLook.TEAL_LIGHT, LeyLook.MAUVE, taint / 100);
            float gaps = TaintStage.of(taint).isAtLeast(TaintStage.FESTERING) ? GAP_CHANCE + 0.2F * Mth.clamp((taint - 50) / 50, 0, 1) : 0;

            seam(pose, buffer, camera, stone, path, rgb, level, gaps);
            if (stone.isRoot())
                swirl(pose, buffer, camera, path, rgb, level, time);
            motes(pose, buffer, camera, stone, path, light, level, time);
        });
    }

    private static void seam(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, LeyStoneTile stone, Path path, int rgb, float level, float gaps)
    {
        float spur = Math.max(stone.spurLength(), 1);
        long seed = stone.getBlockPos().asLong();
        for (int i = 0; i < path.xs.length - 1; i++)
        {
            if (gaps > 0 && LeyLook.hash(seed, i, 3) < gaps)
                continue;

            float along = stone.pathStart() + (i + 0.5F) * path.step;
            float alpha = SEAM_ALPHA * level * (1 - FAR_END_FADE * Mth.clamp(along / spur, 0, 1));
            var a = LeyLook.relative(path.xs[i], path.ys[i], path.zs[i], camera);
            var b = LeyLook.relative(path.xs[i + 1], path.ys[i + 1], path.zs[i + 1], camera);
            var side = flatSide(a, b);

            int core = LeyLook.color(rgb, alpha);
            int none = LeyLook.color(rgb, 0);
            strip(pose, buffer, a, b, side, -SOFT_HALF, -CORE_HALF, none, core);
            strip(pose, buffer, a, b, side, -CORE_HALF, CORE_HALF, core, core);
            strip(pose, buffer, a, b, side, CORE_HALF, SOFT_HALF, core, none);
        }
    }

    /**
     * A small whirl on the ground where the spur leaves its line, turning slowly.
     */
    private static void swirl(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, Path path, int rgb, float level, float time)
    {
        var centre = LeyLook.relative(path.xs[0], path.ys[0] + 0.01, path.zs[0], camera);
        float turn = time * SWIRL_TURN;
        for (int arm = 0; arm < SWIRL_ARMS; arm++)
        {
            float base = turn + Mth.TWO_PI * arm / SWIRL_ARMS;
            for (int i = 0; i < SWIRL_STEPS; i++)
            {
                float t0 = (float) i / SWIRL_STEPS, t1 = (float) (i + 1) / SWIRL_STEPS;
                var a = spiral(centre, base, t0);
                var b = spiral(centre, base, t1);
                var side = flatSide(a, b);
                float width = 0.07F * (1 - t0 * 0.6F);
                int inner = LeyLook.color(rgb, SWIRL_ALPHA * level * (1 - t0));
                int outer = LeyLook.color(rgb, SWIRL_ALPHA * level * (1 - t1));
                int none = LeyLook.color(rgb, 0);
                LeyLook.vertex(buffer, pose, offset(a, side, -width), none);
                LeyLook.vertex(buffer, pose, a, inner);
                LeyLook.vertex(buffer, pose, b, outer);
                LeyLook.vertex(buffer, pose, offset(b, side, -width), none);
                LeyLook.vertex(buffer, pose, a, inner);
                LeyLook.vertex(buffer, pose, offset(a, side, width), none);
                LeyLook.vertex(buffer, pose, offset(b, side, width), none);
                LeyLook.vertex(buffer, pose, b, outer);
            }
        }
    }

    private static Vector3f spiral(Vector3f centre, float base, float t)
    {
        float radius = 0.06F + SWIRL_RADIUS * t;
        float angle = base + SWIRL_CURL * t;
        return new Vector3f(centre).add(Mth.cos(angle) * radius, 0, Mth.sin(angle) * radius);
    }

    /**
     * Soft motes with short tails, a block or two apart, travelling toward the base at about a block a second.
     */
    private static void motes(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, LeyStoneTile stone, Path path, int rgb, float level, float time)
    {
        for (float offset : path.motes)
        {
            float along = (offset + time * MOTE_SPEED) % path.period;
            if (along > path.length)
                continue;
            float ends = Mth.clamp(Math.min(along, path.length - along) / 0.6F, 0, 1);
            float alpha = MOTE_ALPHA * level * ends;

            var head = at(path, along, camera);
            var tail = at(path, Math.max(0, along - MOTE_TAIL), camera);
            head.add(0, MOTE_HEIGHT, 0);
            tail.add(0, MOTE_HEIGHT, 0);
            var side = LeyLook.facingSide(tail, head);
            int bright = LeyLook.color(rgb, alpha * 0.6F);
            int none = LeyLook.color(rgb, 0);
            float half = MOTE_RADIUS * 0.45F;
            LeyLook.vertex(buffer, pose, offset(tail, side, -half * 0.3F), none);
            LeyLook.vertex(buffer, pose, offset(tail, side, half * 0.3F), none);
            LeyLook.vertex(buffer, pose, offset(head, side, half), bright);
            LeyLook.vertex(buffer, pose, offset(head, side, -half), bright);
            LeyLook.softDot(buffer, pose, head, MOTE_RADIUS, rgb, alpha);
        }
    }

    private static Vector3f at(Path path, float along, Vec3 camera)
    {
        float index = path.step <= 0 ? 0 : Mth.clamp(along / path.step, 0, path.xs.length - 1);
        int i = Math.min((int) index, path.xs.length - 2);
        float t = index - i;
        return LeyLook.relative(Mth.lerp(t, path.xs[i], path.xs[i + 1]), Mth.lerp(t, path.ys[i], path.ys[i + 1]), Mth.lerp(t, path.zs[i], path.zs[i + 1]), camera);
    }

    /**
     * Unit vector across a segment along the ground, so a seam lies flat instead of turning to the camera.
     */
    private static Vector3f flatSide(Vector3f a, Vector3f b)
    {
        float dx = b.x - a.x, dz = b.z - a.z;
        float length = Mth.sqrt(dx * dx + dz * dz);
        return length < 1.0E-5F ? new Vector3f(1, 0, 0) : new Vector3f(-dz / length, 0, dx / length);
    }

    private static Vector3f offset(Vector3f point, Vector3f side, float distance)
    {
        return new Vector3f(point).add(side.x * distance, side.y * distance, side.z * distance);
    }

    private static void strip(PoseStack.Pose pose, VertexConsumer buffer, Vector3f a, Vector3f b, Vector3f side, float from, float to, int fromColor, int toColor)
    {
        LeyLook.vertex(buffer, pose, offset(a, side, from), fromColor);
        LeyLook.vertex(buffer, pose, offset(a, side, to), toColor);
        LeyLook.vertex(buffer, pose, offset(b, side, to), toColor);
        LeyLook.vertex(buffer, pose, offset(b, side, from), fromColor);
    }
}
