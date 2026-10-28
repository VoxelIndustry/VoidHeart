package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;

public class AltarItemParticle extends AltarStreamParticle
{

    private final float minU;
    private final float minV;
    private final float maxU;
    private final float maxV;

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
        super(world, x, y, z, velocityX, velocityY, velocityZ, firstPointBezier, secondPointBezier, ParticleSprites.itemSprite(stack, world, world.getRandom()));

        float uDiff = (sprite.getU1() - sprite.getU0()) / 4;
        float vDiff = (sprite.getV1() - sprite.getV0()) / 4;

        int uSlice = random.nextInt(4);
        minU = sprite.getU0() + uDiff * uSlice;
        maxU = sprite.getU0() + uDiff * (uSlice + 1);

        int vSlice = random.nextInt(4);
        minV = sprite.getV0() + vDiff * vSlice;
        maxV = sprite.getV0() + vDiff * (vSlice + 1);

        quadSize = 0.1F * (random.nextFloat() * 0.5F + 0.5F);
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
