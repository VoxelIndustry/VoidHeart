package net.voxelindustry.voidheart.common.content.mob;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InfectionRulesTest
{
    @Test
    void firstHit_startsTheTimer()
    {
        assertThat(InfectionRules.amountAfterHit(0)).isEqualTo(1);
        assertThat(InfectionRules.ticksAfterHit(0, 0)).isEqualTo(InfectionRules.DURATION_TICKS);
    }

    @Test
    void laterHits_addToTheAmountWithoutTouchingTheTimer()
    {
        assertThat(InfectionRules.amountAfterHit(2)).isEqualTo(3);
        assertThat(InfectionRules.ticksAfterHit(2, 500)).isEqualTo(500);
    }

    @Test
    void amount_stopsAtItsLimit()
    {
        assertThat(InfectionRules.amountAfterHit(InfectionRules.MAX_AMOUNT)).isEqualTo(InfectionRules.MAX_AMOUNT);
    }

    @Test
    void warnings_onlyComeInTheLastSecondsAndSpeedUp()
    {
        assertThat(InfectionRules.isWarning(InfectionRules.WARNING_TICKS + 1)).isFalse();
        assertThat(InfectionRules.isWarning(InfectionRules.WARNING_TICKS)).isTrue();
        assertThat(InfectionRules.warningInterval(InfectionRules.WARNING_TICKS)).isGreaterThan(InfectionRules.warningInterval(20));
    }
}
