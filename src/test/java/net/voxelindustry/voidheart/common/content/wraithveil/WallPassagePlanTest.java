package net.voxelindustry.voidheart.common.content.wraithveil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassagePlan.Cell;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class WallPassagePlanTest
{
    /**
     * Open everywhere unless set; a body fits where every block it overlaps is open, or low enough to stand on.
     */
    private static final class Grid implements WallPassagePlan.Cells
    {
        final Map<BlockPos, Cell>   cells   = new HashMap<>();
        final Map<BlockPos, Double> heights = new HashMap<>();

        // A partial block feet can rest on, such as a carpet or a slab
        Grid low(int x, int y, int z, double height)
        {
            var pos = new BlockPos(x, y, z);
            cells.put(pos, Cell.PARTIAL);
            heights.put(pos, height);
            return this;
        }

        Grid fill(int x0, int y0, int z0, int x1, int y1, int z1, Cell cell)
        {
            for (int x = x0; x <= x1; x++)
                for (int y = y0; y <= y1; y++)
                    for (int z = z0; z <= z1; z++)
                        cells.put(new BlockPos(x, y, z), cell);
            return this;
        }

        @Override
        public Cell at(BlockPos pos)
        {
            return cells.getOrDefault(pos, Cell.OPEN);
        }

        @Override
        public double standHeight(BlockPos pos)
        {
            return heights.getOrDefault(pos, 0D);
        }

        @Override
        public boolean fits(Vec3 feet)
        {
            for (int x = Mth.floor(feet.x - 0.3); x <= Mth.floor(feet.x + 0.3 - 1e-6); x++)
                for (int y = Mth.floor(feet.y); y <= Mth.floor(feet.y + WallPassagePlan.BODY_HEIGHT - 1e-6); y++)
                    for (int z = Mth.floor(feet.z - 0.3); z <= Mth.floor(feet.z + 0.3 - 1e-6); z++)
                    {
                        var pos = new BlockPos(x, y, z);
                        if (at(pos) != Cell.OPEN && feet.y < y + heights.getOrDefault(pos, 2D))
                            return false;
                    }
            return true;
        }
    }

    // A wall along x = 0..depth-1, with ground under y = 0
    private static Grid wall(int depth)
    {
        return new Grid().fill(-5, -1, -5, 20, -1, 5, Cell.SOLID).fill(0, 0, -5, depth - 1, 4, 5, Cell.SOLID);
    }

    private static final Vec3 BEFORE_WALL = new Vec3(-0.6, 0, 0.5);

    @Test
    void wall_comesOutOnTheFarSide()
    {
        var plan = WallPassagePlan.plan(wall(3), new BlockPos(0, 0, 0), Direction.WEST, BEFORE_WALL);

        assertThat(plan).isNotNull();
        assertThat(plan.depth()).isEqualTo(3);
        assertThat(plan.into()).isEqualTo(Direction.EAST);
        assertThat(plan.exitSpot()).isEqualTo(new Vec3(3.5, 0, 0.5));
        assertThat(plan.entrySpot()).isEqualTo(new Vec3(-0.5, 0, 0.5));
        assertThat(plan.tubeMin()).isEqualTo(new BlockPos(0, 0, 0));
        assertThat(plan.tubeMax()).isEqualTo(new BlockPos(2, 1, 0));
        assertThat(plan.entry().center()).isEqualTo(new Vec3(0, 1, 0.5));
        assertThat(plan.entry().face()).isEqualTo(Direction.WEST);
        assertThat(plan.exit().center()).isEqualTo(new Vec3(3, 1, 0.5));
        assertThat(plan.exit().face()).isEqualTo(Direction.EAST);
        // Sized so the ink fills the tunnel's mouth, one block wide and two tall
        assertThat(plan.entry().halfU() * WallPassagePlan.MARK_COVER).isCloseTo(0.5, within(1e-9));
        assertThat(plan.entry().halfV() * WallPassagePlan.MARK_COVER).isCloseTo(1, within(1e-9));
    }

    @Test
    void wall_canBeUsedAtHeadHeightButNotAbove()
    {
        assertThat(WallPassagePlan.plan(wall(2), new BlockPos(0, 1, 0), Direction.WEST, BEFORE_WALL)).isNotNull();
        assertThat(WallPassagePlan.plan(wall(2), new BlockPos(0, 2, 0), Direction.WEST, BEFORE_WALL)).isNull();
    }

    @Test
    void wall_tenDeepIsTheLimit()
    {
        assertThat(WallPassagePlan.plan(wall(WallPassagePlan.MAX_DEPTH), new BlockPos(0, 0, 0), Direction.WEST, BEFORE_WALL))
                .isNotNull()
                .extracting(WallPassagePlan.Plan::depth).isEqualTo(10);
        assertThat(WallPassagePlan.plan(wall(WallPassagePlan.MAX_DEPTH + 1), new BlockPos(0, 0, 0), Direction.WEST, BEFORE_WALL)).isNull();
    }

    @Test
    void wall_aGapTooSmallToStandInIsCrossed()
    {
        var grid = wall(5).fill(2, 0, 0, 2, 0, 0, Cell.OPEN);

        assertThat(WallPassagePlan.plan(grid, new BlockPos(0, 0, 0), Direction.WEST, BEFORE_WALL))
                .isNotNull()
                .extracting(WallPassagePlan.Plan::depth).isEqualTo(5);
    }

    @Test
    void wall_comesOutOnTopOfAStepAtTheFarSide()
    {
        var plan = WallPassagePlan.plan(wall(3).fill(3, 0, 0, 3, 0, 0, Cell.SOLID), new BlockPos(0, 0, 0), Direction.WEST, BEFORE_WALL);

        assertThat(plan).isNotNull();
        assertThat(plan.depth()).isEqualTo(3);
        assertThat(plan.exitSpot()).isEqualTo(new Vec3(3.5, 1, 0.5));
        assertThat(plan.entrySpot()).isEqualTo(new Vec3(-0.5, 0, 0.5));
        // Still two tall: the step is taken into the tunnel and erased, so the body walks up to it level and jumps out on top
        assertThat(plan.tubeMin()).isEqualTo(new BlockPos(0, 0, 0));
        assertThat(plan.tubeMax()).isEqualTo(new BlockPos(3, 1, 0));
        assertThat(plan.exit().center()).isEqualTo(new Vec3(3, 1, 0.5));
        assertThat(plan.entry().halfV() * WallPassagePlan.MARK_COVER).isCloseTo(1, within(1e-9));
        // The realm stops at the far mark, short of the erased step
        assertThat(plan.realm().maxX).isEqualTo(3);
    }

    @Test
    void wall_comesOutOnASlabAtTheFarSide()
    {
        var plan = WallPassagePlan.plan(wall(3).low(3, 0, 0, 0.5), new BlockPos(0, 0, 0), Direction.WEST, BEFORE_WALL);

        assertThat(plan).isNotNull();
        assertThat(plan.exitSpot()).isEqualTo(new Vec3(3.5, 0.5, 0.5));
        assertThat(plan.tubeMax()).isEqualTo(new BlockPos(3, 1, 0));
    }

    @Test
    void wall_noRoomToStandAtTheFarSide_goesOnThroughWhatIsInTheWay()
    {
        assertThat(WallPassagePlan.plan(wall(3).fill(3, 1, 0, 3, 1, 0, Cell.SOLID), new BlockPos(0, 0, 0), Direction.WEST, BEFORE_WALL))
                .as("overhang").isNotNull().extracting(WallPassagePlan.Plan::depth).isEqualTo(4);
        assertThat(WallPassagePlan.plan(wall(3).fill(3, 0, 0, 3, 0, 0, Cell.SOLID).fill(3, 2, 0, 3, 2, 0, Cell.SOLID), new BlockPos(0, 0, 0), Direction.WEST,
                BEFORE_WALL)).as("step under a ledge").isNotNull().extracting(WallPassagePlan.Plan::depth).isEqualTo(4);
    }

    @Test
    void wall_aStepBeyondALowWallIsErasedToo()
    {
        var grid = new Grid().fill(-5, -1, -5, 20, -1, 5, Cell.SOLID).fill(0, 0, -5, 2, 1, 5, Cell.SOLID).fill(3, 0, 0, 3, 0, 0, Cell.SOLID);

        var plan = WallPassagePlan.plan(grid, new BlockPos(0, 0, 0), Direction.WEST, BEFORE_WALL);

        assertThat(plan).isNotNull();
        assertThat(plan.tubeMax()).isEqualTo(new BlockPos(3, 1, 0));
        assertThat(plan.exitSpot()).isEqualTo(new Vec3(3.5, 1, 0.5));
    }

    @Test
    void wall_aCarpetAtEitherSideStillPasses()
    {
        var plan = WallPassagePlan.plan(wall(3).low(3, 0, 0, 0.0625).low(-1, 0, 0, 0.0625), new BlockPos(0, 0, 0), Direction.WEST, new Vec3(-0.5, 0.0625, 0.5));

        assertThat(plan).isNotNull();
        assertThat(plan.exitSpot()).isEqualTo(new Vec3(3.5, 0.0625, 0.5));
        assertThat(plan.entrySpot()).isEqualTo(new Vec3(-0.5, 0.0625, 0.5));
    }

    @Test
    void wall_aStepInFrontIsStoodOn()
    {
        // Used at head height from below the step, the tunnel starts on top of it
        var plan = WallPassagePlan.plan(wall(3).fill(-1, 0, 0, -1, 0, 0, Cell.SOLID), new BlockPos(0, 1, 0), Direction.WEST, new Vec3(-1.5, 0, 0.5));

        assertThat(plan).isNotNull();
        assertThat(plan.entrySpot()).isEqualTo(new Vec3(-0.5, 1, 0.5));
        assertThat(plan.tubeMin()).isEqualTo(new BlockPos(0, 1, 0));
        assertThat(plan.tubeMax()).isEqualTo(new BlockPos(2, 2, 0));
        // And drops back to the floor on the far side
        assertThat(plan.exitSpot()).isEqualTo(new Vec3(3.5, 1, 0.5));
    }

    @Test
    void wall_aSlabUnderfootIsErasedIntoTheTunnel()
    {
        var plan = WallPassagePlan.plan(wall(3).low(-1, 0, 0, 0.5), new BlockPos(0, 0, 0), Direction.WEST, new Vec3(-0.5, 0.5, 0.5));

        assertThat(plan).isNotNull();
        // Pushed back out on top of it, which is where it stood
        assertThat(plan.entrySpot()).isEqualTo(new Vec3(-0.5, 0.5, 0.5));
        assertThat(plan.tubeMin()).isEqualTo(new BlockPos(-1, 0, 0));
        assertThat(plan.tubeMax()).isEqualTo(new BlockPos(2, 1, 0));
        assertThat(plan.realm().minX).isEqualTo(0);
    }

    @Test
    void wall_noRoomToStandAtTheMouthRulesItOut()
    {
        assertThat(WallPassagePlan.plan(wall(3).fill(-1, 1, 0, -1, 1, 0, Cell.SOLID), new BlockPos(0, 0, 0), Direction.WEST, new Vec3(-1.5, 0, 0.5))).as("overhang").isNull();
        // Out of reach above the feet, with nothing to stand on there
        assertThat(WallPassagePlan.plan(wall(3), new BlockPos(0, 2, 0), Direction.WEST, BEFORE_WALL)).as("too high").isNull();
    }

    @Test
    void wall_aMouthHalfOnAirIsStillOpened()
    {
        assertThat(WallPassagePlan.plan(wall(3).fill(0, 1, 0, 0, 1, 0, Cell.OPEN), new BlockPos(0, 0, 0), Direction.WEST, BEFORE_WALL))
                .isNotNull().extracting(WallPassagePlan.Plan::depth).isEqualTo(3);
    }

    @Test
    void wall_aPartialBlockInsideIsCrossed()
    {
        assertThat(WallPassagePlan.plan(wall(4).fill(1, 1, 0, 1, 1, 0, Cell.PARTIAL), new BlockPos(0, 0, 0), Direction.WEST, BEFORE_WALL))
                .isNotNull().extracting(WallPassagePlan.Plan::depth).isEqualTo(4);
    }

    @Test
    void wall_aPartialFaceCanBeUsed()
    {
        assertThat(WallPassagePlan.plan(wall(2).fill(0, 0, 0, 0, 1, 0, Cell.PARTIAL), new BlockPos(0, 0, 0), Direction.WEST, BEFORE_WALL)).isNotNull();
    }

    @Test
    void wall_stopsAtTheFirstRoomItFits()
    {
        var grid = wall(6).fill(2, 0, 0, 2, 1, 0, Cell.OPEN);

        assertThat(WallPassagePlan.plan(grid, new BlockPos(0, 0, 0), Direction.WEST, BEFORE_WALL))
                .isNotNull()
                .extracting(WallPassagePlan.Plan::depth).isEqualTo(2);
    }

    @Test
    void forbiddenAnywhereOnTheWay_noPassage()
    {
        for (var forbidden : new BlockPos[]{new BlockPos(1, 0, 0), new BlockPos(1, 1, 0), new BlockPos(3, 1, 0)})
        {
            var grid = wall(3).fill(forbidden.getX(), forbidden.getY(), forbidden.getZ(), forbidden.getX(), forbidden.getY(), forbidden.getZ(), Cell.FORBIDDEN);
            assertThat(WallPassagePlan.plan(grid, new BlockPos(0, 0, 0), Direction.WEST, BEFORE_WALL)).as("forbidden at %s", forbidden).isNull();
        }
    }

    @Test
    void forbiddenBesideTheWay_stillPasses()
    {
        var grid = wall(3).fill(1, 2, 0, 1, 2, 0, Cell.FORBIDDEN).fill(1, 0, 1, 1, 1, 1, Cell.FORBIDDEN);

        assertThat(WallPassagePlan.plan(grid, new BlockPos(0, 0, 0), Direction.WEST, BEFORE_WALL)).isNotNull();
    }

    @Test
    void forbiddenFace_noPassage()
    {
        var grid = wall(3).fill(0, 0, 0, 0, 0, 0, Cell.FORBIDDEN);

        assertThat(WallPassagePlan.plan(grid, new BlockPos(0, 0, 0), Direction.WEST, BEFORE_WALL)).isNull();
    }

    @Test
    void floor_comesOutHangingUnderIt()
    {
        // Floor 2 thick at y = -1..0, room below
        var grid = new Grid().fill(-3, -2, -3, 3, 0, 3, Cell.SOLID).fill(-3, -6, -3, 3, -3, 3, Cell.OPEN).fill(-3, -7, -3, 3, -7, 3, Cell.SOLID);

        var plan = WallPassagePlan.plan(grid, new BlockPos(0, 0, 0), Direction.UP, new Vec3(0.5, 1, 0.5));

        assertThat(plan).isNotNull();
        assertThat(plan.into()).isEqualTo(Direction.DOWN);
        assertThat(plan.depth()).isEqualTo(3);
        assertThat(plan.exitSpot()).isEqualTo(new Vec3(0.5, -4, 0.5));
        assertThat(plan.entrySpot()).isEqualTo(new Vec3(0.5, 1, 0.5));
        assertThat(plan.exit().center()).isEqualTo(new Vec3(0.5, -2, 0.5));
        assertThat(plan.tubeMin()).isEqualTo(new BlockPos(0, -2, 0));
        assertThat(plan.tubeMax()).isEqualTo(new BlockPos(0, 0, 0));
    }

    @Test
    void floor_aPocketTooSmallToHangInIsCrossed()
    {
        var grid = new Grid().fill(-3, -5, -3, 3, 0, 3, Cell.SOLID).fill(0, -2, 0, 0, -2, 0, Cell.OPEN);

        var plan = WallPassagePlan.plan(grid, new BlockPos(0, 0, 0), Direction.UP, new Vec3(0.5, 1, 0.5));

        assertThat(plan).isNotNull();
        assertThat(plan.exitSpot().y).isEqualTo(-7);
    }

    @Test
    void ceiling_comesOutStandingOnTop()
    {
        var grid = new Grid().fill(-3, 4, -3, 3, 5, 3, Cell.SOLID);

        var plan = WallPassagePlan.plan(grid, new BlockPos(0, 4, 0), Direction.DOWN, new Vec3(0.5, 0, 0.5));

        assertThat(plan).isNotNull();
        assertThat(plan.into()).isEqualTo(Direction.UP);
        assertThat(plan.depth()).isEqualTo(2);
        assertThat(plan.exitSpot()).isEqualTo(new Vec3(0.5, 6, 0.5));
        assertThat(plan.exit().face()).isEqualTo(Direction.UP);
        // Climbable from up to four blocks of open air below
        assertThat(plan.climbFrom()).isEqualTo(new BlockPos(0, 0, 0));
        assertThat(plan.climbBox().minY).isEqualTo(0);
    }

    @Test
    void ceiling_anythingOnTopIsCrossedToo()
    {
        var grid = new Grid().fill(-3, 4, -3, 3, 5, 3, Cell.SOLID).low(0, 6, 0, 0.5);

        var plan = WallPassagePlan.plan(grid, new BlockPos(0, 4, 0), Direction.DOWN, new Vec3(0.5, 0, 0.5));

        assertThat(plan).isNotNull();
        assertThat(plan.depth()).isEqualTo(3);
        assertThat(plan.exitSpot()).isEqualTo(new Vec3(0.5, 7, 0.5));
    }

    @Test
    void clickedOpenBlock_noPassage()
    {
        assertThat(WallPassagePlan.plan(new Grid(), new BlockPos(0, 0, 0), Direction.WEST, BEFORE_WALL)).isNull();
    }

    @Test
    void tunnel_marksThenStaysOpenThenCollapses()
    {
        assertThat(WallPassagePlan.Plan.phaseAt(0)).isEqualTo(WallPassagePlan.Phase.MARKING);
        assertThat(WallPassagePlan.Plan.phaseAt(WallPassagePlan.MARK_TICKS)).isEqualTo(WallPassagePlan.Phase.OPEN);
        assertThat(WallPassagePlan.Plan.phaseAt(WallPassagePlan.MARK_TICKS + WallPassagePlan.OPEN_TICKS)).isEqualTo(WallPassagePlan.Phase.COLLAPSING);
        assertThat(WallPassagePlan.Plan.phaseAt(WallPassagePlan.Plan.totalTicks())).isEqualTo(WallPassagePlan.Phase.CLOSED);
        assertThat(WallPassagePlan.Plan.collapse(WallPassagePlan.MARK_TICKS + WallPassagePlan.OPEN_TICKS)).isZero();
        assertThat(WallPassagePlan.Plan.collapse(WallPassagePlan.Plan.totalTicks())).isEqualTo(1);
        assertThat(WallPassagePlan.Plan.cooldownTicks()).isGreaterThan(WallPassagePlan.Plan.totalTicks());
    }

    @Test
    void hole_opensOnceMarkedAndSealsWithTheCollapse()
    {
        assertThat(WallPassagePlan.Plan.holeOpen(WallPassagePlan.MARK_TICKS)).isZero();
        assertThat(WallPassagePlan.Plan.holeOpen(WallPassagePlan.MARK_TICKS + WallPassagePlan.BORE_TICKS)).isEqualTo(1);
        assertThat(WallPassagePlan.Plan.holeOpen(WallPassagePlan.MARK_TICKS + WallPassagePlan.OPEN_TICKS)).isEqualTo(1);
        assertThat(WallPassagePlan.Plan.holeOpen(WallPassagePlan.Plan.totalTicks())).isZero();
    }

    @Test
    void tunnel_boresInFromTheMouthsAndPinchesShutFromTheMiddle()
    {
        float boring = WallPassagePlan.MARK_TICKS + WallPassagePlan.BORE_TICKS / 2F;
        assertThat(WallPassagePlan.Plan.closure(boring, 1)).as("mouth while boring").isZero();
        assertThat(WallPassagePlan.Plan.closure(boring, 0)).as("middle while boring").isEqualTo(1);
        assertThat(WallPassagePlan.Plan.closure(WallPassagePlan.MARK_TICKS + WallPassagePlan.BORE_TICKS, 0)).as("middle once bored").isZero();

        float collapsing = WallPassagePlan.MARK_TICKS + WallPassagePlan.OPEN_TICKS + WallPassagePlan.COLLAPSE_TICKS / 2F;
        assertThat(WallPassagePlan.Plan.closure(collapsing, 0)).as("middle while collapsing").isEqualTo(1);
        assertThat(WallPassagePlan.Plan.closure(collapsing, 1)).as("mouth while collapsing").isZero();
        // A funnel, not a wall: partly shut between
        assertThat(WallPassagePlan.Plan.closure(collapsing, 0.6F)).isStrictlyBetween(0F, 1F);
    }

    @Test
    void collapse_pushesOutOfTheNearerMouth()
    {
        var plan = WallPassagePlan.plan(wall(6), new BlockPos(0, 0, 0), Direction.WEST, BEFORE_WALL);

        assertThat(plan.nearestEnd(new Vec3(1.5, 0, 0.5))).isEqualTo(plan.entrySpot());
        assertThat(plan.nearestEnd(new Vec3(4.5, 0, 0.5))).isEqualTo(plan.exitSpot());
        assertThat(plan.contains(new BlockPos(5, 1, 0))).isTrue();
        assertThat(plan.contains(new BlockPos(6, 0, 0))).isFalse();
        assertThat(plan.contains(new BlockPos(2, 2, 0))).isFalse();
    }
}
