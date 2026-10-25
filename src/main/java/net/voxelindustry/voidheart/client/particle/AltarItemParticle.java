package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.steamlayer.math.Bezier;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import static java.lang.Math.abs;

public class AltarItemParticle extends SingleQuadParticle
{

    private final float minU;
    private final float minV;
    private final float maxU;
    private final float maxV;

    private final double destinationX;
    private final double destinationY;
    private final double destinationZ;

    private final Vector3fc[] controlPoints;

    public AltarItemParticle(ClientLevel world,
                             double x,
                             double y,
                             double z,
                             double velocityX,
                             double velocityY,
                             double velocityZ,
                             ItemStack stack,
                             Vector3fc firstPointBezier,
                             Vector3fc secondPointBezier)
    {
        super(world, x, y, z, velocityX, velocityY, velocityZ, ParticleSprites.itemSprite(stack, world, world.getRandom()));

        float uDiff = (sprite.getU1() - sprite.getU0()) / 4;
        float vDiff = (sprite.getV1() - sprite.getV0()) / 4;

        int uSlice = random.nextInt(4);
        minU = sprite.getU0() + uDiff * uSlice;
        maxU = sprite.getU0() + uDiff * (uSlice + 1);

        int vSlice = random.nextInt(4);
        minV = sprite.getV0() + vDiff * vSlice;
        maxV = sprite.getV0() + vDiff * (vSlice + 1);

        gravity = 0;

        this.xd = velocityX - x;
        this.yd = velocityY - y;
        this.zd = velocityZ - z;

        destinationX = velocityX;
        destinationY = velocityY;
        destinationZ = velocityZ;

        lifetime = 30;

        controlPoints = new Vector3fc[]
                {
                        new Vector3f((float) x, (float) y, (float) z),
                        firstPointBezier,
                        secondPointBezier,
                        new Vector3f((float) destinationX, (float) destinationY, (float) destinationZ)
                };

        quadSize = 0.1F * (random.nextFloat() * 0.5F + 0.5F);
    }

    @Override
    public void tick()
    {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime || (isNearDestination()))
        {
            remove();
            return;
        }

        double delta = age / (double) lifetime;

        var pos = Bezier.pointOfBezier4f(controlPoints, (float) delta);

        setPos(pos.x(), pos.y(), pos.z());
    }

    private boolean isNearDestination()
    {
        return abs(x - destinationX) < 0.05 && abs(y - destinationY) < 0.05 && abs(z - destinationZ) < 0.05;
    }

    @Override
    protected float getU0()
    {
        return minU;
    }

    @Override
    protected float getU1()
    {
        return maxU;
    }

    @Override
    protected float getV0()
    {
        return minV;
    }

    @Override
    protected float getV1()
    {
        return maxV;
    }

    @Override
    protected Layer getLayer()
    {
        return Layer.bySprite(sprite);
    }
}
