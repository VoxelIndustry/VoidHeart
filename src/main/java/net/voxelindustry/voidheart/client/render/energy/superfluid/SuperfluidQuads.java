package net.voxelindustry.voidheart.client.render.energy.superfluid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * Textured quads in block space, gathered while submitting and drawn later in the frame. Superfluid's ribbons, beads and the tesseract's bars all go
 * through one of these per texture.
 */
public final class SuperfluidQuads
{
    private static final Vector3f UP = new Vector3f(0, 1, 0);

    private final FloatArrayList positions = new FloatArrayList();
    private final FloatArrayList uvs       = new FloatArrayList();
    private final FloatArrayList normals   = new FloatArrayList();
    private final IntArrayList   colors    = new IntArrayList();

    public boolean isEmpty()
    {
        return colors.isEmpty();
    }

    public void emit(PoseStack.Pose pose, VertexConsumer buffer, int light)
    {
        for (int i = 0; i < colors.size(); i++)
            buffer.addVertex(pose, positions.getFloat(i * 3), positions.getFloat(i * 3 + 1), positions.getFloat(i * 3 + 2))
                    .setColor(colors.getInt(i))
                    .setUv(uvs.getFloat(i * 2), uvs.getFloat(i * 2 + 1))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, normals.getFloat(i * 3), normals.getFloat(i * 3 + 1), normals.getFloat(i * 3 + 2));
    }

    public void vertex(Vector3f at, float u, float v, Vector3f normal, int color)
    {
        positions.add(at.x);
        positions.add(at.y);
        positions.add(at.z);
        uvs.add(u);
        uvs.add(v);
        normals.add(normal.x);
        normals.add(normal.y);
        normals.add(normal.z);
        colors.add(color);
    }

    /**
     * A strip from one point to the next, each widened along its own side by its own half width, the texture running along it from u0 to u1.
     */
    public void strip(Vector3f from, Vector3f fromSide, float fromHalf, Vector3f to, Vector3f toSide, float toHalf, float u0, float u1, int fromColor,
                      int toColor)
    {
        vertex(new Vector3f(fromSide).mul(-fromHalf).add(from), u0, 0, UP, fromColor);
        vertex(new Vector3f(fromSide).mul(fromHalf).add(from), u0, 1, UP, fromColor);
        vertex(new Vector3f(toSide).mul(toHalf).add(to), u1, 1, UP, toColor);
        vertex(new Vector3f(toSide).mul(-toHalf).add(to), u1, 0, UP, toColor);
    }

    /**
     * The whole texture turned to the camera.
     */
    public void sprite(Vector3f center, Vector3f camera, float radius, int color)
    {
        var toCamera = new Vector3f(camera).sub(center);
        if (toCamera.lengthSquared() < 1e-6F)
            return;
        toCamera.normalize();
        var side = new Vector3f(toCamera).cross(Math.abs(toCamera.y) > 0.99F ? new Vector3f(1, 0, 0) : UP).normalize().mul(radius);
        var up = new Vector3f(side).cross(toCamera).normalize().mul(radius);
        vertex(new Vector3f(center).sub(side).add(up), 0, 0, toCamera, color);
        vertex(new Vector3f(center).sub(side).sub(up), 0, 1, toCamera, color);
        vertex(new Vector3f(center).add(side).sub(up), 1, 1, toCamera, color);
        vertex(new Vector3f(center).add(side).add(up), 1, 0, toCamera, color);
    }

    /**
     * A box from one point to another, square in section, each side showing the whole texture, wound counter-clockwise from outside.
     */
    public void bar(Vector3f from, Vector3f to, float half, int color)
    {
        var along = new Vector3f(to).sub(from);
        var length = along.length();
        if (length < 1e-5F || half <= 0)
            return;
        along.div(length);
        var helper = Math.abs(along.y) > 0.9F ? new Vector3f(1, 0, 0) : UP;
        var a = new Vector3f(along).cross(helper).normalize();
        var b = new Vector3f(along).cross(a).normalize();
        var center = new Vector3f(from).add(to).mul(0.5F);
        box(center, along, length / 2 + half, a, half, b, half, color);
    }

    /**
     * A box on three orthonormal axes, x cross y pointing along z.
     */
    public void box(Vector3f center, Vector3f x, float halfX, Vector3f y, float halfY, Vector3f z, float halfZ, int color)
    {
        side(center, x, halfX, y, halfY, z, halfZ, color);
        side(center, new Vector3f(x).negate(), halfX, z, halfZ, y, halfY, color);
        side(center, y, halfY, z, halfZ, x, halfX, color);
        side(center, new Vector3f(y).negate(), halfY, x, halfX, z, halfZ, color);
        side(center, z, halfZ, x, halfX, y, halfY, color);
        side(center, new Vector3f(z).negate(), halfZ, y, halfY, x, halfX, color);
    }

    private void side(Vector3f center, Vector3f normal, float depth, Vector3f u, float halfU, Vector3f v, float halfV, int color)
    {
        var middle = new Vector3f(normal).mul(depth).add(center);
        var su = new Vector3f(u).mul(halfU);
        var sv = new Vector3f(v).mul(halfV);
        if (new Vector3f(u).cross(v).dot(normal) < 0)
            sv.negate();
        vertex(new Vector3f(middle).sub(su).sub(sv), 0, 1, normal, color);
        vertex(new Vector3f(middle).add(su).sub(sv), 1, 1, normal, color);
        vertex(new Vector3f(middle).add(su).add(sv), 1, 0, normal, color);
        vertex(new Vector3f(middle).sub(su).add(sv), 0, 0, normal, color);
    }

    public static float smooth(float t)
    {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
