package net.voxelindustry.voidheart.common.energy.ley;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SpurNetworkTest
{
    private static final int SUPPLY = LeyStoneBalance.STONE_SUPPLY_RADIUS;
    private static final int TTL    = LeyStoneBalance.DEMAND_TICKS;

    private static final BlockPos STONE   = new BlockPos(10, 64, 0);
    private static final BlockPos WARD    = STONE.east(3);
    private static final BlockPos BEHOLDER = STONE.west(3);

    /**
     * A line that serves each spur up to its own flow, standing in for the lattice, which decides between spurs upstream first.
     */
    private static final class FakeLine implements SpurNetwork.Line
    {
        private final Map<BlockPos, Double> flowByRoot = new HashMap<>();
        private final List<Double>          claims     = new ArrayList<>();
        private double defaultFlow;

        FakeLine(double flow)
        {
            defaultFlow = flow;
        }

        @Override
        public double claim(BlockPos root, SpurTap tap, double amount)
        {
            claims.add(amount);
            return Math.min(amount, flow(root, tap));
        }

        @Override
        public double flow(BlockPos root, SpurTap tap)
        {
            return flowByRoot.getOrDefault(root, defaultFlow);
        }
    }

    private static SpurNetwork network(BlockPos... stones)
    {
        var network = new SpurNetwork(SUPPLY, TTL);
        var map = new HashMap<BlockPos, Optional<SpurTap>>();
        for (var stone : stones)
            map.put(stone, Math.abs(stone.getX()) <= LeyStoneBalance.STONE_REACH ? Optional.of(new SpurTap(0, stone.getZ(), 1, 2)) : Optional.empty());
        network.replan(SpurPlan.of(map, LeyStoneBalance.STONE_REACH));
        return network;
    }

    @Test
    void machineFarFromEveryStoneGetsNothing()
    {
        var network = network(STONE);
        var line = new FakeLine(3.5);

        assertThat(network.supply(STONE.east(SUPPLY + 1), 1.5, 0, line)).isZero();
        network.tick(0, line);
        assertThat(line.claims).isEmpty();
    }

    @Test
    void firstTickOffersWhatTheLineCarries()
    {
        var network = network(STONE);

        assertThat(network.supply(WARD, 1.5, 0, new FakeLine(3.5))).isEqualTo(1);
        assertThat(network.supply(WARD, 1.5, 0, new FakeLine(0.75))).isCloseTo(0.5F, within(1e-6F));
    }

    @Test
    void spurClaimsWhatItsMachinesAskedFor()
    {
        var network = network(STONE);
        var line = new FakeLine(3.5);

        network.supply(WARD, 1.5, 0, line);
        network.supply(BEHOLDER, 2.0, 0, line);
        network.tick(0, line);

        assertThat(line.claims).containsExactly(3.5);
        assertThat(network.supply(WARD, 1.5, 1, line)).isEqualTo(1);
        assertThat(network.supply(BEHOLDER, 2.0, 1, line)).isEqualTo(1);
    }

    /**
     * From N0: the median line carries 3.5 and runs a Beholder and one ward. At new moon it drops to 2.6, and both weaken.
     */
    @Test
    void shortLineWeakensEveryMachineOnTheSpurTogether()
    {
        var network = network(STONE);
        var line = new FakeLine(2.6);

        network.supply(WARD, 1.5, 0, line);
        network.supply(BEHOLDER, 2.0, 0, line);
        network.tick(0, line);

        float share = 2.6F / 3.5F;
        assertThat(network.supply(WARD, 1.5, 1, line)).isCloseTo(share, within(1e-6F));
        assertThat(network.supply(BEHOLDER, 2.0, 1, line)).isCloseTo(share, within(1e-6F));
        assertThat(network.granted(STONE)).isCloseTo(2.6, within(1e-9));
    }

    @Test
    void stonesDownTheChainShareTheSpurOfTheirTap()
    {
        var far = new BlockPos(70, 64, 0);
        var network = network(STONE, new BlockPos(40, 64, 0), far);
        var line = new FakeLine(1.0);

        network.supply(far.north(2), 1.0, 0, line);
        network.supply(WARD, 1.0, 0, line);
        network.tick(0, line);

        assertThat(line.claims).containsExactly(2.0);
        assertThat(network.supply(far.north(2), 1.0, 1, line)).isCloseTo(0.5F, within(1e-6F));
        assertThat(network.consumers(STONE)).isEqualTo(2);
    }

    @Test
    void lineDecidesBetweenSpurs()
    {
        var upstream = new BlockPos(10, 64, -50);
        var downstream = new BlockPos(10, 64, 50);
        var network = network(upstream, downstream);
        var line = new FakeLine(3.5);
        line.flowByRoot.put(downstream, 1.0);

        network.supply(upstream.east(2), 2.0, 0, line);
        network.supply(downstream.east(2), 2.0, 0, line);
        network.tick(0, line);

        assertThat(network.supply(upstream.east(2), 2.0, 1, line)).isEqualTo(1);
        assertThat(network.supply(downstream.east(2), 2.0, 1, line)).isCloseTo(0.5F, within(1e-6F));
    }

    @Test
    void machineDrawsOnTheNearestStone()
    {
        var other = new BlockPos(10, 64, 12);
        var network = network(STONE, other);

        assertThat(network.stoneFor(STONE.south(7))).isEqualTo(other);
        assertThat(network.stoneFor(STONE.south(5))).isEqualTo(STONE);
    }

    @Test
    void needFadesOnceAMachineStopsAsking()
    {
        var network = network(STONE);
        var line = new FakeLine(3.5);

        network.supply(WARD, 1.5, 0, line);
        network.supply(BEHOLDER, 2.0, 0, line);
        for (long tick = 0; tick <= TTL; tick++)
        {
            network.supply(WARD, 1.5, tick, line);
            network.tick(tick, line);
        }

        assertThat(line.claims.getFirst()).isEqualTo(3.5);
        assertThat(line.claims.getLast()).isEqualTo(1.5);
        assertThat(network.consumers(STONE)).isEqualTo(1);
    }

    @Test
    void idleSpurClaimsNothing()
    {
        var network = network(STONE);
        var line = new FakeLine(3.5);

        network.supply(WARD, 1.5, 0, line);
        network.tick(TTL, line);
        network.tick(TTL + 1, line);

        assertThat(line.claims).isEmpty();
        assertThat(network.claimed(STONE)).isZero();
    }

    @Test
    void needingNothingIsAlwaysMet()
    {
        assertThat(network().supply(WARD, 0, 0, new FakeLine(0))).isEqualTo(1);
    }

    @Test
    void breakingTheTapDropsItsSpur()
    {
        var network = network(STONE);
        var line = new FakeLine(3.5);
        network.supply(WARD, 1.5, 0, line);
        network.tick(0, line);

        network.replan(SpurPlan.of(Map.of(), LeyStoneBalance.STONE_REACH));

        assertThat(network.supply(WARD, 1.5, 1, line)).isZero();
        assertThat(network.consumers(STONE)).isZero();
    }
}
