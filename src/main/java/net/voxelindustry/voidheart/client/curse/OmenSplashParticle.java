package net.voxelindustry.voidheart.client.curse;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * The tiny crown a drop leaves where it lands, gone within a few ticks.
 */
final class OmenSplashParticle extends SingleQuadParticle
{
    private static final int   LIFETIME_MIN   = 3;
    private static final int   LIFETIME_EXTRA = 3;
    private static final float SIZE_MIN       = 0.03F;
    private static final float SIZE_EXTRA     = 0.03F;
    private static final float GROWTH         = 0.6F;
    // Places the bottom of the crown on the surface
    private static final float LIFT           = 0.75F;

    private final float size;

    OmenSplashParticle(ClientLevel level, double x, double surfaceY, double z, TextureAtlasSprite sprite)
    {
        super(level, x, surfaceY, z, sprite);
        hasPhysics = false;
        gravity = 0;
        lifetime = LIFETIME_MIN + random.nextInt(LIFETIME_EXTRA);
        size = SIZE_MIN + random.nextFloat() * SIZE_EXTRA;
        quadSize = size;
        setPos(x, surfaceY + size * LIFT, z);
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
        {
            remove();
            return;
        }
        float progress = age / (float) lifetime;
        quadSize = size * (1 + GROWTH * progress);
        alpha = 1 - progress * progress;
    }

    @Override
    protected Layer getLayer()
    {
        return Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightCoords(float partialTick)
    {
        return OmenDropParticle.LIGHT;
    }
}
