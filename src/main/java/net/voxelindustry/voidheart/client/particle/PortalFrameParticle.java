package net.voxelindustry.voidheart.client.particle;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

public class PortalFrameParticle extends Particle
{
    private final FabricSpriteProvider spriteProvider;

    private final Direction direction;
    private final int       width;
    private final int       height;

    public PortalFrameParticle(ClientWorld world,
                               double x,
                               double y,
                               double z,
                               FabricSpriteProvider spriteProvider,
                               Direction direction,
                               int width,
                               int height)
    {
        super(world, x, y, z);
        this.spriteProvider = spriteProvider;
        this.direction = direction;
        this.width = width;
        this.height = height;
        this.setAlpha(0.2F);
        maxAge = 40;
    }

    @Override
    public void buildGeometry(VertexConsumer vertexConsumer, Camera camera, float tickDelta)
    {
        Vec3d vec3d = camera.getPos();
        float f = (float) (MathHelper.lerp(tickDelta, prevPosX, x) - vec3d.getX());
        float g = (float) (MathHelper.lerp(tickDelta, prevPosY, y) - vec3d.getY());
        float h = (float) (MathHelper.lerp(tickDelta, prevPosZ, z) - vec3d.getZ());

        var vecUp = Direction.UP.getUnitVector();
        var horizontalOffset = direction.getUnitVector();
        horizontalOffset.cross(vecUp);

        var verticalOffset = new Vector3f(horizontalOffset);
        verticalOffset.cross(direction.getUnitVector());
        verticalOffset.mul(MathHelper.lerp(age / (float) maxAge, height / 2F, height / 2F - 0.5F));

        horizontalOffset.mul(MathHelper.lerp(age / (float) maxAge, width / 2F, width / 2F - 0.5F));

        var leftTop = new Vector3f(f, g, h);
        leftTop.sub(horizontalOffset);
        leftTop.add(verticalOffset);

        var rightTop = new Vector3f(f, g, h);
        rightTop.add(horizontalOffset);
        rightTop.add(verticalOffset);

        var leftBottom = new Vector3f(f, g, h);
        leftBottom.sub(horizontalOffset);
        leftBottom.sub(verticalOffset);

        var rightBottom = new Vector3f(f, g, h);
        rightBottom.add(horizontalOffset);
        rightBottom.sub(verticalOffset);

        Sprite sprite = spriteProvider.getSprite(0, 1);

        this.red = 0.21F;
        this.green = 0.4F;
        this.blue = 0.33F;

        float l = sprite.getMinU();
        float m = sprite.getMaxU();
        float n = sprite.getMinV();
        float o = sprite.getMaxV();
        int light = 15728880;
        vertexConsumer.vertex(rightBottom.x(), rightBottom.y(), rightBottom.z())
                .texture(l, o)
                .color(this.red, this.green, this.blue, this.alpha)
                .light(light)
                .next();
        vertexConsumer.vertex(leftBottom.x(), leftBottom.y(), leftBottom.z())
                .texture(l, n)
                .color(this.red, this.green, this.blue, this.alpha)
                .light(light)
                .next();
        vertexConsumer.vertex(leftTop.x(), leftTop.y(), leftTop.z())
                .texture(m, o)
                .color(this.red, this.green, this.blue, this.alpha)
                .light(light)
                .next();
        vertexConsumer.vertex(rightTop.x(), rightTop.y(), rightTop.z())
                .texture(m, n)
                .color(this.red, this.green, this.blue, this.alpha)
                .light(light)
                .next();

        vertexConsumer.vertex(rightBottom.x(), rightBottom.y(), rightBottom.z()).texture(l, o).color(this.red, this.green, this.blue, this.alpha).light(light).next();
        vertexConsumer.vertex(leftBottom.x(), leftBottom.y(), leftBottom.z()).texture(l, n).color(this.red, this.green, this.blue, this.alpha).light(light).next();
        vertexConsumer.vertex(leftTop.x(), leftTop.y(), leftTop.z()).texture(m, o).color(this.red, this.green, this.blue, this.alpha).light(light).next();
        vertexConsumer.vertex(rightTop.x(), rightTop.y(), rightTop.z()).texture(m, n).color(this.red, this.green, this.blue, this.alpha).light(light).next();

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

        this.setAlpha(MathHelper.lerp(age / (float) maxAge, 0.2F, 0F));

        if (age++ >= maxAge)
            markDead();
    }
}
