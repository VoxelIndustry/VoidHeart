package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * The window a temple door's hole opens onto its sanctum: one quad over the whole door, a hair behind its ink, which {@code sanctum_window.fsh}
 * fills with the hall it traces beyond. It reads the ink's own mask with the same corners, so it shows exactly where the ink's hole is open and
 * the ink, drawn as a window, leaves that hole empty.
 */
public final class SanctumWindow
{
    /** How far behind the ink the window lies, toward the wall. */
    private static final float BEHIND = 0.006F;
    /** Its place on the door goes to the shader in 64ths of a block. */
    private static final int DOOR_UNITS = 64;

    private SanctumWindow()
    {
    }

    /**
     * Draws the window centered on the pose's origin, where the ink mark is.
     *
     * @param open  how far the hole has opened, 0 to 1, as the ink's
     * @param shown how far the hall has come out of the dark, 0 to 1
     * @param glow  how brightly the hole's edge smoulders
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Direction face, float halfU, float halfV, WraithInk.Variant variant,
                              float open, float shown, float glow)
    {
        if (open <= 0)
            return;
        int color = ARGB.color(255, channel(open), channel(shown), channel(glow));
        var normal = face.getUnitVec3f();
        // Rightward across the door as someone facing it sees it, the way the shader measures it
        float rightX = normal.z(), rightZ = -normal.x();
        collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.sanctumWindow(variant.mask()), (pose, buffer) ->
        {
            for (int corner = 0; corner < 4; corner++)
            {
                float du = (corner == 1 || corner == 2) ? halfU : -halfU;
                float dv = corner >= 2 ? halfV : -halfV;
                var at = WraithInk.onSurface(face, du, dv);
                float x = at[0] - normal.x() * BEHIND, y = at[1], z = at[2] - normal.z() * BEHIND;
                float across = at[0] * rightX + at[2] * rightZ + halfU;
                float up = at[1] + halfV;
                var uv = variant.uv(du, dv, halfU, halfV);
                buffer.addVertex(pose, x, y, z)
                        .setColor(color)
                        .setUv(uv[0], uv[1])
                        .setUv1(Math.round(across * DOOR_UNITS), Math.round(up * DOOR_UNITS))
                        .setLight(LightCoordsUtil.FULL_BRIGHT)
                        .setNormal(pose, normal.x(), normal.y(), normal.z());
            }
        });
    }

    private static int channel(float value)
    {
        return Mth.clamp(Math.round(value * 255), 0, 255);
    }
}
