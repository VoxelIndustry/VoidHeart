package net.voxelindustry.voidheart.common.content.blight;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HarvestTest
{
    private static final int RISEN = BlightBalance.WRAITH_STAIN_TICKS + BlightBalance.WRAITH_RISE_TICKS;

    @Test
    void canRise_onlyMobsWorthAWraith()
    {
        assertThat(Harvest.canRise(20, false, false, false)).isTrue();
        assertThat(Harvest.canRise(BlightBalance.WRAITH_SOURCE_HEALTH - 1, false, false, false)).as("too weak").isFalse();
        assertThat(Harvest.canRise(20, true, false, false)).as("a player").isFalse();
        assertThat(Harvest.canRise(300, false, true, false)).as("a boss").isFalse();
        assertThat(Harvest.canRise(20, false, false, true)).as("a wraith").isFalse();
    }

    @Test
    void phase_followsStainRiseFightSink()
    {
        assertThat(Harvest.phase(0, Harvest.NOT_SINKING)).isEqualTo(Harvest.Phase.STAIN);
        assertThat(Harvest.phase(BlightBalance.WRAITH_STAIN_TICKS, Harvest.NOT_SINKING)).isEqualTo(Harvest.Phase.RISING);
        assertThat(Harvest.phase(RISEN, Harvest.NOT_SINKING)).isEqualTo(Harvest.Phase.FIGHTING);
        assertThat(Harvest.phase(155, 150)).isEqualTo(Harvest.Phase.SINKING);
        assertThat(Harvest.phase(150 + BlightBalance.WRAITH_SINK_TICKS, 150)).isEqualTo(Harvest.Phase.GONE);
    }

    @Test
    void isSpent_afterItsLife()
    {
        assertThat(Harvest.isSpent(RISEN + BlightBalance.WRAITH_LIFE_TICKS - 1)).isFalse();
        assertThat(Harvest.isSpent(RISEN + BlightBalance.WRAITH_LIFE_TICKS)).isTrue();
    }

    @Test
    void buried_comesUpThenGoesBackDown()
    {
        assertThat(Harvest.buried(0, Harvest.NOT_SINKING)).isEqualTo(1);
        assertThat(Harvest.buried(BlightBalance.WRAITH_STAIN_TICKS + BlightBalance.WRAITH_RISE_TICKS / 2F, Harvest.NOT_SINKING)).isCloseTo(0.5F, within(0.01F));
        assertThat(Harvest.buried(RISEN, Harvest.NOT_SINKING)).isZero();
        assertThat(Harvest.buried(100, 100)).isZero();
        assertThat(Harvest.buried(100 + BlightBalance.WRAITH_SINK_TICKS, 100)).isEqualTo(1);
    }

    @Test
    void sinkStartAt_sinksFromHowDeepItIs()
    {
        for (int age = 0; age <= RISEN + 5; age++)
        {
            int start = Harvest.sinkStartAt(age);
            assertThat(start).isNotEqualTo(Harvest.NOT_SINKING);
            assertThat(Harvest.buried(age, start)).as("at age %s", age).isCloseTo(Harvest.buried(age, Harvest.NOT_SINKING), within(1F / BlightBalance.WRAITH_SINK_TICKS));
            // One with less than a tick of sinking left above ground is simply gone
            var expected = Math.round(Harvest.buried(age, Harvest.NOT_SINKING) * BlightBalance.WRAITH_SINK_TICKS) >= BlightBalance.WRAITH_SINK_TICKS ? Harvest.Phase.GONE : Harvest.Phase.SINKING;
            assertThat(Harvest.phase(age, start)).as("at age %s", age).isEqualTo(expected);
        }
        assertThat(Harvest.sinkStartAt(RISEN + 50)).isEqualTo(RISEN + 50);
    }

    @Test
    void toRetire_sendsBackTheOldestToMakeRoom()
    {
        record Out(String name, int age)
        {
        }
        var out = List.of(new Out("young", 10), new Out("old", 300), new Out("middle", 100));
        assertThat(Harvest.toRetire(out, Out::age)).extracting(Out::name).containsExactly("old");
        assertThat(Harvest.toRetire(out.subList(0, 2), Out::age)).isEmpty();
    }
}
