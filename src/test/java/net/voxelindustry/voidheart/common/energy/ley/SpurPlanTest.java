package net.voxelindustry.voidheart.common.energy.ley;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SpurPlanTest
{
    private static final int REACH = 32;

    /**
     * A line along x = 0, flowing from node 1 at z = -100 to node 2 at z = 100.
     */
    private static Optional<SpurTap> tapFor(BlockPos stone)
    {
        if (Math.abs(stone.getX() + 0.5) > REACH)
            return Optional.empty();
        return Optional.of(new SpurTap(0, Math.clamp(stone.getZ() + 0.5, -100, 100), 1, 2));
    }

    private static SpurPlan plan(BlockPos... stones)
    {
        var map = new HashMap<BlockPos, Optional<SpurTap>>();
        for (var stone : stones)
            map.put(stone, tapFor(stone));
        return SpurPlan.of(map, REACH);
    }

    @Test
    void stoneWithinReachOfALineTapsIt()
    {
        var stone = new BlockPos(20, 64, 5);
        var plan = plan(stone);

        var link = plan.link(stone);
        assertThat(link).isNotNull();
        assertThat(link.isRoot()).isTrue();
        assertThat(link.root()).isEqualTo(stone);
        assertThat(link.pathEnd()).isCloseTo(20.5, within(1e-6));
        assertThat(plan.roots()).containsExactly(stone);
    }

    @Test
    void stoneBeyondReachOfLineAndStonesStaysDark()
    {
        var plan = plan(new BlockPos(20, 64, 0), new BlockPos(100, 64, 0));

        assertThat(plan.isConnected(new BlockPos(100, 64, 0))).isFalse();
    }

    @Test
    void stonesChainTowardABase()
    {
        var tap = new BlockPos(20, 64, 0);
        var second = new BlockPos(50, 66, 0);
        var third = new BlockPos(80, 70, 0);
        var plan = plan(tap, second, third);

        assertThat(plan.link(second).parent()).isEqualTo(tap);
        assertThat(plan.link(third).parent()).isEqualTo(second);
        assertThat(plan.link(third).root()).isEqualTo(tap);
        assertThat(plan.link(third).hops()).isEqualTo(2);
        assertThat(plan.link(third).pathStart()).isCloseTo(50.5, within(1e-6));
        assertThat(plan.length(tap)).isCloseTo(80.5, within(1e-6));
    }

    @Test
    void reachIsMeasuredAcrossTheGround()
    {
        var tap = new BlockPos(20, 64, 0);
        var high = new BlockPos(52, 120, 0);

        assertThat(plan(tap, high).link(high).parent()).isEqualTo(tap);
        assertThat(plan(tap, new BlockPos(53, 64, 0)).isConnected(new BlockPos(53, 64, 0))).isFalse();
    }

    @Test
    void stoneJoinsTheSpurFewestHopsFromItsLine()
    {
        var tap = new BlockPos(30, 64, 0);
        var near = new BlockPos(60, 64, 0);
        var far = new BlockPos(62, 64, 20);
        var plan = plan(tap, near, far, new BlockPos(85, 64, 0));

        // far is out of the tap's reach, so it chains through near. The last stone could reach far too, but near is a hop closer to the line
        assertThat(plan.link(far).parent()).isEqualTo(near);
        assertThat(plan.link(new BlockPos(85, 64, 0)).parent()).isEqualTo(near);
    }

    @Test
    void stoneBetweenTwoSpursJoinsTheNearer()
    {
        var south = new BlockPos(31, 64, 0);
        var north = new BlockPos(31, 64, 40);
        var between = new BlockPos(50, 64, 22);
        var plan = plan(south, north, between);

        // Both stones tap the line on their own, since a stone within reach of a line never chains
        assertThat(plan.link(south).isRoot()).isTrue();
        assertThat(plan.link(north).isRoot()).isTrue();
        assertThat(plan.link(between).parent()).isEqualTo(north);
    }

    @Test
    void breakingAMiddleStoneDarkensWhatChainedThroughIt()
    {
        var tap = new BlockPos(20, 64, 0);
        var middle = new BlockPos(50, 64, 0);
        var end = new BlockPos(80, 64, 0);

        assertThat(plan(tap, middle, end).isConnected(end)).isTrue();
        assertThat(plan(tap, end).isConnected(end)).isFalse();
    }

    @Test
    void sameStonesGiveTheSamePlan()
    {
        var stones = new BlockPos[] { new BlockPos(20, 64, 0), new BlockPos(45, 64, 10), new BlockPos(45, 64, -10), new BlockPos(70, 64, 0) };
        var first = plan(stones);
        var reversed = plan(stones[3], stones[2], stones[1], stones[0]);

        for (var stone : stones)
            assertThat(reversed.link(stone)).isEqualTo(first.link(stone));
    }

    @Test
    void emptyMapPlansNothing()
    {
        var plan = SpurPlan.of(Map.of(), REACH);
        assertThat(plan.links()).isEmpty();
        assertThat(plan.roots()).isEmpty();
    }
}
