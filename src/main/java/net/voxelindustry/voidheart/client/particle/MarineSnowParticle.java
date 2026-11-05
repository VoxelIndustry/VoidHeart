package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * Specks sinking so slowly they seem to hang, as organic debris falls through a real deep sea. Faint on purpose: it is background to everything else.
 */
public class MarineSnowParticle extends SingleQuadParticle
{
    private static final float RED = 0.72F;
    private static final float GREEN = 0.84F;
    private static final float BLUE = 0.92F;
    private static final float MAX_ALPHA = 0.32F;
    private static final float MIN_SIZE = 0.02F;
    private static final float SIZE_SPREAD = 0.025F;
    private static final int MIN_LIFETIME = 180;
    private static final int LIFETIME_SPREAD = 160;
    private static final double MIN_SINK = 0.004;
    private static final double SINK_SPREAD = 0.008;
    private static final double SWAY = 0.0035;
    private static final float SWAY_SPEED = 0.03F;
    private static final float FADE_SHARE = 0.15F;
    private static final double CAMERA_FADE_START = 1;
    private static final double CAMERA_FADE_RANGE = 3;

    private final double sink;
    private final float swayPhase;

    public MarineSnowParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite)
    {
        super(level, x, y, z, sprite);
        gravity = 0;
        hasPhysics = false;
        xd = 0;
        yd = 0;
        zd = 0;
        lifetime = MIN_LIFETIME + random.nextInt(LIFETIME_SPREAD);
        sink = MIN_SINK + random.nextDouble() * SINK_SPREAD;
        swayPhase = random.nextFloat() * Mth.TWO_PI;
        quadSize = MIN_SIZE + random.nextFloat() * SIZE_SPREAD;
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

        setPos(x + Mth.sin(swayPhase + age * SWAY_SPEED) * SWAY, y - sink, z + Mth.cos(swayPhase + age * SWAY_SPEED) * SWAY);
        float progress = age / (float) lifetime;
        alpha = MAX_ALPHA * Math.min(1, Math.min(progress, 1 - progress) / FADE_SHARE) * cameraFade();
    }

    /**
     * Right against the eye a speck would swell into a blob.
     */
    private float cameraFade()
    {
        var player = Minecraft.getInstance().player;
        if (player == null)
            return 1;
        double distance = Math.sqrt(player.distanceToSqr(x, y - player.getEyeHeight(), z));
        return (float) Mth.clamp((distance - CAMERA_FADE_START) / CAMERA_FADE_RANGE, 0, 1);
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
