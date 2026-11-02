package net.voxelindustry.voidheart.client.curse.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * Where the sky is this frame. The camera sits at the center of the sky, so only its rotation matters.
 *
 * @param skyView   from a sky that stays still around the player, with +y straight up, to view space
 * @param starView  from the star field, turning with the time of day, to view space
 * @param starBrightness how bright vanilla would draw the stars: zero by day and in the rain
 */
public record SkyFrame(Matrix4fc skyView, Matrix4fc starView, float starBrightness, float partialTicks)
{
    public static SkyFrame of(SkyRenderState state, float partialTicks)
    {
        var skyView = RenderSystem.getModelViewMatrixCopy();
        // As SkyRenderer.renderSunMoonAndStars turns its pose stack
        var starView = new Matrix4f(skyView).rotate(Axis.YP.rotationDegrees(-90)).rotate(Axis.XP.rotation(state.starAngle));
        return new SkyFrame(skyView, starView, state.starBrightness, partialTicks);
    }
}
