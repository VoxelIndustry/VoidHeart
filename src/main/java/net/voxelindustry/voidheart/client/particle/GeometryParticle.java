package net.voxelindustry.voidheart.client.particle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;

import java.util.ArrayList;

/**
 * Particle writing its own vertices, for shapes that are not a single camera facing square.
 * Vertices are expressed relative to the camera and must fill the entity vertex format.
 */
public abstract class GeometryParticle extends Particle
{
    public static final ParticleRenderType GEOMETRY = new ParticleRenderType("voidheart:geometry", "VHG");

    private static final RenderType RENDER_TYPE = RenderTypes.entityTranslucent(TextureAtlas.LOCATION_PARTICLES);

    protected float rCol  = 1.0F;
    protected float gCol  = 1.0F;
    protected float bCol  = 1.0F;
    protected float alpha = 1.0F;

    protected GeometryParticle(ClientLevel level, double x, double y, double z)
    {
        super(level, x, y, z);
    }

    protected GeometryParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za)
    {
        super(level, x, y, z, xa, ya, za);
    }

    protected void setAlpha(float alpha)
    {
        this.alpha = alpha;
    }

    public abstract void render(VertexConsumer buffer, Camera camera, float partialTick);

    @Override
    public ParticleRenderType getGroup()
    {
        return GEOMETRY;
    }

    boolean isVisible(Frustum frustum)
    {
        return frustum.pointInFrustum(x, y, z);
    }

    public static class Group extends ParticleGroup<GeometryParticle>
    {
        public Group(ParticleEngine engine)
        {
            super(engine);
        }

        @Override
        public ParticleGroupRenderState extractRenderState(Frustum frustum, Camera camera, float partialTick)
        {
            var visible = new ArrayList<GeometryParticle>();
            for (var particle : particles)
            {
                if (particle.isVisible(frustum))
                    visible.add(particle);
            }

            return (collector, cameraState) ->
            {
                if (!visible.isEmpty())
                    collector.submitCustomGeometry(new PoseStack(), RENDER_TYPE, (pose, buffer) -> visible.forEach(particle -> particle.render(buffer, camera, partialTick)));
            };
        }
    }
}
