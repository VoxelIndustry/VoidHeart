package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A glyph lifting off a repository's tablet toward the research being fed, swaying a little on the way.
 */
public class KnowledgeGlyphParticle extends SingleQuadParticle
{
    private static final float RED   = 0.96F;
    private static final float GREEN = 0.84F;
    private static final float BLUE  = 0.44F;

    private static final float SIZE         = 0.038F;
    private static final float MAX_ALPHA    = 1;
    private static final float FADE_IN      = 0.2F;
    private static final float FADE_OUT     = 0.3F;
    private static final int   LIFETIME     = 25;
    private static final int   LIFETIME_ADD = 10;
    private static final float SWAY         = 0.05F;
    private static final float SWAY_SPEED   = 0.25F;

    private final Vec3  start;
    private final Vec3  along;
    private final Vec3  side;
    private final float phase;

    public KnowledgeGlyphParticle(ClientLevel level, double x, double y, double z, double toX, double toY, double toZ, TextureAtlasSprite sprite)
    {
        super(level, x, y, z, sprite);
        gravity = 0;
        hasPhysics = false;

        start = new Vec3(x, y, z);
        along = new Vec3(toX - x, toY - y, toZ - z);
        float angle = random.nextFloat() * Mth.TWO_PI;
        side = new Vec3(Mth.cos(angle), 0, Mth.sin(angle));
        phase = random.nextFloat() * Mth.TWO_PI;
        lifetime = LIFETIME + random.nextInt(LIFETIME_ADD);
        quadSize = SIZE * (0.8F + random.nextFloat() * 0.4F);
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
        // Quick off the tablet, then slowing as it nears what it feeds
        float travelled = 1 - (1 - progress) * (1 - progress);
        float sway = SWAY * Mth.sin(age * SWAY_SPEED + phase);
        var at = start.add(along.scale(travelled)).add(side.scale(sway));
        setPos(at.x, at.y, at.z);
        alpha = MAX_ALPHA * Math.min(1, Math.min(progress / FADE_IN, (1 - progress) / FADE_OUT));
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
