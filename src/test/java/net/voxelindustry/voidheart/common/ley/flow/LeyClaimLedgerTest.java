package net.voxelindustry.voidheart.common.ley.flow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LeyClaimLedgerTest
{
    private static final int HOLD = 40;
    private static final String LINE = "line";
    private static final long UPSTREAM = 1, MIDDLE = 2, DOWNSTREAM = 3;

    private final LeyClaimLedger ledger = new LeyClaimLedger(HOLD);

    @Test
    void aloneAClaimantGetsWhatItAsksUpToTheFlow()
    {
        assertThat(ledger.claim(LINE, UPSTREAM, 0.2, 5, 3, 0)).isEqualTo(3);
        assertThat(ledger.claim(LINE, UPSTREAM, 0.2, 5, 8, 1)).isEqualTo(5);
        assertThat(ledger.claim(LINE, UPSTREAM, 0.2, 5, 0, 2)).isZero();
    }

    @Test
    void upstreamIsServedFirstWhateverOrderTheyClaimIn()
    {
        // Downstream claims first each tick, as block entity order may have it
        for (long tick = 0; tick < 3; tick++)
        {
            ledger.claim(LINE, DOWNSTREAM, 0.8, 5, 4, tick);
            ledger.claim(LINE, UPSTREAM, 0.2, 5, 4, tick);
        }

        assertThat(ledger.claim(LINE, DOWNSTREAM, 0.8, 5, 4, 3)).isEqualTo(1);
        assertThat(ledger.claim(LINE, UPSTREAM, 0.2, 5, 4, 3)).isEqualTo(4);
        assertThat(ledger.granted(LINE, 3)).isEqualTo(5);
    }

    @Test
    void threeAlongALineShareInFlowOrder()
    {
        for (long tick = 0; tick < 3; tick++)
        {
            ledger.claim(LINE, MIDDLE, 0.5, 6, 3, tick);
            ledger.claim(LINE, DOWNSTREAM, 0.9, 6, 3, tick);
            ledger.claim(LINE, UPSTREAM, 0.1, 6, 2, tick);
        }

        assertThat(ledger.claims(LINE, 2)).extracting(LeyClaimLedger.Claim::claimant).containsExactly(UPSTREAM, MIDDLE, DOWNSTREAM);
        assertThat(ledger.claims(LINE, 2)).extracting(LeyClaimLedger.Claim::granted).containsExactly(2D, 3D, 1D);
    }

    @Test
    void atTheSameSpotTheOneAlreadyDrawingKeepsItsShare()
    {
        for (long tick = 0; tick < 5; tick++)
            ledger.claim(LINE, MIDDLE, 0, 5, 4, tick);

        assertThat(ledger.claim(LINE, UPSTREAM, 0, 5, 4, 5)).isEqualTo(1);
        assertThat(ledger.claim(LINE, MIDDLE, 0, 5, 4, 5)).isEqualTo(4);
        assertThat(ledger.claim(LINE, UPSTREAM, 0, 5, 4, 6)).isEqualTo(1);
    }

    @Test
    void aClaimantThatStopsReleasesItsShareAfterTheHold()
    {
        ledger.claim(LINE, UPSTREAM, 0.1, 5, 5, 0);

        assertThat(ledger.claim(LINE, DOWNSTREAM, 0.9, 5, 5, HOLD)).isZero();
        assertThat(ledger.claim(LINE, DOWNSTREAM, 0.9, 5, 5, HOLD + 1)).isEqualTo(5);
    }

    @Test
    void lessFlowIsTakenFromDownstreamFirst()
    {
        for (long tick = 0; tick < 2; tick++)
        {
            ledger.claim(LINE, UPSTREAM, 0.1, 6, 3, tick);
            ledger.claim(LINE, DOWNSTREAM, 0.9, 6, 3, tick);
        }

        // A new moon or taint cuts the flow to 4
        assertThat(ledger.claim(LINE, UPSTREAM, 0.1, 4, 3, 2)).isEqualTo(3);
        assertThat(ledger.claim(LINE, DOWNSTREAM, 0.9, 4, 3, 2)).isEqualTo(1);
    }

    @Test
    void poolsAreApart()
    {
        ledger.claim(LINE, UPSTREAM, 0.1, 5, 5, 0);

        assertThat(ledger.claim("other line", DOWNSTREAM, 0.9, 5, 5, 0)).isEqualTo(5);
    }

    @Test
    void pruneForgetsStoppedClaimants()
    {
        ledger.claim(LINE, UPSTREAM, 0.1, 5, 2, 0);
        ledger.claim(LINE, DOWNSTREAM, 0.9, 5, 2, 30);

        ledger.prune(HOLD + 1);

        assertThat(ledger.claims(LINE, HOLD + 1)).extracting(LeyClaimLedger.Claim::claimant).containsExactly(DOWNSTREAM);
        assertThat(ledger.granted(LINE, HOLD + 1)).isEqualTo(2);
    }
}
