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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousRelayTile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Beams carrying essence through the relay: from each collector or storage it draws from into its core, and from its
 * core into the next relay, storage, or the collector a Shatter Forge feeds.
 */
public class RavenousRelayRender implements BlockEntityRenderer<RavenousRelayTile, RavenousRelayRender.State>
{
    private static final Vec3 CENTER = new Vec3(0.5, 0.5, 0.5);

    // Distance from a block center to where a beam leaves or enters it
    private static final float RELAY_INSET     = 0.15F;
    private static final float COLLECTOR_INSET = 0.2F;
    private static final float STORAGE_INSET   = 0.45F;

    public static class State extends BlockEntityRenderState
    {
        public final List<RelayBeamRender.Beam> beams = new ArrayList<>();
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

        var level = relay.getLevel();
        state.time = level.getGameTime() + partialTicks;
        state.beams.clear();

        var origin = relay.getBlockPos();
        var color = VoidBeamRender.aspectColor(relay.getDisplayedAspect());
        relay.getIncomingStrands(partialTicks).forEach((source, link) ->
                state.beams.add(beam(relative(source, origin), inset(level, source), CENTER, RELAY_INSET, link, color)));
        relay.getOutgoingStrands(partialTicks).forEach((target, link) ->
                state.beams.add(beam(CENTER, RELAY_INSET, relative(target, origin), inset(level, target), link, color)));
    }

    private static RelayBeamRender.Beam beam(Vec3 source, float sourceInset, Vec3 target, float targetInset, RavenousRelayTile.Link link, int color)
    {
        var direction = target.subtract(source).normalize();
        return new RelayBeamRender.Beam(source.add(direction.scale(sourceInset)), target.subtract(direction.scale(targetInset)),
                link.growth(), link.active(), color);
    }

    private static float inset(Level level, BlockPos pos)
    {
        return switch (level.getBlockEntity(pos))
        {
            case RavenousRelayTile ignored -> RELAY_INSET;
            case RavenousCollectorTile ignored -> COLLECTOR_INSET;
            case null, default -> STORAGE_INSET;
        };
    }

    private static Vec3 relative(BlockPos pos, BlockPos origin)
    {
        return new Vec3(pos.getX() - origin.getX() + 0.5, pos.getY() - origin.getY() + 0.5, pos.getZ() - origin.getZ() + 0.5);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.beams.isEmpty())
            return;

        collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(VoidBeamRender.VEIL_TEXTURE, true), (pose, buffer) ->
        {
            for (var beam : state.beams)
                RelayBeamRender.render(pose, buffer, beam, state.time);
        });
    }

    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }
}
