package net.voxelindustry.voidheart.common.energy.storage;

import net.voxelindustry.voidheart.common.content.crystal.CrystalHabit;
import net.voxelindustry.voidheart.common.energy.storage.CellHabits.Way;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CellHabitsTest
{
    @Test
    void aPlainCellChargesWhilePoweredAndGivesOtherwise()
    {
        assertThat(CellHabits.way(CrystalHabit.NONE, true, 0)).isEqualTo(Way.CHARGE);
        assertThat(CellHabits.way(CrystalHabit.NONE, false, 100)).isEqualTo(Way.DISCHARGE);
        assertThat(CellHabits.way(CrystalHabit.NONE, false, 0)).isEqualTo(Way.IDLE);
        assertThat(CellHabits.way(CrystalHabit.NONE, true, RubrumBalance.CELL_CAPACITY)).isEqualTo(Way.IDLE);
    }

    @Test
    void aStillCellAlwaysChargesAndNeverGives()
    {
        assertThat(CellHabits.way(CrystalHabit.STILL, false, 100)).isEqualTo(Way.CHARGE);
        assertThat(CellHabits.way(CrystalHabit.STILL, true, 100)).isEqualTo(Way.CHARGE);
        assertThat(CellHabits.way(CrystalHabit.STILL, false, RubrumBalance.CELL_CAPACITY)).isEqualTo(Way.IDLE);
    }

    @Test
    void aSearingCellEmptiesInAboutFiveSeconds()
    {
        assertThat(CellHabits.dischargeRate(CrystalHabit.SEARING) * 100).isEqualTo(RubrumBalance.CELL_CAPACITY);
        assertThat(CellHabits.dischargeRate(CrystalHabit.WRUNG)).isEqualTo(RubrumBalance.CELL_SOCKET_RATE);
        assertThat(CellHabits.way(CrystalHabit.SEARING, true, 0)).as("it still charges like any cell").isEqualTo(Way.CHARGE);
    }

    @Test
    void harmIsBankedUpToItsCapAndTakenInAtARate()
    {
        long banked = CellHabits.bank(0, 5);
        assertThat(banked).isEqualTo(5 * RubrumBalance.WRUNG_PER_DAMAGE);
        assertThat(CellHabits.bank(banked, 1_000)).isEqualTo(RubrumBalance.WRUNG_BANK);
        assertThat(CellHabits.bank(banked, 0)).isEqualTo(banked);
        assertThat(CellHabits.bank(banked, Float.NaN)).isEqualTo(banked);

        assertThat(CellHabits.drink(banked, 0)).isEqualTo(RubrumBalance.WRUNG_RATE);
        assertThat(CellHabits.drink(10, 0)).isEqualTo(10);
        assertThat(CellHabits.drink(banked, RubrumBalance.CELL_CAPACITY - 5)).as("never past the cell's capacity").isEqualTo(5);
        assertThat(CellHabits.drink(banked, RubrumBalance.CELL_CAPACITY)).isZero();
    }
}
