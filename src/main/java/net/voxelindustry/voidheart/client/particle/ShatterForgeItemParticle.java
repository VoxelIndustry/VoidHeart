package net.voxelindustry.voidheart.client.particle;

import org.joml.Vector4f;
import org.joml.Vector4fc;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.ItemStack;

public class ShatterForgeItemParticle extends BeamPathParticle
{
    private final Vector4fc offsetItemUV;

    private final SimpleSpriteProvider spriteProvider;

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

        quadSize = 0.1F * (random.nextFloat() * 0.5F + 0.5F);

        var baseSprite = sprite;
        spriteProvider = new SimpleSpriteProvider(random.nextFloat() * 0.5F + 0.8F, baseSprite, ParticleSprites.itemSprite(outputStack, world, random));
        setSpriteFromAge(spriteProvider);

        float uDiff = (baseSprite.getU1() - baseSprite.getU0()) / 4;
        float vDiff = (baseSprite.getV1() - baseSprite.getV0()) / 4;

        var uSlice = random.nextInt(4);
        var vSlice = random.nextInt(4);

        offsetItemUV = new Vector4f(uDiff * uSlice, vDiff * vSlice, uDiff * (uSlice + 1), vDiff * (vSlice + 1));
    }

    @Override
    public void tick()
    {
        super.tick();
        if (isAlive())
            setSpriteFromAge(spriteProvider);
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
