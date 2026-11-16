package net.voxelindustry.voidheart.common.energy.gnawer;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class GnawerBoxTest
{
    @Test
    void of_ordersCornersWhicheverWayTheStakesStand()
    {
        var box = GnawerBox.of(new BlockPos(5, 70, -3), new BlockPos(1, 60, 2));

        assertThat(box.min()).isEqualTo(new BlockPos(1, 60, -3));
        assertThat(box.max()).isEqualTo(new BlockPos(5, 70, 2));
        assertThat(box.volume()).isEqualTo(5L * 11 * 6);
    }

    @Test
    void at_walksEveryBlockOnceFromTheTopLayerDown()
    {
        var box = GnawerBox.of(new BlockPos(0, 10, 0), new BlockPos(2, 8, 3));
        var seen = new HashSet<BlockPos>();
        var lastY = Integer.MAX_VALUE;
        for (long i = 0; i < box.volume(); i++)
        {
            var pos = box.at(i);
            assertThat(box.contains(pos)).isTrue();
            assertThat(seen.add(pos)).isTrue();
            assertThat(pos.getY()).isLessThanOrEqualTo(lastY);
            lastY = pos.getY();
        }
        assertThat(box.at(0).getY()).isEqualTo(10);
        assertThat(box.at(box.volume() - 1).getY()).isEqualTo(8);
    }

    @Test
    void distanceTo_isZeroInsideAndCountsTheFarthestAxis()
    {
        var box = GnawerBox.of(new BlockPos(0, 0, 0), new BlockPos(9, 9, 9));

        assertThat(box.distanceTo(new BlockPos(4, 4, 4))).isZero();
        assertThat(box.distanceTo(new BlockPos(-3, 12, 5))).isEqualTo(3);
        assertThat(box.distanceTo(new BlockPos(15, 5, 5))).isEqualTo(6);
    }

    @Test
    void check_refusesBoxesTooWideOrTooFar()
    {
        var gnawer = new BlockPos(0, 64, 0);

        assertThat(GnawerBox.check(gnawer, new BlockPos(1, 64, 1), new BlockPos(32, 40, 32))).isNull();
        assertThat(GnawerBox.check(gnawer, new BlockPos(1, 64, 1), new BlockPos(33, 40, 10))).isEqualTo(GnawerBox.Refusal.TOO_WIDE);
        assertThat(GnawerBox.check(gnawer, new BlockPos(9, 64, 9), new BlockPos(20, 40, 20))).isEqualTo(GnawerBox.Refusal.TOO_FAR);
        // Height has no cap
        assertThat(GnawerBox.check(gnawer, new BlockPos(1, 64, 1), new BlockPos(4, -60, 4))).isNull();
    }

    @Test
    void cost_followsHardnessWithAFloorAndHalvesWhenErasing()
    {
        assertThat(GnawerBalance.cost(1.5F, false, false)).isEqualTo(120);
        assertThat(GnawerBalance.cost(1.5F, false, true)).isEqualTo(60);
        assertThat(GnawerBalance.cost(0.5F, false, false)).isEqualTo(GnawerBalance.MIN_COST);
        assertThat(GnawerBalance.cost(100F, true, false)).isEqualTo(GnawerBalance.MIN_COST);
    }

    @Test
    void progressLine_neverShowsTheLastLineBeforeNinetyPercent()
    {
        assertThat(GnawerJade.progressLine(0)).isZero();
        assertThat(GnawerJade.progressLine(0.5F)).isEqualTo(2);
        assertThat(GnawerJade.progressLine(0.89F)).isEqualTo(3);
        assertThat(GnawerJade.progressLine(1)).isEqualTo(4);
    }
}
