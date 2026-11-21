package net.voxelindustry.voidheart.client.render.energy.fissure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.energy.fissure.VoidFissureBlock;
import org.joml.Vector3f;

import static net.voxelindustry.voidheart.client.render.energy.GlowDraw.color;
import static net.voxelindustry.voidheart.client.render.energy.GlowDraw.vertex;

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
