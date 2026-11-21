package net.voxelindustry.voidheart.common.content.armor;

import net.voxelindustry.voidheart.common.content.armor.LoadShedding.Stage;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static net.voxelindustry.voidheart.common.content.armor.ArmorModule.*;
import static org.assertj.core.api.Assertions.assertThat;

class ArmorDrainTest
{
    private static final ArmorStats EVERYTHING = new ArmorStats(1, true, true, 1);
    private static final long       CAPACITY   = EVERYTHING.capacity();

    private static long at(double fill)
    {
        return Math.round(CAPACITY * fill);
    }

    @Test
    void shedsInOrderThenGoesLimp()
    {
        assertThat(LoadShedding.stage(CAPACITY, CAPACITY)).isEqualTo(Stage.FULL);
        assertThat(LoadShedding.stage(at(0.19), CAPACITY)).isEqualTo(Stage.LOW);
        assertThat(LoadShedding.stage(at(0.14), CAPACITY)).isEqualTo(Stage.NO_SPECTRAL);
        assertThat(LoadShedding.stage(at(0.09), CAPACITY)).isEqualTo(Stage.NO_NIGHT_VISION);
        assertThat(LoadShedding.stage(at(0.04), CAPACITY)).isEqualTo(Stage.NO_EXOSKELETON);
        assertThat(LoadShedding.stage(at(0.01), CAPACITY)).isEqualTo(Stage.NO_REGEN);
        assertThat(LoadShedding.stage(1, CAPACITY)).isEqualTo(Stage.NO_REGEN);
        assertThat(LoadShedding.stage(0, CAPACITY)).isEqualTo(Stage.LIMP);
    }

    @Test
    void eachStageKeepsWhatComesAfterItInTheOrder()
    {
        var stages = Stage.values();
        for (int i = 1; i < stages.length; i++)
            for (var module : ArmorModule.values())
                if (!stages[i - 1].keeps(module))
                    assertThat(stages[i].keeps(module)).as("%s once cut stays cut at %s", module, stages[i]).isFalse();

        assertThat(Stage.NO_SPECTRAL.keeps(NIGHT_VISION)).isTrue();
        assertThat(Stage.NO_NIGHT_VISION.keeps(EXOSKELETON_POWER)).isTrue();
        assertThat(Stage.NO_EXOSKELETON.regen()).isTrue();
        assertThat(Stage.NO_REGEN.regen()).isFalse();
        assertThat(Stage.LIMP.limp()).isTrue();
        assertThat(Stage.NO_REGEN.limp()).isFalse();
    }

    @Test
    void sightsRunOnlyWhenSwitchedOnFittedAndKept()
    {
        var off = ArmorLoad.of(EVERYTHING, Set.of(), Stage.FULL);
        assertThat(off.nightVision()).isFalse();
        assertThat(off.spectral()).isFalse();
        assertThat(off.exoskeleton()).as("exoskeleton power is passive").isTrue();

        var on = ArmorLoad.of(EVERYTHING, Set.of(NIGHT_VISION, SPECTRAL_LENS), Stage.LOW);
        assertThat(on.nightVision()).isTrue();
        assertThat(on.spectral()).isTrue();

        var shed = ArmorLoad.of(EVERYTHING, Set.of(NIGHT_VISION, SPECTRAL_LENS), Stage.NO_SPECTRAL);
        assertThat(shed.nightVision()).isTrue();
        assertThat(shed.spectral()).isFalse();

        var unfitted = ArmorLoad.of(new ArmorStats(0, false, false, 0), Set.of(NIGHT_VISION), Stage.FULL);
        assertThat(unfitted).isEqualTo(ArmorLoad.NONE);
    }

    @Test
    void steadyDrainAddsWhatRuns()
    {
        assertThat(ArmorDrain.perTick(ArmorLoad.NONE)).isEqualTo(ArmorBalance.STANDING_DRAIN);
        var everything = ArmorLoad.of(EVERYTHING, Set.of(NIGHT_VISION, SPECTRAL_LENS), Stage.FULL);
        assertThat(ArmorDrain.perTick(everything)).isEqualTo(ArmorBalance.STANDING_DRAIN + ArmorBalance.EXOSKELETON_DRAIN
                + ArmorBalance.NIGHT_VISION_DRAIN + ArmorBalance.SPECTRAL_DRAIN);
        assertThat(ArmorDrain.perTick(ArmorLoad.of(EVERYTHING, Set.of(), Stage.LIMP))).isZero();
    }

    @Test
    void sprintingCostsMoreByTheBlockAndStandingCostsNothing()
    {
        assertThat(ArmorDrain.walking(0, true)).isZero();
        assertThat(ArmorDrain.walking(-1, false)).isZero();
        assertThat(ArmorDrain.walking(1, true)).isGreaterThan(ArmorDrain.walking(1, false));
        assertThat(ArmorDrain.walking(2, false)).isEqualTo(2 * ArmorDrain.walking(1, false));
    }

    @Test
    void legsTakeWhatTheChargePaysForInWholePoints()
    {
        long point = ArmorBalance.FALL_DRAIN_PER_POINT;
        assertThat(ArmorDrain.fallTaken(6, 100 * point)).isEqualTo(6);
        assertThat(ArmorDrain.fallTaken(6, 4 * point + point / 2)).isEqualTo(4);
        assertThat(ArmorDrain.fallTaken(6, point - 1)).isZero();
        assertThat(ArmorDrain.fallTaken(0, 100 * point)).isZero();
        assertThat(ArmorDrain.fallCost(4)).isEqualTo(4 * point);
    }

    @Test
    void framePowersTheSocketedShieldUntilItCutsRegen()
    {
        assertThat(ArmorDrain.shieldRegen(0.2F, Stage.FULL)).isEqualTo(0.2F * ArmorBalance.REGEN_BOOST);
        assertThat(ArmorDrain.shieldRegen(0.2F, Stage.NO_EXOSKELETON)).isEqualTo(0.2F * ArmorBalance.REGEN_BOOST);
        assertThat(ArmorDrain.shieldRegen(0.2F, Stage.NO_REGEN)).isZero();
        assertThat(ArmorDrain.shieldRegen(0.2F, Stage.LIMP)).isZero();
        assertThat(LoadShedding.stage(at(0.019), CAPACITY)).isEqualTo(Stage.NO_REGEN);
    }

    @Test
    void shieldCostsByThePointRefilled()
    {
        assertThat(ArmorDrain.shieldCost(2.5F)).isEqualTo(2.5 * ArmorBalance.REGEN_DRAIN_PER_POINT);
        assertThat(ArmorDrain.shieldCost(0)).isZero();
        assertThat(ArmorDrain.shieldCost(-1)).isZero();
    }
}
