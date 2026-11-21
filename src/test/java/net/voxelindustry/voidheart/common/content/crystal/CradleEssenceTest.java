package net.voxelindustry.voidheart.common.content.crystal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static net.voxelindustry.voidheart.common.content.crystal.CrystalHabit.CLEAR;
import static net.voxelindustry.voidheart.common.content.crystal.CrystalHabit.SEARING;
import static net.voxelindustry.voidheart.common.content.crystal.CrystalHabit.STILL;
import static net.voxelindustry.voidheart.common.content.crystal.CrystalHabit.WRUNG;
import static org.assertj.core.api.Assertions.assertThat;

class CradleEssenceTest
{
    private static final Predicate<CrystalHabit> ALL = habit -> true;

    @Test
    void fill_onlyTakesWhatATankHolds()
    {
        var essence = new CradleEssence(200);
        assertThat(essence.fill(SEARING, 150)).isEqualTo(150);
        assertThat(essence.fill(SEARING, 150)).isEqualTo(50);
        assertThat(essence.room(SEARING)).isZero();
        assertThat(essence.room(STILL)).isEqualTo(200);
        assertThat(essence.fill(CLEAR, 10)).as("no essence teaches Clear").isZero();
    }

    @Test
    void pay_takesAWholePointToPayForTicksAtATime()
    {
        var essence = new CradleEssence(200);
        essence.fill(SEARING, 1);
        for (int tick = 0; tick < 10; tick++)
            assertThat(essence.pay(0.1, ALL)).as("tick " + tick).isEqualTo(SEARING);
        assertThat(essence.amount(SEARING)).isZero();
        assertThat(essence.pay(0.1, ALL)).as("a point pays for ten ticks at a tenth each, and no more").isNull();
    }

    @Test
    void pay_comesFromTheEssenceHeldMost()
    {
        var essence = new CradleEssence(200);
        essence.fill(SEARING, 5);
        essence.fill(STILL, 20);
        assertThat(essence.pay(0.1, ALL)).isEqualTo(STILL);
        assertThat(essence.pay(0.1, habit -> habit != STILL)).as("an essence the crystal won't take is passed over").isEqualTo(SEARING);
    }

    @Test
    void pay_refusesWhatCantPayAWholeTick()
    {
        var essence = new CradleEssence(200);
        assertThat(essence.pay(0.1, ALL)).isNull();
        essence.fill(WRUNG, 1);
        assertThat(essence.pay(0, ALL)).isNull();
        assertThat(essence.pay(Double.NaN, ALL)).isNull();
        assertThat(essence.pay(0.1, habit -> false)).isNull();
        assertThat(essence.amount(WRUNG)).as("nothing taken when nothing was paid").isOne();
    }

    @Test
    void load_mendsWhatCantBe()
    {
        var essence = new CradleEssence(200);
        essence.load(new CradleEssence.Saved(List.of(500, -3), List.of(Double.NaN, 4.0, 0.5)));
        assertThat(essence.amount(SEARING)).isEqualTo(200);
        assertThat(essence.amount(STILL)).isZero();
        assertThat(essence.amount(WRUNG)).isZero();

        var saved = new CradleEssence(200);
        saved.fill(STILL, 40);
        saved.pay(0.3, ALL);
        var copy = new CradleEssence(200);
        copy.load(saved.save());
        assertThat(copy.save()).isEqualTo(saved.save());
    }
}
