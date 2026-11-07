package net.voxelindustry.voidheart.common.content.research;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class StrainTest
{
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    @Test
    void tolerance_growsWithEachStepOfAttunement()
    {
        assertThat(Strain.tolerance(0)).isZero();
        assertThat(Strain.tolerance(1)).isEqualTo(10);
        assertThat(Strain.tolerance(2)).isEqualTo(25);
        assertThat(Strain.tolerance(3)).isEqualTo(45);
        assertThat(Strain.tolerance(9)).isEqualTo(45);
    }

    @Test
    void proxiesNeeded_roundsTheMissingStrainUp()
    {
        assertThat(Strain.proxiesNeeded(10, 10, 10)).isZero();
        assertThat(Strain.proxiesNeeded(20, 10, 10)).isEqualTo(1);
        assertThat(Strain.proxiesNeeded(25, 10, 10)).isEqualTo(2);
        assertThat(Strain.proxiesNeeded(45, 0, 10)).isEqualTo(5);
    }

    @Test
    void cover_givenNothingMissing_thenCoversAllAndWearsNothing()
    {
        var cover = Strain.cover(Map.of(A, 5D), 0, 100);

        assertThat(cover.seconds()).isEqualTo(100);
        assertThat(cover.proxies()).containsEntry(A, 5D);
    }

    @Test
    void cover_givenTooFewProxies_thenCoversNothing()
    {
        var cover = Strain.cover(Map.of(A, 50D), 2, 10);

        assertThat(cover.seconds()).isZero();
        assertThat(cover.proxies()).containsEntry(A, 50D);
    }

    @Test
    void cover_wearsTheMostWornFirstAndHandsOverWhenItGivesOut()
    {
        var cover = Strain.cover(Map.of(A, 30D, B, 10D), 1, 25);

        assertThat(cover.seconds()).isEqualTo(25);
        assertThat(cover.proxies().get(B)).isZero();
        assertThat(cover.proxies().get(A)).isCloseTo(15, within(1E-9));
    }

    @Test
    void cover_stopsWhenTheLastNeededProxyGivesOut()
    {
        var cover = Strain.cover(Map.of(A, 30D, B, 10D), 1, 100);

        assertThat(cover.seconds()).isCloseTo(40, within(1E-9));
        assertThat(cover.proxies().values()).allMatch(left -> left == 0);
    }

    @Test
    void cover_givenTwoNeeded_thenEndsWhenFewerThanTwoRemain()
    {
        var cover = Strain.cover(Map.of(A, 10D, B, 10D, C, 30D), 2, 100);

        // A and B bear together, one hands over to C after 10 seconds, the other gives out at the same time and leaves C alone
        assertThat(cover.seconds()).isCloseTo(10, within(1E-9));
        assertThat(cover.proxies().get(C)).isEqualTo(30);
    }

    @Test
    void cover_ignoresProxiesAlreadyWornOut()
    {
        assertThat(Strain.cover(Map.of(A, 0D), 1, 10).seconds()).isZero();
        assertThat(Strain.alive(Map.of(A, 0D, B, 3D))).isEqualTo(1);
    }

    @Test
    void cover_cutIntoPieces_thenWearsTheSame()
    {
        Map<UUID, Double> proxies = Map.of(A, 30D, B, 10D, C, 20D);
        for (int step = 0; step < 7; step++)
            proxies = Strain.cover(proxies, 1, 5).proxies();

        var whole = Strain.cover(Map.of(A, 30D, B, 10D, C, 20D), 1, 35).proxies();
        assertThat(proxies).isEqualTo(whole);
    }

    @Test
    void secondsCovered_addsUpEveryProxyWhenOneIsNeeded()
    {
        assertThat(Strain.secondsCovered(Map.of(A, 30D, B, 10D), 1)).isCloseTo(40, within(1E-9));
        assertThat(Strain.secondsCovered(Map.of(), 0)).isInfinite();
    }
}
