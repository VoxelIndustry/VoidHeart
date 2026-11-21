package net.voxelindustry.voidheart.client.render.energy.storage;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Red mote drifting along a gentle curve from a construct to its bud, or between a cell and the field it feeds,
 * fading out as it arrives.
 */
public class RubrumMoteParticle extends SingleQuadParticle
{
    // Rubrum mid tone, from the bundle textures
    private static final float RED   = 1F;
    private static final float GREEN = 0.35F;
    private static final float BLUE  = 0.15F;

    private static final float SIZE         = 0.07F;
    private static final float MAX_ALPHA    = 0.9F;
    private static final float FADE_IN      = 0.15F;
    private static final float FADE_OUT     = 0.55F;
    private static final int   MIN_LIFETIME = 20;
    private static final int   MAX_LIFETIME = 30;
    private static final float BOW          = 0.18F;

    // Wrung harm: deep crimson, from the cell's Wrung shard texture
    private static final float WRUNG_RED          = 1F;
    private static final float WRUNG_GREEN        = 0.16F;
    private static final float WRUNG_BLUE         = 0.2F;
    private static final float WRUNG_SCALE        = 1.8F;
    private static final int   WRUNG_MIN_LIFETIME = 28;
    private static final int   WRUNG_MAX_LIFETIME = 40;

    private final Vec3  start;
    private final Vec3  bend;
    private final Vec3  end;
    private float size;

    public RubrumMoteParticle(ClientLevel level, double x, double y, double z, double toX, double toY, double toZ, TextureAtlasSprite sprite)
    {
        super(level, x, y, z, sprite);
        gravity = 0;
        hasPhysics = false;

        start = new Vec3(x, y, z);
        end = new Vec3(toX, toY, toZ);
        var along = end.subtract(start);
        var distance = along.length();
        var side = along.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-6)
            side = along.cross(new Vec3(1, 0, 0));
        var bow = side.lengthSqr() > 1.0E-6 ? side.normalize().scale(distance * BOW * (random.nextFloat() * 2 - 1)) : Vec3.ZERO;
        bend = start.add(along.scale(0.5)).add(bow);

        lifetime = MIN_LIFETIME + random.nextInt(MAX_LIFETIME - MIN_LIFETIME + 1);
        size = SIZE * (0.8F + random.nextFloat() * 0.4F);
        quadSize = size;
        alpha = 0;
        setColor(RED, GREEN, BLUE);
    }

    /**
     * A crimson wisp of harm rising from something hurt into a Wrung cell, slower and darker than the cell's own motes.
     */
    public static RubrumMoteParticle wrung(ClientLevel level, double x, double y, double z, double toX, double toY, double toZ, TextureAtlasSprite sprite)
    {
        var mote = new RubrumMoteParticle(level, x, y, z, toX, toY, toZ, sprite);
        mote.setColor(WRUNG_RED, WRUNG_GREEN, WRUNG_BLUE);
        mote.size *= WRUNG_SCALE;
        mote.quadSize = mote.size;
        mote.lifetime = WRUNG_MIN_LIFETIME + mote.random.nextInt(WRUNG_MAX_LIFETIME - WRUNG_MIN_LIFETIME + 1);
        return mote;
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
        var inverse = 1 - progress;
        setPos(start.x * inverse * inverse + bend.x * 2 * inverse * progress + end.x * progress * progress,
                start.y * inverse * inverse + bend.y * 2 * inverse * progress + end.y * progress * progress,
                start.z * inverse * inverse + bend.z * 2 * inverse * progress + end.z * progress * progress);
        var fadeIn = Math.min(1, progress / FADE_IN);
        var fadeOut = progress < FADE_OUT ? 1 : 1 - (progress - FADE_OUT) / (1 - FADE_OUT);
        alpha = MAX_ALPHA * fadeIn * fadeOut;
        quadSize = size * Mth.lerp(progress, 1, 0.6F);
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
