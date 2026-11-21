package net.voxelindustry.voidheart.common.energy.storage;

import net.voxelindustry.voidheart.common.content.crystal.CrystalHabit;
import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout.Size;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A bundle set in a construct comes back out as it went in, so a cradle-grown bundle never turns back into world crystal there.
 */
class SocketSetHabitTest
{
    @Test
    void habit_survivesSavingAndLoading()
    {
        var sockets = new SocketSet();
        sockets.set(0, SocketBundle.LARGE_RUBRUM, CrystalHabit.PLAIN);
        sockets.set(1, SocketBundle.SMALL_AZURIUM, CrystalHabit.CLEAR);
        sockets.set(2, SocketBundle.SMALL_AZURIUM);

        var copy = new SocketSet();
        copy.load(sockets.entries());
        assertThat(copy.habit(0)).isEqualTo(CrystalHabit.PLAIN);
        assertThat(copy.habit(1)).isEqualTo(CrystalHabit.CLEAR);
        assertThat(copy.habit(2)).isEqualTo(CrystalHabit.NONE);
    }

    @Test
    void habit_clearsWithItsBundle()
    {
        var sockets = new SocketSet();
        sockets.set(0, SocketBundle.SMALL_RUBRUM, CrystalHabit.PLAIN);
        sockets.set(0, null);
        sockets.set(0, SocketBundle.SMALL_RUBRUM);
        assertThat(sockets.habit(0)).isEqualTo(CrystalHabit.NONE);
    }

    @Test
    void habit_movesWithItsBundleToASmallerFrame()
    {
        var sockets = new SocketSet();
        int last = ConstructLayout.socketCount(Size.LARGE) - 1;
        sockets.set(last, SocketBundle.MEDIUM_AZURIUM, CrystalHabit.CLEAR);
        sockets.arrangeFor(Size.SMALL);

        var entry = sockets.entries().getFirst();
        assertThat(entry.socket()).isLessThan(ConstructLayout.socketCount(Size.SMALL));
        assertThat(entry.habit()).isEqualTo(CrystalHabit.CLEAR);
    }

    @Test
    void fouledRubrum_holdsTwiceAndLeaks()
    {
        var sockets = new SocketSet();
        sockets.set(0, SocketBundle.LARGE_RUBRUM, CrystalHabit.FOULED);
        sockets.set(1, SocketBundle.SMALL_RUBRUM, CrystalHabit.PLAIN);
        sockets.set(2, SocketBundle.SMALL_AZURIUM, CrystalHabit.FOULED);

        assertThat(sockets.capacity()).isEqualTo(2 * SocketBundle.LARGE_RUBRUM.capacity() + SocketBundle.SMALL_RUBRUM.capacity());
        assertThat(sockets.holdsFouledRubrum()).isTrue();
        assertThat(new ConstructContents(0, sockets.entries()).capacity()).isEqualTo(sockets.capacity());
    }

    @Test
    void fouledAzurium_changesNothingInASocket()
    {
        var sockets = new SocketSet();
        sockets.set(0, SocketBundle.LARGE_AZURIUM, CrystalHabit.FOULED);

        assertThat(sockets.capacity()).isZero();
        assertThat(sockets.holdsFouledRubrum()).isFalse();
    }

    @Test
    void entry_fromBeforeHabitsLoadsAsWorldCrystal()
    {
        var sockets = new SocketSet();
        sockets.load(List.of(new SocketSet.Entry(3, SocketBundle.LARGE_RUBRUM)));
        assertThat(sockets.habit(3)).isEqualTo(CrystalHabit.NONE);
    }

    @Test
    void searingAndStillAzurium_setTheWaysApart()
    {
        var sockets = new SocketSet();
        sockets.set(0, SocketBundle.SMALL_AZURIUM, CrystalHabit.SEARING);
        sockets.set(1, SocketBundle.MEDIUM_AZURIUM, CrystalHabit.STILL);
        sockets.set(2, SocketBundle.LARGE_AZURIUM, CrystalHabit.PLAIN);
        sockets.set(3, SocketBundle.LARGE_RUBRUM, CrystalHabit.SEARING);

        long small = RubrumBalance.SMALL_AZURIUM_RATE, medium = RubrumBalance.MEDIUM_AZURIUM_RATE, large = RubrumBalance.LARGE_AZURIUM_RATE;
        assertThat(sockets.rateOut()).as("Searing gives out twice as fast, Still gives nothing").isEqualTo(2 * small + large);
        assertThat(sockets.rateIn()).as("Still takes in twice as fast, Searing takes nothing").isEqualTo(2 * medium + large);
        assertThat(new ConstructContents(5, sockets.entries()).rateOut()).isEqualTo(sockets.rateOut());
        assertThat(SocketBundle.LARGE_RUBRUM.rateIn(CrystalHabit.STILL)).as("rubrum gives no rate whatever its habit").isZero();
    }

    @Test
    void aConstructsWaysCapWhatGoesInAndComesOut()
    {
        var charge = new ConstructCharge(() -> 0);
        charge.setSockets(1_000, 100, 0);
        charge.setActive(true);
        assertThat(charge.insert(500, false)).isEqualTo(100);
        assertThat(charge.extract(50, false)).as("nothing comes out of a construct that only takes in").isZero();
        assertThat(charge.maxRateIn()).isEqualTo(100);
        assertThat(charge.maxRateOut()).isZero();
        assertThat(charge.maxRate()).isEqualTo(100);
    }
}
