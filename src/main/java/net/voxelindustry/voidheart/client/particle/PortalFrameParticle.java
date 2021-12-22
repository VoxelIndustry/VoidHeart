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
import net.minecraft.util.math.Vec3f;

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
        setColorAlpha(0.2F);
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

        var verticalOffset = horizontalOffset.copy();
        verticalOffset.cross(direction.getUnitVector());
        verticalOffset.scale(MathHelper.lerp(age / (float) maxAge, height / 2F, height / 2F - 0.5F));

        horizontalOffset.scale(MathHelper.lerp(age / (float) maxAge, width / 2F, width / 2F - 0.5F));

        var leftTop = new Vec3f(f, g, h);
        leftTop.subtract(horizontalOffset);
        leftTop.add(verticalOffset);

        var rightTop = new Vec3f(f, g, h);
        rightTop.add(horizontalOffset);
        rightTop.add(verticalOffset);

        var leftBottom = new Vec3f(f, g, h);
        leftBottom.subtract(horizontalOffset);
        leftBottom.subtract(verticalOffset);

        var rightBottom = new Vec3f(f, g, h);
        rightBottom.add(horizontalOffset);
        rightBottom.subtract(verticalOffset);

        Sprite sprite = spriteProvider.getSprite(0, 1);

        colorRed = 0.21F;
        colorGreen = 0.4F;
        colorBlue = 0.33F;

        float l = sprite.getMinU();
        float m = sprite.getMaxU();
        float n = sprite.getMinV();
        float o = sprite.getMaxV();
        int light = 15728880;
        vertexConsumer.vertex(rightBottom.getX(), rightBottom.getY(), rightBottom.getZ())
                .texture(l, o)
                .color(colorRed, colorGreen, colorBlue, colorAlpha)
                .light(light)
                .next();
        vertexConsumer.vertex(leftBottom.getX(), leftBottom.getY(), leftBottom.getZ())
                .texture(l, n)
                .color(colorRed, colorGreen, colorBlue, colorAlpha)
                .light(light)
                .next();
        vertexConsumer.vertex(leftTop.getX(), leftTop.getY(), leftTop.getZ())
                .texture(m, o)
                .color(colorRed, colorGreen, colorBlue, colorAlpha)
                .light(light)
                .next();
        vertexConsumer.vertex(rightTop.getX(), rightTop.getY(), rightTop.getZ())
                .texture(m, n)
                .color(colorRed, colorGreen, colorBlue, colorAlpha)
                .light(light)
                .next();

        vertexConsumer.vertex(rightBottom.getX(), rightBottom.getY(), rightBottom.getZ()).texture(l, o).color(colorRed, colorGreen, colorBlue, colorAlpha).light(light).next();
        vertexConsumer.vertex(leftBottom.getX(), leftBottom.getY(), leftBottom.getZ()).texture(l, n).color(colorRed, colorGreen, colorBlue, colorAlpha).light(light).next();
        vertexConsumer.vertex(leftTop.getX(), leftTop.getY(), leftTop.getZ()).texture(m, o).color(colorRed, colorGreen, colorBlue, colorAlpha).light(light).next();
        vertexConsumer.vertex(rightTop.getX(), rightTop.getY(), rightTop.getZ()).texture(m, n).color(colorRed, colorGreen, colorBlue, colorAlpha).light(light).next();

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

        setColorAlpha(MathHelper.lerp(age / (float) maxAge, 0.2F, 0F));

        if (age++ >= maxAge)
            markDead();
    }
}
