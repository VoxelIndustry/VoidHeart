package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * One light of a shoal far off in the dark. Members of a shoal share one exact heading, from which each works out the same swerve and hue, so they
 * turn together without talking to each other. Their own small lag in that swerve is what makes them read as a shoal rather than a rigid cloud.
 */
public class ShoalSpeckParticle extends SingleQuadParticle
{
    private static final float[][] HUES = {
            { 0.42F, 0.86F, 0.75F },
            { 0.45F, 0.82F, 0.87F },
            { 0.21F, 0.77F, 0.69F },
            { 0.64F, 0.55F, 0.93F }
    };
    private static final float MIN_SIZE = 0.15F;
    private static final float SIZE_SPREAD = 0.08F;
    private static final float MAX_ALPHA = 1F;
    private static final int MIN_LIFETIME = 140;
    private static final int LIFETIME_SPREAD = 60;
    private static final float FADE_SHARE = 0.15F;
    private static final float MIN_LAG = 0;
    private static final float LAG_SPREAD = 14;
    private static final double SWERVE = 1.6;
    private static final double RISE_AND_FALL = 0.7;
    private static final float MIN_SWERVE_SPEED = 0.035F;
    private static final float SWERVE_SPEED_SPREAD = 0.025F;
    private static final float PULSE_SPEED = 0.17F;
    private static final float PULSE_DEPTH = 0.25F;

    private final double startX;
    private final double startY;
    private final double startZ;
    private final double headingX;
    private final double headingY;
    private final double headingZ;
    private final double sideX;
    private final double sideZ;
    private final float swerveSpeed;
    private final float lag;
    private final float peakAlpha;

    public ShoalSpeckParticle(ClientLevel level, double x, double y, double z, double headingX, double headingY, double headingZ, TextureAtlasSprite sprite)
    {
        super(level, x, y, z, sprite);
        gravity = 0;
        hasPhysics = false;
        xd = 0;
        yd = 0;
        zd = 0;
        startX = x;
        startY = y;
        startZ = z;
        this.headingX = headingX;
        this.headingY = headingY;
        this.headingZ = headingZ;

        double speed = Math.max(1.0E-4, Math.hypot(headingX, headingZ));
        sideX = -headingZ / speed;
        sideZ = headingX / speed;
        double shared = Math.abs(headingX) * 977 + Math.abs(headingZ) * 613;
        float sharedRoll = (float) (shared - Math.floor(shared));
        swerveSpeed = MIN_SWERVE_SPEED + sharedRoll * SWERVE_SPEED_SPREAD;
        var hue = HUES[Math.min(HUES.length - 1, (int) (sharedRoll * HUES.length))];
        setColor(hue[0], hue[1], hue[2]);

        lag = MIN_LAG + random.nextFloat() * LAG_SPREAD;
        peakAlpha = MAX_ALPHA * (0.8F + random.nextFloat() * 0.2F);
        lifetime = MIN_LIFETIME + random.nextInt(LIFETIME_SPREAD);
        quadSize = MIN_SIZE + random.nextFloat() * SIZE_SPREAD;
        alpha = 0;
    }

    @Override
    public void tick()
    {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime)
        {
            remove();
            return;
        }

        float swerve = (age + lag) * swerveSpeed;
        double across = Mth.sin(swerve) * SWERVE;
        setPos(startX + headingX * age + sideX * across,
                startY + headingY * age + Mth.sin(swerve * 1.7F) * RISE_AND_FALL,
                startZ + headingZ * age + sideZ * across);

        float progress = age / (float) lifetime;
        float pulse = 1 - PULSE_DEPTH * (0.5F + 0.5F * Mth.sin((age + lag * 3) * PULSE_SPEED));
        alpha = peakAlpha * pulse * Math.min(1, Math.min(progress, 1 - progress) / FADE_SHARE);
    }

    @Override
    protected Layer getLayer()
    {
        return Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightCoords(float partialTick)
    {
        return LightCoordsUtil.FULL_BRIGHT;
    }
}
