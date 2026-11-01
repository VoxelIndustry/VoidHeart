package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A spark circling around a Shoulder Eye beam while running along it, from the eye to its target.
 */
public class EyeBeamParticle extends SingleQuadParticle
{
    private final Vec3   start;
    private final Vec3   along;
    private final Vec3   sideA;
    private final Vec3   sideB;
    private final float  phase;
    private final float  radius;
    private final float  spin;
    private final float  size;

    public EyeBeamParticle(ClientLevel level, double x, double y, double z, double toX, double toY, double toZ, TextureAtlasSprite sprite)
    {
        super(level, x, y, z, sprite);
        gravity = 0;
        hasPhysics = false;

        start = new Vec3(x, y, z);
        along = new Vec3(toX - x, toY - y, toZ - z);
        var direction = along.lengthSqr() > 1.0E-6 ? along.normalize() : new Vec3(0, 1, 0);
        var reference = Math.abs(direction.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        sideA = direction.cross(reference).normalize();
        sideB = direction.cross(sideA);

        phase = random.nextFloat() * Mth.TWO_PI;
        radius = 0.1F + random.nextFloat() * 0.08F;
        spin = (random.nextBoolean() ? 1 : -1) * (0.7F + random.nextFloat() * 0.4F);
        size = 0.035F + random.nextFloat() * 0.025F;
        lifetime = 8 + random.nextInt(5);

        float shade = 0.75F + random.nextFloat() * 0.25F;
        setColor(0.36F * shade, 1.0F * shade, 0.42F * shade);
        quadSize = size;
        place(0);
        xo = this.x;
        yo = this.y;
        zo = this.z;
    }

    private void place(int age)
    {
        float progress = age / (float) lifetime;
        float angle = phase + age * spin;
        float ring = radius * (1 - progress * 0.4F);
        var at = start.add(along.scale(progress))
                .add(sideA.scale(Mth.cos(angle) * ring))
                .add(sideB.scale(Mth.sin(angle) * ring));
        setPos(at.x, at.y, at.z);
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

        place(age);
        float progress = age / (float) lifetime;
        quadSize = size * (1 - progress * 0.6F);
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
        return LightCoordsUtil.FULL_BRIGHT;
    }
}
