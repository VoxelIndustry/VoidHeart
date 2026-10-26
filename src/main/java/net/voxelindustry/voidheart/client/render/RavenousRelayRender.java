package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.essence.RavenousRelayTile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Strands carrying essence through the relay: from each collector it drains into its core, and from its core into the
 * next relay or storage.
 */
public class RavenousRelayRender implements BlockEntityRenderer<RavenousRelayTile, RavenousRelayRender.State>
{
    private static final int  STRANDS = 3;
    private static final Vec3 CENTER  = new Vec3(0.5, 0.5, 0.5);

    public static class State extends BlockEntityRenderState
    {
        public final List<EssenceStrandRender.Bundle> bundles = new ArrayList<>();
        public float time;
    }

    public RavenousRelayRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(RavenousRelayTile relay, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(relay, state, partialTicks, cameraPosition, breakProgress);

        state.time = relay.getLevel().getGameTime() + partialTicks;
        state.bundles.clear();

        var origin = relay.getBlockPos();
        var color = VoidBeamRender.aspectColor(relay.getDisplayedAspect());
        relay.getIncomingStrands(partialTicks).forEach((source, growth) ->
        {
            if (growth > 0)
                state.bundles.add(new EssenceStrandRender.Bundle(relative(source, origin), CENTER, 0.2F, 0.1F, 0.05F, growth, color,
                        source.asLong() ^ origin.asLong(), STRANDS));
        });
        relay.getOutgoingStrands(partialTicks).forEach((target, growth) ->
        {
            if (growth > 0)
                state.bundles.add(new EssenceStrandRender.Bundle(CENTER, relative(target, origin), 0.2F, 0.08F, 0.12F, growth, color,
                        target.asLong() * 31 + origin.asLong(), STRANDS));
        });
    }

    private static Vec3 relative(BlockPos pos, BlockPos origin)
    {
        return new Vec3(pos.getX() - origin.getX() + 0.5, pos.getY() - origin.getY() + 0.5, pos.getZ() - origin.getZ() + 0.5);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.bundles.isEmpty())
            return;

        collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(VoidBeamRender.VEIL_TEXTURE, true), (pose, buffer) ->
        {
            for (var bundle : state.bundles)
                EssenceStrandRender.render(pose, buffer, bundle, state.time);
        });
    }

    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }
}
