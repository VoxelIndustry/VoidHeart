package net.voxelindustry.voidheart.common.energy.ley;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GrowerTicksTest
{
    private static final int VOLUME = GrowerTicks.volume(LeyStoneBalance.GROWER_RADIUS, LeyStoneBalance.GROWER_HEIGHT);

    @Test
    void volumeCoversA9By9Plot()
    {
        assertThat(GrowerTicks.volume(4, 2)).isEqualTo(9 * 9 * 5);
    }

    /**
     * Each block in the volume should get as many extra picks as vanilla gives it random ticks, so growth doubles at full supply.
     */
    @Test
    void fullSupplyMatchesVanillaRandomTicks()
    {
        var ticks = new GrowerTicks();
        long picks = 0;
        int duration = 100_000;
        for (int i = 0; i < duration; i++)
            picks += ticks.next(VOLUME, 3, LeyStoneBalance.GROWER_BOOST, 1);

        double perBlockPerTick = picks / (double) VOLUME / duration;
        assertThat(perBlockPerTick).isCloseTo(3 / 4096D, within(1e-6));
    }

    @Test
    void halfSupplyHalvesTheBoost()
    {
        var full = new GrowerTicks();
        var half = new GrowerTicks();
        long fullPicks = 0, halfPicks = 0;
        for (int i = 0; i < 10_000; i++)
        {
            fullPicks += full.next(VOLUME, 3, 1, 1);
            halfPicks += half.next(VOLUME, 3, 1, 0.5F);
        }
        assertThat(halfPicks / (double) fullPicks).isCloseTo(0.5, within(0.01));
    }

    @Test
    void unpoweredOrFrozenGrowerPicksNothing()
    {
        var ticks = new GrowerTicks();
        for (int i = 0; i < 1000; i++)
        {
            assertThat(ticks.next(VOLUME, 3, 1, 0)).isZero();
            assertThat(ticks.next(VOLUME, 0, 1, 1)).isZero();
        }
    }
}
