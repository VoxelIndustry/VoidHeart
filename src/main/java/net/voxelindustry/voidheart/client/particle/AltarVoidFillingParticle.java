package net.voxelindustry.voidheart.client.particle;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Quaternion;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3f;

import static java.lang.Math.abs;

public class AltarVoidFillingParticle extends Particle
{
    private final FabricSpriteProvider spriteProvider;

    private final double destinationX;
    private final double destinationY;
    private final double destinationZ;

    private double speed;

    public AltarVoidFillingParticle(ClientWorld world,
                                    double x,
                                    double y,
                                    double z,
                                    double velocityX,
                                    double velocityY,
                                    double velocityZ,
                                    double speed,
                                    FabricSpriteProvider spriteProvider)
    {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
        this.spriteProvider = spriteProvider;
        gravityStrength = 0;

        this.velocityX = velocityX - x;
        this.velocityY = velocityY - y;
        this.velocityZ = velocityZ - z;

        destinationX = velocityX;
        destinationY = velocityY;
        destinationZ = velocityZ;
        this.speed = speed;

        collidesWithWorld = false;

        maxAge = (int) (32.0F / (random.nextFloat() * 0.9F + 0.1F));
    }

    @Override
    public void buildGeometry(VertexConsumer vertexConsumer, Camera camera, float tickDelta)
    {
        Vec3d vec3d = camera.getPos();
        float f = (float) (MathHelper.lerp(tickDelta, prevPosX, x) - vec3d.getX());
        float g = (float) (MathHelper.lerp(tickDelta, prevPosY, y) - vec3d.getY());
        float h = (float) (MathHelper.lerp(tickDelta, prevPosZ, z) - vec3d.getZ());
        Quaternion quaternion2;
        if (angle == 0.0F)
        {
            quaternion2 = camera.getRotation();
        }
        else
        {
            quaternion2 = new Quaternion(camera.getRotation());
            float i = MathHelper.lerp(tickDelta, prevAngle, angle);
            quaternion2.hamiltonProduct(Vec3f.POSITIVE_Z.getRadialQuaternion(i));
        }

        Vec3f vector3f = new Vec3f(-1.0F, -1.0F, 0.0F);
        vector3f.rotate(quaternion2);
        Vec3f[] vector3fs = new Vec3f[]{new Vec3f(-1.0F, -1.0F, 0.0F), new Vec3f(-1.0F, 1.0F, 0.0F), new Vec3f(1.0F, 1.0F, 0.0F), new Vec3f(1.0F, -1.0F, 0.0F)};
        float size = 1 / 48F;

        for (int k = 0; k < 4; ++k)
        {
            Vec3f vector3f2 = vector3fs[k];
            vector3f2.rotate(quaternion2);
            vector3f2.scale(size);
            vector3f2.add(f, g, h);
        }

        Sprite sprite = spriteProvider.getSprite(age, maxAge);
        
        this.red = 0.21F;
        this.green = 0.4F;
        this.blue = 0.33F;

        float l = sprite.getMinU();
        float m = sprite.getMaxU();
        float n = sprite.getMinV();
        float o = sprite.getMaxV();
        int light = 15728880;
        vertexConsumer.vertex(vector3fs[0].getX(), vector3fs[0].getY(), vector3fs[0].getZ()).texture(m, o).color(this.red, this.green, this.blue, this.alpha).light(light).next();
        vertexConsumer.vertex(vector3fs[1].getX(), vector3fs[1].getY(), vector3fs[1].getZ()).texture(m, n).color(this.red, this.green, this.blue, this.alpha).light(light).next();
        vertexConsumer.vertex(vector3fs[2].getX(), vector3fs[2].getY(), vector3fs[2].getZ()).texture(l, n).color(this.red, this.green, this.blue, this.alpha).light(light).next();
        vertexConsumer.vertex(vector3fs[3].getX(), vector3fs[3].getY(), vector3fs[3].getZ()).texture(l, o).color(this.red, this.green, this.blue, this.alpha).light(light).next();
    }

    @Override
    public ParticleTextureSheet getType()
    {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick()
    {
        prevPosX = x;
        prevPosY = y;
        prevPosZ = z;
        if (age++ >= maxAge || isNearDestination())
        {
            markDead();
            return;
        }

        move(velocityX * speed, velocityY * speed, velocityZ * speed);
        speed *= 0.9800000190734863D;
    }

    private boolean isNearDestination()
    {
        return abs(x - destinationX) < 0.05 && abs(y - destinationY) < 0.05 && abs(z - destinationZ) < 0.05;
    }
}
