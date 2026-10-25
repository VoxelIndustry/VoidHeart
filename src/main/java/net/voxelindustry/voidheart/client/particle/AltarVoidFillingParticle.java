package net.voxelindustry.voidheart.client.particle;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static java.lang.Math.abs;

import com.mojang.blaze3d.vertex.VertexConsumer;

public class AltarVoidFillingParticle extends Particle
{
    private final FabricSpriteProvider spriteProvider;

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
                                    FabricSpriteProvider spriteProvider)
    {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
        this.spriteProvider = spriteProvider;
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
    }

    @Override
    public void render(VertexConsumer vertexConsumer, Camera camera, float tickDelta)
    {
        Vec3 vec3d = camera.getPosition();
        float f = (float) (Mth.lerp(tickDelta, xo, x) - vec3d.x());
        float g = (float) (Mth.lerp(tickDelta, yo, y) - vec3d.y());
        float h = (float) (Mth.lerp(tickDelta, zo, z) - vec3d.z());
        Quaternionf quaternion2;
        if (roll == 0.0F)
        {
            quaternion2 = camera.rotation();
        }
        else
        {
            quaternion2 = new Quaternionf(camera.rotation());
            quaternion2.mul(Direction.NORTH.getRotation());
        }

        var vector3f = new Vector3f(-1.0F, -1.0F, 0.0F);
        vector3f.rotate(quaternion2);
        var vector3fs = new Vector3f[]{new Vector3f(-1.0F, -1.0F, 0.0F), new Vector3f(-1.0F, 1.0F, 0.0F), new Vector3f(1.0F, 1.0F, 0.0F), new Vector3f(1.0F, -1.0F, 0.0F)};
        float size = 1 / 48F;

        for (int k = 0; k < 4; ++k)
        {
            var vector3f2 = vector3fs[k];
            vector3f2.rotate(quaternion2);
            vector3f2.mul(size);
            vector3f2.add(f, g, h);
        }

        TextureAtlasSprite sprite = spriteProvider.get(age, lifetime);
        
        this.rCol = 0.21F;
        this.gCol = 0.4F;
        this.bCol = 0.33F;

        float l = sprite.getU0();
        float m = sprite.getU1();
        float n = sprite.getV0();
        float o = sprite.getV1();
        int light = 15728880;
        vertexConsumer.vertex(vector3fs[0].x(), vector3fs[0].y(), vector3fs[0].z()).uv(m, o).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        vertexConsumer.vertex(vector3fs[1].x(), vector3fs[1].y(), vector3fs[1].z()).uv(m, n).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        vertexConsumer.vertex(vector3fs[2].x(), vector3fs[2].y(), vector3fs[2].z()).uv(l, n).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        vertexConsumer.vertex(vector3fs[3].x(), vector3fs[3].y(), vector3fs[3].z()).uv(l, o).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
    }

    @Override
    public ParticleRenderType getRenderType()
    {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
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

        move(xd * speed, yd * speed, zd * speed);
        speed *= 0.98D;
    }

    private boolean isNearDestination()
    {
        return abs(x - destinationX) < 0.05 && abs(y - destinationY) < 0.05 && abs(z - destinationZ) < 0.05;
    }
}
