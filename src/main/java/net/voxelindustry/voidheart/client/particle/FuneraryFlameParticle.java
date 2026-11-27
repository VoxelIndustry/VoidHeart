package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * A lick of the mausoleum's funerary fire: pale yellow-green as it leaves the flame, lime as it rises, and a deep green as it gutters out. It keeps
 * the push it was given on top of its own rise, and stays lit in the dark.
 */
public class FuneraryFlameParticle extends SingleQuadParticle
{
    private static final float TIP_RED = 0.93F;
    private static final float TIP_GREEN = 1F;
    private static final float TIP_BLUE = 0.75F;
    private static final float LIME_RED = 0.49F;
    private static final float LIME_GREEN = 0.91F;
    private static final float LIME_BLUE = 0.23F;
    private static final float DEEP_RED = 0.12F;
    private static final float DEEP_GREEN = 0.42F;
    private static final float DEEP_BLUE = 0.07F;

    private static final float START_SIZE = 0.13F;
    private static final float END_SIZE = 0.03F;
    private static final int LIFETIME = 12;
    private static final int LIFETIME_SPREAD = 10;
    private static final double RISE = 0.02;
    private static final float TIP_UNTIL = 0.25F;
    private static final float START_ALPHA = 0.95F;
    private static final double DRAG = 0.9;

    private final float startSize;

    public FuneraryFlameParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, TextureAtlasSprite sprite)
    {
        super(level, x, y, z, sprite);
        startSize = START_SIZE * (0.7F + random.nextFloat() * 0.6F);
        lifetime = LIFETIME + random.nextInt(LIFETIME_SPREAD);
        this.xd = xd;
        this.yd = yd + RISE;
        this.zd = zd;
        gravity = 0;
        hasPhysics = false;
        friction = (float) DRAG;
        quadSize = startSize;
        alpha = START_ALPHA;
        setColor(TIP_RED, TIP_GREEN, TIP_BLUE);
    }

    @Override
    public void tick()
    {
        super.tick();
        float progress = Math.min(1, age / (float) lifetime);
        quadSize = Mth.lerp(progress, startSize, END_SIZE);
        alpha = START_ALPHA * Math.min(1, (1 - progress) * 3);
        if (progress < TIP_UNTIL)
        {
            float share = progress / TIP_UNTIL;
            setColor(Mth.lerp(share, TIP_RED, LIME_RED), Mth.lerp(share, TIP_GREEN, LIME_GREEN), Mth.lerp(share, TIP_BLUE, LIME_BLUE));
        }
        else
        {
            float share = (progress - TIP_UNTIL) / (1 - TIP_UNTIL);
            setColor(Mth.lerp(share, LIME_RED, DEEP_RED), Mth.lerp(share, LIME_GREEN, DEEP_GREEN), Mth.lerp(share, LIME_BLUE, DEEP_BLUE));
        }
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
