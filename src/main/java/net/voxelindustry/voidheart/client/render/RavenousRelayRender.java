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
import net.voxelindustry.voidheart.client.render.energy.pearl.HangingPearlRender;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import net.voxelindustry.voidheart.common.content.crystal.CrystalCradleBlock;
import net.voxelindustry.voidheart.common.content.crystal.CrystalCradleTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousRelayTile;
import net.voxelindustry.voidheart.common.energy.pearl.HangingPearlTile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Beams carrying essence through the relay: from each collector or storage it draws from into its core, and from its
 * core into the next relay, storage, or the collector a Shatter Forge feeds. Into a hung pearl the beam splits into threads, so the pearl is seen
 * drinking it in rather than the beam stopping in the air beside it.
 */
public class RavenousRelayRender implements BlockEntityRenderer<RavenousRelayTile, RavenousRelayRender.State>
{
    private static final Vec3 CENTER = new Vec3(0.5, 0.5, 0.5);

    // Distance from a block center to where a beam leaves or enters it
    private static final float RELAY_INSET     = 0.15F;
    private static final float COLLECTOR_INSET = 0.2F;
    private static final float STORAGE_INSET   = 0.45F;
    private static final double CRADLE_FLOOR   = (CrystalCradleBlock.FLOOR + 0.5) / 16;
    /** Threads the beam splits into to sink into a hung pearl. */
    private static final int   PEARL_THREADS   = 5;
    // Where the catalyst floats over an altar, and how far around it the threads sink in
    private static final double CATALYST_HEIGHT  = 1.25;
    private static final float  CATALYST_REACH   = 0.22F;
    private static final int    CATALYST_THREADS = 4;

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
        {
            if (level.getBlockEntity(target) instanceof HangingPearlTile pearl)
                state.beams.add(intoPearl(pearl, origin, link, color));
            else if (level.getBlockEntity(target) instanceof VoidAltarTile)
                state.beams.add(intoCatalyst(target, origin, link, color));
            // A cradle is a low basin, so the beam sinks into its floor rather than stopping in the air above it
            else if (level.getBlockEntity(target) instanceof CrystalCradleTile)
                state.beams.add(beam(CENTER, RELAY_INSET, relative(target, origin).add(0, CRADLE_FLOOR - 0.5, 0), 0, link, color));
            else
                state.beams.add(beam(CENTER, RELAY_INSET, relative(target, origin), inset(level, target), link, color));
        });
    }

    /**
     * A hung pearl takes its Fiery in through threads sinking into it from around, the beam aimed at where it hangs in its block.
     */
    private static RelayBeamRender.Beam intoPearl(HangingPearlTile pearl, BlockPos origin, RavenousRelayTile.Link link, int color)
    {
        var share = pearl.growth().share();
        var hung = HangingPearlRender.center(share);
        var pos = pearl.getBlockPos();
        var center = new Vec3(pos.getX() - origin.getX() + hung.x, pos.getY() - origin.getY() + hung.y, pos.getZ() - origin.getZ() + hung.z);
        var source = CENTER.add(center.subtract(CENTER).normalize().scale(RELAY_INSET));
        return new RelayBeamRender.Beam(source, center, link.growth(), link.active(), color,
                new RelayBeamRender.Intake(HangingPearlRender.reach(share), PEARL_THREADS, pearl.seed() ^ origin.asLong()));
    }

    /**
     * An altar drinks a rite's essence through the item floating over it, so the beam splits into threads that sink into the catalyst.
     */
    private static RelayBeamRender.Beam intoCatalyst(BlockPos altar, BlockPos origin, RavenousRelayTile.Link link, int color)
    {
        var center = new Vec3(altar.getX() - origin.getX() + 0.5, altar.getY() - origin.getY() + CATALYST_HEIGHT, altar.getZ() - origin.getZ() + 0.5);
        var source = CENTER.add(center.subtract(CENTER).normalize().scale(RELAY_INSET));
        return new RelayBeamRender.Beam(source, center, link.growth(), link.active(), color,
                new RelayBeamRender.Intake(CATALYST_REACH, CATALYST_THREADS, altar.asLong() ^ origin.asLong()));
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
