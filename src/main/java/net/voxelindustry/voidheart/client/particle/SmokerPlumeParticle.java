package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * Black smoke rising as if through deep water: a hard push out of the vent, then heavy drag and a slow roll outward. Stays lit at the same level everywhere,
 * so its near-black keeps reading against the blue fog.
 */
public class SmokerPlumeParticle extends SingleQuadParticle
{
    private static final float HOT_RED = 0.62F;
    private static final float HOT_GREEN = 0.2F;
    private static final float HOT_BLUE = 0.05F;
    private static final float SMOKE_RED = 0.035F;
    private static final float SMOKE_GREEN = 0.03F;
    private static final float SMOKE_BLUE = 0.045F;
    private static final float THIN_RED = 0.11F;
    private static final float THIN_GREEN = 0.12F;
    private static final float THIN_BLUE = 0.15F;
    private static final float HOT_UNTIL = 0.14F;

    private static final float PLUME_START_SIZE = 0.3F;
    private static final float PLUME_END_SIZE = 1.0F;
    private static final float WISP_START_SIZE = 0.08F;
    private static final float WISP_END_SIZE = 0.3F;
    private static final float MAX_ALPHA = 0.94F;
    private static final float WISP_ALPHA = 0.5F;
    private static final float FADE_IN = 0.06F;
    private static final float FADE_OUT_FROM = 0.4F;

    private static final int PLUME_LIFETIME = 70;
    private static final int WISP_LIFETIME = 40;
    private static final int LIFETIME_SPREAD = 26;
    private static final double DRAG = 0.945;
    private static final double BUOYANCY = 0.028;
    private static final double ROLL_STRENGTH = 0.0035;
    private static final double OUTWARD_STRENGTH = 0.0018;
    private static final float MIN_ROLL_SPEED = 0.07F;
    private static final float ROLL_SPEED_SPREAD = 0.08F;

    private final float startSize;
    private final float endSize;
    private final float peakAlpha;
    private final double buoyancy;
    private final float rollPhase;
    private final float rollSpeed;
    private final double outwardX;
    private final double outwardZ;

    private SmokerPlumeParticle(ClientLevel level, double x, double y, double z, double rise, TextureAtlasSprite sprite,
                                float startSize, float endSize, float peakAlpha, int lifetime, double buoyancy)
    {
        super(level, x, y, z, sprite);
        this.startSize = startSize * (0.8F + random.nextFloat() * 0.4F);
        this.endSize = endSize * (0.75F + random.nextFloat() * 0.5F);
        this.peakAlpha = peakAlpha;
        this.buoyancy = buoyancy;
        this.lifetime = lifetime + random.nextInt(LIFETIME_SPREAD);
        yd = rise;
        xd = 0;
        zd = 0;
        gravity = 0;
        hasPhysics = true;
        rollPhase = random.nextFloat() * Mth.TWO_PI;
        rollSpeed = MIN_ROLL_SPEED + random.nextFloat() * ROLL_SPEED_SPREAD;
        float heading = random.nextFloat() * Mth.TWO_PI;
        outwardX = Mth.cos(heading) * OUTWARD_STRENGTH;
        outwardZ = Mth.sin(heading) * OUTWARD_STRENGTH;
        quadSize = this.startSize;
        alpha = 0;
        roll = random.nextFloat() * Mth.TWO_PI;
        oRoll = roll;
        setColor(HOT_RED, HOT_GREEN, HOT_BLUE);
    }

    public static SmokerPlumeParticle plume(ClientLevel level, double x, double y, double z, double rise, TextureAtlasSprite sprite)
    {
        return new SmokerPlumeParticle(level, x, y, z, rise, sprite, PLUME_START_SIZE, PLUME_END_SIZE, MAX_ALPHA, PLUME_LIFETIME, BUOYANCY);
    }

    public static SmokerPlumeParticle wisp(ClientLevel level, double x, double y, double z, double rise, TextureAtlasSprite sprite)
    {
        return new SmokerPlumeParticle(level, x, y, z, rise, sprite, WISP_START_SIZE, WISP_END_SIZE, WISP_ALPHA, WISP_LIFETIME, BUOYANCY / 2);
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
        double spread = Math.min(1, progress * 2);
        xd = xd * DRAG + outwardX * spread + Mth.cos(rollPhase) * wander * ROLL_STRENGTH;
        zd = zd * DRAG + outwardZ * spread + Mth.sin(rollPhase) * wander * ROLL_STRENGTH;
        yd = yd * DRAG + buoyancy * (1 - DRAG);
        yd = Math.max(yd, buoyancy);
        move(xd, yd, zd);
        roll += (rollSpeed - MIN_ROLL_SPEED - ROLL_SPEED_SPREAD / 2) * 0.3F;

        float grow = 1 - (1 - progress) * (1 - progress);
        quadSize = Mth.lerp(grow, startSize, endSize);
        alpha = peakAlpha * Math.min(1, progress / FADE_IN);
        if (progress > FADE_OUT_FROM)
            alpha *= 1 - (progress - FADE_OUT_FROM) / (1 - FADE_OUT_FROM);
        applyColor(progress);
    }

    private void applyColor(float progress)
    {
        if (progress < HOT_UNTIL)
        {
            float cooling = progress / HOT_UNTIL;
            setColor(Mth.lerp(cooling, HOT_RED, SMOKE_RED), Mth.lerp(cooling, HOT_GREEN, SMOKE_GREEN), Mth.lerp(cooling, HOT_BLUE, SMOKE_BLUE));
            return;
        }
        float thinning = (progress - HOT_UNTIL) / (1 - HOT_UNTIL);
        setColor(Mth.lerp(thinning, SMOKE_RED, THIN_RED), Mth.lerp(thinning, SMOKE_GREEN, THIN_GREEN), Mth.lerp(thinning, SMOKE_BLUE, THIN_BLUE));
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
