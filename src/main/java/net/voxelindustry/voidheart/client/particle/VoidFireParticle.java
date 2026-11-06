package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * The Kernacian Dragoon's void fire: flames that lick up white-hot and cool to violet, and embers that fly off with their own speed and fall. Both stay lit in the
 * dark.
 */
public class VoidFireParticle extends SingleQuadParticle
{
    private static final float HOT_RED = 0.98F;
    private static final float HOT_GREEN = 0.93F;
    private static final float HOT_BLUE = 1F;
    private static final float COOL_RED = 0.54F;
    private static final float COOL_GREEN = 0.24F;
    private static final float COOL_BLUE = 0.94F;
    private static final float DEEP_RED = 0.36F;
    private static final float DEEP_GREEN = 0.13F;
    private static final float DEEP_BLUE = 0.66F;

    private static final float FLAME_START_SIZE = 0.22F;
    private static final float FLAME_END_SIZE = 0.05F;
    private static final int FLAME_LIFETIME = 10;
    private static final int FLAME_LIFETIME_SPREAD = 8;
    private static final double FLAME_RISE = 0.06;
    private static final float FLAME_HOT_UNTIL = 0.35F;

    private static final float EMBER_START_SIZE = 0.06F;
    private static final float EMBER_END_SIZE = 0.025F;
    private static final int EMBER_LIFETIME = 18;
    private static final int EMBER_LIFETIME_SPREAD = 14;
    private static final float EMBER_GRAVITY = 0.5F;
    private static final double DRAG = 0.92;

    private final float startSize;
    private final float endSize;
    private final float hotUntil;
    private final float startAlpha;

    private VoidFireParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, TextureAtlasSprite sprite,
                             float startSize, float endSize, float hotUntil, int lifetime, float gravity, boolean physics)
    {
        super(level, x, y, z, sprite);
        float size = 0.75F + random.nextFloat() * 0.5F;
        this.startSize = startSize * size;
        this.endSize = endSize * size;
        this.hotUntil = hotUntil;
        this.lifetime = lifetime;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = gravity;
        hasPhysics = physics;
        friction = (float) DRAG;
        quadSize = this.startSize;
        startAlpha = 0.95F;
        alpha = startAlpha;
        setColor(HOT_RED, HOT_GREEN, HOT_BLUE);
    }

    /**
     * @param xd how it drifts, on top of its own rise
     */
    public static VoidFireParticle flame(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, TextureAtlasSprite sprite)
    {
        int lifetime = FLAME_LIFETIME + level.getRandom().nextInt(FLAME_LIFETIME_SPREAD);
        return new VoidFireParticle(level, x, y, z, xd, yd + FLAME_RISE, zd, sprite, FLAME_START_SIZE, FLAME_END_SIZE, FLAME_HOT_UNTIL, lifetime, 0, false);
    }

    public static VoidFireParticle ember(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, TextureAtlasSprite sprite)
    {
        int lifetime = EMBER_LIFETIME + level.getRandom().nextInt(EMBER_LIFETIME_SPREAD);
        return new VoidFireParticle(level, x, y, z, xd, yd, zd, sprite, EMBER_START_SIZE, EMBER_END_SIZE, 0.15F, lifetime, EMBER_GRAVITY, true);
    }

    @Override
    public void tick()
    {
        super.tick();
        float progress = Math.min(1, age / (float) lifetime);
        quadSize = Mth.lerp(progress, startSize, endSize);
        alpha = startAlpha * Math.min(1, (1 - progress) * 3);

        // White-hot, then violet, then a deep purple as it dies
        if (progress < hotUntil)
        {
            float share = progress / hotUntil;
            setColor(Mth.lerp(share, HOT_RED, COOL_RED), Mth.lerp(share, HOT_GREEN, COOL_GREEN), Mth.lerp(share, HOT_BLUE, COOL_BLUE));
        }
        else
        {
            float share = (progress - hotUntil) / (1 - hotUntil);
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
