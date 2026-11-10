package net.voxelindustry.voidheart.client.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * What a scene writes over its blocks, worked out from the time alone: two steps of a hundred ticks each.
 */
class SceneCuesTest
{
    private static final SceneView     VIEW     = new SceneView(0, 30, 10, 0, 0, 0);
    private static final SceneTimeline TIMELINE = new SceneTimeline(new int[] { 100, 100 });
    private static final Vec3          FAR      = new Vec3(10, 0, 0);

    private static Scene.Builder scene()
    {
        var scene = Scene.builder(Identifier.fromNamespaceAndPath("voidheart", "test"));
        scene.step("first", 100, VIEW, Vec3.ZERO);
        return scene;
    }

    private static SceneFrame at(Scene.Builder scene, float ticks)
    {
        return SceneFrame.of(scene.build(), TIMELINE, ticks);
    }

    @Test
    void ring_spreadsFromItsMiddleToItsRadius()
    {
        var scene = scene().ring(10, new Scene.Moment(1, 0), Vec3.ZERO, 16, Scene.THEME);

        assertThat(at(scene, 9).rings()).isEmpty();
        assertThat(at(scene, 14).rings().getFirst().radius()).isBetween(0F, 8F);
        assertThat(at(scene, 60).rings().getFirst().radius()).isEqualTo(16);
        assertThat(at(scene, 60).rings().getFirst().alpha()).isEqualTo(1);
    }

    @Test
    void ring_fadesBeforeItsEndAndIsGoneAfter()
    {
        var scene = scene().ring(10, new Scene.Moment(1, 0), Vec3.ZERO, 16, Scene.THEME);

        assertThat(at(scene, 98).rings().getFirst().alpha()).isLessThan(0.5F);
        assertThat(at(scene, 100).rings()).isEmpty();
    }

    @Test
    void link_reachesItsEndAndKeepsCountingItsAge()
    {
        var scene = scene().link(20, Scene.Moment.NEVER, Vec3.ZERO, FAR, Scene.THEME, Scene.Flow.PULSE);

        assertThat(at(scene, 19).links()).isEmpty();
        assertThat(at(scene, 22).links().getFirst().growth()).isBetween(0F, 0.5F);

        var settled = at(scene, 150).links().getFirst();
        assertThat(settled.growth()).isEqualTo(1);
        assertThat(settled.age()).isEqualTo(130);
    }

    /**
     * Jumping to a step has to show what playing up to it would have, or a held scene and a played one would differ.
     */
    @Test
    void frame_isTheSameHoweverItsTickWasReached()
    {
        var scene = scene().link(20, Scene.Moment.NEVER, Vec3.ZERO, FAR, Scene.THEME, Scene.Flow.ARC)
                .ring(10, Scene.Moment.NEVER, Vec3.ZERO, 6, Scene.THEME);

        at(scene, 30);
        var played = at(scene, 77.5F);
        var jumped = SceneFrame.of(scene.build(), TIMELINE, 77.5F);

        assertThat(jumped).isEqualTo(played);
    }

    @Test
    void click_showsOnceAndBriefly()
    {
        var scene = scene().click(30, Vec3.ZERO).sneakClick(60, FAR);

        assertThat(at(scene, 29).clicks()).isEmpty();
        assertThat(at(scene, 31).clicks()).singleElement().matches(click -> !click.sneak() && click.progress() < 0.2F);
        assertThat(at(scene, 55).clicks()).isEmpty();
        assertThat(at(scene, 61).clicks()).singleElement().matches(SceneFrame.ShownClick::sneak);
    }

    @Test
    void measure_writesItsLengthAtTheMiddleOfItsLine()
    {
        var scene = scene().measure(10, Scene.Moment.NEVER, Vec3.ZERO, FAR, Component.literal("10"), Scene.THEME);
        var frame = at(scene, 50);

        assertThat(frame.links()).singleElement().matches(link -> link.flow() == Scene.Flow.STILL);
        assertThat(frame.labels().getFirst().at().x).isCloseTo(5, within(0.001));
        assertThat(frame.labels().getFirst().text().getString()).isEqualTo("10");
    }

    @Test
    void outline_goesAroundBothCornersWhicheverComesFirst()
    {
        var scene = scene().outline(0, Scene.Moment.NEVER, new BlockPos(1, 2, 0), new BlockPos(-1, 0, 0), Scene.THEME);
        var outline = at(scene, 50).outlines().getFirst();

        assertThat(outline.min()).isEqualTo(new Vec3(-1, 0, 0));
        assertThat(outline.max()).isEqualTo(new Vec3(2, 3, 1));
    }

    @Test
    void actor_onlyWorksFromItsMoment()
    {
        var scene = scene().actor(10, new Scene.Moment(1, 50), BlockPos.ZERO, Scene.Role.SPIKE, new Scene.Moment(1, 0), FAR, Scene.THEME, 1);

        assertThat(at(scene, 5).actors()).isEmpty();
        assertThat(at(scene, 50).actors().getFirst().working()).isNegative();
        assertThat(at(scene, 120).actors().getFirst().working()).isEqualTo(20);
        assertThat(at(scene, 150).actors()).isEmpty();
    }

    @Test
    void clear_takesWhatWasPlacedOutOfWhatStandsAtTheEnd()
    {
        var scene = scene().place(0, BlockPos.ZERO, null).place(0, BlockPos.ZERO.above(), null).clear(50, BlockPos.ZERO);

        assertThat(scene.build().finished()).extracting(Scene.Place::pos).containsExactly(BlockPos.ZERO.above());
        assertThat(at(scene, 40).blocks()).hasSize(2);
        assertThat(at(scene, 60).blocks()).hasSize(1);
    }

    @Test
    void depth_putsWhatIsNearerTheViewerInFront()
    {
        var above = new SceneView(0, 30, 10, 0, 0, 0);

        assertThat(above.depth(0, 1, 0)).isLessThan(above.depth(0, 0, 0));
    }
}
