package net.voxelindustry.voidheart.common.world.dungeon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.stream.Stream;

import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMob.Family.FALLEN;
import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMob.Family.PEST;
import static org.assertj.core.api.Assertions.assertThat;

class DungeonMobPlanTest
{
    private static final int SEEDS = 3;

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
            assertThat(mobs).contains(DungeonMob.VOID_SKELETON, DungeonMob.VOID_GHOUL, DungeonMob.WRAITH);

            boolean pests = theme == DungeonTheme.FOREST || theme == DungeonTheme.MYCOSIS;
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
    }

    @Test
    void nests_onlyGrowWherePestsLive()
    {
        for (var theme : DungeonTheme.values())
        {
            boolean pests = theme == DungeonTheme.FOREST || theme == DungeonTheme.MYCOSIS;
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
        boolean pests = blueprint.theme() == DungeonTheme.FOREST || blueprint.theme() == DungeonTheme.MYCOSIS;
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
    void mobs_areDeterministic(DungeonBlueprint blueprint)
    {
        assertThat(DungeonPlan.generate(blueprint).mobs()).isEqualTo(DungeonPlan.generate(blueprint).mobs());
    }
}
