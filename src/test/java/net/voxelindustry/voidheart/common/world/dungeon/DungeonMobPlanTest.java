package net.voxelindustry.voidheart.common.world.dungeon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.stream.Stream;

import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMob.Family.EYE;
import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMob.Family.FALLEN;
import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMob.Family.GENERIC;
import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMob.Family.PEST;
import static org.assertj.core.api.Assertions.assertThat;

class DungeonMobPlanTest
{
    private static final int SEEDS = 3;
    private static final int MIX_DUNGEONS = 40;

    static Stream<Arguments> blueprints()
    {
        var all = new ArrayList<Arguments>();
        for (var size : DungeonSize.values())
        {
            for (var theme : DungeonTheme.values())
            {
                if (!theme.comesIn(size))
                    continue;

                for (var layout : DungeonLayout.values())
                {
                    for (int seed = 0; seed < SEEDS; seed++)
                    {
                        int difficulty = DungeonAddress.MIN_DIFFICULTY + seed * 2;
                        all.add(Arguments.of(new DungeonBlueprint(size, theme, difficulty, layout, 4242L * (seed + 1) + theme.ordinal())));
                    }
                }
            }
        }
        return all.stream();
    }

    @Test
    void budget_doublesFromEasiestToHardest()
    {
        assertThat(DungeonMobTable.budget(DungeonSize.SMALL, 1)).isEqualTo(5);
        assertThat(DungeonMobTable.budget(DungeonSize.MEDIUM, 1)).isEqualTo(15);
        assertThat(DungeonMobTable.budget(DungeonSize.LARGE, 1)).isEqualTo(45);
        assertThat(DungeonMobTable.budget(DungeonSize.SMALL, 5)).isEqualTo(10);
        assertThat(DungeonMobTable.budget(DungeonSize.MEDIUM, 5)).isEqualTo(30);
        assertThat(DungeonMobTable.budget(DungeonSize.LARGE, 5)).isEqualTo(90);
    }

    @Test
    void roster_keepsFamiliesToTheirThemes()
    {
        for (var theme : DungeonTheme.values())
        {
            var mobs = DungeonMobTable.roster(theme, 5);
            boolean depths = theme == DungeonTheme.DEPTHS;
            assertThat(mobs.stream().anyMatch(mob -> mob.family == GENERIC)).as("generic in %s", theme).isEqualTo(!depths);
            assertThat(mobs.stream().anyMatch(mob -> mob.family == EYE)).as("eyes in %s", theme).isEqualTo(depths);

            boolean pests = theme == DungeonTheme.FOREST || theme == DungeonTheme.MYCOSIS || depths;
            boolean fallen = theme == DungeonTheme.TEMPLE || theme == DungeonTheme.CATHEDRAL;
            assertThat(mobs.stream().anyMatch(mob -> mob.family == PEST)).as("pests in %s", theme).isEqualTo(pests);
            assertThat(mobs.stream().anyMatch(mob -> mob.family == FALLEN)).as("fallen in %s", theme).isEqualTo(fallen);
        }
    }

    @Test
    void roster_unlocksTougherTypesWithDifficulty()
    {
        assertThat(DungeonMobTable.roster(DungeonTheme.TEMPLE, 1)).doesNotContain(DungeonMob.WRAITH, DungeonMob.FALLEN_ADEPT, DungeonMob.FALLEN_ELDER).contains(DungeonMob.FALLEN_NOVICE);
        assertThat(DungeonMobTable.roster(DungeonTheme.TEMPLE, 2)).contains(DungeonMob.WRAITH).doesNotContain(DungeonMob.FALLEN_ADEPT);
        assertThat(DungeonMobTable.roster(DungeonTheme.TEMPLE, 3)).contains(DungeonMob.FALLEN_ADEPT).doesNotContain(DungeonMob.FALLEN_ELDER);
        assertThat(DungeonMobTable.roster(DungeonTheme.TEMPLE, 4)).contains(DungeonMob.FALLEN_ELDER);
        assertThat(DungeonMobTable.roster(DungeonTheme.FOREST, 1)).contains(DungeonMob.CRAB_SMALL, DungeonMob.CRAB_MEDIUM, DungeonMob.CRAB_LARGE, DungeonMob.BARNACLE);
        assertThat(DungeonMobTable.roster(DungeonTheme.DEPTHS, 1)).contains(DungeonMob.EYE_MAW, DungeonMob.CRAB_SMALL, DungeonMob.BARNACLE).doesNotContain(DungeonMob.ARCANE_EYE, DungeonMob.LARGE_EYE_MAW);
        assertThat(DungeonMobTable.roster(DungeonTheme.DEPTHS, 2)).contains(DungeonMob.ARCANE_EYE).doesNotContain(DungeonMob.LARGE_EYE_MAW);
        assertThat(DungeonMobTable.roster(DungeonTheme.DEPTHS, 3)).contains(DungeonMob.ARCANE_EYE, DungeonMob.LARGE_EYE_MAW);
    }

    @Test
    void nests_onlyGrowWherePestsLive()
    {
        for (var theme : DungeonTheme.values())
        {
            boolean pests = theme == DungeonTheme.FOREST || theme == DungeonTheme.MYCOSIS || theme == DungeonTheme.DEPTHS;
            assertThat(DungeonMobTable.nests(theme, DungeonSize.MEDIUM, 3) > 0).as("nests in %s", theme).isEqualTo(pests);
        }
    }

    @ParameterizedTest
    @MethodSource("blueprints")
    void mobs_fillTheBudgetAndBelongToTheTheme(DungeonBlueprint blueprint)
    {
        var plan = DungeonPlan.generate(blueprint);
        int budget = DungeonMobTable.budget(blueprint.size(), blueprint.difficulty());
        var roster = DungeonMobTable.roster(blueprint.theme(), blueprint.difficulty());

        assertThat(plan.mobs()).as("%s", blueprint).hasSizeLessThanOrEqualTo(budget);
        assertThat(plan.mobs().size()).as("%s", blueprint).isGreaterThanOrEqualTo(budget / 2);
        assertThat(plan.mobs()).allSatisfy(placement -> assertThat(roster).contains(placement.mob()));
    }

    @ParameterizedTest
    @MethodSource("blueprints")
    void mobs_standOnGroundOrHoldASurface(DungeonBlueprint blueprint)
    {
        var plan = DungeonPlan.generate(blueprint);
        var grid = plan.grid();
        for (var placement : plan.mobs())
        {
            if (placement.scanHeight() > 0)
                continue;

            int x = placement.x();
            int y = placement.y();
            int z = placement.z();
            assertThat(grid.material(x, y, z).isOpen()).as("%s in %s", placement, blueprint).isTrue();

            if (placement.mob().flies)
                continue;

            var surface = placement.surface();
            var held = grid.material(x - surface.getStepX(), y - surface.getStepY(), z - surface.getStepZ());
            assertThat(held.isOpen()).as("%s in %s holds %s", placement, blueprint, held).isFalse();
        }
    }

    @ParameterizedTest
    @MethodSource("blueprints")
    void nests_alwaysHoldOnToSomething(DungeonBlueprint blueprint)
    {
        var plan = DungeonPlan.generate(blueprint);
        var grid = plan.grid();
        boolean pests = blueprint.theme() == DungeonTheme.FOREST || blueprint.theme() == DungeonTheme.MYCOSIS || blueprint.theme() == DungeonTheme.DEPTHS;
        int found = 0;
        for (int y = 0; y < grid.height; y++)
        {
            for (int z = 0; z < blueprint.size().edge(); z++)
            {
                for (int x = 0; x < blueprint.size().edge(); x++)
                {
                    short cell = grid.cell(x, y, z);
                    if (DungeonCell.material(cell) != DungeonMaterial.NEST)
                        continue;

                    found++;
                    var facing = DungeonCell.facing(cell);
                    assertThat(grid.material(x - facing.getStepX(), y - facing.getStepY(), z - facing.getStepZ()).isAnchor()).as("nest at %d %d %d", x, y, z).isTrue();
                }
            }
        }
        if (!pests)
            assertThat(found).isZero();
    }

    @ParameterizedTest
    @MethodSource("blueprints")
    void flyers_hangInTheAirWithRoomForThemselves(DungeonBlueprint blueprint)
    {
        var plan = DungeonPlan.generate(blueprint);
        var grid = plan.grid();
        for (var placement : plan.mobs())
        {
            var mob = placement.mob();
            if (!mob.flies)
                continue;

            for (int up = 0; up < mob.height; up++)
            {
                for (int dz = -mob.halfWidth; dz <= mob.halfWidth; dz++)
                {
                    for (int dx = -mob.halfWidth; dx <= mob.halfWidth; dx++)
                        assertThat(grid.material(placement.x() + dx, placement.y() + up, placement.z() + dz).isOpen()).as("%s in %s", placement, blueprint).isTrue();
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("blueprints")
    void eyeMaws_comeInFlocks(DungeonBlueprint blueprint)
    {
        var maws = DungeonPlan.generate(blueprint).mobs().stream().filter(placement -> placement.mob() == DungeonMob.EYE_MAW).toList();
        if (maws.size() < 2)
            return;

        long withMates = maws.stream().filter(one -> maws.stream().anyMatch(other -> other != one
                && Math.abs(other.x() - one.x()) <= 6 && Math.abs(other.y() - one.y()) <= 6 && Math.abs(other.z() - one.z()) <= 6)).count();
        assertThat(withMates).as("%s", blueprint).isGreaterThanOrEqualTo(maws.size() * 3L / 4);
    }

    @Test
    void depths_holdAllTheirMobsOverManyDungeons()
    {
        for (int difficulty : new int[] { 1, 3, 5 })
        {
            var counts = new java.util.EnumMap<DungeonMob, Integer>(DungeonMob.class);
            int total = 0;
            for (int seed = 0; seed < MIX_DUNGEONS; seed++)
            {
                var layout = DungeonLayout.values()[seed % DungeonLayout.values().length];
                var blueprint = new DungeonBlueprint(DungeonSize.MEDIUM, DungeonTheme.DEPTHS, difficulty, layout, 97L * seed + 13);
                for (var placement : DungeonPlan.generate(blueprint).mobs())
                {
                    counts.merge(placement.mob(), 1, Integer::sum);
                    total++;
                }
            }
            System.out.println("[DepthsMix] difficulty " + difficulty + " average " + total / MIX_DUNGEONS + ": " + counts);

            double maws = counts.getOrDefault(DungeonMob.EYE_MAW, 0) / (double) total;
            assertThat(maws).as("maw share at difficulty %d", difficulty).isBetween(0.15, 0.6);
            if (difficulty >= 2)
                assertThat(counts.getOrDefault(DungeonMob.ARCANE_EYE, 0) / (double) total).as("arcane share at difficulty %d", difficulty).isGreaterThan(0.06);
            if (difficulty >= 3)
                assertThat(counts.getOrDefault(DungeonMob.LARGE_EYE_MAW, 0)).as("large maws at difficulty %d", difficulty).isGreaterThan(0);
        }
    }

    @ParameterizedTest
    @MethodSource("blueprints")
    void mobs_areDeterministic(DungeonBlueprint blueprint)
    {
        assertThat(DungeonPlan.generate(blueprint).mobs()).isEqualTo(DungeonPlan.generate(blueprint).mobs());
    }
}
