package net.voxelindustry.voidheart.common.energy.machine;

import net.voxelindustry.voidheart.common.world.dungeon.DungeonSize;
import org.junit.jupiter.api.Test;

import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class MachineEnergyTest
{
    @Test
    void openingsCostMoreForLargerDungeons()
    {
        assertThat(MachineEnergy.dungeonOpening(DungeonSize.SMALL)).isEqualTo(60_000);
        assertThat(MachineEnergy.dungeonOpening(DungeonSize.MEDIUM)).isEqualTo(90_000);
        assertThat(MachineEnergy.dungeonOpening(DungeonSize.LARGE)).isEqualTo(120_000);
    }

    @Test
    void brewCostsPerAspectAndPerRefinement()
    {
        // The plain brews of the numbers: 300 to 1000 aspect points cost 7.2k to 24k
        assertThat(MachineEnergy.brew(300, 0)).isEqualTo(7_200);
        assertThat(MachineEnergy.brew(1000, 0)).isEqualTo(24_000);
        assertThat(MachineEnergy.brew(300, 1)).isEqualTo(10_800);
        assertThat(MachineEnergy.brew(300, 3)).isEqualTo(24_300);
        assertThat(MachineEnergy.brew(-5, 2)).isZero();
    }

    @Test
    void ticksOfARunAddUpToItsCost()
    {
        for (long cost : new long[] { 7_200, 10_801, 3, 0 })
        {
            int ticks = 180;
            long sum = LongStream.range(0, ticks).map(tick -> MachineEnergy.shareOfTick(cost, ticks, (int) tick)).sum();
            assertThat(sum).isEqualTo(cost);
        }
        assertThat(MachineEnergy.shareOfTick(7_200, 180, 0)).isEqualTo(40);
        assertThat(MachineEnergy.shareOfTick(7_200, 180, 180)).isZero();
        assertThat(MachineEnergy.shareOfTick(7_200, 0, 0)).isZero();
    }

    @Test
    void beholderIsBlindUnderATenthOfItsLey()
    {
        assertThat(MachineEnergy.beholderSight(0.09F)).isZero();
        assertThat(MachineEnergy.beholderSight(0.1F)).isEqualTo(0.1F);
        assertThat(MachineEnergy.beholderSight(0.6F)).isEqualTo(0.6F);
        assertThat(MachineEnergy.beholderSight(1.4F)).isEqualTo(1);
    }

    @Test
    void chargeOnlyShowsFullOnceComplete()
    {
        assertThat(MachineEnergy.percent(0, 60_000)).isZero();
        assertThat(MachineEnergy.percent(59_999, 60_000)).isEqualTo(99);
        assertThat(MachineEnergy.percent(60_000, 60_000)).isEqualTo(100);
        assertThat(MachineEnergy.percent(5, 0)).isEqualTo(100);
    }

    @Test
    void creditSlowsAMachineOnAWeakSupplyWithoutLosingAnything()
    {
        var credit = new DrawCredit();
        int paid = 0;
        long drawn = 0;
        for (int tick = 0; tick < 100; tick++)
        {
            if (credit.pay(8, amount -> Math.min(amount, 4)))
                paid++;
            drawn += credit.lastDrawn();
        }
        assertThat(paid).isEqualTo(50);
        assertThat(drawn).isEqualTo(paid * 8L + credit.credit());
    }

    @Test
    void creditStopsAMachineWithNoSupply()
    {
        var credit = new DrawCredit();
        assertThat(credit.pay(8, amount -> 0)).isFalse();
        assertThat(credit.pay(8, amount -> amount)).isTrue();
        assertThat(credit.credit()).isZero();
        assertThat(credit.pay(0, amount -> 0)).isTrue();
    }

    @Test
    void meterJudgesAWholeSecond()
    {
        var meter = new PowerMeter();
        for (int tick = 0; tick < PowerMeter.WINDOW - 1; tick++)
            assertThat(meter.record(8, 0)).isFalse();
        assertThat(meter.record(8, 0)).isTrue();
        assertThat(meter.state()).isEqualTo(MachinePower.UNPOWERED);

        boolean changed = false;
        for (int tick = 0; tick < PowerMeter.WINDOW; tick++)
            changed |= meter.record(8, tick % 2 == 0 ? 8 : 0);
        assertThat(changed).isTrue();
        assertThat(meter.state()).isEqualTo(MachinePower.WEAK);

        for (int tick = 0; tick < PowerMeter.WINDOW; tick++)
            meter.record(0, 0);
        assertThat(meter.state()).isEqualTo(MachinePower.IDLE);
    }

    @Test
    void powerOfADraw()
    {
        assertThat(MachinePower.of(0, 0)).isEqualTo(MachinePower.IDLE);
        assertThat(MachinePower.of(8, 8)).isEqualTo(MachinePower.POWERED);
        assertThat(MachinePower.of(8, 3)).isEqualTo(MachinePower.WEAK);
        assertThat(MachinePower.of(8, 0)).isEqualTo(MachinePower.UNPOWERED);
        assertThat(MachinePower.byOrdinal(42)).isEqualTo(MachinePower.IDLE);
    }
}
