package net.voxelindustry.voidheart.client.render.energy.ley_taint;

import net.voxelindustry.voidheart.common.ley.TaintStage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class AnchorFlickerTest
{
    private static final int TEN_MINUTES = 12_000;

    @Test
    void aCleanAnchorNeverFlickers()
    {
        var flicker = new AnchorFlicker(7);
        for (long tick = 0; tick < TEN_MINUTES; tick++)
        {
            flicker.tick(tick, TaintStage.CLEAN);
            assertThat(flicker.mauve(tick)).isZero();
        }
    }

    @Test
    void aTaintedAnchorDipsBrieflyEveryFiveToTenSeconds()
    {
        var starts = dipStarts(TaintStage.TAINTED);

        assertThat(starts.size()).isBetween(TEN_MINUTES / 200, TEN_MINUTES / 100 + 1);
        for (int i = 1; i < starts.size(); i++)
            assertThat(starts.get(i) - starts.get(i - 1)).isBetween(90L, 210L);
        assertThat(mauveShare(TaintStage.TAINTED)).isLessThan(0.1);
    }

    @Test
    void aFesteringAnchorDipsOftenAndLonger()
    {
        var starts = dipStarts(TaintStage.FESTERING);

        double meanGap = (starts.getLast() - starts.getFirst()) / (double) (starts.size() - 1);
        assertThat(meanGap).isBetween(20D, 60D);
        assertThat(mauveShare(TaintStage.FESTERING)).isGreaterThan(mauveShare(TaintStage.TAINTED));
    }

    @Test
    void aBreachedAnchorIsMostlyMauve()
    {
        assertThat(mauveShare(TaintStage.BREACHED)).isGreaterThan(0.75);
    }

    @Test
    void dipsEaseRatherThanSwitch()
    {
        for (var stage : TaintStage.values())
        {
            var flicker = new AnchorFlicker(11);
            float previous = 0;
            for (long tick = 0; tick < TEN_MINUTES; tick++)
            {
                flicker.tick(tick, stage);
                for (int sub = 0; sub < 4; sub++)
                {
                    float now = flicker.mauve(tick + sub / 4D);
                    assertThat(Math.abs(now - previous)).as("%s at tick %d", stage, tick).isLessThan(0.3F);
                    previous = now;
                }
            }
        }
    }

    private static ArrayList<Long> dipStarts(TaintStage stage)
    {
        var flicker = new AnchorFlicker(3);
        var starts = new ArrayList<Long>();
        boolean inDip = false;
        for (long tick = 0; tick < TEN_MINUTES; tick++)
        {
            flicker.tick(tick, stage);
            boolean dipping = flicker.mauve(tick + 0.5) > 0.01;
            if (dipping && !inDip)
                starts.add(tick);
            inDip = dipping;
        }
        return starts;
    }

    /**
     * Share of the time spent more mauve than teal.
     */
    private static double mauveShare(TaintStage stage)
    {
        var flicker = new AnchorFlicker(5);
        int mauve = 0;
        for (long tick = 0; tick < TEN_MINUTES; tick++)
        {
            flicker.tick(tick, stage);
            if (flicker.mauve(tick) > 0.5F)
                mauve++;
        }
        return mauve / (double) TEN_MINUTES;
    }
}
