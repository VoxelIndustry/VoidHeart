package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Soft speck of knowledge drifting along a bowed path into a repository's tablet, so the far ones take longer without crossing a room in a straight line.
 */
public class KnowledgeMoteParticle extends SingleQuadParticle
{
    private static final float RED   = 0.21F;
    private static final float GREEN = 0.77F;
    private static final float BLUE  = 0.69F;

    private static final float SIZE       = 0.1F;
    private static final float MAX_ALPHA  = 0.95F;
    private static final float FADE_IN    = 0.2F;
    private static final float SHRINK_END = 0.45F;

    private static final float SHORTEST_LIFETIME = 16;
    private static final float LONGEST_LIFETIME  = 38;
    private static final float LONG_DISTANCE     = 8;
    private static final float LIFETIME_JITTER   = 0.3F;
    private static final float BOW               = 0.22F;
    private static final float LIFT              = 0.12F;

    private final Vec3  start;
    private final Vec3  bend;
    private final Vec3  end;
    private final float size;

    public KnowledgeMoteParticle(ClientLevel level, double x, double y, double z, double toX, double toY, double toZ, TextureAtlasSprite sprite)
    {
        super(level, x, y, z, sprite);
        gravity = 0;
        hasPhysics = false;

        start = new Vec3(x, y, z);
        end = new Vec3(toX, toY, toZ);
        var along = end.subtract(start);
        double distance = along.length();
        var side = along.cross(new Vec3(0, 1, 0));
        var bow = side.lengthSqr() > 1.0E-6 ? side.normalize().scale(distance * BOW * (random.nextFloat() * 2 - 1)) : Vec3.ZERO;
        bend = start.add(along.scale(0.5)).add(bow).add(0, distance * LIFT * random.nextFloat(), 0);

        float reach = Math.min(1, (float) distance / LONG_DISTANCE);
        lifetime = Math.round(Mth.lerp(reach, SHORTEST_LIFETIME, LONGEST_LIFETIME) * (1 - LIFETIME_JITTER / 2 + random.nextFloat() * LIFETIME_JITTER));
        size = SIZE * (0.75F + random.nextFloat() * 0.5F);
        quadSize = size;
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
        var inverse = 1 - progress;
        setPos(start.x * inverse * inverse + bend.x * 2 * inverse * progress + end.x * progress * progress,
                start.y * inverse * inverse + bend.y * 2 * inverse * progress + end.y * progress * progress,
                start.z * inverse * inverse + bend.z * 2 * inverse * progress + end.z * progress * progress);
        alpha = MAX_ALPHA * Math.min(1, progress / FADE_IN);
        quadSize = size * Mth.lerp(progress, 1, SHRINK_END);
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
