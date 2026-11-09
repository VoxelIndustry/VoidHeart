package net.voxelindustry.voidheart.client.render.energy.ley_taint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Bruise veins over the ground around tainted nodes near the player, from docs/plans/energy-looks.md ("Taint on the ground").
 * Each node's veins are laid over the terrain once per taint step and kept; every frame only scales and clips them by the
 * node's eased look.
 */
public final class LeyTaintGroundRender
{
    /**
     * Nodes are laid out and drawn while the camera is this close.
     */
    private static final double DRAW_DISTANCE = 112;
    /**
     * Terrain changes show after this many ticks, when the veins are laid again.
     */
    private static final int RELAY_TICKS = 200;
    /**
     * Lifted off the ground on top of the decal offset, the darkened ground lower than the veins.
     */
    private static final float VEIN_LIFT = 0.02F;
    private static final float DARK_LIFT = 0.012F;
    /**
     * Veins don't climb steps higher than this; past it they break.
     */
    private static final float MAX_STEP = 1.1F;
    /**
     * The darkened ground: a disc around the node and a band along each line.
     */
    private static final float DARK_DISC = 3.5F;
    private static final float DARK_BAND = 2.5F;
    /**
     * The visible end of a vein fades over this share of the reach.
     */
    private static final float END_FADE = 0.25F;
    /**
     * Veins thinner than this on screen are drawn this wide and fainter instead, from their core only, so hairlines stay
     * continuous rather than breaking into dots between pixels.
     */
    private static final float MIN_PIXELS = 1.5F;
    /**
     * Below this many pixels wide a vein shows its core only, since its rim would be sampled at random.
     */
    private static final float RIM_PIXELS = 4;

    private static final float ASH_EVERY = 4;
    private static final float ASH_LIFE = 60;
    private static final float ASH_RISE = 1.4F;
    private static final float ASH_SIZE = 0.09F;
    private static final float ASH_ALPHA = 0.75F;
    private static final float GLINT_EVERY = 5;
    private static final float GLINT_SIZE = 0.11F;
    private static final float GLINT_ALPHA = 0.55F;

    /**
     * Regions of the texture: the vein's cross-section runs across u in the top left, white in the top right, a soft dot below.
     */
    private static final float VEIN_U0 = 0, VEIN_U1 = 0.5F, VEIN_V = 0.25F;
    private static final float CORE_U0 = 0.15F, CORE_U1 = 0.35F;
    private static final float SOLID_U = 0.75F, SOLID_V = 0.25F;
    private static final float DOT_U0 = 0, DOT_U1 = 0.5F, DOT_V0 = 0.5F, DOT_V1 = 1;

    /**
     * A flat piece of vein within one block column, or a riser up the side of a step, as its center line.
     */
    /**
     * @param push 0 for a flat piece; for a riser, 1 when the higher column comes first along the vein, -1 when it comes after
     */
    private record Strip(float x0, float y0, float z0, float x1, float y1, float z1, float sideX, float sideZ,
                         float distance, int level, float width, int light, float push)
    {
        boolean riser()
        {
            return push != 0;
        }
    }

    /**
     * One block column of darkened ground, with what its corners need to fade by distance.
     */
    private record Column(int x, int z, float y, int light, float[] radial, float[] axial, float[] lateral)
    {
    }

    private record Laid(float steppedTaint, long laidAt, List<Strip> strips, List<Column> columns)
    {
    }

    private static final Long2ObjectOpenHashMap<Laid> LAID = new Long2ObjectOpenHashMap<>();

    private LeyTaintGroundRender()
    {
    }

    public static void collectSubmits(LevelRenderContext context)
    {
        var level = Minecraft.getInstance().level;
        if (level == null || level.dimension() != Level.OVERWORLD || LeyTaintView.isEmpty())
        {
            LAID.clear();
            return;
        }

        var levelState = context.levelState();
        var camera = levelState.cameraRenderState.pos;
        double time = LeyTaintView.ticks() + levelState.worldPartialTicks;
        long tick = LeyTaintView.ticks();

        var drawn = new ArrayList<LeyTaintView.Node>();
        for (var node : LeyTaintView.nodes())
        {
            double dx = node.x() + 0.5 - camera.x, dz = node.z() + 0.5 - camera.z;
            var shape = node.shape(time);
            if (shape.isNone() || dx * dx + dz * dz > DRAW_DISTANCE * DRAW_DISTANCE)
                continue;
            var laid = LAID.get(node.id());
            if (laid == null || laid.steppedTaint() != node.steppedTaint() || tick - laid.laidAt() > RELAY_TICKS || tick < laid.laidAt())
                LAID.put(node.id(), lay(level, node, tick));
            drawn.add(node);
        }
        LAID.keySet().removeIf(id -> drawn.stream().noneMatch(node -> node.id() == id));
        if (drawn.isEmpty())
            return;

        // World size of one pixel at a distance of one block
        float pixel = 2 / (levelState.cameraRenderState.projectionMatrix.m11() * Math.max(1, Minecraft.getInstance().getWindow().getHeight()));
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), LeyTaintRenderTypes.GROUND, (pose, buffer) ->
        {
            for (var node : drawn)
                drawDark(pose, buffer, LAID.get(node.id()), node.shape(time), camera);
            for (var node : drawn)
                drawVeins(pose, buffer, LAID.get(node.id()), node.shape(time), camera, pixel);
            for (var node : drawn)
                drawAsh(pose, buffer, LAID.get(node.id()), node, node.shape(time), camera, time, levelState.cameraRenderState.orientation);
        });
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), VoidHeartRenderTypes.GLOW, (pose, buffer) ->
        {
            for (var node : drawn)
                drawGlints(pose, buffer, LAID.get(node.id()), node, node.shape(time), camera, time);
        });
    }

    /**
     * Lays the node's veins and darkened ground over the terrain, out to the larger of where they are and where they're going.
     */
    private static Laid lay(ClientLevel level, LeyTaintView.Node node, long tick)
    {
        var target = LeyTaintLook.shape(node.taint());
        var current = node.shape(tick);
        float reach = Math.max(target.reach(), current.reach()) + 1;
        boolean dark = Math.max(target.dark(), current.dark()) > 0;

        var heights = new Long2FloatOpenHashMap();
        heights.defaultReturnValue(Float.NaN);
        var strips = new ArrayList<Strip>();
        for (var piece : LeyTaintVeins.generate(node.id(), node.x(), node.z(), node.spokes()))
        {
            if (piece.distance() <= reach)
                layPiece(level, heights, piece, strips);
        }

        var columns = new ArrayList<Column>();
        if (dark)
            layDark(level, heights, node, reach, columns);
        return new Laid(node.steppedTaint(), tick, strips, columns);
    }

    private static void layPiece(ClientLevel level, Long2FloatOpenHashMap heights, LeyTaintVeins.Piece piece, List<Strip> strips)
    {
        double dx = piece.x1() - piece.x0(), dz = piece.z1() - piece.z0();
        double length = Math.hypot(dx, dz);
        if (length < 1.0E-4)
            return;
        float sideX = (float) (-dz / length), sideZ = (float) (dx / length);

        // Cut the piece where it crosses into another block column, so each part lies flat on its own column
        var cuts = new ArrayList<Double>();
        cuts.add(0D);
        addCrossings(piece.x0(), dx, cuts);
        addCrossings(piece.z0(), dz, cuts);
        cuts.add(1D);
        cuts.sort(Double::compare);

        float previousY = Float.NaN;
        for (int i = 0; i + 1 < cuts.size(); i++)
        {
            double t0 = cuts.get(i), t1 = cuts.get(i + 1);
            if (t1 - t0 < 1.0E-6)
                continue;
            double mid = (t0 + t1) / 2;
            int columnX = (int) Math.floor(piece.x0() + dx * mid), columnZ = (int) Math.floor(piece.z0() + dz * mid);
            float top = surface(level, heights, columnX, columnZ);
            if (Float.isNaN(top))
            {
                previousY = Float.NaN;
                continue;
            }

            float x0 = (float) (piece.x0() + dx * t0), z0 = (float) (piece.z0() + dz * t0);
            float x1 = (float) (piece.x0() + dx * t1), z1 = (float) (piece.z0() + dz * t1);
            int light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(columnX, top + 0.1, columnZ));
            float distance = (float) (piece.distance() + length * t0);

            if (!Float.isNaN(previousY) && previousY != top)
            {
                if (Math.abs(previousY - top) > MAX_STEP)
                {
                    previousY = top;
                    continue;
                }
                // Up the face of the higher column, at the boundary between the two
                strips.add(new Strip(x0, Math.min(previousY, top), z0, x0, Math.max(previousY, top), z0, sideX, sideZ, distance, piece.level(), piece.width(), light,
                        previousY > top ? 1 : -1));
            }
            strips.add(new Strip(x0, top, z0, x1, top, z1, sideX, sideZ, distance, piece.level(), piece.width(), light, 0));
            previousY = top;
        }
    }

    private static void addCrossings(double start, double delta, List<Double> cuts)
    {
        if (Math.abs(delta) < 1.0E-9)
            return;
        double end = start + delta;
        for (double boundary = Math.ceil(Math.min(start, end)); boundary < Math.max(start, end); boundary++)
        {
            double t = (boundary - start) / delta;
            if (t > 0 && t < 1)
                cuts.add(t);
        }
    }

    /**
     * Top of the ground in the column, or NaN where there is no loaded ground, it is under water or an Old Anchor stands on it.
     */
    private static float surface(ClientLevel level, Long2FloatOpenHashMap heights, int x, int z)
    {
        long key = BlockPos.asLong(x, 0, z);
        float cached = heights.get(key);
        if (!Float.isNaN(cached) || heights.containsKey(key))
            return cached;

        float top = Float.NaN;
        int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (height > level.getMinY())
        {
            var below = new BlockPos(x, height - 1, z);
            var state = level.getBlockState(below);
            // The anchor stands on its node; the ground around it shows the veins, not its top
            if (state.getFluidState().isEmpty() && !state.is(VoidHeartBlocks.OLD_ANCHOR))
            {
                var shape = state.getShape(level, below);
                top = height - 1 + (float) (shape.isEmpty() ? 1 : shape.max(Direction.Axis.Y));
            }
        }
        heights.put(key, top);
        return top;
    }

    private static void layDark(ClientLevel level, Long2FloatOpenHashMap heights, LeyTaintView.Node node, float reach, List<Column> columns)
    {
        double centerX = node.x() + 0.5, centerZ = node.z() + 0.5;
        var axes = new ArrayList<double[]>();
        for (var spoke : node.spokes())
        {
            double ax = spoke.x() - node.x(), az = spoke.z() - node.z();
            double length = Math.hypot(ax, az);
            if (length > 0)
                axes.add(new double[] { ax / length, az / length });
        }

        int radius = (int) Math.ceil(reach + DARK_BAND);
        for (int x = node.x() - radius; x <= node.x() + radius; x++)
        {
            for (int z = node.z() - radius; z <= node.z() + radius; z++)
            {
                var corners = new double[][] { { x, z }, { x, z + 1 }, { x + 1, z + 1 }, { x + 1, z } };
                var radial = new float[4];
                var axial = new float[4];
                var lateral = new float[4];
                boolean near = false;
                for (int corner = 0; corner < 4; corner++)
                {
                    double px = corners[corner][0] - centerX, pz = corners[corner][1] - centerZ;
                    radial[corner] = (float) Math.hypot(px, pz);
                    axial[corner] = Float.MAX_VALUE;
                    lateral[corner] = Float.MAX_VALUE;
                    for (var axis : axes)
                    {
                        double along = px * axis[0] + pz * axis[1];
                        double across = Math.abs(-px * axis[1] + pz * axis[0]);
                        if (along >= 0 && across < lateral[corner])
                        {
                            lateral[corner] = (float) across;
                            axial[corner] = (float) along;
                        }
                    }
                    near |= radial[corner] < DARK_DISC || lateral[corner] < DARK_BAND && axial[corner] < reach;
                }
                if (!near)
                    continue;
                float top = surface(level, heights, x, z);
                if (Float.isNaN(top))
                    continue;
                int light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(x, top + 0.1, z));
                columns.add(new Column(x, z, top, light, radial, axial, lateral));
            }
        }
    }

    private static void drawDark(PoseStack.Pose pose, VertexConsumer buffer, Laid laid, LeyTaintLook.Shape shape, Vec3 camera)
    {
        if (shape.dark() <= 0.001F)
            return;
        int rgb = LeyTaintLook.SEEP_DARK;
        for (var column : laid.columns())
        {
            float y = (float) (column.y() + DARK_LIFT - camera.y);
            float x0 = (float) (column.x() - camera.x), z0 = (float) (column.z() - camera.z);
            float[][] at = { { x0, z0 }, { x0, z0 + 1 }, { x0 + 1, z0 + 1 }, { x0 + 1, z0 } };
            for (int corner = 0; corner < 4; corner++)
            {
                float alpha = shape.dark() * darkness(column, corner, shape.reach());
                buffer.addVertex(pose, at[corner][0], y, at[corner][1])
                        .setColor(ARGB.color(alpha, rgb))
                        .setUv(SOLID_U, SOLID_V)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(column.light())
                        .setNormal(pose, 0, 1, 0);
            }
        }
    }

    private static float darkness(Column column, int corner, float reach)
    {
        float disc = 1 - smooth(column.radial()[corner] / DARK_DISC);
        float band = (1 - smooth(column.lateral()[corner] / DARK_BAND)) * (1 - smooth(column.axial()[corner] / Math.max(reach, 0.01F)));
        return Math.max(disc, band);
    }

    private static void drawVeins(PoseStack.Pose pose, VertexConsumer buffer, Laid laid, LeyTaintLook.Shape shape, Vec3 camera, float pixel)
    {
        float reach = shape.reach();
        float fade = Math.max(reach * END_FADE, 0.5F);
        for (var strip : laid.strips())
        {
            if (strip.distance() > reach)
                continue;
            float halfWidth = shape.width() * strip.width() * shape.levelWeight(strip.level()) * Math.min(1, (reach - strip.distance()) / fade) / 2;
            if (halfWidth < 0.002F)
                continue;

            double distance = Math.sqrt(sq(strip.x0() - camera.x) + sq(strip.y0() - camera.y) + sq(strip.z0() - camera.z));
            float pixels = (float) (2 * halfWidth / (pixel * Math.max(distance, 0.1)));
            float alpha = 1;
            if (pixels < MIN_PIXELS)
            {
                alpha = pixels / MIN_PIXELS;
                halfWidth *= MIN_PIXELS / pixels;
            }
            boolean coreOnly = pixels < RIM_PIXELS;
            float u0 = coreOnly ? CORE_U0 : VEIN_U0, u1 = coreOnly ? CORE_U1 : VEIN_U1;
            int color = ARGB.white(alpha);

            float sx = strip.sideX() * halfWidth, sz = strip.sideZ() * halfWidth;
            float lift = strip.riser() ? 0 : VEIN_LIFT;
            float x0 = (float) (strip.x0() - camera.x), y0 = (float) (strip.y0() + lift - camera.y), z0 = (float) (strip.z0() - camera.z);
            float x1 = (float) (strip.x1() - camera.x), y1 = (float) (strip.y1() + lift - camera.y), z1 = (float) (strip.z1() - camera.z);
            if (strip.riser())
            {
                // Pushed off the face of the higher column toward the lower one, along the vein
                float push = 0.01F * strip.push();
                x0 += strip.sideZ() * push;
                x1 += strip.sideZ() * push;
                z0 -= strip.sideX() * push;
                z1 -= strip.sideX() * push;
            }
            float nx = strip.riser() ? -strip.sideZ() : 0, ny = strip.riser() ? 0 : 1, nz = strip.riser() ? strip.sideX() : 0;
            veinVertex(pose, buffer, x0 - sx, y0, z0 - sz, u0, color, strip.light(), nx, ny, nz);
            veinVertex(pose, buffer, x0 + sx, y0, z0 + sz, u1, color, strip.light(), nx, ny, nz);
            veinVertex(pose, buffer, x1 + sx, y1, z1 + sz, u1, color, strip.light(), nx, ny, nz);
            veinVertex(pose, buffer, x1 - sx, y1, z1 - sz, u0, color, strip.light(), nx, ny, nz);
        }
    }

    private static void veinVertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, int color, int light, float nx, float ny, float nz)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, VEIN_V)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    /**
     * Slow dark ash rising off a breached node's veins, about one per {@link #ASH_EVERY} blocks of vein every second.
     */
    private static void drawAsh(PoseStack.Pose pose, VertexConsumer buffer, Laid laid, LeyTaintView.Node node, LeyTaintLook.Shape shape, Vec3 camera,
                                double time, Quaternionf orientation)
    {
        if (shape.breach() <= 0.01F)
            return;
        var right = orientation.transform(new Vector3f(1, 0, 0));
        var up = orientation.transform(new Vector3f(0, 1, 0));
        int perSecond = (int) Math.ceil(ASH_LIFE / 20);
        float travelled = 0;
        int index = 0;
        for (var strip : laid.strips())
        {
            if (strip.riser() || strip.distance() > shape.reach())
                continue;
            travelled += Math.hypot(strip.x1() - strip.x0(), strip.z1() - strip.z0());
            if (travelled < ASH_EVERY)
                continue;
            travelled = 0;
            index++;
            for (int mote = 0; mote < perSecond; mote++)
            {
                double phase = (time / ASH_LIFE + mote / (double) perSecond + hash(node.id(), index) ) % 1;
                float alpha = (float) (Math.sin(Math.PI * phase)) * ASH_ALPHA * shape.breach();
                float drift = (float) (Math.sin(phase * 3 + index) * 0.15);
                float x = (float) (strip.x0() + drift - camera.x);
                float y = (float) (strip.y0() + 0.1 + phase * ASH_RISE - camera.y);
                float z = (float) (strip.z0() + drift * 0.5 - camera.z);
                billboard(pose, buffer, x, y, z, right, up, ASH_SIZE, ARGB.color(alpha, LeyTaintLook.ASH), strip.light());
            }
        }
    }

    private static void billboard(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, Vector3f right, Vector3f up,
                                  float size, int color, int light)
    {
        float rx = right.x * size, ry = right.y * size, rz = right.z * size;
        float ux = up.x * size, uy = up.y * size, uz = up.z * size;
        dotVertex(pose, buffer, x - rx - ux, y - ry - uy, z - rz - uz, DOT_U0, DOT_V1, color, light);
        dotVertex(pose, buffer, x + rx - ux, y + ry - uy, z + rz - uz, DOT_U1, DOT_V1, color, light);
        dotVertex(pose, buffer, x + rx + ux, y + ry + uy, z + rz + uz, DOT_U1, DOT_V0, color, light);
        dotVertex(pose, buffer, x - rx + ux, y - ry + uy, z - rz + uz, DOT_U0, DOT_V0, color, light);
    }

    private static void dotVertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int color, int light)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0, 1, 0);
    }

    /**
     * Faint Contravoid lavender glints flickering in a breached node's veins, flat on the ground and full bright.
     */
    private static void drawGlints(PoseStack.Pose pose, VertexConsumer buffer, Laid laid, LeyTaintView.Node node, LeyTaintLook.Shape shape, Vec3 camera, double time)
    {
        if (shape.breach() <= 0.01F)
            return;
        float travelled = 0;
        int index = 0;
        for (var strip : laid.strips())
        {
            if (strip.riser() || strip.level() > 0 || strip.distance() > shape.reach())
                continue;
            travelled += Math.hypot(strip.x1() - strip.x0(), strip.z1() - strip.z0());
            if (travelled < GLINT_EVERY)
                continue;
            travelled = 0;
            index++;

            double seed = hash(node.id(), index * 31);
            double wave = Math.sin(time * (0.15 + 0.2 * seed) + seed * 40);
            float flicker = (float) Math.pow(Math.max(0, wave), 4);
            if (flicker < 0.02F)
                continue;
            int color = ARGB.color(flicker * GLINT_ALPHA * shape.breach(), LeyTaintLook.CONTRAVOID_LAVENDER);
            float x = (float) ((strip.x0() + strip.x1()) / 2 - camera.x);
            float y = (float) (strip.y0() + VEIN_LIFT + 0.005F - camera.y);
            float z = (float) ((strip.z0() + strip.z1()) / 2 - camera.z);
            float size = GLINT_SIZE * (0.7F + 0.6F * flicker);
            buffer.addVertex(pose, x - size, y, z).setColor(color);
            buffer.addVertex(pose, x, y, z + size).setColor(color);
            buffer.addVertex(pose, x + size, y, z).setColor(color);
            buffer.addVertex(pose, x, y, z - size).setColor(color);
        }
    }

    private static double hash(long seed, int index)
    {
        long h = seed * 0x9E3779B97F4A7C15L + index * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) / (double) (1L << 53);
    }

    private static double sq(double value)
    {
        return value * value;
    }

    private static float smooth(float t)
    {
        float clamped = Math.clamp(t, 0, 1);
        return clamped * clamped * (3 - 2 * clamped);
    }

    public static void clear()
    {
        LAID.clear();
    }
}
