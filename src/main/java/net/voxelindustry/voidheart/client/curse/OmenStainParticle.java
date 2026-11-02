package net.voxelindustry.voidheart.client.curse;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/**
 * A wet spatter lying flat on the surface a drop hit. It spreads out for a moment, stays, then dries away.
 * Its quad faces up and is drawn once, so nothing shows from below, and each sits at its own small height, so that neither the ground nor another stain can fight it.
 */
final class OmenStainParticle extends SingleQuadParticle
{
    private static final float LIFT_MIN   = 0.012F;
    private static final float LIFT_EXTRA = 0.008F;

    private static final float SPREAD_TICKS = 6;
    private static final float SPREAD_FROM  = 0.55F;
    // Share of its life spent fading out
    private static final float DRY_SHARE    = 0.45F;
    private static final float WET_ALPHA    = 0.96F;

    private static final int LIGHT = LightCoordsUtil.pack(9, 0);

    private final float size;
    private final float spin;

    OmenStainParticle(ClientLevel level, double x, double surfaceY, double z, float size, int quarterTurns, int lifetime, TextureAtlasSprite sprite)
    {
        super(level, x, surfaceY, z, sprite);
        this.size = size;
        this.lifetime = lifetime;
        spin = quarterTurns * Mth.HALF_PI;
        hasPhysics = false;
        gravity = 0;
        quadSize = size;
        setPos(x, surfaceY + LIFT_MIN + random.nextFloat() * LIFT_EXTRA, z);
        xo = this.x;
        yo = this.y;
        zo = this.z;
        float shade = OmenDropParticle.randomShade(random);
        setColor(shade, shade, shade);
    }

    @Override
    public void tick()
    {
        if (age++ >= lifetime)
            remove();
    }

    @Override
    public float getQuadSize(float partialTick)
    {
        return size * Mth.lerp(Mth.clamp((age + partialTick) / SPREAD_TICKS, 0, 1), SPREAD_FROM, 1);
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick)
    {
        alpha = WET_ALPHA * Mth.clamp((lifetime - age - partialTick) / (lifetime * DRY_SHARE), 0, 1);
        extractRotatedQuad(state, camera, new Quaternionf().rotationY(spin).rotateX(-Mth.HALF_PI), partialTick);
    }

    @Override
    protected Layer getLayer()
    {
        return Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightCoords(float partialTick)
    {
        return LIGHT;
    }
}
