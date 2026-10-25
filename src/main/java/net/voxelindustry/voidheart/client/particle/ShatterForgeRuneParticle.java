package net.voxelindustry.voidheart.client.particle;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.stream.IntStream;

public class ShatterForgeRuneParticle extends Particle
{
    private final FabricSpriteProvider spriteProvider;

    private final Axis axis;
    private final int[] spriteIndices;

    public ShatterForgeRuneParticle(ClientLevel world,
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
        lifetime = 20;

        spriteIndices = IntStream.generate(() -> this.random.nextInt(12)).limit(14).toArray();
    }

    @Override
    public void render(VertexConsumer vertexConsumer, Camera camera, float tickDelta)
    {
        var cameraPos = camera.getPosition();
        float posX = (float) (Mth.lerp(tickDelta, xo, x) - cameraPos.x());
        float posY = (float) (Mth.lerp(tickDelta, yo, y) - cameraPos.y()) - 1.31F;
        float posZ = (float) (Mth.lerp(tickDelta, zo, z) - cameraPos.z());

        var vecUp = Direction.UP.step();
        var horizontalOffset = Direction.fromAxisAndDirection(axis, AxisDirection.POSITIVE).step();
        horizontalOffset.cross(vecUp);

        var verticalOffset = new Vector3f(horizontalOffset);
        verticalOffset.cross(Direction.fromAxisAndDirection(axis, AxisDirection.POSITIVE).step());
        verticalOffset.mul(0.1875F / 2);
        horizontalOffset.mul(0.1875F / 2);

        var perpendicular = axis == Axis.X ? Direction.NORTH : Direction.WEST;
        var offset = perpendicular.step().mul(1.35F);

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

        this.rCol = 0.309F;
        this.gCol = 0.784F;
        this.bCol = 0.415F;

        var light = 15728880;

        for (int i = 0; i < 7; i++)
        {
            var offsetY = i * (7 / 16F);

            var sprite = spriteProvider.get(spriteIndices[i], 12);
            var uMin = sprite.getU0();
            var uMax = sprite.getU1();
            var vMin = sprite.getV0();
            var vMax = sprite.getV1();

            vertexConsumer.vertex(leftTop.x(), leftTop.y() + offsetY, leftTop.z())
                    .uv(uMin, vMin)
                    .color(this.rCol, this.gCol, this.bCol, this.alpha)
                    .uv2(light)
                    .endVertex();
            vertexConsumer.vertex(rightTop.x(), rightTop.y() + offsetY, rightTop.z())
                    .uv(uMax, vMin)
                    .color(this.rCol, this.gCol, this.bCol, this.alpha)
                    .uv2(light)
                    .endVertex();
            vertexConsumer.vertex(rightBottom.x(), rightBottom.y() + offsetY, rightBottom.z())
                    .uv(uMax, vMax)
                    .color(this.rCol, this.gCol, this.bCol, this.alpha)
                    .uv2(light)
                    .endVertex();
            vertexConsumer.vertex(leftBottom.x(), leftBottom.y() + offsetY, leftBottom.z())
                    .uv(uMin, vMax)
                    .color(this.rCol, this.gCol, this.bCol, this.alpha)
                    .uv2(light)
                    .endVertex();

            vertexConsumer.vertex(leftBottom.x(), leftBottom.y() + offsetY, leftBottom.z())
                    .uv(uMin, vMax)
                    .color(this.rCol, this.gCol, this.bCol, this.alpha)
                    .uv2(light)
                    .endVertex();
            vertexConsumer.vertex(rightBottom.x(), rightBottom.y() + offsetY, rightBottom.z())
                    .uv(uMax, vMax)
                    .color(this.rCol, this.gCol, this.bCol, this.alpha)
                    .uv2(light)
                    .endVertex();
            vertexConsumer.vertex(rightTop.x(), rightTop.y() + offsetY, rightTop.z())
                    .uv(uMax, vMin)
                    .color(this.rCol, this.gCol, this.bCol, this.alpha)
                    .uv2(light)
                    .endVertex();
            vertexConsumer.vertex(leftTop.x(), leftTop.y() + offsetY, leftTop.z())
                    .uv(uMin, vMin)
                    .color(this.rCol, this.gCol, this.bCol, this.alpha)
                    .uv2(light)
                    .endVertex();
        }

        offset = perpendicular.step().mul(-2.65F);
        leftTop.add(offset);
        rightTop.add(offset);
        leftBottom.add(offset);
        rightBottom.add(offset);

        for (int i = 0; i < 7; i++)
        {
            var offsetY = i * (7 / 16F);

            var sprite = spriteProvider.get(spriteIndices[i + 7], 29);
            var uMin = sprite.getU0();
            var uMax = sprite.getU1();
            var vMin = sprite.getV0();
            var vMax = sprite.getV1();

            vertexConsumer.vertex(leftTop.x(), leftTop.y() + offsetY, leftTop.z())
                    .uv(uMin, vMin)
                    .color(this.rCol, this.gCol, this.bCol, this.alpha)
                    .uv2(light)
                    .endVertex();
            vertexConsumer.vertex(rightTop.x(), rightTop.y() + offsetY, rightTop.z())
                    .uv(uMax, vMin)
                    .color(this.rCol, this.gCol, this.bCol, this.alpha)
                    .uv2(light)
                    .endVertex();
            vertexConsumer.vertex(rightBottom.x(), rightBottom.y() + offsetY, rightBottom.z())
                    .uv(uMax, vMax)
                    .color(this.rCol, this.gCol, this.bCol, this.alpha)
                    .uv2(light)
                    .endVertex();
            vertexConsumer.vertex(leftBottom.x(), leftBottom.y() + offsetY, leftBottom.z())
                    .uv(uMin, vMax)
                    .color(this.rCol, this.gCol, this.bCol, this.alpha)
                    .uv2(light)
                    .endVertex();

            vertexConsumer.vertex(leftBottom.x(), leftBottom.y() + offsetY, leftBottom.z())
                    .uv(uMin, vMax)
                    .color(this.rCol, this.gCol, this.bCol, this.alpha)
                    .uv2(light)
                    .endVertex();
            vertexConsumer.vertex(rightBottom.x(), rightBottom.y() + offsetY, rightBottom.z())
                    .uv(uMax, vMax)
                    .color(this.rCol, this.gCol, this.bCol, this.alpha)
                    .uv2(light)
                    .endVertex();
            vertexConsumer.vertex(rightTop.x(), rightTop.y() + offsetY, rightTop.z())
                    .uv(uMax, vMin)
                    .color(this.rCol, this.gCol, this.bCol, this.alpha)
                    .uv2(light)
                    .endVertex();
            vertexConsumer.vertex(leftTop.x(), leftTop.y() + offsetY, leftTop.z())
                    .uv(uMin, vMin)
                    .color(this.rCol, this.gCol, this.bCol, this.alpha)
                    .uv2(light)
                    .endVertex();
        }
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

        x += (1 / 256F) - random.nextFloat() / 128;
        y += (1 / 256F) - random.nextFloat() / 128;
        z += (1 / 256F) - random.nextFloat() / 128;

        //  this.setAlpha(MathHelper.lerp(age / (float) maxAge, 0.2F, 0F));

        if (age++ >= lifetime)
            remove();
    }
}
