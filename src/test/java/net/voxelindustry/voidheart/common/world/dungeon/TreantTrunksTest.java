package net.voxelindustry.voidheart.common.world.dungeon;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TreantTrunksTest
{
    private static final int SEEDS = 4;

    static Stream<Arguments> forests()
    {
        var all = new ArrayList<Arguments>();
        for (var size : DungeonSize.values())
        {
            if (!DungeonTheme.FOREST.comesIn(size))
                continue;
            for (var layout : DungeonLayout.values())
            {
                for (int seed = 0; seed < SEEDS; seed++)
                {
                    int difficulty = DungeonAddress.MIN_DIFFICULTY + seed;
                    all.add(Arguments.of(new DungeonBlueprint(size, DungeonTheme.FOREST, difficulty, layout, 7331L * (seed + 1) + size.ordinal())));
                }
            }
        }
        return all.stream();
    }

    @Test
    void count_onlyForestsAndMoreWithDifficulty()
    {
        for (var theme : DungeonTheme.values())
            assertThat(DungeonMobTable.treants(theme, DungeonSize.MEDIUM, 3) > 0).as("%s", theme).isEqualTo(theme == DungeonTheme.FOREST);

        for (var size : DungeonSize.values())
        {
            assertThat(DungeonMobTable.treants(DungeonTheme.FOREST, size, DungeonAddress.MIN_DIFFICULTY)).isPositive();
            assertThat(DungeonMobTable.treants(DungeonTheme.FOREST, size, DungeonAddress.MAX_DIFFICULTY))
                    .isGreaterThan(DungeonMobTable.treants(DungeonTheme.FOREST, size, DungeonAddress.MIN_DIFFICULTY));
        }
    }

    @ParameterizedTest
    @MethodSource("forests")
    void trunks_standOnGroundWithTheirTreantInTheFoot(DungeonBlueprint blueprint)
    {
        var plan = DungeonPlan.generate(blueprint);
        var grid = plan.grid();
        assertThat(plan.treants().size()).isLessThanOrEqualTo(DungeonMobTable.treants(blueprint.theme(), blueprint.size(), blueprint.difficulty()));

        for (var trunk : plan.treants())
        {
            int x = trunk[0], y = trunk[1], z = trunk[2];
            var at = "%d %d %d in %s".formatted(x, y, z, blueprint);
            assertThat(grid.material(x, y, z)).as(at).isEqualTo(DungeonMaterial.TREANT);
            var ground = grid.material(x, y - 1, z);
            assertThat(ground.isCube() && !ground.isOpen()).as("ground under %s is %s", at, ground).isTrue();
            for (int up = 1; up < TreantTrunks.TREANT_HEIGHT; up++)
                assertThat(grid.material(x, y + up, z)).as("trunk over %s", at).isEqualTo(DungeonMaterial.LOG);
        }
    }

    @ParameterizedTest
    @MethodSource("forests")
    void trunks_leaveTheirTreantAWayOut(DungeonBlueprint blueprint)
    {
        var plan = DungeonPlan.generate(blueprint);
        var grid = plan.grid();
        for (var trunk : plan.treants())
        {
            boolean out = false;
            for (var side : Direction.Plane.HORIZONTAL)
            {
                boolean open = true;
                for (int step = 1; step <= 2; step++)
                {
                    for (int up = 0; up < TreantTrunks.TREANT_HEIGHT; up++)
                    {
                        var cell = grid.material(trunk[0] + side.getStepX() * step, trunk[1] + up, trunk[2] + side.getStepZ() * step);
                        open &= TreantTrunks.isClear(cell);
                    }
                }
                out |= open;
                if (open)
                {
                    // Nor any root on either side of that way out, where its shoulders pass
                    int lateralX = side.getStepZ();
                    int lateralZ = side.getStepX();
                    for (int flank = -1; flank <= 1; flank += 2)
                    {
                        var beside = grid.material(trunk[0] + side.getStepX() + lateralX * flank, trunk[1], trunk[2] + side.getStepZ() + lateralZ * flank);
                        assertThat(beside).as("beside the way out of %d %d %d in %s", trunk[0], trunk[1], trunk[2], blueprint).isNotEqualTo(DungeonMaterial.ROOTS);
                    }
                }
            }
            assertThat(out).as("%d %d %d in %s", trunk[0], trunk[1], trunk[2], blueprint).isTrue();
        }
    }

    @Test
    void clear_onlyWhatHasNoCollisionInGame()
    {
        for (var clear : new DungeonMaterial[] { DungeonMaterial.AIR, DungeonMaterial.UNSET, DungeonMaterial.FILL_LIGHT, DungeonMaterial.PLANT,
                DungeonMaterial.TALL_PLANT, DungeonMaterial.HANGING })
            assertThat(TreantTrunks.isClear(clear)).as("%s", clear).isTrue();
        for (var blocking : new DungeonMaterial[] { DungeonMaterial.LANTERN, DungeonMaterial.CHAIN, DungeonMaterial.CANDLE, DungeonMaterial.RUNNER,
                DungeonMaterial.FLUID, DungeonMaterial.ROOTS, DungeonMaterial.LOG, DungeonMaterial.WOOD_FENCE, DungeonMaterial.TREANT })
            assertThat(TreantTrunks.isClear(blocking)).as("%s", blocking).isFalse();
    }

    @Test
    void forests_holdTreants()
    {
        int total = forests().mapToInt(arguments -> DungeonPlan.generate((DungeonBlueprint) arguments.get()[0]).treants().size()).sum();
        assertThat(total).isGreaterThan((int) forests().count());
    }

    @Test
    void otherThemes_haveNoTrunks()
    {
        for (var theme : DungeonTheme.values())
        {
            if (theme == DungeonTheme.FOREST || !theme.comesIn(DungeonSize.MEDIUM))
                continue;
            var plan = DungeonPlan.generate(new DungeonBlueprint(DungeonSize.MEDIUM, theme, 5, DungeonLayout.values()[0], 99));
            assertThat(plan.treants()).as("%s", theme).isEmpty();
            var grid = plan.grid();
            for (int y = 0; y < grid.height; y++)
                for (int z = 0; z < grid.depth; z++)
                    for (int x = 0; x < grid.width; x++)
                        assertThat(grid.material(x, y, z)).isNotEqualTo(DungeonMaterial.TREANT);
        }
    }

    @Test
    void sameBlueprint_sameTrunks()
    {
        var blueprint = new DungeonBlueprint(DungeonSize.MEDIUM, DungeonTheme.FOREST, 4, DungeonLayout.values()[0], 1234);
        var first = DungeonPlan.generate(blueprint).treants();
        var second = DungeonPlan.generate(blueprint).treants();
        assertThat(second).hasSameSizeAs(first);
        for (int i = 0; i < first.size(); i++)
            assertThat(second.get(i)).containsExactly(first.get(i));
    }
}
