package net.voxelindustry.voidheart.common.energy.superfluid;

import net.voxelindustry.voidheart.common.energy.superfluid.SuperfluidRings.Fill;
import org.junit.jupiter.api.Test;

import static net.voxelindustry.voidheart.common.energy.superfluid.SuperfluidBalance.PEARL_CAPACITY;
import static net.voxelindustry.voidheart.common.energy.superfluid.SuperfluidBalance.RING_UNITS;
import static net.voxelindustry.voidheart.common.energy.superfluid.SuperfluidBalance.SHARD_UNITS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SuperfluidRingsTest
{
    @Test
    void ringsFillFromTheInnermostOut()
    {
        var units = RING_UNITS + RING_UNITS / 2.0;

        assertThat(SuperfluidRings.closed(units, 0)).isEqualTo(1);
        assertThat(SuperfluidRings.closed(units, 1)).isCloseTo(0.5F, within(1e-6F));
        assertThat(SuperfluidRings.closed(units, 2)).isZero();
        assertThat(SuperfluidRings.begun(units)).isEqualTo(2);
    }

    @Test
    void largeBundleCloses_oneRing()
    {
        assertThat(SuperfluidRings.closed(4 * SHARD_UNITS, 0)).isEqualTo(1);
        assertThat(SuperfluidRings.begun(4 * SHARD_UNITS)).isOne();
        assertThat(SuperfluidRings.begun(4 * SHARD_UNITS + 1)).isEqualTo(2);
    }

    @Test
    void fullPearl_hasEveryRingClosed_andNoMore()
    {
        assertThat(SuperfluidRings.begun(PEARL_CAPACITY)).isEqualTo(SuperfluidBalance.RINGS);
        assertThat(SuperfluidRings.begun(PEARL_CAPACITY * 2.0)).isEqualTo(SuperfluidBalance.RINGS);
        assertThat(SuperfluidRings.closed(PEARL_CAPACITY, SuperfluidBalance.RINGS - 1)).isEqualTo(1);
        assertThat(SuperfluidRings.closed(PEARL_CAPACITY, SuperfluidBalance.RINGS)).isZero();
    }

    @Test
    void nothingHeld_noRings()
    {
        assertThat(SuperfluidRings.begun(0)).isZero();
        assertThat(SuperfluidRings.closed(0, 0)).isZero();
    }

    @Test
    void fill_readsInWords_fullOnlyWhenExactlyFull()
    {
        var capacity = SuperfluidBalance.TESSERACT_CAPACITY;

        assertThat(SuperfluidRings.fill(0, capacity)).isEqualTo(Fill.EMPTY);
        assertThat(SuperfluidRings.fill(1, capacity)).isEqualTo(Fill.THREAD);
        assertThat(SuperfluidRings.fill(capacity / 2, capacity)).isEqualTo(Fill.PART);
        assertThat(SuperfluidRings.fill(capacity - 1, capacity)).isEqualTo(Fill.MOST);
        assertThat(SuperfluidRings.fill(capacity, capacity)).isEqualTo(Fill.FULL);
    }

    @Test
    void tesseract_holdsAWholePearl()
    {
        assertThat(SuperfluidBalance.TESSERACT_CAPACITY).isEqualTo(PEARL_CAPACITY);
        assertThat(PEARL_CAPACITY).isEqualTo(16 * SHARD_UNITS);
    }
}
