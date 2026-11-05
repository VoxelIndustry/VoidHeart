package net.voxelindustry.voidheart.common.content.mob;

import net.voxelindustry.voidheart.common.content.mob.ArcaneStalk.Move;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArcaneStalkTest
{
    @Test
    void canFire_neverBeyondFiveBlocks()
    {
        assertThat(ArcaneStalk.canFire(5, true)).isTrue();
        assertThat(ArcaneStalk.canFire(5.01, true)).isFalse();
        assertThat(ArcaneStalk.canFire(100, true)).isFalse();
    }

    @Test
    void canFire_onlyAtWhatItSees()
    {
        assertThat(ArcaneStalk.canFire(3, false)).isFalse();
    }

    @Test
    void decide_closesInWhenUnwatchedUntilInRange()
    {
        assertThat(ArcaneStalk.decide(20, false, false)).isEqualTo(Move.CLOSE_IN);
        assertThat(ArcaneStalk.decide(6, false, false)).isEqualTo(Move.CLOSE_IN);
        assertThat(ArcaneStalk.decide(4, false, false)).isEqualTo(Move.HOLD);
    }

    @Test
    void decide_stopsOrDriftsBackWhenWatched()
    {
        assertThat(ArcaneStalk.decide(20, true, false)).isEqualTo(Move.HOLD);
        assertThat(ArcaneStalk.decide(ArcaneStalk.FOLLOW_NEAR, true, false)).isEqualTo(Move.HOLD);
        assertThat(ArcaneStalk.decide(6, true, false)).isEqualTo(Move.DRIFT_BACK);
    }

    @Test
    void decide_backsOffAfterFiringWatchedOrNot()
    {
        assertThat(ArcaneStalk.decide(4, false, true)).isEqualTo(Move.BACK_OFF);
        assertThat(ArcaneStalk.decide(4, true, true)).isEqualTo(Move.BACK_OFF);
        assertThat(ArcaneStalk.decide(9, false, true)).isEqualTo(Move.HOLD);
    }
}
