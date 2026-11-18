package net.voxelindustry.voidheart.client.secondheart;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * A mote of a body the second heart is taking apart or putting back: white-hot as it leaves the front, cooling to violet, then a deep dark
 * violet. Lifting ones drift up and away, slowing; infalling ones run straight in to the front over a set time and go out as they reach it.
 */
public class HeartMoteParticle extends SingleQuadParticle
{
    public static final int INFALL_TICKS = 10;

    private static final float HOT_RED   = 0.98F, HOT_GREEN  = 0.92F, HOT_BLUE  = 1F;
    private static final float COOL_RED  = 0.52F, COOL_GREEN = 0.20F, COOL_BLUE = 0.92F;
    private static final float DEEP_RED  = 0.20F, DEEP_GREEN = 0.06F, DEEP_BLUE = 0.36F;

    private static final float START_SIZE  = 0.055F;
    private static final float END_SIZE    = 0.02F;
    private static final int   LIFETIME    = 16;
    private static final int   LIFETIME_SPREAD = 10;
    private static final float HOT_UNTIL   = 0.25F;
    private static final float DRAG        = 0.94F;
    private static final float ALPHA       = 0.95F;

    private final boolean infall;
    private final float   startSize;
    private final float   endSize;

    private HeartMoteParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, TextureAtlasSprite sprite,
                              boolean infall, int lifetime)
    {
        super(level, x, y, z, sprite);
        this.infall = infall;
        float size = 0.7F + random.nextFloat() * 0.6F;
        startSize = START_SIZE * size;
        endSize = END_SIZE * size;
        this.lifetime = lifetime;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        gravity = 0;
        hasPhysics = false;
        friction = infall ? 1 : DRAG;
        quadSize = infall ? endSize : startSize;
        alpha = ALPHA;
        setColor(infall ? DEEP_RED : HOT_RED, infall ? DEEP_GREEN : HOT_GREEN, infall ? DEEP_BLUE : HOT_BLUE);
    }

    public static HeartMoteParticle lifting(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, TextureAtlasSprite sprite)
    {
        return new HeartMoteParticle(level, x, y, z, xd, yd, zd, sprite, false, LIFETIME + level.getRandom().nextInt(LIFETIME_SPREAD));
    }

    public static HeartMoteParticle infalling(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, TextureAtlasSprite sprite)
    {
        return new HeartMoteParticle(level, x, y, z, xd, yd, zd, sprite, true, INFALL_TICKS);
    }

    @Override
    public void tick()
    {
        super.tick();
        float progress = Math.min(1, age / (float) lifetime);
        // Infalling motes run the same colours backwards, so they arrive white-hot at the front
        float heat = infall ? 1 - progress : progress;
        quadSize = Mth.lerp(heat, startSize, endSize);
        alpha = ALPHA * (infall ? Math.min(1, progress * 4) : Math.min(1, (1 - progress) * 3));
        if (heat < HOT_UNTIL)
        {
            float share = heat / HOT_UNTIL;
            setColor(Mth.lerp(share, HOT_RED, COOL_RED), Mth.lerp(share, HOT_GREEN, COOL_GREEN), Mth.lerp(share, HOT_BLUE, COOL_BLUE));
        }
        else
        {
            float share = (heat - HOT_UNTIL) / (1 - HOT_UNTIL);
            setColor(Mth.lerp(share, COOL_RED, DEEP_RED), Mth.lerp(share, COOL_GREEN, DEEP_GREEN), Mth.lerp(share, COOL_BLUE, DEEP_BLUE));
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
