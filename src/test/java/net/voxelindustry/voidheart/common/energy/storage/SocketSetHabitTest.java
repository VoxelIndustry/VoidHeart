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
}
