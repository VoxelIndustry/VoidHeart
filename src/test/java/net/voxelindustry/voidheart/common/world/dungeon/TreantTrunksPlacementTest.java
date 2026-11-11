package net.voxelindustry.voidheart.common.world.dungeon;

import net.minecraft.core.Direction;
import net.voxelindustry.voidheart.common.world.dungeon.rooms.RoomKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial.*;
import static org.assertj.core.api.Assertions.assertThat;

class TreantTrunksPlacementTest
{
    private static final int X = 16;
    private static final int Y = 16;
    private static final int Z = 16;
    // A synthetic room with one candidate column makes rejection tests independent of random spot selection.
    private static final DungeonRoom ROOM = new DungeonRoom(RoomKind.of("test", 0, 0, 0, 0, 1, canvas -> { }), 3, 3, 3, 3, 3, 3);
    private final DungeonGrid grid = new DungeonGrid(48, 48, 48);
    private final DungeonBlueprint blueprint = new DungeonBlueprint(DungeonSize.SMALL, DungeonTheme.FOREST, 1, DungeonLayout.FLOATING, 1234);

    private List<int[]> grow(TreantTrunks.Safety safety, int[] entry, List<DungeonMobTable.Placement> mobs)
    {
        return new TreantTrunks(blueprint, grid, List.of(ROOM), safety, entry, mobs).grow();
    }

    private List<int[]> grow()
    {
        return grow((x, y, z) -> false, new int[] { 0, Y, Z }, List.of());
    }

    private void ground()
    {
        grid.set(X, Y - 1, Z, SOIL);
    }

    @Test
    void openColumn_growsOneTrunkAndACrown()
    {
        ground();
        assertThat(grow()).singleElement().satisfies(pos -> assertThat(pos).containsExactly(X, Y, Z));
        assertThat(grid.material(X, Y, Z)).isEqualTo(TREANT);
        for (int up = 1; up < TreantTrunks.MAX_TRUNK; up++)
            assertThat(grid.material(X, Y + up, Z)).as("height %d", up).isEqualTo(LOG);
        assertThat(grid.material(X + 1, Y + TreantTrunks.MAX_TRUNK, Z)).isEqualTo(LEAVES);
        assertThat(grid.material(X, Y - 1, Z)).isEqualTo(SOIL);
    }

    @Test
    void trunk_stopsAtExistingCanopyWithoutReplacingIt()
    {
        ground();
        grid.set(X, Y + TreantTrunks.MIN_HEADROOM, Z, LEAVES);
        assertThat(grow()).hasSize(1);
        assertThat(grid.material(X, Y + TreantTrunks.MIN_HEADROOM - 1, Z)).isEqualTo(LOG);
        assertThat(grid.material(X, Y + TreantTrunks.MIN_HEADROOM, Z)).isEqualTo(LEAVES);
        assertThat(grid.material(X + 1, Y + TreantTrunks.MIN_HEADROOM, Z)).isEqualTo(UNSET);
    }

    @Test
    void crown_doesNotOverwriteProtectedAirOrExistingBlocks()
    {
        ground();
        int top = Y + TreantTrunks.MAX_TRUNK;
        grid.set(X - 1, top, Z, CHEST);
        assertThat(grow((x, y, z) -> x == X + 1 && y == top && z == Z, new int[] { 0, Y, Z }, List.of())).hasSize(1);
        assertThat(grid.material(X - 1, top, Z)).isEqualTo(CHEST);
        assertThat(grid.material(X + 1, top, Z)).isEqualTo(UNSET);
        assertThat(grid.material(X, top, Z + 1)).isEqualTo(LEAVES);
    }

    @ParameterizedTest
    @EnumSource(value = DungeonMaterial.class, names = { "UNSET", "AIR", "FLUID", "TEMPLATE", "NEST", "POCKET_WALL", "TREANT" })
    void unsuitableGround_rejectsTheColumn(DungeonMaterial ground)
    {
        grid.set(X, Y - 1, Z, ground);
        assertThat(grow()).isEmpty();
        assertThat(grid.material(X, Y, Z)).isEqualTo(UNSET);
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 2, 3, 4 })
    void obstructionAnywhereInMinimumHeadroom_rejectsTheColumn(int up)
    {
        ground();
        grid.set(X, Y + up, Z, ROCK);
        assertThat(grow()).isEmpty();
        assertThat(grid.material(X, Y + up, Z)).isEqualTo(ROCK);
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 2, 3, 4 })
    void protectedCellAnywhereInMinimumHeadroom_rejectsTheColumn(int up)
    {
        ground();
        assertThat(grow((x, y, z) -> x == X && y == Y + up && z == Z, new int[] { 0, Y, Z }, List.of())).isEmpty();
        assertThat(grid.material(X, Y, Z)).isEqualTo(UNSET);
    }

    @ParameterizedTest
    @CsvSource({ "11, false", "12, true", "13, true" })
    void entryClearance_includesTheExactBoundary(int distance, boolean accepted)
    {
        ground();
        assertThat(grow((x, y, z) -> false, new int[] { X - distance, Y, Z }, List.of())).hasSize(accepted ? 1 : 0);
    }

    @ParameterizedTest
    @CsvSource({ "2, 0, false", "0, -2, false", "3, 0, true", "0, -3, true" })
    void nearbyMobClearance_checksBothHorizontalAxes(int dx, int dz, boolean accepted)
    {
        ground();
        var mob = new DungeonMobTable.Placement(DungeonMob.VOID_GHOUL, X + dx, Y, Z + dz, Direction.UP, 0);
        assertThat(grow((x, y, z) -> false, new int[] { 0, Y, Z }, List.of(mob))).hasSize(accepted ? 1 : 0);
    }

    @ParameterizedTest
    @EnumSource(value = DungeonMaterial.class, names = { "AIR", "PLANT", "TALL_PLANT", "HANGING", "LANTERN", "CHAIN", "ROOTS" })
    void exit_requiresRoomForBothShouldersAndAllowsOnlyNonCollidingDecoration(DungeonMaterial decoration)
    {
        ground();
        // Three blocked sides leave only the south exit available.
        grid.set(X - 1, Y, Z, ROCK);
        grid.set(X + 1, Y, Z, ROCK);
        grid.set(X, Y, Z - 1, ROCK);
        // The far shoulder at head height is part of that exit too.
        grid.set(X + 1, Y + 2, Z + 2, decoration);
        boolean accepted = decoration == AIR || decoration == PLANT || decoration == TALL_PLANT || decoration == HANGING;
        assertThat(grow()).hasSize(accepted ? 1 : 0);
        assertThat(grid.material(X + 1, Y + 2, Z + 2)).isEqualTo(decoration);
        if (accepted)
        {
            for (int flank = -1; flank <= 1; flank++)
                assertThat(grid.material(X + flank, Y, Z + 1)).as("exit shoulder %d", flank).isEqualTo(UNSET);
        }
    }

    @Test
    void noRooms_producesNoTrunks()
    {
        ground();
        assertThat(new TreantTrunks(blueprint, grid, List.of(), (x, y, z) -> false, new int[] { 0, Y, Z }, List.of()).grow()).isEmpty();
        assertThat(grid.material(X, Y, Z)).isEqualTo(UNSET);
    }

    @ParameterizedTest
    @CsvSource({ "SMALL, 1, 1", "SMALL, 4, 1", "SMALL, 5, 2",
            "MEDIUM, 1, 2", "MEDIUM, 2, 2", "MEDIUM, 3, 3", "MEDIUM, 4, 3", "MEDIUM, 5, 4",
            "LARGE, 1, 6", "LARGE, 2, 7", "LARGE, 3, 9", "LARGE, 4, 10", "LARGE, 5, 12" })
    void forestCounts_scaleAcrossDifficultyBoundaries(DungeonSize size, int difficulty, int expected)
    {
        assertThat(DungeonMobTable.treants(DungeonTheme.FOREST, size, difficulty)).isEqualTo(expected);
    }
}
