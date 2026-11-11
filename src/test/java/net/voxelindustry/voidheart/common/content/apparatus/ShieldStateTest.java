package net.voxelindustry.voidheart.common.content.apparatus;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ShieldStateTest
{
    private static final ShieldStats STATS = new ShieldStats(8, 0.1F, 40, Set.of(), Optional.empty());

    @Test
    void aNewApparatusStartsEmptyAndWaiting()
    {
        var state = ShieldState.NONE.fitTo(STATS);
        assertThat(state.points()).isZero();
        assertThat(state.capacity()).isEqualTo(8);
        assertThat(state.delay()).isEqualTo(40);
    }

    @Test
    void removingTheApparatusDropsTheShield()
    {
        assertThat(new ShieldState(5, 8, 0).fitTo(null)).isEqualTo(ShieldState.NONE);
    }

    @Test
    void aSmallerApparatusCutsThePointsDown()
    {
        var smaller = new ShieldStats(4, 0.1F, 40, Set.of(), Optional.empty());
        var state = new ShieldState(7, 8, 0).fitTo(smaller);
        assertThat(state.points()).isEqualTo(4);
        assertThat(state.capacity()).isEqualTo(4);
    }

    @Test
    void itWaitsThenRefillsUpToCapacity()
    {
        var state = new ShieldState(0, 8, 2);
        state = state.tick(STATS).tick(STATS);
        assertThat(state.points()).isZero();
        assertThat(state.delay()).isZero();

        for (int i = 0; i < 200; i++)
            state = state.tick(STATS);
        assertThat(state.points()).isEqualTo(8);
        assertThat(state.isFull()).isTrue();
    }

    @Test
    void aHitSmallerThanTheShieldIsStoppedWhole()
    {
        var hit = new ShieldState(8, 8, 0).absorb(STATS, 3);
        assertThat(hit.absorbed()).isEqualTo(3);
        assertThat(hit.state().points()).isEqualTo(5);
        assertThat(hit.broke()).isFalse();
        assertThat(hit.state().delay()).isEqualTo(40);
    }

    @Test
    void aHitLargerThanTheShieldBreaksItAndPassesTheRest()
    {
        var hit = new ShieldState(5, 8, 0).absorb(STATS, 9);
        assertThat(hit.absorbed()).isEqualTo(5);
        assertThat(hit.state().points()).isZero();
        assertThat(hit.broke()).isTrue();
        assertThat(hit.state().delay()).isEqualTo(STATS.brokenDelayTicks());
    }

    @Test
    void anEmptyShieldDoesNotBreakAgain()
    {
        var hit = new ShieldState(0, 8, 10).absorb(STATS, 4);
        assertThat(hit.absorbed()).isZero();
        assertThat(hit.broke()).isFalse();
        assertThat(hit.state().delay()).isEqualTo(40);
    }

    @Test
    void aHitNeverShortensTheWait()
    {
        var hit = new ShieldState(0, 8, 55).absorb(STATS, 4);
        assertThat(hit.state().delay()).isEqualTo(55);
    }

    @Test
    void aWardedHitCostsHalf()
    {
        var hit = new ShieldState(4, 8, 0).absorb(STATS, 6, true);
        assertThat(hit.absorbed()).isEqualTo(6);
        assertThat(hit.state().points()).isCloseTo(1, within(0.0001F));

        var big = new ShieldState(4, 8, 0).absorb(STATS, 20, true);
        assertThat(big.absorbed()).isEqualTo(8);
        assertThat(big.broke()).isTrue();
    }

    @Test
    void nothingIsAbsorbedFromNoDamage()
    {
        var state = new ShieldState(4, 8, 0);
        assertThat(state.absorb(STATS, 0).state()).isEqualTo(state);
    }
}
