package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * What glows in a smoker's jet: the short hot core at its mouth, and embers carried up through the black plume so that it shows in the dark.
 */
public class SmokerGlowParticle extends SingleQuadParticle
{
    private static final float WHITE_RED = 1F;
    private static final float WHITE_GREEN = 0.93F;
    private static final float WHITE_BLUE = 0.72F;
    private static final float ORANGE_RED = 1F;
    private static final float ORANGE_GREEN = 0.42F;
    private static final float ORANGE_BLUE = 0.07F;

    private static final float CORE_START_SIZE = 0.3F;
    private static final float CORE_END_SIZE = 0.1F;
    private static final float EMBER_START_SIZE = 0.075F;
    private static final float EMBER_END_SIZE = 0.03F;
    private static final float CORE_ALPHA = 0.95F;
    private static final int CORE_LIFETIME = 8;
    private static final int CORE_LIFETIME_SPREAD = 7;
    private static final int EMBER_LIFETIME = 34;
    private static final int EMBER_LIFETIME_SPREAD = 22;
    private static final double CORE_DRAG = 0.97;
    private static final double EMBER_DRAG = 0.965;
    private static final double EMBER_WIGGLE = 0.012;
    private static final float WIGGLE_SPEED = 0.35F;

    private final float startSize;
    private final float endSize;
    private final float whiteUntil;
    private final double drag;
    private final double wiggle;
    private final float wigglePhase;

    private SmokerGlowParticle(ClientLevel level, double x, double y, double z, double rise, TextureAtlasSprite sprite,
                               float startSize, float endSize, float whiteUntil, int lifetime, double drag, double wiggle)
    {
        super(level, x, y, z, sprite);
        float size = 0.8F + random.nextFloat() * 0.4F;
        this.startSize = startSize * size;
        this.endSize = endSize * size;
        this.whiteUntil = whiteUntil;
        this.drag = drag;
        this.wiggle = wiggle;
        this.lifetime = lifetime;
        wigglePhase = random.nextFloat() * Mth.TWO_PI;
        yd = rise;
        xd = 0;
        zd = 0;
        gravity = 0;
        hasPhysics = false;
        quadSize = this.startSize;
        alpha = CORE_ALPHA;
        setColor(WHITE_RED, WHITE_GREEN, WHITE_BLUE);
    }

    public static SmokerGlowParticle core(ClientLevel level, double x, double y, double z, double rise, TextureAtlasSprite sprite)
    {
        int lifetime = CORE_LIFETIME + level.getRandom().nextInt(CORE_LIFETIME_SPREAD);
        return new SmokerGlowParticle(level, x, y, z, rise, sprite, CORE_START_SIZE, CORE_END_SIZE, 0.45F, lifetime, CORE_DRAG, 0);
    }

    public static SmokerGlowParticle ember(ClientLevel level, double x, double y, double z, double rise, TextureAtlasSprite sprite)
    {
        int lifetime = EMBER_LIFETIME + level.getRandom().nextInt(EMBER_LIFETIME_SPREAD);
        return new SmokerGlowParticle(level, x, y, z, rise, sprite, EMBER_START_SIZE, EMBER_END_SIZE, 0, lifetime, EMBER_DRAG, EMBER_WIGGLE);
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
        yd *= drag;
        x += xd + Mth.sin(wigglePhase + age * WIGGLE_SPEED) * wiggle;
        y += yd;
        z += zd + Mth.cos(wigglePhase + age * WIGGLE_SPEED) * wiggle;
        setPos(x, y, z);

        quadSize = Mth.lerp(progress, startSize, endSize);
        alpha = CORE_ALPHA * Math.min(1, (1 - progress) * 3);
        float cooling = whiteUntil <= 0 ? 1 : Math.min(1, progress / whiteUntil);
        setColor(Mth.lerp(cooling, WHITE_RED, ORANGE_RED), Mth.lerp(cooling, WHITE_GREEN, ORANGE_GREEN), Mth.lerp(cooling, WHITE_BLUE, ORANGE_BLUE));
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
