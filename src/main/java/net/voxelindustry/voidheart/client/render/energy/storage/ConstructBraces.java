package net.voxelindustry.voidheart.client.render.energy.storage;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout;
import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout.Size;
import net.voxelindustry.voidheart.common.energy.storage.FrameGeometry;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The brace across each face of a construct that its bundles grow from: two voidstone bars running beam to beam along
 * the face's middle row and column, which every socket of either size lies on, and at each socket a voidstone plate
 * rimmed in gold for a bundle's base. Bundles point inward from their plate, so none hangs in the air.
 * <p>
 * Solid entity quads on standalone block textures, so they light and write depth like the frame. The brace sits half a
 * pixel inside the cube's surface, the plates stand half a pixel past it on both sides and the rims a quarter pixel
 * past the plates, so no two faces are coplanar; the crossing bar is cut around the other. At worst a large construct
 * draws six braces of about ten boxes and thirty rimmed plates, about a thousand quads.
 */
final class ConstructBraces
{
    private static final Identifier VOIDSTONE = Identifier.fromNamespaceAndPath(MODID, "textures/block/voidstone_polished.png");
    private static final Identifier GOLD      = Identifier.fromNamespaceAndPath(MODID, "textures/block/cut_ravenous_gold.png");

    private static final float BAR_WIDTH  = 4 / 16F;
    private static final float BAR_DEPTH  = 2 / 16F;
    private static final float INSET      = 0.5F / 16;
    private static final float PLATE_HALF  = 4 / 16F;
    private static final float PLATE_DEPTH = BAR_DEPTH + 2 * INSET;
    /** The gold rim around each plate, standing a hair past the plate on every side so none of their faces meet. */
    private static final float RIM_WIDTH   = 1 / 16F;
    private static final float RIM_PROUD   = 0.25F / 16;

    private ConstructBraces()
    {
    }

    /**
     * @param faceLights light at the middle of each face, by {@link Direction#ordinal()}
     */
    static void submit(PoseStack poseStack, SubmitNodeCollector collector, Size size, int[] faceLights)
    {
        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(VOIDSTONE), (pose, buffer) ->
        {
            for (var face : Direction.values())
                bars(pose, buffer, size, face, faceLights[face.ordinal()]);
            for (var socket : ConstructLayout.sockets(size))
            {
                var face = ConstructLayout.inward(size, socket).getOpposite();
                plate(pose, buffer, size, face, socket.get(axisA(face)) + 0.5F, socket.get(axisB(face)) + 0.5F, faceLights[face.ordinal()]);
            }
        });
        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(GOLD), (pose, buffer) ->
        {
            for (var socket : ConstructLayout.sockets(size))
            {
                var face = ConstructLayout.inward(size, socket).getOpposite();
                rim(pose, buffer, size, face, socket.get(axisA(face)) + 0.5F, socket.get(axisB(face)) + 0.5F, faceLights[face.ordinal()]);
            }
        });
    }

    private static Direction.Axis axisA(Direction face)
    {
        return face.getAxis() == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
    }

    private static Direction.Axis axisB(Direction face)
    {
        return face.getAxis() == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
    }

    /**
     * The face's outer plane and the depth range below it, in blocks from the core's corner, as {from, to}.
     */
    private static float[] depth(Size size, Direction face, float outside, float inside)
    {
        var positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        var plane = positive ? size.half() + 1 : -size.half();
        return positive ? new float[]{plane - inside, plane - outside} : new float[]{plane + outside, plane + inside};
    }

    private static void bars(PoseStack.Pose pose, VertexConsumer buffer, Size size, Direction face, int light)
    {
        var depth = depth(size, face, INSET, INSET + BAR_DEPTH);
        var lo = -size.half() + FrameGeometry.BEAM / 16F;
        var hi = size.half() + 1 - FrameGeometry.BEAM / 16F;
        var low = 0.5F - BAR_WIDTH / 2;
        var high = 0.5F + BAR_WIDTH / 2;

        // Along the first axis, whole; along the second, cut around the first where they cross
        segments(pose, buffer, face, depth, axisA(face), lo, hi, low, high, light);
        segments(pose, buffer, face, depth, axisB(face), lo, low, low, high, light);
        segments(pose, buffer, face, depth, axisB(face), high, hi, low, high, light);
    }

    /**
     * A bar along one axis of the face from {@code from} to {@code to}, across the other from {@code low} to
     * {@code high}, split at block edges so each piece takes its texture within one block.
     */
    private static void segments(PoseStack.Pose pose, VertexConsumer buffer, Direction face, float[] depth, Direction.Axis along,
                                 float from, float to, float low, float high, int light)
    {
        var start = from;
        while (start < to - 1.0E-4F)
        {
            var end = Math.min(to, (float) Math.floor(start) + 1);
            var box = new float[6];
            set(box, face.getAxis(), depth[0], depth[1]);
            set(box, along, start, end);
            set(box, along == axisA(face) ? axisB(face) : axisA(face), low, high);
            box(pose, buffer, box, light);
            start = end;
        }
    }

    private static void plate(PoseStack.Pose pose, VertexConsumer buffer, Size size, Direction face, float a, float b, int light)
    {
        var depth = depth(size, face, 0, PLATE_DEPTH);
        var box = new float[6];
        set(box, face.getAxis(), depth[0], depth[1]);
        set(box, axisA(face), a - PLATE_HALF, a + PLATE_HALF);
        set(box, axisB(face), b - PLATE_HALF, b + PLATE_HALF);
        box(pose, buffer, box, light);
    }

    /**
     * Four bars framing the plate, the bundle's base showing dark inside them.
     */
    private static void rim(PoseStack.Pose pose, VertexConsumer buffer, Size size, Direction face, float a, float b, int light)
    {
        var depth = depth(size, face, -RIM_PROUD, PLATE_DEPTH + RIM_PROUD);
        var outer = PLATE_HALF + RIM_PROUD;
        var inner = PLATE_HALF - RIM_WIDTH;
        float[][] pieces = {
                {-outer, outer, inner, outer},
                {-outer, outer, -outer, -inner},
                {-outer, -inner, -inner, inner},
                {inner, outer, -inner, inner}};
        for (var piece : pieces)
        {
            var box = new float[6];
            set(box, face.getAxis(), depth[0], depth[1]);
            set(box, axisA(face), a + piece[0], a + piece[1]);
            set(box, axisB(face), b + piece[2], b + piece[3]);
            box(pose, buffer, box, light);
        }
    }

    private static void set(float[] box, Direction.Axis axis, float from, float to)
    {
        box[axis.ordinal()] = Math.min(from, to);
        box[axis.ordinal() + 3] = Math.max(from, to);
    }

    /**
     * A cuboid {x0, y0, z0, x1, y1, z1}, each face textured with the part of the texture its own size covers.
     */
    private static void box(PoseStack.Pose pose, VertexConsumer buffer, float[] box, int light)
    {
        float x0 = box[0], y0 = box[1], z0 = box[2], x1 = box[3], y1 = box[4], z1 = box[5];
        face(pose, buffer, Direction.DOWN, light, x1 - x0, z1 - z0, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
        face(pose, buffer, Direction.UP, light, x1 - x0, z1 - z0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        face(pose, buffer, Direction.NORTH, light, x1 - x0, y1 - y0, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0);
        face(pose, buffer, Direction.SOUTH, light, x1 - x0, y1 - y0, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1);
        face(pose, buffer, Direction.WEST, light, z1 - z0, y1 - y0, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1);
        face(pose, buffer, Direction.EAST, light, z1 - z0, y1 - y0, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0);
    }

    /**
     * Corners walked counter-clockwise from outside, as GlowCube does, with UVs (0,0), (0,1), (1,1), (1,0) scaled to the face's size.
     */
    private static void face(PoseStack.Pose pose, VertexConsumer buffer, Direction direction, int light, float width, float height,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz)
    {
        var normal = direction.getUnitVec3i();
        vertex(pose, buffer, ax, ay, az, 0, 0, light, normal.getX(), normal.getY(), normal.getZ());
        vertex(pose, buffer, bx, by, bz, 0, height, light, normal.getX(), normal.getY(), normal.getZ());
        vertex(pose, buffer, cx, cy, cz, width, height, light, normal.getX(), normal.getY(), normal.getZ());
        vertex(pose, buffer, dx, dy, dz, width, 0, light, normal.getX(), normal.getY(), normal.getZ());
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int light, int nx, int ny, int nz)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
