package net.voxelindustry.voidheart.common.energy.grid;

import net.voxelindustry.voidheart.common.energy.grid.GridRouter.Demand;
import net.voxelindustry.voidheart.common.energy.grid.GridRouter.Supply;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GridRouterTest
{
    private static int[] at(int... pylons)
    {
        return pylons;
    }

    private static Supply transit(long amount, int... pylons)
    {
        return new Supply(amount, pylons, false);
    }

    private static Supply stored(long amount, int... pylons)
    {
        return new Supply(amount, pylons, true);
    }

    private static Demand machine(long amount, int... pylons)
    {
        return new Demand(amount, pylons, true);
    }

    private static Demand charging(long amount, int... pylons)
    {
        return new Demand(amount, pylons, false);
    }

    @Test
    void onePylonPassesUpToItsCap()
    {
        long[] caps = { 80 };
        var result = GridRouter.route(caps, new int[0][], List.of(transit(500, 0)), List.of(machine(500, 0)));

        assertThat(result.delivered()[0]).isEqualTo(80);
        assertThat(result.taken()[0]).isEqualTo(80);
        assertThat(result.throughput()[0]).isEqualTo(80);
        assertThat(caps[0]).isZero();
    }

    @Test
    void aChainCarriesWithoutLossAndEveryPylonCountsWhatPasses()
    {
        long[] caps = { 192, 128, 80 };
        int[][] arcs = { { 0, 1 }, { 1, 2 } };

        var small = GridRouter.route(caps, arcs, List.of(transit(1000, 0)), List.of(machine(50, 2)));
        assertThat(small.delivered()[0]).isEqualTo(50);
        assertThat(small.taken()[0]).isEqualTo(50);
        assertThat(small.throughput()).containsExactly(50, 50, 50);
        assertThat(small.arcFlow()).containsExactly(50, 50);

        // The last pylon has 30 of its 80 left
        var large = GridRouter.route(caps, arcs, List.of(transit(1000, 0)), List.of(machine(1000, 2)));
        assertThat(large.delivered()[0]).isEqualTo(30);
        assertThat(caps).containsExactly(112, 48, 0);
    }

    @Test
    void flowRunsAgainstTheArcOrderToo()
    {
        long[] caps = { 80, 80 };
        var result = GridRouter.route(caps, new int[][] { { 0, 1 } }, List.of(transit(40, 1)), List.of(machine(40, 0)));

        assertThat(result.delivered()[0]).isEqualTo(40);
        assertThat(result.arcFlow()[0]).isEqualTo(-40);
    }

    @Test
    void severalPathsAddUp()
    {
        // 0 splits through 1 and 2, which meet again at 3
        long[] caps = { 192, 50, 60, 192 };
        int[][] arcs = { { 0, 1 }, { 0, 2 }, { 1, 3 }, { 2, 3 } };

        var result = GridRouter.route(caps, arcs, List.of(transit(1000, 0)), List.of(machine(1000, 3)));

        assertThat(result.delivered()[0]).isEqualTo(110);
        assertThat(result.throughput()).containsExactly(110, 50, 60, 110);
    }

    @Test
    void aHubSaturatesAndServesItsSpokesInOrder()
    {
        // Source pylon 0, hub 1, spokes 2, 3 and 4
        long[] caps = { 192, 80, 128, 128, 128 };
        int[][] arcs = { { 0, 1 }, { 1, 2 }, { 1, 3 }, { 1, 4 } };

        var result = GridRouter.route(caps, arcs, List.of(transit(1000, 0)), List.of(machine(60, 2), machine(60, 3), machine(60, 4)));

        assertThat(result.totalDelivered()).isEqualTo(80);
        assertThat(result.delivered()).containsExactly(60, 20, 0);
        assertThat(result.throughput()[1]).isEqualTo(80);
        assertThat(caps[1]).isZero();
    }

    @Test
    void aLaterDemandReroutesAnEarlierOneRatherThanStarve()
    {
        // The first machine sits in the fields of 1 and 2, the second only in 2's
        long[] caps = { 192, 50, 50 };
        int[][] arcs = { { 0, 1 }, { 0, 2 } };

        var result = GridRouter.route(caps, arcs, List.of(transit(1000, 0)), List.of(machine(50, 1, 2), machine(50, 2)));

        assertThat(result.delivered()).containsExactly(50, 50);
    }

    @Test
    void anEarlierDemandKeepsWhatItGot()
    {
        long[] caps = { 192, 50 };
        int[][] arcs = { { 0, 1 } };

        var result = GridRouter.route(caps, arcs, List.of(transit(1000, 0)), List.of(machine(50, 1), machine(50, 1)));

        assertThat(result.delivered()).containsExactly(50, 0);
    }

    @Test
    void transitIsTakenBeforeStoredEnergy()
    {
        long[] caps = { 192 };
        var result = GridRouter.route(caps, new int[0][], List.of(stored(1000, 0), transit(30, 0)), List.of(machine(50, 0)));

        assertThat(result.taken()).containsExactly(20, 30);
        assertThat(result.delivered()[0]).isEqualTo(50);
    }

    @Test
    void holdersChargeOnlyFromTransitAndAfterMachines()
    {
        long[] caps = { 192 };
        var supplies = List.of(stored(1000, 0), transit(70, 0));
        var demands = List.of(charging(500, 0), machine(50, 0));

        var result = GridRouter.route(caps, new int[0][], supplies, demands);

        // The machine is served first, from transit; the holder gets the 20 left in transit and nothing from the other holder
        assertThat(result.delivered()).containsExactly(20, 50);
        assertThat(result.taken()).containsExactly(0, 70);
    }

    @Test
    void separateNetworksDoNotShare()
    {
        long[] caps = { 80, 80 };
        var result = GridRouter.route(caps, new int[0][], List.of(transit(1000, 0)), List.of(machine(50, 1)));

        assertThat(result.totalDelivered()).isZero();
        assertThat(caps).containsExactly(80, 80);
    }

    @Test
    void whatIsTakenAlwaysEqualsWhatIsDelivered()
    {
        long[] caps = { 100, 70, 90, 40, 120 };
        int[][] arcs = { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 4 }, { 0, 4 }, { 1, 3 } };
        var supplies = List.of(transit(60, 0), stored(500, 2), transit(25, 4));
        var demands = List.of(machine(90, 3), machine(70, 1, 4), charging(300, 2), machine(10, 0));

        var result = GridRouter.route(caps, arcs, supplies, demands);

        assertThat(result.totalTaken()).isEqualTo(result.totalDelivered());
        for (int i = 0; i < demands.size(); i++)
        {
            long fed = 0;
            for (long amount : result.fed()[i])
                fed += amount;
            assertThat(fed).isEqualTo(result.delivered()[i]);
        }
        for (long cap : caps)
            assertThat(cap).isNotNegative();
    }

    @Test
    void fedSaysWhichPylonHandedTheEnergy()
    {
        long[] caps = { 192, 30, 192 };
        int[][] arcs = { { 0, 1 } };

        // The machine is covered by 1 and 2, but only 1 reaches the source
        var result = GridRouter.route(caps, arcs, List.of(transit(100, 0)), List.of(machine(100, 1, 2)));

        assertThat(result.fed()[0]).containsExactly(30, 0);
    }
}
