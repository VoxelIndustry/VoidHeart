package net.voxelindustry.voidheart.client.particle;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.Direction.AxisDirection;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;

import java.util.stream.IntStream;

public class ShatterForgeRuneParticle extends Particle
{
    private final FabricSpriteProvider spriteProvider;

    private final Axis axis;
    private final int[] spriteIndices;

    public ShatterForgeRuneParticle(ClientWorld world,
                                    double x,
                                    double y,
                                    double z,
                                    FabricSpriteProvider spriteProvider,
                                    Axis axis)
    {
        super(world, x, y, z);
        this.spriteProvider = spriteProvider;
        this.axis = axis;
        this.setAlpha(0.6F);
        maxAge = 20;

        spriteIndices = IntStream.generate(() -> this.random.nextInt(12)).limit(14).toArray();
    }

    @Override
    public void buildGeometry(VertexConsumer vertexConsumer, Camera camera, float tickDelta)
    {
        var cameraPos = camera.getPos();
        float posX = (float) (MathHelper.lerp(tickDelta, prevPosX, x) - cameraPos.getX());
        float posY = (float) (MathHelper.lerp(tickDelta, prevPosY, y) - cameraPos.getY()) - 1.31F;
        float posZ = (float) (MathHelper.lerp(tickDelta, prevPosZ, z) - cameraPos.getZ());

        var vecUp = Direction.UP.getUnitVector();
        var horizontalOffset = Direction.from(axis, AxisDirection.POSITIVE).getUnitVector();
        horizontalOffset.cross(vecUp);

        var verticalOffset = new Vector3f(horizontalOffset);
        verticalOffset.cross(Direction.from(axis, AxisDirection.POSITIVE).getUnitVector());
        verticalOffset.mul(0.1875F / 2);
        horizontalOffset.mul(0.1875F / 2);

        var perpendicular = axis == Axis.X ? Direction.NORTH : Direction.WEST;
        var offset = perpendicular.getUnitVector().mul(1.35F);

        var leftTop = new Vector3f(posX, posY, posZ);
        leftTop.sub(horizontalOffset);
        leftTop.add(verticalOffset);

        var rightTop = new Vector3f(posX, posY, posZ);
        rightTop.add(horizontalOffset);
        rightTop.add(verticalOffset);

        var leftBottom = new Vector3f(posX, posY, posZ);
        leftBottom.sub(horizontalOffset);
        leftBottom.sub(verticalOffset);

        var rightBottom = new Vector3f(posX, posY, posZ);
        rightBottom.add(horizontalOffset);
        rightBottom.sub(verticalOffset);

        leftTop.add(offset);
        rightTop.add(offset);
        leftBottom.add(offset);
        rightBottom.add(offset);

        this.red = 0.309F;
        this.green = 0.784F;
        this.blue = 0.415F;

        var light = 15728880;

        for (int i = 0; i < 7; i++)
        {
            var offsetY = i * (7 / 16F);

            var sprite = spriteProvider.getSprite(spriteIndices[i], 12);
            var uMin = sprite.getMinU();
            var uMax = sprite.getMaxU();
            var vMin = sprite.getMinV();
            var vMax = sprite.getMaxV();

            vertexConsumer.vertex(leftTop.x(), leftTop.y() + offsetY, leftTop.z())
                    .texture(uMin, vMin)
                    .color(this.red, this.green, this.blue, this.alpha)
                    .light(light)
                    .next();
            vertexConsumer.vertex(rightTop.x(), rightTop.y() + offsetY, rightTop.z())
                    .texture(uMax, vMin)
                    .color(this.red, this.green, this.blue, this.alpha)
                    .light(light)
                    .next();
            vertexConsumer.vertex(rightBottom.x(), rightBottom.y() + offsetY, rightBottom.z())
                    .texture(uMax, vMax)
                    .color(this.red, this.green, this.blue, this.alpha)
                    .light(light)
                    .next();
            vertexConsumer.vertex(leftBottom.x(), leftBottom.y() + offsetY, leftBottom.z())
                    .texture(uMin, vMax)
                    .color(this.red, this.green, this.blue, this.alpha)
                    .light(light)
                    .next();

            vertexConsumer.vertex(leftBottom.x(), leftBottom.y() + offsetY, leftBottom.z())
                    .texture(uMin, vMax)
                    .color(this.red, this.green, this.blue, this.alpha)
                    .light(light)
                    .next();
            vertexConsumer.vertex(rightBottom.x(), rightBottom.y() + offsetY, rightBottom.z())
                    .texture(uMax, vMax)
                    .color(this.red, this.green, this.blue, this.alpha)
                    .light(light)
                    .next();
            vertexConsumer.vertex(rightTop.x(), rightTop.y() + offsetY, rightTop.z())
                    .texture(uMax, vMin)
                    .color(this.red, this.green, this.blue, this.alpha)
                    .light(light)
                    .next();
            vertexConsumer.vertex(leftTop.x(), leftTop.y() + offsetY, leftTop.z())
                    .texture(uMin, vMin)
                    .color(this.red, this.green, this.blue, this.alpha)
                    .light(light)
                    .next();
        }

        offset = perpendicular.getUnitVector().mul(-2.65F);
        leftTop.add(offset);
        rightTop.add(offset);
        leftBottom.add(offset);
        rightBottom.add(offset);

        for (int i = 0; i < 7; i++)
        {
            var offsetY = i * (7 / 16F);

            var sprite = spriteProvider.getSprite(spriteIndices[i + 7], 29);
            var uMin = sprite.getMinU();
            var uMax = sprite.getMaxU();
            var vMin = sprite.getMinV();
            var vMax = sprite.getMaxV();

            vertexConsumer.vertex(leftTop.x(), leftTop.y() + offsetY, leftTop.z())
                    .texture(uMin, vMin)
                    .color(this.red, this.green, this.blue, this.alpha)
                    .light(light)
                    .next();
            vertexConsumer.vertex(rightTop.x(), rightTop.y() + offsetY, rightTop.z())
                    .texture(uMax, vMin)
                    .color(this.red, this.green, this.blue, this.alpha)
                    .light(light)
                    .next();
            vertexConsumer.vertex(rightBottom.x(), rightBottom.y() + offsetY, rightBottom.z())
                    .texture(uMax, vMax)
                    .color(this.red, this.green, this.blue, this.alpha)
                    .light(light)
                    .next();
            vertexConsumer.vertex(leftBottom.x(), leftBottom.y() + offsetY, leftBottom.z())
                    .texture(uMin, vMax)
                    .color(this.red, this.green, this.blue, this.alpha)
                    .light(light)
                    .next();

            vertexConsumer.vertex(leftBottom.x(), leftBottom.y() + offsetY, leftBottom.z())
                    .texture(uMin, vMax)
                    .color(this.red, this.green, this.blue, this.alpha)
                    .light(light)
                    .next();
            vertexConsumer.vertex(rightBottom.x(), rightBottom.y() + offsetY, rightBottom.z())
                    .texture(uMax, vMax)
                    .color(this.red, this.green, this.blue, this.alpha)
                    .light(light)
                    .next();
            vertexConsumer.vertex(rightTop.x(), rightTop.y() + offsetY, rightTop.z())
                    .texture(uMax, vMin)
                    .color(this.red, this.green, this.blue, this.alpha)
                    .light(light)
                    .next();
            vertexConsumer.vertex(leftTop.x(), leftTop.y() + offsetY, leftTop.z())
                    .texture(uMin, vMin)
                    .color(this.red, this.green, this.blue, this.alpha)
                    .light(light)
                    .next();
        }
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

        x += (1 / 256F) - random.nextFloat() / 128;
        y += (1 / 256F) - random.nextFloat() / 128;
        z += (1 / 256F) - random.nextFloat() / 128;

        //  this.setAlpha(MathHelper.lerp(age / (float) maxAge, 0.2F, 0F));

        if (age++ >= maxAge)
            markDead();
    }
}
