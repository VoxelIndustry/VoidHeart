package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.energy.engine.RavenousEngineRender;
import net.voxelindustry.voidheart.client.render.energy.fissure.VoidFissureRender;
import net.voxelindustry.voidheart.client.render.energy.reactor.ContravoidReactorRender;
import net.voxelindustry.voidheart.client.render.energy.spike.AzuriumSpikeRender;
import net.voxelindustry.voidheart.client.render.state.PermeableBarrierRenderState;
import net.voxelindustry.voidheart.client.scene.SceneFrame;
import net.voxelindustry.voidheart.common.energy.engine.EngineFuel;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorCycle;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Lends a scene the renderers the world uses for what a block model cannot show, each handed a state written from the scene's time instead of read from a tile.
 * None of them look at the context they are built with or at the camera they are given, so both stay empty here.
 * <p>
 * A state is made anew for every actor and frame: the geometry is built later in the frame, by which time a shared one would hold the last actor's values.
 */
final class SceneActorsRender
{
    private static final int FULL_BRIGHT = 15728880;

    private static final float SPIKE_LEAN       = 6;
    private static final float SPIKE_YAW        = 35;
    private static final float SPIKE_POINT      = 31 / 16F;
    private static final float SPIKE_FADE_TICKS = 10;
    private static final int   SPIKE_BANDS      = 3;
    private static final float BAND_TICKS       = 36;

    private static final float LEAP_TICKS = 14;
    private static final float JAG_TICKS  = 3;

    private static final float ENGINE_OPEN_TICKS = 12;
    private static final float ENGINE_PULSE      = 20;

    private static final float IGNITION_TICKS = 50;
    private static final float LIGHT_TICKS    = 14;
    private static final float FLARE_TICKS    = 16;

    // As CrackShape gives a fissure its length and half width from how wide it was torn
    private static final float TEAR_TICKS        = 20;
    private static final float CLOSE_TICKS       = 40;
    private static final float CRACK_HALF        = 0.05F;
    private static final float CRACK_HALF_GROWTH = 0.11F;

    private static final StaticGeoGhostRender COLLECTOR = new StaticGeoGhostRender(
            Identifier.fromNamespaceAndPath(MODID, "ravenous_collector"),
            Identifier.fromNamespaceAndPath(MODID, "textures/block/ravenous_collector.png"));

    private final AzuriumSpikeRender      spike   = new AzuriumSpikeRender(null);
    private final RavenousEngineRender    engine  = new RavenousEngineRender(null);
    private final ContravoidReactorRender reactor = new ContravoidReactorRender(null);
    private final VoidFissureRender       fissure = new VoidFissureRender(null);
    private final PermeableBarrierRender  barrier = new PermeableBarrierRender(null);

    /**
     * @param toViewer the way to the stage's camera, in scene blocks
     */
    void submit(PoseStack poseStack, SubmitNodeCollector collector, List<SceneFrame.ShownActor> actors, Vector3f toViewer, float worldTime)
    {
        for (var actor : actors)
        {
            poseStack.pushPose();
            poseStack.translate(actor.pos().getX(), actor.pos().getY(), actor.pos().getZ());
            var target = actor.target() == null ? null : actor.target().subtract(Vec3.atLowerCornerOf(actor.pos()));

            switch (actor.role())
            {
                case COLLECTOR -> COLLECTOR.submit(poseStack, collector, ARGB.color((int) (255 * actor.appear()), 0xFFFFFF));
                case SPIKE -> spike.submit(spike(actor, target), poseStack, collector, null);
                case ENGINE -> engine.submit(engine(actor, target, worldTime), poseStack, collector, null);
                case FISSURE -> fissure.submit(fissure(actor, target, worldTime), poseStack, collector, null);
                case FISSURE_CLOSING ->
                {
                    var closing = fissure(actor, null, worldTime);
                    closing.length = actor.amount();
                    closing.half = CRACK_HALF + CRACK_HALF_GROWTH * (actor.amount() - 1);
                    closing.flare = 0;
                    closing.closed = Mth.clamp(actor.working() / CLOSE_TICKS, 0, 1);
                    fissure.submit(closing, poseStack, collector, null);
                }
                case BARRIER -> barrier.submit(barrier(actor, worldTime), poseStack, collector, null);
                case REACTOR ->
                {
                    if (actor.working() >= 0)
                        reactor.submit(reactor(actor, target, toViewer, worldTime), poseStack, collector, null);
                }
            }
            poseStack.popPose();
        }
    }

    private static <S extends BlockEntityRenderState> S lit(S state, SceneFrame.ShownActor actor)
    {
        state.blockPos = actor.pos();
        state.lightCoords = FULL_BRIGHT;
        return state;
    }

    private static float leap(float working)
    {
        return working % LEAP_TICKS / LEAP_TICKS;
    }

    private static AzuriumSpikeRender.State spike(SceneFrame.ShownActor actor, Vec3 target)
    {
        var state = lit(new AzuriumSpikeRender.State(), actor);
        state.leanDegrees = SPIKE_LEAN;
        state.leanYaw = SPIKE_YAW;
        new Quaternionf().rotateY(SPIKE_YAW * Mth.DEG_TO_RAD).rotateX(SPIKE_LEAN * Mth.DEG_TO_RAD).transform(state.arcStart.set(0, SPIKE_POINT, 0)).add(0.5F, 0, 0.5F);
        if (actor.working() < 0)
            return state;

        state.fade = Math.min(1, actor.working() / SPIKE_FADE_TICKS);
        for (int band = 0; band < SPIKE_BANDS; band++)
            state.bandHeads.add((actor.working() / BAND_TICKS + band / (float) SPIKE_BANDS) % 1);
        state.arcEnd = target;
        state.jagSeed = (long) (actor.working() / JAG_TICKS);
        if (target != null)
            state.leapHeads.add(leap(actor.working()));
        return state;
    }

    private static RavenousEngineRender.State engine(SceneFrame.ShownActor actor, Vec3 target, float worldTime)
    {
        var state = lit(new RavenousEngineRender.State(), actor);
        if (actor.working() < 0)
            return state;

        state.open = Math.min(1, actor.working() / ENGINE_OPEN_TICKS);
        state.fuel = EngineFuel.STILLNESS;
        state.time = worldTime;
        state.pulse = actor.working() % ENGINE_PULSE / ENGINE_PULSE;
        state.pulses = (int) (actor.working() / ENGINE_PULSE);
        state.arcEnd = target;
        state.jagSeed = (long) (actor.working() / JAG_TICKS);
        if (target != null)
            state.leapHeads.add(leap(actor.working()));
        return state;
    }

    /**
     * Torn from the width before to its own over the first second it works, the way a bundle pressed into the crack widens it.
     */
    private static VoidFissureRender.State fissure(SceneFrame.ShownActor actor, Vec3 target, float worldTime)
    {
        var state = lit(new VoidFissureRender.State(), actor);
        float tear = actor.working() < 0 ? 1 : Math.min(1, actor.working() / TEAR_TICKS);
        tear = tear * tear * (3 - 2 * tear);
        float width = actor.amount() - 1 + tear;

        state.axisX = true;
        state.seed = actor.pos().asLong();
        state.length = Math.max(0, width);
        state.half = CRACK_HALF + CRACK_HALF_GROWTH * Math.max(0, width - 1);
        state.flare = 1 - tear;
        state.time = worldTime;
        state.arcEnd = target;
        state.arcSeed = (long) (worldTime / JAG_TICKS);
        state.arcFade = 1;
        if (target != null)
            state.leapHeads.add(leap(worldTime));
        return state;
    }

    /**
     * A barrier nobody chose a look for yet: its sigil, turning.
     */
    private static PermeableBarrierRenderState barrier(SceneFrame.ShownActor actor, float worldTime)
    {
        var state = lit(new PermeableBarrierRenderState(), actor);
        state.mode = PermeableBarrierRenderState.Mode.SIGIL;
        state.worldTime = worldTime;
        return state;
    }

    /**
     * Gathers sparks while it lights, then holds its point steady: a scene shows a reactor that is fed, never one that fails.
     */
    private static ContravoidReactorRender.State reactor(SceneFrame.ShownActor actor, Vec3 target, Vector3f toViewer, float worldTime)
    {
        var state = lit(new ContravoidReactorRender.State(), actor);
        float burning = actor.working() - IGNITION_TICKS;

        state.axis = Direction.Axis.X;
        state.essenceColor = actor.rgb();
        state.time = worldTime;
        state.seed = actor.pos().asLong();
        state.backdrop = true;
        state.toCamera.set(toViewer);
        state.streams = 1;

        if (burning < 0)
        {
            state.phase = ReactorCycle.Phase.IGNITING;
            state.charge = actor.working() / IGNITION_TICKS;
        }
        else
        {
            state.phase = ReactorCycle.Phase.LIT;
            state.light = Math.min(1, burning / LIGHT_TICKS);
            state.flare = Math.max(0, 1 - burning / FLARE_TICKS);
            state.activity = 1;
            state.charge = 1;
        }

        if (target != null)
            state.arcs.add(new ContravoidReactorRender.ArcDraw(target, state.seed, List.of(leap(actor.working())), burning < 0));
        return state;
    }
}
