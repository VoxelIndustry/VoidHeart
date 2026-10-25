package net.voxelindustry.voidheart.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class PortalFrameParticle extends Particle
{
    private final FabricSpriteProvider spriteProvider;

    private final Direction direction;
    private final int       width;
    private final int       height;

    public PortalFrameParticle(ClientLevel world,
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
        lifetime = 40;
    }

    @Override
    public void render(VertexConsumer vertexConsumer, Camera camera, float tickDelta)
    {
        Vec3 vec3d = camera.getPosition();
        float f = (float) (Mth.lerp(tickDelta, xo, x) - vec3d.x());
        float g = (float) (Mth.lerp(tickDelta, yo, y) - vec3d.y());
        float h = (float) (Mth.lerp(tickDelta, zo, z) - vec3d.z());

        var vecUp = Direction.UP.step();
        var horizontalOffset = direction.step();
        horizontalOffset.cross(vecUp);

        var verticalOffset = new Vector3f(horizontalOffset);
        verticalOffset.cross(direction.step());
        verticalOffset.mul(Mth.lerp(age / (float) lifetime, height / 2F, height / 2F - 0.5F));

        horizontalOffset.mul(Mth.lerp(age / (float) lifetime, width / 2F, width / 2F - 0.5F));

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

        TextureAtlasSprite sprite = spriteProvider.get(0, 1);

        this.rCol = 0.21F;
        this.gCol = 0.4F;
        this.bCol = 0.33F;

        float l = sprite.getU0();
        float m = sprite.getU1();
        float n = sprite.getV0();
        float o = sprite.getV1();
        int light = 15728880;
        vertexConsumer.vertex(rightBottom.x(), rightBottom.y(), rightBottom.z())
                .uv(l, o)
                .color(this.rCol, this.gCol, this.bCol, this.alpha)
                .uv2(light)
                .endVertex();
        vertexConsumer.vertex(leftBottom.x(), leftBottom.y(), leftBottom.z())
                .uv(l, n)
                .color(this.rCol, this.gCol, this.bCol, this.alpha)
                .uv2(light)
                .endVertex();
        vertexConsumer.vertex(leftTop.x(), leftTop.y(), leftTop.z())
                .uv(m, o)
                .color(this.rCol, this.gCol, this.bCol, this.alpha)
                .uv2(light)
                .endVertex();
        vertexConsumer.vertex(rightTop.x(), rightTop.y(), rightTop.z())
                .uv(m, n)
                .color(this.rCol, this.gCol, this.bCol, this.alpha)
                .uv2(light)
                .endVertex();

        vertexConsumer.vertex(rightBottom.x(), rightBottom.y(), rightBottom.z()).uv(l, o).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        vertexConsumer.vertex(leftBottom.x(), leftBottom.y(), leftBottom.z()).uv(l, n).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        vertexConsumer.vertex(leftTop.x(), leftTop.y(), leftTop.z()).uv(m, o).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        vertexConsumer.vertex(rightTop.x(), rightTop.y(), rightTop.z()).uv(m, n).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();

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

        this.setAlpha(Mth.lerp(age / (float) lifetime, 0.2F, 0F));

        if (age++ >= lifetime)
            remove();
    }
}
