package net.voxelindustry.voidheart.client.scene;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SceneTimelineTest
{
    private static final SceneTimeline TIMELINE = new SceneTimeline(new int[] { 40, 100, 60 });

    @Test
    void stepAt_findsTheStepOfATick()
    {
        assertThat(TIMELINE.stepAt(0)).isEqualTo(0);
        assertThat(TIMELINE.stepAt(39.9F)).isEqualTo(0);
        assertThat(TIMELINE.stepAt(40)).isEqualTo(1);
        assertThat(TIMELINE.stepAt(139)).isEqualTo(1);
        assertThat(TIMELINE.stepAt(140)).isEqualTo(2);
    }

    @Test
    void stepAt_staysOnTheEndsPastThem()
    {
        assertThat(TIMELINE.stepAt(-5)).isEqualTo(0);
        assertThat(TIMELINE.stepAt(9999)).isEqualTo(2);
    }

    @Test
    void at_placesAMomentWithinItsStep()
    {
        assertThat(TIMELINE.at(1, 15)).isEqualTo(55);
        assertThat(TIMELINE.total()).isEqualTo(200);
    }

    @Test
    void share_givesEveryStepTheSameWidth()
    {
        assertThat(TIMELINE.share(0)).isEqualTo(0);
        assertThat(TIMELINE.share(40)).isCloseTo(1 / 3F, within(0.001F));
        assertThat(TIMELINE.share(90)).isCloseTo(1 / 2F, within(0.001F));
        assertThat(TIMELINE.share(200)).isEqualTo(1);
    }

    @Test
    void duration_waitsForTheTextToBeRead()
    {
        assertThat(SceneTimeline.duration(200, 10)).isEqualTo(200);
        assertThat(SceneTimeline.duration(20, 100)).isGreaterThan(100);
    }

    @Test
    void duration_growsWithALongerTranslation()
    {
        assertThat(SceneTimeline.duration(20, 200)).isGreaterThan(SceneTimeline.duration(20, 100));
    }
}
