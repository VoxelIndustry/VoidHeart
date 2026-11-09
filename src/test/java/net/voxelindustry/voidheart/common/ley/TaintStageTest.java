package net.voxelindustry.voidheart.common.ley;

import net.voxelindustry.voidheart.common.energy.FoundationBalance;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaintStageTest
{
    @Test
    void stagesBeginAtTheirThresholds()
    {
        assertThat(TaintStage.of(0)).isEqualTo(TaintStage.CLEAN);
        assertThat(TaintStage.of(Math.nextDown(FoundationBalance.TAINTED_FROM))).isEqualTo(TaintStage.CLEAN);
        assertThat(TaintStage.of(FoundationBalance.TAINTED_FROM)).isEqualTo(TaintStage.TAINTED);
        assertThat(TaintStage.of(FoundationBalance.FESTERING_FROM)).isEqualTo(TaintStage.FESTERING);
        assertThat(TaintStage.of(FoundationBalance.BREACHED_FROM)).isEqualTo(TaintStage.BREACHED);
        assertThat(TaintStage.of(FoundationBalance.MAX_TAINT)).isEqualTo(TaintStage.BREACHED);
    }

    @Test
    void thresholdsRiseWithinTheTaintRange()
    {
        assertThat(FoundationBalance.TAINTED_FROM).isPositive();
        assertThat(FoundationBalance.FESTERING_FROM).isGreaterThan(FoundationBalance.TAINTED_FROM);
        assertThat(FoundationBalance.BREACHED_FROM).isGreaterThan(FoundationBalance.FESTERING_FROM);
        assertThat(FoundationBalance.MAX_TAINT).isGreaterThanOrEqualTo(FoundationBalance.BREACHED_FROM);
    }

    @Test
    void stagesAreOrdered()
    {
        assertThat(TaintStage.FESTERING.isAtLeast(TaintStage.TAINTED)).isTrue();
        assertThat(TaintStage.TAINTED.isAtLeast(TaintStage.TAINTED)).isTrue();
        assertThat(TaintStage.CLEAN.isAtLeast(TaintStage.TAINTED)).isFalse();
    }
}
