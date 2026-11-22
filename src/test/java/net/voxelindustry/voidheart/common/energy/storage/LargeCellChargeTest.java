package net.voxelindustry.voidheart.common.energy.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LargeCellChargeTest
{
    private static final long CAPACITY = RubrumBalance.LARGE_CELL_CAPACITY;
    private static final long HOUR     = LargeCellCharge.TICKS_PER_HOUR;

    @Test
    void anHourIdleLosesOnePercentOfCapacity()
    {
        assertThat(LargeCellCharge.decay(0, HOUR, CAPACITY).loss()).isEqualTo(CAPACITY / 100);
    }

    @Test
    void tickByTickLosesTheSameAsAllAtOnce()
    {
        var carry = 0L;
        var lost = 0L;
        for (var tick = 0; tick < HOUR; tick++)
        {
            var decay = LargeCellCharge.decay(carry, 1, CAPACITY);
            carry = decay.carry();
            lost += decay.loss();
        }
        assertThat(lost).isEqualTo(CAPACITY / 100);
        assertThat(carry).isZero();
    }

    @Test
    void aFractionOfAnEnergyIsCarriedOver()
    {
        // 32,000,000 parts of a 7,200,000-part energy: one tick loses four energy and carries the rest
        var decay = LargeCellCharge.decay(0, 1, CAPACITY);
        assertThat(decay.loss()).isEqualTo(4);
        assertThat(decay.carry()).isEqualTo(3_200_000);
    }

    @Test
    void aHundredHoursOrMoreLosesEverythingWithoutOverflow()
    {
        assertThat(LargeCellCharge.decay(0, 100 * HOUR, CAPACITY).loss()).isEqualTo(CAPACITY);
        assertThat(LargeCellCharge.decay(0, Long.MAX_VALUE, CAPACITY).loss()).isEqualTo(CAPACITY);
        assertThat(LargeCellCharge.decay(0, 99 * HOUR, CAPACITY).loss()).isEqualTo(CAPACITY / 100 * 99);
    }

    @Test
    void noIdleTicksLoseNothing()
    {
        assertThat(LargeCellCharge.decay(123, 0, CAPACITY).loss()).isZero();
        assertThat(LargeCellCharge.decay(123, 0, CAPACITY).carry()).isEqualTo(123);
        assertThat(LargeCellCharge.decay(-5, -1, CAPACITY).carry()).isZero();
    }

    @Test
    void segmentsLightPerStartedQuarter()
    {
        assertThat(LargeCellCharge.segments(0, CAPACITY)).isZero();
        assertThat(LargeCellCharge.segments(1, CAPACITY)).isEqualTo(1);
        assertThat(LargeCellCharge.segments(CAPACITY / 4, CAPACITY)).isEqualTo(1);
        assertThat(LargeCellCharge.segments(CAPACITY / 4 + 1, CAPACITY)).isEqualTo(2);
        assertThat(LargeCellCharge.segments(CAPACITY - 1, CAPACITY)).isEqualTo(4);
        assertThat(LargeCellCharge.segments(CAPACITY * 2, CAPACITY)).isEqualTo(4);
    }

    @Test
    void comparatorReadsFill()
    {
        assertThat(LargeCellCharge.comparatorSignal(0, CAPACITY)).isZero();
        assertThat(LargeCellCharge.comparatorSignal(1, CAPACITY)).isEqualTo(1);
        assertThat(LargeCellCharge.comparatorSignal(CAPACITY, CAPACITY)).isEqualTo(15);
    }
}
