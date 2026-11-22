package net.voxelindustry.voidheart.client.levitation;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * A faint azurium mote shed by a Levitation Belt in flight, sinking slowly from the waist and fading before it reaches the ground.
 */
public class BeltMoteParticle extends SingleQuadParticle
{
    // Azurium's pale tone, as the pylon arcs use it
    private static final float RED   = 0.8F;
    private static final float GREEN = 1F;
    private static final float BLUE  = 0.95F;

    private static final float SIZE         = 0.05F;
    private static final float MAX_ALPHA    = 0.7F;
    private static final float FADE_IN      = 0.2F;
    private static final float FADE_OUT     = 0.5F;
    private static final int   MIN_LIFETIME = 18;
    private static final int   MAX_LIFETIME = 30;
    private static final double SINK        = 0.035;
    private static final double DRIFT       = 0.01;

    private final float size;

    public BeltMoteParticle(ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, TextureAtlasSprite sprite)
    {
        super(level, x, y, z, sprite);
        gravity = 0;
        hasPhysics = false;
        friction = 1;
        // The wearer's own motion is passed in, so motes leave a flying wearer behind gently instead of standing still in the air
        xd = xAux * 0.3 + (random.nextDouble() - 0.5) * DRIFT;
        yd = Math.min(0, yAux * 0.3) - SINK * (0.7 + random.nextDouble() * 0.6);
        zd = zAux * 0.3 + (random.nextDouble() - 0.5) * DRIFT;

        lifetime = MIN_LIFETIME + random.nextInt(MAX_LIFETIME - MIN_LIFETIME + 1);
        size = SIZE * (0.8F + random.nextFloat() * 0.4F);
        quadSize = size;
        alpha = 0;
        setColor(RED, GREEN, BLUE);
    }

    @Override
    public void tick()
    {
        super.tick();
        float progress = age / (float) lifetime;
        float fadeIn = Math.min(1, progress / FADE_IN);
        float fadeOut = progress < FADE_OUT ? 1 : 1 - (progress - FADE_OUT) / (1 - FADE_OUT);
        alpha = MAX_ALPHA * fadeIn * fadeOut;
        quadSize = size * Mth.lerp(progress, 1, 0.5F);
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
