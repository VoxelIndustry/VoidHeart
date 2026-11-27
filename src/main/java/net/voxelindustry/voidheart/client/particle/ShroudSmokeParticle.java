package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * The ink a shroud gate's seal turns into: dense violet-black smoke rolling away from the seal's edge as it lifts, thinning to a dusky violet. It keeps
 * the way it was sent, rising while the seal lifts and sinking while it settles, and is lit the same everywhere so its dark reads in a dim room.
 */
public class ShroudSmokeParticle extends SingleQuadParticle
{
    private static final float INK_RED = 0.12F;
    private static final float INK_GREEN = 0.045F;
    private static final float INK_BLUE = 0.19F;
    private static final float THIN_RED = 0.34F;
    private static final float THIN_GREEN = 0.19F;
    private static final float THIN_BLUE = 0.48F;

    private static final float START_SIZE = 0.4F;
    private static final float END_SIZE = 1.25F;
    private static final float MAX_ALPHA = 0.8F;
    private static final float FADE_IN = 0.1F;
    private static final float FADE_OUT_FROM = 0.45F;

    private static final int LIFETIME = 42;
    private static final int LIFETIME_SPREAD = 20;
    private static final double DRAG = 0.93;
    /** How fast it keeps drifting up or down once its push is spent. */
    private static final double DRIFT = 0.012;
    private static final double ROLL_STRENGTH = 0.004;
    private static final float MIN_ROLL_SPEED = 0.06F;
    private static final float ROLL_SPEED_SPREAD = 0.08F;

    private final float startSize;
    private final float endSize;
    private final double drift;
    private final float rollPhase;
    private final float rollSpeed;

    public ShroudSmokeParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, TextureAtlasSprite sprite)
    {
        super(level, x, y, z, sprite);
        startSize = START_SIZE * (0.8F + random.nextFloat() * 0.4F);
        endSize = END_SIZE * (0.75F + random.nextFloat() * 0.5F);
        lifetime = LIFETIME + random.nextInt(LIFETIME_SPREAD);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        drift = Math.signum(yd) * DRIFT;
        gravity = 0;
        hasPhysics = true;
        rollPhase = random.nextFloat() * Mth.TWO_PI;
        rollSpeed = MIN_ROLL_SPEED + random.nextFloat() * ROLL_SPEED_SPREAD;
        quadSize = startSize;
        alpha = 0;
        roll = random.nextFloat() * Mth.TWO_PI;
        oRoll = roll;
        setColor(INK_RED, INK_GREEN, INK_BLUE);
    }

    @Override
    public void tick()
    {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime)
        {
            remove();
            return;
        }

        float progress = age / (float) lifetime;
        float wander = Mth.sin(rollPhase + age * rollSpeed) * progress;
        xd = xd * DRAG + Mth.cos(rollPhase) * wander * ROLL_STRENGTH;
        zd = zd * DRAG + Mth.sin(rollPhase) * wander * ROLL_STRENGTH;
        yd = yd * DRAG + drift * (1 - DRAG);
        move(xd, yd, zd);
        roll += (rollSpeed - MIN_ROLL_SPEED - ROLL_SPEED_SPREAD / 2) * 0.3F;

        float grow = 1 - (1 - progress) * (1 - progress);
        quadSize = Mth.lerp(grow, startSize, endSize);
        alpha = MAX_ALPHA * Math.min(1, progress / FADE_IN);
        if (progress > FADE_OUT_FROM)
            alpha *= 1 - (progress - FADE_OUT_FROM) / (1 - FADE_OUT_FROM);
        setColor(Mth.lerp(progress, INK_RED, THIN_RED), Mth.lerp(progress, INK_GREEN, THIN_GREEN), Mth.lerp(progress, INK_BLUE, THIN_BLUE));
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
