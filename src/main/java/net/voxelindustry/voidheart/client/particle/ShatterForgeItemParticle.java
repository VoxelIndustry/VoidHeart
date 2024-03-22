package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import static java.lang.Math.*;
import static org.joml.Math.abs;

public class ShatterForgeItemParticle extends SpriteBillboardParticle
{
    private final Vector4fc offsetItemUV;

    private final SimpleSpriteProvider spriteProvider;

    private final Vector3dc start;
    private final Vector3dc end;

    private final double angleOffset;

    public ShatterForgeItemParticle(ClientWorld world,
                                    double x,
                                    double y,
                                    double z,
                                    double destinationX,
                                    double destinationY,
                                    double destinationZ,
                                    ItemStack inputStack,
                                    ItemStack outputStack)
    {
        super(world, x, y, z, destinationX, destinationY, destinationZ);

        gravityStrength = 0;

        this.velocityX = 0;
        this.velocityY = 0;
        this.velocityZ = 0;

        start = new Vector3d(x, y, z);
        end = new Vector3d(destinationX, destinationY, destinationZ);

        maxAge = 20;

        scale = 0.1F * (random.nextFloat() * 0.5F + 0.5F);

        var models = MinecraftClient.getInstance().getItemRenderer().getModels();
        var baseSprite = models.getModel(inputStack).getParticleSprite();
        spriteProvider = new SimpleSpriteProvider(random.nextFloat() * 0.5F + 0.8F, baseSprite, models.getModel(outputStack).getParticleSprite());
        setSpriteForAge(spriteProvider);

        float uDiff = (baseSprite.getMaxU() - baseSprite.getMinU()) / 4;
        float vDiff = (baseSprite.getMaxV() - baseSprite.getMinV()) / 4;

        var uSlice = random.nextInt(4);
        var vSlice = random.nextInt(4);

        offsetItemUV = new Vector4f(uDiff * uSlice, vDiff * vSlice, uDiff * (uSlice + 1), vDiff * (vSlice + 1));

        angleOffset = random.nextBoolean() ? PI : 0;
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

        setSpriteForAge(spriteProvider);

        double delta = age / (double) maxAge;

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
    protected float getMinU()
    {
        return this.sprite.getMinU() + offsetItemUV.x();
    }

    @Override
    protected float getMaxU()
    {
        return this.sprite.getMinU() + offsetItemUV.z();
    }

    @Override
    protected float getMinV()
    {
        return this.sprite.getMinV() + offsetItemUV.y();
    }

    @Override
    protected float getMaxV()
    {
        return this.sprite.getMinV() + offsetItemUV.w();
    }

    @Override
    public ParticleTextureSheet getType()
    {
        return ParticleTextureSheet.TERRAIN_SHEET;
    }
}
