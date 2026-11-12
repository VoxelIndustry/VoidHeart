package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.underway.security.Tripwire;
import org.jspecify.annotations.Nullable;

/**
 * A tripwire's emitter is a block on the wall. This draws its beam, see {@link SecurityBeams}: lit while it is armed, and dark for the moment after it was broken, which is how whoever broke it knows.
 */
public class TripwireRender extends EntityRenderer<Tripwire, TripwireRender.State>
{
    public static class State extends EntityRenderState
    {
        /** The ends of its beam, from its position, or null while it is dark. */
        public @Nullable Vec3 from;
        public Vec3 to = Vec3.ZERO;
    }

    public TripwireRender(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(Tripwire wire, State state, float partialTicks)
    {
        super.extractRenderState(wire, state, partialTicks);
        boolean lit = wire.isArmed() && wire.reach() > 0;
        state.from = lit ? wire.lens().subtract(wire.position()) : null;
        state.to = wire.end().subtract(wire.position());
    }

    /** Its beam reaches far past its own small box. */
    @Override
    protected AABB getBoundingBoxForCulling(Tripwire wire, float partialTicks)
    {
        return wire.getBoundingBox().inflate(wire.reach());
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.from != null)
            SecurityBeams.submitWire(poseStack, collector, state.from, state.to, state.ageInTicks);
        super.submit(state, poseStack, collector, camera);
    }
}
