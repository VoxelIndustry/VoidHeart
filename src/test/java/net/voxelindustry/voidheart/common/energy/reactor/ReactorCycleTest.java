package net.voxelindustry.voidheart.common.energy.reactor;

import net.voxelindustry.voidheart.common.energy.reactor.ReactorCycle.Cause;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorCycle.Event;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorCycle.Phase;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorCycle.Refusal;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorCycle.Situation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReactorCycleTest
{
    private static final Situation FINE        = new Situation(true, false, 1_000, 1_000, 0);
    private static final Situation POWERED     = new Situation(true, true, 1_000, 1_000, 0);
    private static final Situation BREACHED    = new Situation(false, false, 1_000, 1_000, 0);
    private static final Situation NO_CONTRA   = new Situation(true, false, 0, 1_000, 0);
    private static final Situation NO_ESSENCE  = new Situation(true, false, 1_000, 0, 0);

    private static ReactorCycle lit()
    {
        var cycle = new ReactorCycle();
        assertThat(cycle.light(FINE)).isEqualTo(Refusal.NONE);
        assertThat(cycle.tick(FINE, ReactorBalance.IGNITION)).isEqualTo(Event.LIT);
        return cycle;
    }

    private static ReactorCycle warned(Situation mistake)
    {
        var cycle = lit();
        assertThat(cycle.tick(mistake, 0)).isEqualTo(Event.WARNING);
        return cycle;
    }

    // Cold

    @Test
    void aColdReactorStaysColdOnItsOwn()
    {
        var cycle = new ReactorCycle();
        for (var situation : new Situation[]{FINE, POWERED, BREACHED, NO_CONTRA})
            assertThat(cycle.tick(situation, 1_000)).isEqualTo(Event.NONE);
        assertThat(cycle.phase()).isEqualTo(Phase.COLD);
        assertThat(cycle.charge()).isZero();
        assertThat(cycle.burns()).isFalse();
    }

    @Test
    void lightingNeedsAWholeContainment()
    {
        var cycle = new ReactorCycle();
        assertThat(cycle.light(BREACHED)).isEqualTo(Refusal.INCOMPLETE);
        assertThat(cycle.phase()).isEqualTo(Phase.COLD);
    }

    @Test
    void lightingIsRefusedWhileRedstoneHoldsItDown()
    {
        var cycle = new ReactorCycle();
        assertThat(cycle.light(POWERED)).isEqualTo(Refusal.POWERED);
        assertThat(cycle.phase()).isEqualTo(Phase.COLD);
    }

    @Test
    void lightingNeedsBothInputs()
    {
        var cycle = new ReactorCycle();
        assertThat(cycle.light(NO_CONTRA)).isEqualTo(Refusal.NO_FUEL);
        assertThat(cycle.light(NO_ESSENCE)).isEqualTo(Refusal.NO_FUEL);
        assertThat(cycle.phase()).isEqualTo(Phase.COLD);
        // A credit big enough for the minimum counts as fuel
        assertThat(cycle.light(new Situation(true, false, 0, 0, ReactorBalance.MIN_PAIRS))).isEqualTo(Refusal.NONE);
    }

    @Test
    void lightingTwiceIsRefused()
    {
        var cycle = new ReactorCycle();
        assertThat(cycle.light(FINE)).isEqualTo(Refusal.NONE);
        assertThat(cycle.light(FINE)).isEqualTo(Refusal.RUNNING);
        assertThat(lit().light(FINE)).isEqualTo(Refusal.RUNNING);
    }

    // Igniting

    @Test
    void ignitionGathersItsEnergyThenLights()
    {
        var cycle = new ReactorCycle();
        cycle.light(FINE);
        assertThat(cycle.phase()).isEqualTo(Phase.IGNITING);
        assertThat(cycle.ignitionWanted()).isEqualTo(ReactorBalance.IGNITION);
        assertThat(cycle.burns()).isFalse();

        var half = ReactorBalance.IGNITION / 2;
        assertThat(cycle.tick(FINE, half)).isEqualTo(Event.NONE);
        assertThat(cycle.charge()).isEqualTo(half);
        assertThat(cycle.ignitionWanted()).isEqualTo(ReactorBalance.IGNITION - half);

        assertThat(cycle.tick(FINE, ReactorBalance.IGNITION)).isEqualTo(Event.LIT);
        assertThat(cycle.phase()).isEqualTo(Phase.LIT);
        assertThat(cycle.charge()).isZero();
        assertThat(cycle.ignitionWanted()).isZero();
        assertThat(cycle.burns()).isTrue();
    }

    @Test
    void aFullChargeWaitsForBothInputsBeforeLighting()
    {
        var cycle = new ReactorCycle();
        cycle.light(FINE);
        assertThat(cycle.tick(NO_ESSENCE, ReactorBalance.IGNITION)).isEqualTo(Event.NONE);
        assertThat(cycle.phase()).isEqualTo(Phase.IGNITING);
        assertThat(cycle.ignitionWanted()).isZero();
        assertThat(cycle.tick(FINE, 0)).isEqualTo(Event.LIT);
    }

    @Test
    void aBreachDuringIgnitionAbortsItAndKeepsTheCharge()
    {
        var cycle = new ReactorCycle();
        cycle.light(FINE);
        cycle.tick(FINE, 50_000);
        assertThat(cycle.tick(BREACHED, 50_000)).isEqualTo(Event.ABORTED);
        assertThat(cycle.phase()).isEqualTo(Phase.COLD);
        assertThat(cycle.charge()).isEqualTo(50_000);

        cycle.light(FINE);
        assertThat(cycle.ignitionWanted()).isEqualTo(ReactorBalance.IGNITION - 50_000);
    }

    @Test
    void redstoneDuringIgnitionShutsItDownAndKeepsTheCharge()
    {
        var cycle = new ReactorCycle();
        cycle.light(FINE);
        cycle.tick(FINE, 30_000);
        assertThat(cycle.tick(POWERED, 30_000)).isEqualTo(Event.SHUT_DOWN);
        assertThat(cycle.phase()).isEqualTo(Phase.COLD);
        assertThat(cycle.charge()).isEqualTo(30_000);
    }

    @Test
    void negativeDrawsNeverLowerTheCharge()
    {
        var cycle = new ReactorCycle();
        cycle.light(FINE);
        cycle.tick(FINE, 10);
        cycle.tick(FINE, -500);
        assertThat(cycle.charge()).isEqualTo(10);
    }

    // Lit

    @Test
    void aLitReactorStaysLitWhileNothingIsWrong()
    {
        var cycle = lit();
        for (int i = 0; i < 100; i++)
            assertThat(cycle.tick(FINE, 0)).isEqualTo(Event.NONE);
        assertThat(cycle.phase()).isEqualTo(Phase.LIT);
    }

    @Test
    void redstoneShutsALitReactorDownSafely()
    {
        var cycle = lit();
        assertThat(cycle.tick(POWERED, 0)).isEqualTo(Event.SHUT_DOWN);
        assertThat(cycle.phase()).isEqualTo(Phase.COLD);
        assertThat(cycle.burns()).isFalse();
    }

    @Test
    void eachMistakeStartsTheWarning()
    {
        assertThat(warned(NO_CONTRA).causes()).isEqualTo(Cause.CONTRAVOID_DRY.bit());
        assertThat(warned(NO_ESSENCE).causes()).isEqualTo(Cause.ESSENCE_DRY.bit());
        assertThat(warned(BREACHED).causes()).isEqualTo(Cause.BREACH.bit());

        var cycle = warned(NO_CONTRA);
        assertThat(cycle.phase()).isEqualTo(Phase.FAILING);
        assertThat(cycle.countdown()).isEqualTo(ReactorBalance.COUNTDOWN);
    }

    @Test
    void aCreditCoveringTheMinimumIsNotDry()
    {
        var cycle = lit();
        assertThat(cycle.tick(new Situation(true, false, 0, 0, ReactorBalance.MIN_PAIRS), 0)).isEqualTo(Event.NONE);
        assertThat(cycle.tick(new Situation(true, false, 0, 0, ReactorBalance.MIN_PAIRS / 2), 0)).isEqualTo(Event.WARNING);
    }

    // Failing

    @Test
    void aBreachKeepsItBurningButADryInputDoesNot()
    {
        assertThat(warned(BREACHED).burns()).isTrue();
        assertThat(warned(NO_CONTRA).burns()).isFalse();
        assertThat(warned(NO_ESSENCE).burns()).isFalse();
    }

    @Test
    void theCountdownRunsOneTickAtATime()
    {
        var cycle = warned(BREACHED);
        for (int i = 1; i <= 100; i++)
        {
            assertThat(cycle.tick(BREACHED, 0)).isEqualTo(Event.NONE);
            assertThat(cycle.countdown()).isEqualTo(ReactorBalance.COUNTDOWN - i);
        }
    }

    @Test
    void replacingTheBlockEndsTheWarning()
    {
        var cycle = warned(BREACHED);
        cycle.tick(BREACHED, 0);
        assertThat(cycle.tick(FINE, 0)).isEqualTo(Event.RECOVERED);
        assertThat(cycle.phase()).isEqualTo(Phase.LIT);
        assertThat(cycle.countdown()).isZero();
        assertThat(cycle.causes()).isZero();
    }

    @Test
    void aDryInputRecoversOnlyOnceItHoldsAReserve()
    {
        var cycle = warned(NO_CONTRA);
        var trickle = new Situation(true, false, ReactorBalance.RECOVER_FUEL - 1, 1_000, 0);
        assertThat(cycle.tick(trickle, 0)).isEqualTo(Event.NONE);
        assertThat(cycle.phase()).isEqualTo(Phase.FAILING);

        var refilled = new Situation(true, false, ReactorBalance.RECOVER_FUEL, 1_000, 0);
        assertThat(cycle.tick(refilled, 0)).isEqualTo(Event.RECOVERED);
        assertThat(cycle.phase()).isEqualTo(Phase.LIT);
    }

    @Test
    void fixingOneOfTwoMistakesKeepsTheCountdownGoing()
    {
        var cycle = warned(BREACHED);
        var both = new Situation(false, false, 0, 1_000, 0);
        cycle.tick(both, 0);
        assertThat(cycle.causes()).isEqualTo(Cause.BREACH.bit() | Cause.CONTRAVOID_DRY.bit());

        assertThat(cycle.tick(NO_CONTRA, 0)).isEqualTo(Event.NONE);
        assertThat(cycle.phase()).isEqualTo(Phase.FAILING);
        assertThat(cycle.causes()).isEqualTo(Cause.CONTRAVOID_DRY.bit());
        assertThat(cycle.countdown()).isEqualTo(ReactorBalance.COUNTDOWN - 2);
    }

    @Test
    void redstoneShutsAWarnedReactorDownSafely()
    {
        var cycle = warned(NO_ESSENCE);
        for (int i = 0; i < ReactorBalance.COUNTDOWN / 2; i++)
            cycle.tick(NO_ESSENCE, 0);
        assertThat(cycle.tick(POWERED, 0)).isEqualTo(Event.SHUT_DOWN);
        assertThat(cycle.phase()).isEqualTo(Phase.COLD);
        assertThat(cycle.countdown()).isZero();
        assertThat(cycle.causes()).isZero();

        // Holding it down for far longer than the countdown never fails it
        for (int i = 0; i < ReactorBalance.COUNTDOWN * 2; i++)
            assertThat(cycle.tick(new Situation(false, true, 0, 0, 0), 0)).isEqualTo(Event.NONE);
        assertThat(cycle.phase()).isEqualTo(Phase.COLD);
    }

    @Test
    void theCountdownRunningOutCollapsesThenFails()
    {
        var cycle = warned(NO_CONTRA);
        for (int i = 1; i < ReactorBalance.COUNTDOWN; i++)
            assertThat(cycle.tick(NO_CONTRA, 0)).isEqualTo(Event.NONE);
        assertThat(cycle.tick(NO_CONTRA, 0)).isEqualTo(Event.COLLAPSING);
        assertThat(cycle.phase()).isEqualTo(Phase.COLLAPSING);
        assertThat(cycle.burns()).isFalse();

        for (int i = 1; i < ReactorBalance.COLLAPSE; i++)
            assertThat(cycle.tick(FINE, 0)).isEqualTo(Event.NONE);
        assertThat(cycle.tick(POWERED, 0)).isEqualTo(Event.FAILED);
        assertThat(cycle.phase()).isEqualTo(Phase.FAILED);

        // Nothing moves a failed reactor on
        assertThat(cycle.tick(FINE, 0)).isEqualTo(Event.NONE);
        assertThat(cycle.light(FINE)).isEqualTo(Refusal.RUNNING);
    }

    @Test
    void ticksThatNeverCome_unloaded_stopTheCountdownWhereItWas()
    {
        var cycle = warned(BREACHED);
        cycle.tick(BREACHED, 0);
        var left = cycle.countdown();

        var restored = new ReactorCycle();
        restored.restore(cycle.phase(), cycle.charge(), cycle.countdown(), cycle.causes(), cycle.collapse());
        assertThat(restored.phase()).isEqualTo(Phase.FAILING);
        assertThat(restored.countdown()).isEqualTo(left);
        assertThat(restored.causes()).isEqualTo(Cause.BREACH.bit());
        assertThat(restored.tick(BREACHED, 0)).isEqualTo(Event.NONE);
        assertThat(restored.countdown()).isEqualTo(left - 1);
    }

    // Saving

    @Test
    void aStaleSaveNeverStartsAReactorFailing()
    {
        var noCause = new ReactorCycle();
        noCause.restore(Phase.FAILING, 0, 300, 0, 0);
        assertThat(noCause.phase()).isEqualTo(Phase.LIT);

        var noTime = new ReactorCycle();
        noTime.restore(Phase.FAILING, 0, -4, Cause.BREACH.bit(), 0);
        assertThat(noTime.phase()).isEqualTo(Phase.LIT);

        var tooLong = new ReactorCycle();
        tooLong.restore(Phase.FAILING, 0, ReactorBalance.COUNTDOWN * 10, Cause.BREACH.bit() | 0xF0, 0);
        assertThat(tooLong.countdown()).isEqualTo(ReactorBalance.COUNTDOWN);
        assertThat(tooLong.causes()).isEqualTo(Cause.BREACH.bit());

        var overcharged = new ReactorCycle();
        overcharged.restore(Phase.COLD, ReactorBalance.IGNITION * 3, 99, Cause.BREACH.bit(), 0);
        assertThat(overcharged.charge()).isEqualTo(ReactorBalance.IGNITION);
        assertThat(overcharged.countdown()).isZero();
        assertThat(overcharged.causes()).isZero();

        var collapsing = new ReactorCycle();
        collapsing.restore(Phase.COLLAPSING, 0, 0, 0, 0);
        assertThat(collapsing.tick(FINE, 0)).isEqualTo(Event.FAILED);
    }
}
