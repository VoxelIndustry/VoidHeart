package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.render.state.CrawlerRenderState;
import net.voxelindustry.voidheart.common.content.mob.CrawlerMonster;
import org.joml.Vector3f;

/**
 * Turns the model onto the surface the mob holds, easing from one face to the next. Its feet stay on the matching side of the collision box,
 * which cannot turn with it.
 */
public abstract class CrawlerRender<T extends CrawlerMonster, S extends CrawlerRenderState, M extends EntityModel<? super S>> extends MobRenderer<T, S, M>
{
    protected CrawlerRender(EntityRendererProvider.Context context, M model, float shadow)
    {
        super(context, model, shadow);
    }

    @Override
    public void extractRenderState(T mob, S state, float partialTicks)
    {
        super.extractRenderState(mob, state, partialTicks);

        var from = mob.previousSurface;
        var to = mob.surface();
        float blend = mob.surfaceBlend >= CrawlerMonster.SURFACE_BLEND_TICKS ? 1 : Mth.clamp((mob.surfaceBlend + partialTicks) / CrawlerMonster.SURFACE_BLEND_TICKS, 0, 1);
        float width = mob.getBbWidth();
        float height = mob.getBbHeight();

        state.surfaceRotation.set(from.getRotation()).slerp(to.getRotation(), blend);
        feetOffset(from, width, height, state.surfaceOffset).lerp(feetOffset(to, width, height, new Vector3f()), blend);
        state.clinging = to != Direction.UP;
        state.aggressive = mob.isAggressive();
        state.attack = mob.getSwingAnimation(partialTicks);
        state.stride = Mth.lerp(partialTicks, mob.strideO, mob.stride);
        state.strideAmount = mob.strideAmount;
    }

    // From the bottom center of the collision box to the middle of its face on the side of the surface
    private static Vector3f feetOffset(Direction surface, float width, float height, Vector3f out)
    {
        float extent = surface.getAxis() == Direction.Axis.Y ? height / 2 : width / 2;
        return out.set(-surface.getStepX() * extent, height / 2 - surface.getStepY() * extent, -surface.getStepZ() * extent);
    }

    @Override
    protected void setupRotations(S state, PoseStack poseStack, float bodyRot, float entityScale)
    {
        poseStack.translate(state.surfaceOffset.x, state.surfaceOffset.y, state.surfaceOffset.z);
        poseStack.rotate(state.surfaceRotation);
        super.setupRotations(state, poseStack, bodyRot, entityScale);
    }

    @Override
    protected float getShadowRadius(S state)
    {
        return state.clinging ? 0 : super.getShadowRadius(state);
    }
}
