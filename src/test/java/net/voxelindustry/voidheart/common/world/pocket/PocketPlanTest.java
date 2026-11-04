package net.voxelindustry.voidheart.common.world.pocket;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import static net.voxelindustry.voidheart.common.world.pocket.PocketGrid.WALL_REACH;
import static org.assertj.core.api.Assertions.assertThat;

class PocketPlanTest
{
    private static final List<Long> SEEDS = LongStream.range(0, 40).boxed().toList();

    private static PocketPlan plan(PocketVariant variant, long seed)
    {
        return PocketPlan.generate(new PocketBlueprint(variant, seed * 7919));
    }

    @ParameterizedTest
    @EnumSource(PocketVariant.class)
    void pocket_keepsTheOriginalSizeSealedByItsWall(PocketVariant variant)
    {
        for (long seed : SEEDS)
        {
            var grid = plan(variant, seed).grid();
            assertThat(grid.top() - grid.bottom()).isEqualTo(WALL_REACH * 2);

            forEachCell(grid, (x, y, z) ->
            {
                if (grid.isBoundary(x, y, z))
                    assertThat(grid.slot(x, y, z)).as("%s at %s %s %s", variant, x, y, z).isEqualTo(PocketSlot.WALL);
                else if (!grid.isInner(x, y, z))
                    assertThat(grid.slot(x, y, z).isOpen()).as("the rock layer of %s at %s %s %s", variant, x, y, z).isFalse();
            });
        }
    }

    @ParameterizedTest
    @EnumSource(PocketVariant.class)
    void arrivalPoint_isDrySupportedAndFree(PocketVariant variant)
    {
        for (long seed : SEEDS)
        {
            var plan = plan(variant, seed);
            var grid = plan.grid();

            assertThat(grid.slot(0, -1, 0)).isEqualTo(PocketSlot.PEDESTAL);
            for (int y = 0; y <= 3; y++)
                assertThat(grid.slot(0, y, 0)).as("%s %s at height %s", variant, seed, y).isEqualTo(PocketSlot.AIR);

            assertThat(plan.crystals()).noneMatch(crystal -> Math.abs(crystal.x()) <= 1 && Math.abs(crystal.z()) <= 1 && crystal.y() >= 0 && crystal.y() <= 3);
            assertThat(plan.decor()).noneMatch(decor -> Math.abs(decor.x()) <= 1 && Math.abs(decor.z()) <= 1 && decor.y() >= 0 && decor.y() <= 3);
        }
    }

    @ParameterizedTest
    @EnumSource(PocketVariant.class)
    void fluid_hasNowhereToFlow(PocketVariant variant)
    {
        for (long seed : SEEDS)
        {
            var grid = plan(variant, seed).grid();
            forEachCell(grid, (x, y, z) ->
            {
                if (grid.slot(x, y, z) != PocketSlot.FLUID)
                    return;
                for (var side : Direction.Plane.HORIZONTAL)
                    assertThat(grid.slot(x + side.getStepX(), y, z + side.getStepZ())).as("%s %s beside %s %s %s", variant, seed, x, y, z).isNotEqualTo(PocketSlot.AIR);
                assertThat(grid.slot(x, y - 1, z)).isNotEqualTo(PocketSlot.AIR);
            });
        }
    }

    @ParameterizedTest
    @EnumSource(PocketVariant.class)
    void everyPocket_holdsBothCrystalsAndNoOthers(PocketVariant variant)
    {
        for (long seed : SEEDS)
        {
            var plan = plan(variant, seed);
            assertThat(plan.crystals().stream().filter(crystal -> crystal.type() == PocketCrystal.Type.AZURIUM).count())
                    .as("azurium in %s %s", variant, seed).isGreaterThanOrEqualTo(PocketPlan.MINIMUM_CRYSTALS_PER_TYPE);
            assertThat(plan.crystals().stream().filter(crystal -> crystal.type() == PocketCrystal.Type.RUBRUM).count())
                    .as("rubrum in %s %s", variant, seed).isGreaterThanOrEqualTo(PocketPlan.MINIMUM_CRYSTALS_PER_TYPE);
        }
    }

    @ParameterizedTest
    @EnumSource(PocketVariant.class)
    void crystals_growFromRockInOpenCells(PocketVariant variant)
    {
        for (long seed : SEEDS)
        {
            var plan = plan(variant, seed);
            var grid = plan.grid();
            var cells = new HashSet<String>();
            for (var crystal : plan.crystals())
            {
                assertThat(grid.slot(crystal.x(), crystal.y(), crystal.z()).isOpen()).isTrue();
                var rock = crystal.facing().getOpposite();
                assertThat(grid.slot(crystal.x() + rock.getStepX(), crystal.y() + rock.getStepY(), crystal.z() + rock.getStepZ()).isAnchor()).isTrue();
                assertThat(cells.add(crystal.x() + " " + crystal.y() + " " + crystal.z())).isTrue();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(PocketVariant.class)
    void crystals_canBeReachedWithoutDigging(PocketVariant variant)
    {
        for (long seed : SEEDS)
        {
            var plan = plan(variant, seed);
            var reached = reachableFromHeart(plan.grid());
            long reachable = plan.crystals().stream().filter(crystal -> reached.contains(key(crystal.x(), crystal.y(), crystal.z()))).count();
            assertThat(reachable).as("%s %s reached %s of %s", variant, seed, reachable, plan.crystals().size())
                    .isGreaterThanOrEqualTo(plan.crystals().size() * 3L / 4);
        }
    }

    @ParameterizedTest
    @EnumSource(PocketVariant.class)
    void plan_isDeterministicAndVariesWithTheSeed(PocketVariant variant)
    {
        var first = plan(variant, 3);
        var again = plan(variant, 3);
        var other = plan(variant, 4);

        assertThat(first.crystals()).isEqualTo(again.crystals());
        assertThat(first.decor()).isEqualTo(again.decor());
        assertThat(first.crystals()).isNotEqualTo(other.crystals());
    }

    @Test
    void neighbouringPockets_cycleThroughEveryAmbienceThenEveryVariant()
    {
        var variants = new HashSet<PocketVariant>();
        for (int index = 0; index < 60; index++)
        {
            var blueprint = PocketBlueprint.forPocket(12345L, index, new BlockPos(index * 256, 64, 0));
            variants.add(blueprint.variant());

            var next = PocketBlueprint.forPocket(12345L, index + 1, new BlockPos((index + 1) * 256, 64, 0));
            assertThat(next.variant().ambience()).isNotEqualTo(blueprint.variant().ambience());
        }
        assertThat(variants).containsExactlyInAnyOrder(PocketVariant.values());
    }

    @Test
    void heartData_fromBeforeTerrain_keepsItsCube()
    {
        var saved = JsonParser.parseString("{\"pocketPos\":[256,64,0],\"maxFlexion\":3}");
        var data = VoidHeartData.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();

        assertThat(data.heartRise()).isZero();
        assertThat(data.contains(new BlockPos(256 + 8, 64 + 14, 8))).isTrue();
        assertThat(data.contains(new BlockPos(256 + 9, 64, 0))).isFalse();
        assertThat(data.contains(new BlockPos(256, 64 + 15, 0))).isFalse();
        assertThat(data.contains(new BlockPos(256, 64 - 2, 0))).isTrue();
        assertThat(data.contains(new BlockPos(256, 64 - 3, 0))).isFalse();
    }

    @Test
    void heartData_followsThePocketDownWithItsHeartRise()
    {
        var data = VoidHeartData.create(new BlockPos(0, 64, 0));
        data.heartRise(3);

        assertThat(data.contains(new BlockPos(0, 64 - 5, 0))).isTrue();
        assertThat(data.contains(new BlockPos(0, 64 - 6, 0))).isFalse();
        assertThat(data.contains(new BlockPos(0, 64 + 11, 0))).isTrue();
        assertThat(data.contains(new BlockPos(0, 64 + 12, 0))).isFalse();

        var saved = VoidHeartData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        assertThat(VoidHeartData.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow().heartRise()).isEqualTo(3);
    }

    private static Set<Long> reachableFromHeart(PocketGrid grid)
    {
        var reached = new HashSet<Long>();
        var frontier = new ArrayDeque<int[]>();
        frontier.add(new int[] { 0, 1, 0 });
        reached.add(key(0, 1, 0));

        while (!frontier.isEmpty())
        {
            var cell = frontier.poll();
            for (var direction : Direction.values())
            {
                int x = cell[0] + direction.getStepX(), y = cell[1] + direction.getStepY(), z = cell[2] + direction.getStepZ();
                var slot = grid.slot(x, y, z);
                boolean thinIce = slot == PocketSlot.FROZEN_FLUID;
                if ((slot.isOpen() || thinIce) && reached.add(key(x, y, z)))
                    frontier.add(new int[] { x, y, z });
            }
        }
        return reached;
    }

    private static long key(int x, int y, int z)
    {
        return x + 100L + (z + 100L) * 1000L + (y + 100L) * 1000000L;
    }

    private interface CellVisitor
    {
        void visit(int x, int y, int z);
    }

    private static void forEachCell(PocketGrid grid, CellVisitor visitor)
    {
        for (int y = grid.bottom(); y <= grid.top(); y++)
        {
            for (int z = -WALL_REACH; z <= WALL_REACH; z++)
            {
                for (int x = -WALL_REACH; x <= WALL_REACH; x++)
                    visitor.visit(x, y, z);
            }
        }
    }
}
