package net.voxelindustry.voidheart.client.particle;

import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import static java.lang.Math.*;
import static org.joml.Math.abs;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.world.item.ItemStack;

public class ShatterForgeItemParticle extends SingleQuadParticle
{
    private final Vector4fc offsetItemUV;

    private final SimpleSpriteProvider spriteProvider;

    private final Vector3dc start;
    private final Vector3dc end;

    private final double angleOffset;

    public ShatterForgeItemParticle(ClientLevel world,
                                    double x,
                                    double y,
                                    double z,
                                    double destinationX,
                                    double destinationY,
                                    double destinationZ,
                                    ItemStack inputStack,
                                    ItemStack outputStack)
    {
        super(world, x, y, z, destinationX, destinationY, destinationZ, ParticleSprites.itemSprite(inputStack, world, world.getRandom()));

        gravity = 0;

        this.xd = 0;
        this.yd = 0;
        this.zd = 0;

        start = new Vector3d(x, y, z);
        end = new Vector3d(destinationX, destinationY, destinationZ);

        lifetime = 20;

        quadSize = 0.1F * (random.nextFloat() * 0.5F + 0.5F);

        var baseSprite = sprite;
        spriteProvider = new SimpleSpriteProvider(random.nextFloat() * 0.5F + 0.8F, baseSprite, ParticleSprites.itemSprite(outputStack, world, random));
        setSpriteFromAge(spriteProvider);

        float uDiff = (baseSprite.getU1() - baseSprite.getU0()) / 4;
        float vDiff = (baseSprite.getV1() - baseSprite.getV0()) / 4;

        var uSlice = random.nextInt(4);
        var vSlice = random.nextInt(4);

        offsetItemUV = new Vector4f(uDiff * uSlice, vDiff * vSlice, uDiff * (uSlice + 1), vDiff * (vSlice + 1));

        angleOffset = random.nextBoolean() ? PI : 0;
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

        setSpriteFromAge(spriteProvider);

        double delta = age / (double) lifetime;

        var diff = end.sub(start, new Vector3d());

        var direction = diff.normalize(new Vector3d());
        var rightDirection = direction.cross(0, 1, 0, new Vector3d());
        var upDirection = rightDirection.cross(direction, new Vector3d());

        double radius = 0.25;
        double pitch = 0.001;
        double angle = 0.005 * diff.length() / pitch * delta + angleOffset;

        double x = radius * cos(angle) * rightDirection.x() + (radius * sin(angle) * upDirection.x()) + (pitch * angle * direction.x());
        double y = radius * cos(angle) * rightDirection.y() + (radius * sin(angle) * upDirection.y()) + (pitch * angle * direction.y());
        double z = radius * cos(angle) * rightDirection.z() + (radius * sin(angle) * upDirection.z()) + (pitch * angle * direction.z());

        var pos = diff.mul(delta);

        setPos(start.x() + pos.x() + x, start.y() + pos.y() + y, start.z() + pos.z() + z);
    }

    private boolean isNearDestination()
    {
        return abs(x - end.x()) < 0.05 && abs(y - end.y()) < 0.05 && abs(z - end.z()) < 0.05;
    }

    @Override
    protected float getU0()
    {
        return this.sprite.getU0() + offsetItemUV.x();
    }

    @Override
    protected float getU1()
    {
        return this.sprite.getU0() + offsetItemUV.z();
    }

    @Override
    protected float getV0()
    {
        return this.sprite.getV0() + offsetItemUV.y();
    }

    @Override
    protected float getV1()
    {
        return this.sprite.getV0() + offsetItemUV.w();
    }

    @Override
    protected Layer getLayer()
    {
        return Layer.bySprite(sprite);
    }
}
