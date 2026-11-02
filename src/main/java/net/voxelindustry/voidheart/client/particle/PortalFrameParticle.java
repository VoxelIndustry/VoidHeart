package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class PortalFrameParticle extends GeometryParticle
{
    private final FabricSpriteSet spriteProvider;

    private final Direction direction;
    private final int       width;
    private final int       height;

    public PortalFrameParticle(ClientLevel world,
                               double x,
                               double y,
                               double z,
                               FabricSpriteSet spriteProvider,
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
        Vec3 vec3d = camera.position();
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
        vertexConsumer.addVertex(rightBottom.x(), rightBottom.y(), rightBottom.z())
                .setUv(l, o)
                .setColor(this.rCol, this.gCol, this.bCol, this.alpha)
                .setLight(light)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setNormal(0, 1, 0);
        vertexConsumer.addVertex(leftBottom.x(), leftBottom.y(), leftBottom.z())
                .setUv(l, n)
                .setColor(this.rCol, this.gCol, this.bCol, this.alpha)
                .setLight(light)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setNormal(0, 1, 0);
        vertexConsumer.addVertex(leftTop.x(), leftTop.y(), leftTop.z())
                .setUv(m, o)
                .setColor(this.rCol, this.gCol, this.bCol, this.alpha)
                .setLight(light)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setNormal(0, 1, 0);
        vertexConsumer.addVertex(rightTop.x(), rightTop.y(), rightTop.z())
                .setUv(m, n)
                .setColor(this.rCol, this.gCol, this.bCol, this.alpha)
                .setLight(light)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setNormal(0, 1, 0);
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
