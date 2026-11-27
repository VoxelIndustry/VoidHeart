package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * A faint pale-violet mote drifting slowly in front of an open shroud gate's void. Soft and slow, it fades in and out rather than popping.
 */
public class ShroudMoteParticle extends SingleQuadParticle
{
    private static final float RED = 0.72F;
    private static final float GREEN = 0.52F;
    private static final float BLUE = 1.0F;
    private static final float MIN_SIZE = 0.045F;
    private static final float SIZE_SPREAD = 0.035F;
    private static final float MAX_ALPHA = 0.7F;
    private static final float FADE = 0.25F;
    private static final int LIFETIME = 70;
    private static final int LIFETIME_SPREAD = 40;
    private static final double DRAG = 0.98;
    private static final double SWAY = 0.0015;
    private static final float SWAY_RATE = 0.05F;

    private final float swayPhase;

    public ShroudMoteParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, TextureAtlasSprite sprite)
    {
        super(level, x, y, z, sprite);
        lifetime = LIFETIME + random.nextInt(LIFETIME_SPREAD);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        gravity = 0;
        hasPhysics = false;
        quadSize = MIN_SIZE + random.nextFloat() * SIZE_SPREAD;
        swayPhase = random.nextFloat() * Mth.TWO_PI;
        alpha = 0;
        setColor(RED, GREEN, BLUE);
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

        float progress = age / (float) lifetime;
        xd = xd * DRAG + Mth.cos(swayPhase + age * SWAY_RATE) * SWAY;
        zd = zd * DRAG + Mth.sin(swayPhase + age * SWAY_RATE) * SWAY;
        yd *= DRAG;
        move(xd, yd, zd);
        alpha = MAX_ALPHA * Math.min(1, Math.min(progress, 1 - progress) / FADE);
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
