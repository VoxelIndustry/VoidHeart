package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;

import static java.lang.Math.abs;

public class AltarVoidFillingParticle extends SingleQuadParticle
{
    private final SpriteSet spriteSet;

    private final double destinationX;
    private final double destinationY;
    private final double destinationZ;

    private double speed;

    public AltarVoidFillingParticle(ClientLevel world,
                                    double x,
                                    double y,
                                    double z,
                                    double velocityX,
                                    double velocityY,
                                    double velocityZ,
                                    double speed,
                                    SpriteSet spriteSet)
    {
        super(world, x, y, z, velocityX, velocityY, velocityZ, spriteSet.first());
        this.spriteSet = spriteSet;
        gravity = 0;

        this.xd = velocityX - x;
        this.yd = velocityY - y;
        this.zd = velocityZ - z;

        destinationX = velocityX;
        destinationY = velocityY;
        destinationZ = velocityZ;
        this.speed = speed;

        hasPhysics = false;

        lifetime = (int) (32.0F / (random.nextFloat() * 0.9F + 0.1F));
        quadSize = 1 / 48F;
        setColor(0.21F, 0.4F, 0.33F);
        setSpriteFromAge(spriteSet);
    }

    @Override
    protected Layer getLayer()
    {
        return Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightCoords(float partialTick)
    {
        return 15728880;
    }

    @Override
    public void tick()
    {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime || isNearDestination())
        {
            remove();
            return;
        }

        setSpriteFromAge(spriteSet);
        move(xd * speed, yd * speed, zd * speed);
        speed *= 0.98D;
    }

    private boolean isNearDestination()
    {
        return abs(x - destinationX) < 0.05 && abs(y - destinationY) < 0.05 && abs(z - destinationZ) < 0.05;
    }
}
