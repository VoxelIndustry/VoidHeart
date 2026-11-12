package net.voxelindustry.voidheart.common.content.underway;

import net.voxelindustry.voidheart.common.energy.storage.RubrumBalance;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SectionStateTest
{
    @Test
    void anUntouchedSectionOnlyWatches()
    {
        var state = new SectionState();
        assertThat(state.isLive()).isFalse();
        assertThat(state.isAlarmed()).isFalse();
        assertThat(state.isAwake(true)).isFalse();
        assertThat(state.isAwake(false)).isFalse();
        assertThat(state.isBlank()).isTrue();
    }

    @Test
    void anAlarmWithoutPowerWakesOnlyTheEmergencyCircuit()
    {
        var state = new SectionState();
        state.raiseAlarm();
        assertThat(state.isAlarmed()).isTrue();
        assertThat(state.isAwake(true)).isTrue();
        assertThat(state.isAwake(false)).isFalse();

        for (int tick = 0; tick < SecurityBalance.ALARM_TICKS; tick++)
            state.tick();
        assertThat(state.isAlarmed()).isFalse();
        assertThat(state.isAwake(true)).as("what woke stays awake once the klaxon stops").isTrue();
    }

    @Test
    void aChargedCellWakesEverythingForGood()
    {
        var state = new SectionState();
        state.insert(RubrumBalance.CELL_CAPACITY);
        assertThat(state.isLive()).isTrue();
        assertThat(state.isAlarmed()).isTrue();
        assertThat(state.isAwake(false)).isTrue();

        assertThat(state.remove()).isEqualTo(RubrumBalance.CELL_CAPACITY);
        assertThat(state.isLive()).isFalse();
        assertThat(state.hasCell()).isFalse();
        assertThat(state.isAwake(false)).as("pulling the cell calms nothing").isTrue();
    }

    @Test
    void anEmptyCellPowersNothing()
    {
        var state = new SectionState();
        state.insert(0);
        assertThat(state.hasCell()).isTrue();
        assertThat(state.isLive()).isFalse();
        assertThat(state.isAwake(false)).isFalse();
        assertThat(state.remove()).isZero();
    }

    @Test
    void aCellDrainsWhileTheSectionIsLiveAndRunsDryOnce()
    {
        var state = new SectionState();
        state.insert(SecurityBalance.DRAIN_PER_TICK * 3 - 1);
        assertThat(state.drain()).isFalse();
        assertThat(state.drain()).isFalse();
        assertThat(state.drain()).as("the tick the cell runs dry").isTrue();
        assertThat(state.isLive()).isFalse();
        assertThat(state.hasCell()).as("the spent cell stays in the cabinet").isTrue();
        assertThat(state.drain()).isFalse();
        assertThat(state.charge()).isZero();
    }

    @Test
    void aFullCellLastsAboutTenMinutes()
    {
        long ticks = RubrumBalance.CELL_CAPACITY / SecurityBalance.DRAIN_PER_TICK;
        assertThat(ticks).isBetween(9L * 60 * 20, 11L * 60 * 20);
    }

    @Test
    void aSectionIsSecuredOnceEveryUnitIsAWreck()
    {
        var state = new SectionState();
        assertThat(state.isSecured(0)).as("a section built with nothing is safe from the start").isTrue();
        assertThat(state.isSecured(2)).isFalse();
        assertThat(state.destroy(4)).isTrue();
        assertThat(state.destroy(4)).as("a wreck counts once").isFalse();
        assertThat(state.isSecured(2)).isFalse();
        assertThat(state.destroy(7)).isTrue();
        assertThat(state.isSecured(2)).isTrue();
    }

    @Test
    void aRebuiltWreckKeepsTheSectionFromBeingSecured()
    {
        var state = new SectionState();
        state.destroy(4);
        state.destroy(7);
        assertThat(state.isSecured(2)).isTrue();
        assertThat(state.restore(4)).isTrue();
        assertThat(state.restore(4)).as("only a wreck is rebuilt").isFalse();
        assertThat(state.isSecured(2)).isFalse();
        assertThat(state.destroyed()).containsExactly(7);
    }

    @Test
    void onlyAnAlarmInASectionWithPowerLocksItDown()
    {
        var state = new SectionState();
        state.raiseAlarm();
        assertThat(state.isLockedDown()).as("an alarm on the emergency circuit").isFalse();
        state.insert(RubrumBalance.CELL_CAPACITY);
        assertThat(state.isLockedDown()).isTrue();
        state.remove();
        assertThat(state.isLockedDown()).as("pulling the cell frees the doors").isFalse();

        state.insert(RubrumBalance.CELL_CAPACITY);
        for (int tick = 0; tick < SecurityBalance.ALARM_TICKS; tick++)
            state.tick();
        assertThat(state.isLockedDown()).as("the doors free once the alarm stops").isFalse();
        state.raiseAlarm();
        assertThat(state.isLockedDown()).isTrue();
        state.standDown();
        assertThat(state.isLockedDown()).isFalse();
    }

    @Test
    void savedStateComesBackAsItWas()
    {
        var state = new SectionState();
        state.insert(1000);
        state.destroy(3);
        state.destroy(1);
        var loaded = new SectionState(state.hasCell(), state.charge(), state.wake(), state.alarm(), state.destroyed());
        assertThat(loaded.isLive()).isTrue();
        assertThat(loaded.charge()).isEqualTo(1000);
        assertThat(loaded.wake()).isEqualTo(SectionState.Wake.ALL);
        assertThat(loaded.destroyed()).containsExactly(1, 3);
        assertThat(loaded.alarm()).isEqualTo(SecurityBalance.ALARM_TICKS);
    }

    @Test
    void staleSavedStateIsMadeConsistent()
    {
        var loaded = new SectionState(false, 5000, SectionState.Wake.of(9), -4, List.of());
        assertThat(loaded.isLive()).isFalse();
        assertThat(loaded.charge()).as("charge without a cell").isZero();
        assertThat(loaded.wake()).isEqualTo(SectionState.Wake.ALL);
        assertThat(loaded.isAlarmed()).isFalse();
    }
}
