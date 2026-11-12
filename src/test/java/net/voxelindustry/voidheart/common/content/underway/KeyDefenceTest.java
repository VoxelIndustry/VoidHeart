package net.voxelindustry.voidheart.common.content.underway;

import net.voxelindustry.voidheart.common.content.underway.KeyDefence.Outcome;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeyDefenceTest
{
    @Test
    void shieldTakesAHitPerPaneThenTheKeyTakesItsOwn()
    {
        var defence = new KeyDefence();
        for (int hit = 1; hit < KeyDefence.PANES; hit++)
        {
            assertThat(defence.hit(0, -3)).isEqualTo(Outcome.ABSORBED);
            assertThat(Integer.bitCount(defence.panes())).isEqualTo(KeyDefence.PANES - hit);
        }
        assertThat(defence.hit(0, -3)).isEqualTo(Outcome.SHIELD_BROKEN);
        assertThat(defence.isShielded()).isFalse();
        for (int hit = 1; hit < HatchBalance.KEY_HITS; hit++)
            assertThat(defence.hit(0, -3)).isEqualTo(Outcome.KEY_HIT);
        assertThat(defence.hit(0, -3)).isEqualTo(Outcome.KEY_DESTROYED);
    }

    @Test
    void aBlowDarkensThePaneItCameThrough()
    {
        var defence = new KeyDefence();
        assertThat(defence.paneFor(0, -3)).isZero();
        assertThat(defence.paneFor(3, 1)).isEqualTo(1);
        assertThat(defence.paneFor(-1, 3)).isEqualTo(2);
        assertThat(defence.paneFor(-3, 0)).isEqualTo(3);

        defence.hit(3, 1);
        assertThat(defence.panes() & 1 << 1).isZero();
        assertThat(defence.paneFor(3, 1)).isEqualTo(KeyDefence.ROOF_EAST);
        defence.hit(3, 1);
        assertThat(defence.paneFor(3, 1)).isEqualTo(KeyDefence.ROOF_WEST);
    }

    @Test
    void shieldComesBackWholeAfterALullButTheKeyDoesNotMend()
    {
        var defence = new KeyDefence();
        for (int hit = 0; hit <= KeyDefence.PANES; hit++)
            defence.hit(1, 0);
        assertThat(defence.keyHits()).isEqualTo(HatchBalance.KEY_HITS - 1);

        for (int tick = 1; tick < HatchBalance.SHIELD_LULL_TICKS; tick++)
            assertThat(defence.tick()).isFalse();
        assertThat(defence.tick()).isTrue();
        assertThat(defence.panes()).isEqualTo(KeyDefence.ALL_PANES);
        assertThat(defence.keyHits()).isEqualTo(HatchBalance.KEY_HITS - 1);
        assertThat(defence.tick()).isFalse();
    }

    @Test
    void aHitStartsTheLullAgain()
    {
        var defence = new KeyDefence();
        defence.hit(1, 0);
        for (int tick = 1; tick < HatchBalance.SHIELD_LULL_TICKS; tick++)
            defence.tick();
        defence.hit(-1, 0);
        assertThat(defence.tick()).isFalse();
        assertThat(Integer.bitCount(defence.panes())).isEqualTo(KeyDefence.PANES - 2);
    }
}
