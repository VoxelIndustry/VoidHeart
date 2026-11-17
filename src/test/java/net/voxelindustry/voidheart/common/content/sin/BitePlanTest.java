package net.voxelindustry.voidheart.common.content.sin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BitePlanTest
{
    private static final BlockPos ORIGIN = BlockPos.ZERO;

    private static int total(List<List<BlockPos>> rings)
    {
        return rings.stream().mapToInt(List::size).sum();
    }

    @Test
    void plan_inSolidStone_eatsExactlyTheBiteNearestFirst()
    {
        var rings = BitePlan.vein(ORIGIN, pos -> true, 9, 4);

        assertThat(total(rings)).isEqualTo(9);
        assertThat(rings.getFirst()).allMatch(pos -> pos.distManhattan(ORIGIN) <= 2);
        // The six faces come before any edge
        assertThat(rings.getFirst().subList(0, 6)).allMatch(pos -> pos.distManhattan(ORIGIN) == 1);
    }

    @Test
    void plan_neverEatsTheOriginOrAnythingTwice()
    {
        var rings = BitePlan.vein(ORIGIN, pos -> true, 20, 4);
        var seen = new HashSet<BlockPos>();
        for (var ring : rings)
            assertThat(ring).allMatch(seen::add);
        assertThat(seen).doesNotContain(ORIGIN);
    }

    @Test
    void plan_followsAVeinThroughEdgesButNotCorners()
    {
        // Two blocks touching the origin only by an edge, and one only by a corner
        Set<BlockPos> vein = Set.of(new BlockPos(1, 1, 0), new BlockPos(2, 2, 0), new BlockPos(-1, -1, -1));
        var rings = BitePlan.vein(ORIGIN, vein::contains, 20, 4);

        assertThat(rings).hasSize(2);
        assertThat(rings.get(0)).containsExactly(new BlockPos(1, 1, 0));
        assertThat(rings.get(1)).containsExactly(new BlockPos(2, 2, 0));
    }

    @Test
    void plan_stopsAtItsReach()
    {
        // A straight line of edible blocks going far up
        var rings = BitePlan.vein(ORIGIN, pos -> pos.getX() == 0 && pos.getZ() == 0 && pos.getY() > 0, 20, 4);

        assertThat(total(rings)).isEqualTo(4);
        assertThat(rings.getLast()).containsExactly(new BlockPos(0, 4, 0));
    }

    @Test
    void plan_withNothingEdible_isEmpty()
    {
        assertThat(BitePlan.vein(ORIGIN, pos -> false, 20, 4)).isEmpty();
        assertThat(BitePlan.vein(ORIGIN, pos -> true, 0, 4)).isEmpty();
    }

    @Test
    void square_onANorthFace_goesSouthIntoTheWall()
    {
        var rings = BitePlan.square(ORIGIN, Direction.NORTH, 1, 2, Integer.MIN_VALUE, pos -> true);
        var all = rings.stream().flatMap(List::stream).toList();

        assertThat(all).hasSize(3 * 3 * 2 - 1);
        assertThat(all).allMatch(pos -> pos.getZ() == 0 || pos.getZ() == 1);
        assertThat(all).allMatch(pos -> Math.abs(pos.getX()) <= 1 && Math.abs(pos.getY()) <= 1);
        assertThat(all).doesNotContain(ORIGIN);
    }

    @Test
    void square_onATopFace_goesDown()
    {
        var all = BitePlan.square(ORIGIN, Direction.UP, 2, 3, Integer.MIN_VALUE, pos -> true).stream().flatMap(List::stream).toList();

        assertThat(all).hasSize(5 * 5 * 3 - 1);
        assertThat(all).allMatch(pos -> pos.getY() <= 0 && pos.getY() >= -2);
    }

    @Test
    void square_neverGoesBelowItsLowestY()
    {
        var all = BitePlan.square(ORIGIN, Direction.EAST, 1, 1, 0, pos -> true).stream().flatMap(List::stream).toList();

        assertThat(all).hasSize(3 * 2 - 1);
        assertThat(all).allMatch(pos -> pos.getY() >= 0);
    }

    @Test
    void square_eatsNearestRingsFirstAndSkipsWhatIsntEdible()
    {
        var rings = BitePlan.square(ORIGIN, Direction.SOUTH, 2, 3, Integer.MIN_VALUE, pos -> pos.getX() != 2);

        assertThat(rings).hasSize(2);
        for (int ring = 0; ring < rings.size(); ring++)
        {
            // The origin alone is distance 0, so the first ring is distance 1
            var distance = ring + 1;
            assertThat(rings.get(ring)).allMatch(pos -> Math.max(Math.abs(pos.getX()), Math.max(Math.abs(pos.getY()), Math.abs(pos.getZ()))) == distance);
        }
        assertThat(rings.stream().flatMap(List::stream)).noneMatch(pos -> pos.getX() == 2);
    }

    @Test
    void sizes_growWithEachStage()
    {
        for (int stage = 2; stage <= SinBalance.STAGES; stage++)
        {
            var before = (2 * DiggingProficiency.squareRadius(stage - 1) + 1) * (2 * DiggingProficiency.squareRadius(stage - 1) + 1) * DiggingProficiency.squareDepth(stage - 1);
            var after = (2 * DiggingProficiency.squareRadius(stage) + 1) * (2 * DiggingProficiency.squareRadius(stage) + 1) * DiggingProficiency.squareDepth(stage);
            assertThat(after).isGreaterThan(before);
            assertThat(DiggingProficiency.veinSize(stage)).isGreaterThan(DiggingProficiency.veinSize(stage - 1));
        }
        assertThat(DiggingProficiency.veinSize(0)).isEqualTo(DiggingProficiency.veinSize(1));
        assertThat(DiggingProficiency.squareDepth(9)).isEqualTo(DiggingProficiency.squareDepth(SinBalance.STAGES));
    }

    @Test
    void stageOf_followsTheSharedStageStarts()
    {
        assertThat(DiggingProficiency.stageOf(0)).isEqualTo(1);
        assertThat(DiggingProficiency.stageOf(SinBalance.DIGGING_DEVOTED_POINTS)).isEqualTo(SinBalance.STAGES);
        assertThat(DiggingProficiency.stageOf(SinBalance.DIGGING_DEVOTED_POINTS * 2)).isEqualTo(SinBalance.STAGES);
        assertThat(DiggingProficiency.stageOf(SinBalance.STAGE_STARTS[2] * SinBalance.DIGGING_DEVOTED_POINTS)).isEqualTo(3);
    }
}
