package net.voxelindustry.voidheart.client.scene;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.scene.SceneEmitter.Crumbs;
import net.voxelindustry.voidheart.client.scene.SceneEmitter.Drift;
import net.voxelindustry.voidheart.client.scene.SceneEmitter.Frames;
import net.voxelindustry.voidheart.client.scene.SceneEmitter.Look;
import net.voxelindustry.voidheart.client.scene.SceneEmitter.Spiral;
import net.voxelindustry.voidheart.client.scene.SceneEmitter.Stream;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * A scene's particles, worked out from the time alone: two steps of a hundred ticks each.
 */
class SceneParticlesTest
{
    private static final SceneView     VIEW     = new SceneView(0, 30, 10, 0, 0, 0);
    private static final SceneTimeline TIMELINE = new SceneTimeline(new int[] { 100, 100 });
    private static final Vec3          FAR      = new Vec3(10, 0, 0);

    private static final Frames FOUR  = new Frames(List.of(frame(0), frame(1), frame(2), frame(3)), true);
    private static final Look   PLAIN = new Look(FOUR, 0xFFFFFF, 0.1F, 1, 1, 0, 0);

    private static Identifier frame(int index)
    {
        return Identifier.fromNamespaceAndPath("voidheart", "frame" + index);
    }

    private static Scene.Builder scene()
    {
        var scene = Scene.builder(Identifier.fromNamespaceAndPath("voidheart", "test"));
        scene.step("first", 100, VIEW, Vec3.ZERO);
        return scene;
    }

    private static List<SceneFrame.ShownParticle> at(Scene.Builder scene, float ticks)
    {
        return SceneFrame.of(scene.build(), TIMELINE, ticks).particles();
    }

    private static SceneEmitter rising(float rate, int burst, float life)
    {
        return new SceneEmitter(PLAIN, new Drift(Vec3.ZERO, Vec3.ZERO, new Vec3(0, 0.1, 0), Vec3.ZERO, Vec3.ZERO), rate, burst, life, 0);
    }

    @Test
    void emitter_letsOutItsRateAndHoldsAsManyAsLiveAtOnce()
    {
        var scene = scene().particles(10, Scene.Moment.NEVER, rising(2, 0, 10));

        assertThat(at(scene, 9)).isEmpty();
        assertThat(at(scene, 12)).hasSizeBetween(3, 5);
        assertThat(at(scene, 60)).hasSizeBetween(19, 21);
    }

    @Test
    void emitter_stopsAtItsEndAndItsLastParticlesLiveOn()
    {
        var scene = scene().particles(10, new Scene.Moment(0, 50), rising(2, 0, 10));

        assertThat(at(scene, 55)).isNotEmpty().hasSizeLessThan(12);
        assertThat(at(scene, 55)).allMatch(particle -> particle.y() >= 0.49);
        assertThat(at(scene, 61)).isEmpty();
    }

    @Test
    void puff_isBornAtOnceAndGoneAfterALife()
    {
        var scene = scene().puff(20, rising(0, 12, 10));

        assertThat(at(scene, 19)).isEmpty();
        assertThat(at(scene, 20)).hasSize(12);
        assertThat(at(scene, 25)).hasSize(12).allMatch(particle -> Math.abs(particle.y() - 0.5) < 1.0E-4);
        assertThat(at(scene, 30)).isEmpty();
    }

    /**
     * Jumping to a step has to show what playing up to it would have, and a held scene must not shimmer.
     */
    @Test
    void particles_areTheSameHoweverTheirTickWasReached()
    {
        var scene = scene().particles(0, Scene.Moment.NEVER, SceneFx.smoke(Vec3.ZERO)).puff(30, SceneFx.reshape(FAR, new Vec3(1, 1, 1)));

        at(scene, 30);
        var played = at(scene, 77.5F);
        var jumped = at(scene, 77.5F);

        assertThat(jumped).isNotEmpty().isEqualTo(played);
    }

    @Test
    void twoEmittersOfOneKind_rollApart()
    {
        var scene = scene().puff(0, SceneFx.reshape(Vec3.ZERO, new Vec3(1, 1, 1))).puff(0, SceneFx.reshape(Vec3.ZERO, new Vec3(1, 1, 1)));
        var shown = at(scene, 5);

        assertThat(shown.get(0).x()).isNotEqualTo(shown.get(shown.size() / 2).x());
    }

    @Test
    void drift_isThrownThenPulled()
    {
        var falling = new SceneEmitter(PLAIN, new Drift(Vec3.ZERO, Vec3.ZERO, new Vec3(0.1, 0, 0), Vec3.ZERO, new Vec3(0, -0.02, 0)), 0, 1, 50, 0);
        var particle = at(scene().puff(0, falling), 10).getFirst();

        assertThat(particle.x()).isCloseTo(1, within(1.0E-4));
        assertThat(particle.y()).isCloseTo(-1, within(1.0E-4));
    }

    @Test
    void stream_leavesItsStartAndArrivesAtItsEnd()
    {
        var stream = new SceneEmitter(PLAIN, new Stream(Vec3.ZERO, Vec3.ZERO, FAR, 0.2F), 0, 8, 20, 0);
        var scene = scene().puff(0, stream);

        assertThat(at(scene, 0)).allMatch(particle -> new Vec3(particle.x(), particle.y(), particle.z()).length() < 1.0E-4);
        assertThat(at(scene, 10)).anyMatch(particle -> Math.abs(particle.z()) > 0.1);
        assertThat(at(scene, 19.99F)).allMatch(particle -> new Vec3(particle.x(), particle.y(), particle.z()).distanceTo(FAR) < 0.05);
    }

    @Test
    void spiral_keepsItsRadiusAroundItsLine()
    {
        var spiral = new SceneEmitter(PLAIN, new Spiral(Vec3.ZERO, FAR, 0.25F, 5), 0, 8, 20, 0);

        assertThat(at(scene().puff(0, spiral), 7)).allSatisfy(particle ->
        {
            assertThat(particle.x()).isCloseTo(3.5, within(1.0E-4));
            assertThat(Math.hypot(particle.y(), particle.z())).isCloseTo(0.25, within(1.0E-4));
        });
    }

    @Test
    void look_fadesShrinksAndPlaysItsFramesThrough()
    {
        var look = new Look(FOUR, Scene.THEME, 0.1F, 0, 0.8F, 0.25F, 0.25F);
        var scene = scene().puff(0, new SceneEmitter(look, new Drift(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO), 0, 1, 40, 0));

        var young = at(scene, 5).getFirst();
        var grown = at(scene, 20).getFirst();
        var old = at(scene, 38).getFirst();

        assertThat(young.alpha()).isCloseTo(0.4F, within(1.0E-4F));
        assertThat(grown.alpha()).isCloseTo(0.8F, within(1.0E-4F));
        assertThat(old.alpha()).isLessThan(0.2F);
        assertThat(old.size()).isLessThan(young.size());
        assertThat(List.of(young.frame(), grown.frame(), old.frame())).containsExactly(0, 2, 3);
        assertThat(young.rgb()).isEqualTo(Scene.THEME);
    }

    @Test
    void crumbs_keepTheirSliceAndTurnIntoWhatTheyBecome()
    {
        // No game runs here to make items of, and a crumb only hands its stack on to whoever draws it
        var look = new Look(new Crumbs(null, net.minecraft.world.item.ItemStack.EMPTY), 0xFFFFFF, 0.1F, 1, 1, 0, 0);
        var scene = scene().puff(0, new SceneEmitter(look, new Spiral(Vec3.ZERO, FAR, 0.25F, 5), 0, 20, 40, 0));
        int slices = SceneEmitter.CRUMB_SLICES * SceneEmitter.CRUMB_SLICES;

        var leaving = at(scene, 4);
        var arriving = at(scene, 36);

        assertThat(leaving).allMatch(particle -> particle.frame() < slices);
        assertThat(arriving).allMatch(particle -> particle.frame() >= slices);
        for (int index = 0; index < leaving.size(); index++)
            assertThat(arriving.get(index).frame() - slices).isEqualTo(leaving.get(index).frame());
        assertThat(leaving.stream().map(SceneFrame.ShownParticle::frame).distinct()).hasSizeGreaterThan(4);
    }
}
