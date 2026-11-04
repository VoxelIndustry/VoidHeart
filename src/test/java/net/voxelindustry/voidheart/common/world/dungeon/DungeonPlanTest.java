package net.voxelindustry.voidheart.common.world.dungeon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.stream.Stream;

import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial.*;
import static org.assertj.core.api.Assertions.assertThat;

class DungeonPlanTest
{
    private static final int MAX_STEP_UP = 1;
    private static final int MAX_DROP = 4;

    static Stream<Arguments> dungeons()
    {
        var all = Stream.<Arguments>builder();
        for (var size : DungeonSize.values())
        {
            int seeds = size == DungeonSize.LARGE ? 2 : size == DungeonSize.MEDIUM ? 4 : 8;
            for (var theme : DungeonTheme.values())
            {
                for (var layout : DungeonLayout.values())
                {
                    for (int seed = 0; seed < seeds; seed++)
                        all.add(Arguments.of(new DungeonBlueprint(size, theme, 1 + seed % DungeonAddress.MAX_DIFFICULTY, layout, 7919L * seed + theme.ordinal())));
                }
            }
        }
        return all.build();
    }

    @Test
    void harderDungeons_tendToBeBuried()
    {
        double[][] expected = { { 0, 0.15 }, { 0.1, 0.4 }, { 0.35, 0.65 }, { 0.6, 0.9 }, { 0.85, 1 } };
        int samples = 1000;
        for (int difficulty = DungeonAddress.MIN_DIFFICULTY; difficulty <= DungeonAddress.MAX_DIFFICULTY; difficulty++)
        {
            int buried = 0;
            for (int index = 0; index < samples; index++)
            {
                var address = new DungeonAddress(DungeonSize.MEDIUM, DungeonTheme.PALACE, difficulty, index);
                if (DungeonBlueprint.of(12345L, address).isBuried())
                    buried++;
            }
            assertThat(buried / (double) samples).as("buried share at difficulty %s", difficulty)
                    .isBetween(expected[difficulty - 1][0], expected[difficulty - 1][1]);
        }
    }

    @ParameterizedTest
    @MethodSource("dungeons")
    void shell_isSealedByUnbreakableWall(DungeonBlueprint blueprint)
    {
        var grid = DungeonPlan.generate(blueprint).grid();
        int last = grid.width - 1;
        for (int a = 0; a <= last; a++)
        {
            for (int b = 0; b <= last; b++)
            {
                assertThat(grid.material(0, a, b)).isEqualTo(POCKET_WALL);
                assertThat(grid.material(last, a, b)).isEqualTo(POCKET_WALL);
                assertThat(grid.material(a, 0, b)).isEqualTo(POCKET_WALL);
                assertThat(grid.material(a, last, b)).isEqualTo(POCKET_WALL);
                assertThat(grid.material(a, b, 0)).isEqualTo(POCKET_WALL);
                assertThat(grid.material(a, b, last)).isEqualTo(POCKET_WALL);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("dungeons")
    void everyRoom_isReachableOnFootFromTheEntry(DungeonBlueprint blueprint)
    {
        var plan = DungeonPlan.generate(blueprint);
        var reached = walk(plan);

        assertThat(plan.rooms()).isNotEmpty();
        for (var room : plan.rooms())
        {
            if (room != plan.entryRoom())
                assertThat(room.doors).as("doors of %s in %s", room.kind.name(), blueprint).isNotEmpty();
            for (var door : room.doors)
            {
                boolean found = false;
                for (int y = door.y() - 3; y <= door.y() + 3 && !found; y++)
                    found = reached.get(index(plan.grid(), door.x(), y, door.z()));
                assertThat(found).as("door %s of %s in %s%n%s", door, room.kind.name(), blueprint, map(plan, reached, door.y() + 1) + map(plan, reached, plan.entry()[1])).isTrue();
            }
        }
    }

    /**
     * A top view for failure messages: + reached on foot, # solid, . open, ~ water.
     */
    private static String map(DungeonPlan plan, BitSet reached, int y)
    {
        var grid = plan.grid();
        var text = new StringBuilder("entry ").append(java.util.Arrays.toString(plan.entry())).append(" at height ").append(y).append('\n');
        for (int z = 0; z < grid.depth; z++)
        {
            for (int x = 0; x < grid.width; x++)
            {
                var material = grid.material(x, y, z);
                char symbol = material == FLUID ? '~' : material.isPassable() ? '.' : '#';
                if (reached.get(index(grid, x, y, z)))
                    symbol = '+';
                text.append(symbol);
            }
            text.append('\n');
        }
        return text.toString();
    }

    @ParameterizedTest
    @MethodSource("dungeons")
    void resources_includeEveryCrystalAndChests(DungeonBlueprint blueprint)
    {
        var plan = DungeonPlan.generate(blueprint);
        var counts = new EnumMap<DungeonCrystal, Integer>(DungeonCrystal.class);
        int metal = 0;
        var grid = plan.grid();
        for (int y = 0; y < grid.height; y++)
        {
            for (int z = 0; z < grid.depth; z++)
            {
                for (int x = 0; x < grid.width; x++)
                {
                    var material = grid.material(x, y, z);
                    if (material.isCrystal())
                        counts.merge(material.crystalKind(), 1, Integer::sum);
                    else if (material.name().startsWith("METAL"))
                        metal++;
                }
            }
        }

        assertThat(counts).as("crystals of %s", blueprint).containsKey(DungeonCrystal.MEMORIAM);
        assertThat(counts.size() >= 2).as("crystal kinds of %s", blueprint).isTrue();
        assertThat(plan.chests()).as("chests of %s", blueprint).isNotEmpty();
        if (blueprint.size() != DungeonSize.SMALL)
            assertThat(metal).as("metal of %s", blueprint).isPositive();
    }

    @ParameterizedTest
    @MethodSource("dungeons")
    void forestRooms_eachHoldToxicWater(DungeonBlueprint blueprint)
    {
        if (blueprint.theme() != DungeonTheme.FOREST)
            return;

        var plan = DungeonPlan.generate(blueprint);
        var grid = plan.grid();
        for (var room : plan.rooms())
        {
            if (room.kind.template() != null)
                continue;

            boolean found = false;
            for (int y = room.minY() - 3; y <= room.maxY() && !found; y++)
            {
                for (int z = room.minZ(); z <= room.maxZ() && !found; z++)
                {
                    for (int x = room.minX(); x <= room.maxX() && !found; x++)
                        found = grid.material(x, y, z) == FLUID;
                }
            }
            assertThat(found).as("water in %s of %s", room.kind.name(), blueprint).isTrue();
        }
    }

    @ParameterizedTest
    @MethodSource("dungeons")
    void generation_isDeterministic(DungeonBlueprint blueprint)
    {
        if (blueprint.size() == DungeonSize.LARGE)
            return;

        var first = DungeonPlan.generate(blueprint).grid();
        var second = DungeonPlan.generate(blueprint).grid();
        for (int y = 0; y < first.height; y++)
        {
            for (int z = 0; z < first.depth; z++)
            {
                for (int x = 0; x < first.width; x++)
                    assertThat(first.cell(x, y, z)).isEqualTo(second.cell(x, y, z));
            }
        }
    }

    static int index(DungeonGrid grid, int x, int y, int z)
    {
        return (y * grid.depth + z) * grid.width + x;
    }

    private static boolean canStand(DungeonGrid grid, int x, int y, int z)
    {
        if (!grid.isInside(x, y - 1, z) || !grid.isInside(x, y + 1, z))
            return false;

        var below = grid.material(x, y - 1, z);
        boolean support = !below.isPassable() && below != FENCE && below != WOOD_FENCE && below != METAL_WALL && below != GRATE && below != TEMPLATE;
        return support && grid.material(x, y, z).isPassable() && grid.material(x, y + 1, z).isPassable();
    }

    static BitSet walk(DungeonPlan plan)
    {
        var grid = plan.grid();
        var reached = new BitSet(grid.width * grid.height * grid.depth);
        var queue = new ArrayDeque<int[]>();
        var entry = plan.entry();
        assertThat(canStand(grid, entry[0], entry[1], entry[2])).as("entry of %s", plan.blueprint()).isTrue();

        reached.set(index(grid, entry[0], entry[1], entry[2]));
        queue.add(entry);
        int[][] sides = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        while (!queue.isEmpty())
        {
            var at = queue.poll();
            for (var side : sides)
            {
                int x = at[0] + side[0];
                int z = at[2] + side[1];
                for (int y = at[1] + MAX_STEP_UP; y >= at[1] - MAX_DROP; y--)
                {
                    if (!canStand(grid, x, y, z) || !grid.material(x, Math.max(y, at[1]) + 1, z).isPassable())
                        continue;
                    if (y > at[1] && !grid.material(at[0], y + 1, at[2]).isPassable())
                        continue;

                    int key = index(grid, x, y, z);
                    if (!reached.get(key))
                    {
                        reached.set(key);
                        queue.add(new int[] { x, y, z });
                    }
                }
            }
        }
        return reached;
    }
}
