package net.voxelindustry.voidheart.common.content.armillary;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionTimelineTest
{
    private static final int MINUTE = 20 * 60;

    @Test
    void stages_worsenAsTimeRunsOut()
    {
        assertThat(ConnectionTimeline.stage(30 * MINUTE)).isEqualTo(ConnectionTimeline.Stage.STABLE);
        assertThat(ConnectionTimeline.stage(10 * MINUTE)).isEqualTo(ConnectionTimeline.Stage.FADING);
        assertThat(ConnectionTimeline.stage(5 * MINUTE)).isEqualTo(ConnectionTimeline.Stage.FRAYING);
        assertThat(ConnectionTimeline.stage(2 * MINUTE)).isEqualTo(ConnectionTimeline.Stage.FAILING);
        assertThat(ConnectionTimeline.stage(MINUTE)).isEqualTo(ConnectionTimeline.Stage.COLLAPSING);
        assertThat(ConnectionTimeline.stage(0)).isEqualTo(ConnectionTimeline.Stage.COLLAPSING);
    }

    @Test
    void warnings_fallAtTenFiveTwoAndOneMinutes_once()
    {
        var at = new ArrayList<Long>();
        for (long remaining = 40L * MINUTE; remaining >= 0; remaining--)
        {
            if (ConnectionTimeline.warningAt(remaining) != null)
                at.add(remaining);
        }
        assertThat(at).containsExactly(10L * MINUTE, 5L * MINUTE, 2L * MINUTE, (long) MINUTE);
    }

    @Test
    void countdown_runsThroughTheLastTenSeconds()
    {
        var shown = new ArrayList<Integer>();
        for (long remaining = 2L * MINUTE; remaining >= 0; remaining--)
        {
            int countdown = ConnectionTimeline.countdownAt(remaining);
            if (countdown > 0)
                shown.add(countdown);
        }
        assertThat(shown).containsExactly(10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
    }

    @Test
    void tremor_startsAtTheLastMinute_andGrows()
    {
        assertThat(ConnectionTimeline.tremor(2 * MINUTE)).isZero();
        assertThat(ConnectionTimeline.tremor(MINUTE)).isZero();

        float previous = 0;
        for (long remaining = MINUTE - 1; remaining >= 0; remaining--)
        {
            float tremor = ConnectionTimeline.tremor(remaining);
            assertThat(tremor).isGreaterThan(previous);
            previous = tremor;
        }
        assertThat(ConnectionTimeline.tremor(0)).isEqualTo(1);
    }
}
