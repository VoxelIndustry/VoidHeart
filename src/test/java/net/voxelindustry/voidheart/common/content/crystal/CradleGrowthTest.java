package net.voxelindustry.voidheart.common.content.crystal;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Predicate;

import static net.voxelindustry.voidheart.common.content.crystal.CrystalHabit.CLEAR;
import static net.voxelindustry.voidheart.common.content.crystal.CrystalHabit.DEEP;
import static net.voxelindustry.voidheart.common.content.crystal.CrystalHabit.NONE;
import static net.voxelindustry.voidheart.common.content.crystal.CrystalHabit.PLAIN;
import static net.voxelindustry.voidheart.common.content.crystal.CrystalHabit.WRUNG;
import static net.voxelindustry.voidheart.common.content.crystal.CrystalKind.AZURIUM;
import static net.voxelindustry.voidheart.common.content.crystal.CrystalKind.RUBRUM;
import static org.assertj.core.api.Assertions.assertThat;

class CradleGrowthTest
{
    private static final Predicate<CrystalHabit> ALL = habit -> true;

    @Test
    void grows_upToTheLargestBundleItsWorthMakes()
    {
        var growth = new CradleGrowth();
        growth.seed(AZURIUM, 3, NONE);

        assertThat(growth.target()).isEqualTo(BundleSize.MEDIUM);
        growth.grow(PLAIN, 10);
        assertThat(growth.grown()).isEqualTo(2);
        assertThat(growth.isGrowing()).isFalse();

        var harvest = growth.harvest(ALL);
        assertThat(harvest.bundle()).isEqualTo(BundleSize.MEDIUM);
        assertThat(harvest.shards()).isOne();
    }

    @Test
    void harvest_neverGivesMoreThanWentIn()
    {
        for (int worth = 1; worth <= CradleGrowth.MAX_WORTH; worth++)
        {
            for (double grown = 0; grown <= 4; grown += 0.25)
            {
                var growth = new CradleGrowth();
                growth.seed(RUBRUM, worth, NONE);
                growth.grow(PLAIN, grown);
                var harvest = growth.harvest(ALL);
                int out = harvest.shards() + (harvest.bundle() == null ? 0 : harvest.bundle().worth());
                assertThat(out).as("worth %d grown %.2f", worth, grown).isEqualTo(worth);
            }
        }
    }

    @Test
    void harvest_beforeAnythingGrewGivesTheShardsBack()
    {
        var growth = new CradleGrowth();
        growth.seed(AZURIUM, 2, CLEAR);
        growth.grow(PLAIN, 0.9);

        var harvest = growth.harvest(ALL);
        assertThat(harvest.bundle()).isNull();
        assertThat(harvest.shards()).isEqualTo(2);
        assertThat(harvest.shardHabit()).isEqualTo(CLEAR);
        assertThat(growth.worth()).isZero();
    }

    @Test
    void habit_settlesFromTheFeedThatPaidTwoThirds()
    {
        var growth = new CradleGrowth();
        growth.seed(AZURIUM, 4, NONE);
        growth.grow(CLEAR, 2.7);
        growth.grow(PLAIN, 1.3);
        assertThat(growth.habit(ALL)).isEqualTo(CLEAR);

        var split = new CradleGrowth();
        split.seed(AZURIUM, 4, NONE);
        split.grow(CLEAR, 2.6);
        split.grow(PLAIN, 1.4);
        assertThat(split.habit(ALL)).isEqualTo(PLAIN);
    }

    @Test
    void habit_ofTheShardsStaysUnlessAnotherFeedPaysTwoThirds()
    {
        var kept = new CradleGrowth();
        kept.seed(AZURIUM, 4, CLEAR);
        kept.grow(PLAIN, 4);
        assertThat(kept.habit(ALL)).isEqualTo(CLEAR);

        var overridden = new CradleGrowth();
        overridden.seed(AZURIUM, 4, CLEAR);
        overridden.grow(DEEP, 3);
        overridden.grow(PLAIN, 1);
        assertThat(overridden.habit(ALL)).isEqualTo(DEEP);
    }

    @Test
    void habit_onlySettlesOnACrystalThatTakesIt()
    {
        var growth = new CradleGrowth();
        growth.seed(RUBRUM, 2, NONE);
        growth.grow(CLEAR, 2);
        assertThat(growth.habit(ALL)).isEqualTo(PLAIN);

        var wrung = new CradleGrowth();
        wrung.seed(RUBRUM, 2, NONE);
        wrung.grow(WRUNG, 2);
        assertThat(wrung.habit(ALL)).isEqualTo(WRUNG);
    }

    @Test
    void habit_onlySettlesWhereTheOwnerHasStudiedIt()
    {
        var growth = new CradleGrowth();
        growth.seed(AZURIUM, 1, NONE);
        growth.grow(CLEAR, 1);
        assertThat(growth.habit(habit -> habit != CLEAR)).isEqualTo(PLAIN);
    }

    @Test
    void seed_refusesAnotherCrystalAnotherHabitOrTooMuch()
    {
        var growth = new CradleGrowth();
        assertThat(growth.seed(AZURIUM, 2, CLEAR)).isTrue();

        assertThat(growth.refusal(RUBRUM, 1, CLEAR)).isEqualTo(CradleGrowth.Refusal.OTHER_CRYSTAL);
        assertThat(growth.refusal(AZURIUM, 1, NONE)).isEqualTo(CradleGrowth.Refusal.OTHER_HABIT);
        assertThat(growth.refusal(AZURIUM, 3, CLEAR)).isEqualTo(CradleGrowth.Refusal.FULL);
        assertThat(growth.seed(AZURIUM, 2, CLEAR)).isTrue();
        assertThat(growth.room()).isZero();
    }

    @Test
    void seed_treatsWorldAndPlainCrystalAlike()
    {
        var growth = new CradleGrowth();
        growth.seed(AZURIUM, 1, NONE);
        assertThat(growth.refusal(AZURIUM, 1, PLAIN)).isEqualTo(CradleGrowth.Refusal.NONE);
    }

    @Test
    void seed_moreWorthAfterGrowingKeepsTheGrowthAndGrowsOn()
    {
        var growth = new CradleGrowth();
        growth.seed(AZURIUM, 2, NONE);
        growth.grow(PLAIN, 2);
        growth.seed(AZURIUM, 2, NONE);

        assertThat(growth.target()).isEqualTo(BundleSize.LARGE);
        assertThat(growth.isGrowing()).isTrue();
        assertThat(growth.grownSize()).isEqualTo(BundleSize.MEDIUM);
    }

    @Test
    void load_mendsWhatCannotBe()
    {
        var growth = new CradleGrowth();
        growth.load(new CradleGrowth.Saved(AZURIUM, 9, CLEAR, 99, Map.of(CLEAR, Double.NaN, PLAIN, -1D, DEEP, 1D)));

        assertThat(growth.worth()).isEqualTo(CradleGrowth.MAX_WORTH);
        assertThat(growth.grown()).isEqualTo(4);
        assertThat(growth.habit(ALL)).isEqualTo(DEEP);

        var empty = new CradleGrowth();
        empty.load(new CradleGrowth.Saved(null, 3, CLEAR, 2, Map.of()));
        assertThat(empty.worth()).isZero();
    }

    @Test
    void save_roundTrips()
    {
        var growth = new CradleGrowth();
        growth.seed(AZURIUM, 4, CLEAR);
        growth.grow(PLAIN, 1.5);

        var copy = new CradleGrowth();
        copy.load(growth.save());
        assertThat(copy.save()).isEqualTo(growth.save());
    }
}
