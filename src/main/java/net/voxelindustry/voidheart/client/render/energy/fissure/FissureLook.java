package net.voxelindustry.voidheart.client.render.energy.fissure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.energy.fissure.VoidFissureBlock;
import org.joml.Vector3f;

/**
 * Colors and drawing helpers shared by the fissure's renderers, from docs/plans/energy-looks.md.
 */
final class FissureLook
{
    static final int FISSURE_VIOLET     = 0x7A3FD0;
    static final int CONTRAVOID_LAVENDER = 0xE9DDFF;
    static final int SEEP_DARK          = 0x1A0F24;
    static final int BRUISE             = 0x24122C;
    static final int LEY_MAUVE          = 0x8A6A94;
    static final int MOTE_BLACK         = 0x050208;
    static final int AZURIUM_LIGHT      = 0x9EECFF;
    static final int AZURIUM_MID        = 0x3D7ED9;

    // Searched for ground around a height, so effects lie on terrain and not on a roof
    private static final int ABOVE = 3;
    private static final int BELOW = 6;

    private FissureLook()
    {
    }

    static int color(int rgb, float alpha)
    {
        return ARGB.color(Mth.clamp((int) (alpha * 255), 0, 255), rgb);
    }

    static int lerp(int from, int to, float t)
    {
        return ARGB.srgbLerp(Mth.clamp(t, 0, 1), from | 0xFF000000, to | 0xFF000000) & 0xFFFFFF;
    }

    /**
     * A cheap hash of a few longs into 0..1, for stable randomness that never rerolls between frames.
     */
    static float hash(long a, long b, long c)
    {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL + c * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h >>> 40) / (float) (1L << 24);
    }

    /**
     * Camera relative position, subtracted in double so it stays precise far from the world origin.
     */
    static Vector3f relative(double x, double y, double z, Vec3 camera)
    {
        return new Vector3f((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z));
    }

    static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, int color)
    {
        buffer.addVertex(pose, x, y, z).setColor(color);
    }

    static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vector3f at, int color)
    {
        buffer.addVertex(pose, at.x, at.y, at.z).setColor(color);
    }

    /**
     * A soft blob lying flat, stretched along a direction: dense at its middle and fading to nothing at its rim. A fan of quads, since the pipelines here
     * have no texture.
     */
    static void flatBlob(VertexConsumer buffer, PoseStack.Pose pose, Vector3f center, float dirX, float dirZ, float length, float width, int rgb, float alpha)
    {
        if (alpha <= 0.004F)
            return;
        int core = color(rgb, alpha);
        int rim = color(rgb, 0);
        int segments = 8;
        for (int i = 0; i < segments; i++)
        {
            float a0 = Mth.TWO_PI * i / segments;
            float a1 = Mth.TWO_PI * (i + 1) / segments;
            vertex(buffer, pose, center, core);
            vertex(buffer, pose, center, core);
            vertex(buffer, pose, flatEdge(center, dirX, dirZ, length, width, a0), rim);
            vertex(buffer, pose, flatEdge(center, dirX, dirZ, length, width, a1), rim);
        }
    }

    private static Vector3f flatEdge(Vector3f center, float dirX, float dirZ, float length, float width, float angle)
    {
        float along = Mth.cos(angle) * length;
        float across = Mth.sin(angle) * width;
        return new Vector3f(center.x + dirX * along - dirZ * across, center.y, center.z + dirZ * along + dirX * across);
    }

    /**
     * A soft round puff facing the camera.
     */
    static void softDot(VertexConsumer buffer, PoseStack.Pose pose, Vector3f center, float radius, int rgb, float alpha)
    {
        if (alpha <= 0.004F)
            return;
        var toCamera = new Vector3f(center).negate();
        if (toCamera.lengthSquared() < 1.0E-6F)
            return;
        toCamera.normalize();
        var right = new Vector3f(0, 1, 0).cross(toCamera);
        if (right.lengthSquared() < 1.0E-6F)
            right.set(1, 0, 0);
        right.normalize();
        var up = new Vector3f(toCamera).cross(right).normalize();

        int core = color(rgb, alpha);
        int rim = color(rgb, 0);
        int segments = 8;
        for (int i = 0; i < segments; i++)
        {
            float a0 = Mth.TWO_PI * i / segments;
            float a1 = Mth.TWO_PI * (i + 1) / segments;
            vertex(buffer, pose, center, core);
            vertex(buffer, pose, center, core);
            vertex(buffer, pose, billboardEdge(center, right, up, a0, radius), rim);
            vertex(buffer, pose, billboardEdge(center, right, up, a1, radius), rim);
        }
    }

    /**
     * A small hard square facing the camera, for the black motes.
     */
    static void mote(VertexConsumer buffer, PoseStack.Pose pose, Vector3f center, float half, int color)
    {
        var toCamera = new Vector3f(center).negate();
        if (toCamera.lengthSquared() < 1.0E-6F)
            return;
        toCamera.normalize();
        var right = new Vector3f(0, 1, 0).cross(toCamera);
        if (right.lengthSquared() < 1.0E-6F)
            right.set(1, 0, 0);
        right.normalize().mul(half);
        var up = new Vector3f(toCamera).cross(right).normalize().mul(half);
        vertex(buffer, pose, center.x - right.x - up.x, center.y - right.y - up.y, center.z - right.z - up.z, color);
        vertex(buffer, pose, center.x + right.x - up.x, center.y + right.y - up.y, center.z + right.z - up.z, color);
        vertex(buffer, pose, center.x + right.x + up.x, center.y + right.y + up.y, center.z + right.z + up.z, color);
        vertex(buffer, pose, center.x - right.x + up.x, center.y - right.y + up.y, center.z - right.z + up.z, color);
    }

    private static Vector3f billboardEdge(Vector3f center, Vector3f right, Vector3f up, float angle, float radius)
    {
        return new Vector3f(center)
                .add(right.x * Mth.cos(angle) * radius, right.y * Mth.cos(angle) * radius, right.z * Mth.cos(angle) * radius)
                .add(up.x * Mth.sin(angle) * radius, up.y * Mth.sin(angle) * radius, up.z * Mth.sin(angle) * radius);
    }

    /**
     * The height of the first walkable top at x, z, searching from a few blocks above reference down to a few below, or NaN if there is none. A fissure or
     * wound is not ground.
     */
    static double surface(Level level, double x, double z, int reference)
    {
        var cursor = new BlockPos.MutableBlockPos(Mth.floor(x), reference + ABOVE, Mth.floor(z));
        boolean openAbove = isOpen(level, cursor);
        for (int y = reference + ABOVE - 1; y >= reference - BELOW; y--)
        {
            cursor.setY(y);
            boolean open = isOpen(level, cursor);
            if (!open && openAbove)
                return y + level.getBlockState(cursor).getCollisionShape(level, cursor).max(Direction.Axis.Y);
            openAbove = open;
        }
        return Double.NaN;
    }

    private static boolean isOpen(Level level, BlockPos pos)
    {
        var state = level.getBlockState(pos);
        return state.getBlock() instanceof VoidFissureBlock || state.getCollisionShape(level, pos).isEmpty();
    }
}
