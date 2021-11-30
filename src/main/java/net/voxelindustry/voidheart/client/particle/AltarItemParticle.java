package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.voxelindustry.steamlayer.math.Bezier;
import net.voxelindustry.steamlayer.math.Vec3f;

import static java.lang.Math.abs;

public class AltarItemParticle extends SpriteBillboardParticle
{

    private final float minU;
    private final float minV;
    private final float maxU;
    private final float maxV;

    private final double destinationX;
    private final double destinationY;
    private final double destinationZ;

    private final Vec3f[] controlPoints;

    public AltarItemParticle(ClientWorld world,
                             double x,
                             double y,
                             double z,
                             double velocityX,
                             double velocityY,
                             double velocityZ,
                             ItemStack stack,
                             Vec3f firstPointBezier,
                             Vec3f secondPointBezier)
    {
        super(world, x, y, z, velocityX, velocityY, velocityZ);

        setSprite(MinecraftClient.getInstance().getItemRenderer().getModels().getModel(stack).getParticleSprite());

        float uDiff = (sprite.getMaxU() - sprite.getMinU()) / 4;
        float vDiff = (sprite.getMaxV() - sprite.getMinV()) / 4;

        int uSlice = random.nextInt(4);
        minU = sprite.getMinU() + uDiff * uSlice;
        maxU = sprite.getMinU() + uDiff * (uSlice + 1);

        int vSlice = random.nextInt(4);
        minV = sprite.getMinV() + vDiff * vSlice;
        maxV = sprite.getMinV() + vDiff * (vSlice + 1);

        gravityStrength = 0;

        this.velocityX = velocityX - x;
        this.velocityY = velocityY - y;
        this.velocityZ = velocityZ - z;

        destinationX = velocityX;
        destinationY = velocityY;
        destinationZ = velocityZ;

        maxAge = 30;

        controlPoints = new Vec3f[]
                {
                        new Vec3f((float) x, (float) y, (float) z),
                        firstPointBezier,
                        secondPointBezier,
                        new Vec3f((float) destinationX, (float) destinationY, (float) destinationZ)
                };

        scale = 0.1F * (random.nextFloat() * 0.5F + 0.5F);
    }

    @Override
    public void tick()
    {
        prevPosX = x;
        prevPosY = y;
        prevPosZ = z;
        if (age++ >= maxAge || (isNearDestination()))
        {
            markDead();
            return;
        }

        double delta = age / (double) maxAge;

        Vec3f pos = Bezier.pointOfBezier4f(controlPoints, (float) delta);

        setPos(pos.getX(), pos.getY(), pos.getZ());
    }

    private boolean isNearDestination()
    {
        return abs(x - destinationX) < 0.05 && abs(y - destinationY) < 0.05 && abs(z - destinationZ) < 0.05;
    }

    @Override
    protected float getMinU()
    {
        return minU;
    }

    @Override
    protected float getMaxU()
    {
        return maxU;
    }

    @Override
    protected float getMinV()
    {
        return minV;
    }

    @Override
    protected float getMaxV()
    {
        return maxV;
    }

    @Override
    public ParticleTextureSheet getType()
    {
        return ParticleTextureSheet.TERRAIN_SHEET;
    }
}
