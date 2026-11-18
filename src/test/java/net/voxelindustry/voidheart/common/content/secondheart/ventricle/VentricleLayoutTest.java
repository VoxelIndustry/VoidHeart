package net.voxelindustry.voidheart.common.content.secondheart.ventricle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleLayout.Orientation;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleLayout.Part;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VentricleLayoutTest
{
    private static Map<BlockPos, Part> built(Orientation orientation)
    {
        var world = new HashMap<BlockPos, Part>();
        for (var expected : VentricleLayout.expected(orientation))
            world.put(expected.offset(), expected.part());
        return world;
    }

    @Test
    void expected_isNineByThreeAroundTheCradle()
    {
        for (var orientation : Orientation.ALL)
        {
            var parts = VentricleLayout.expected(orientation);
            assertThat(parts).hasSize(VentricleLayout.BLOCKS);
            assertThat(new HashSet<>(parts.stream().map(VentricleLayout.Expected::offset).toList())).hasSize(VentricleLayout.BLOCKS)
                    .doesNotContain(BlockPos.ZERO);
            for (var part : parts)
            {
                boolean x = orientation.axis() == Direction.Axis.X;
                int along = x ? part.offset().getX() : part.offset().getZ();
                int across = x ? part.offset().getZ() : part.offset().getX();
                assertThat(Math.abs(along)).isLessThanOrEqualTo(4);
                assertThat(Math.abs(across)).isLessThanOrEqualTo(1);
                assertThat(part.offset().getY()).isZero();
            }
        }
    }

    @Test
    void expected_hasEightDaisAndTwoOfEachSidePiece()
    {
        var parts = VentricleLayout.expected(new Orientation(Direction.Axis.X, true));
        assertThat(parts.stream().filter(p -> p.part() == Part.DAIS)).hasSize(8);
        assertThat(parts.stream().filter(p -> p.part() == Part.VALVE)).hasSize(2);
        assertThat(parts.stream().filter(p -> p.part() == Part.BASIN)).hasSize(2);
        assertThat(parts.stream().filter(p -> p.part() == Part.RUBRUM_WALL)).hasSize(7);
        assertThat(parts.stream().filter(p -> p.part() == Part.AZURIUM_WALL)).hasSize(7);
    }

    @Test
    void basin_isInsideItsOwnWalls()
    {
        for (var orientation : Orientation.ALL)
        {
            var world = built(orientation);
            var essenceBasin = orientation.basin(true);
            assertThat(world.get(essenceBasin)).isEqualTo(Part.BASIN);
            var beside = orientation.axis() == Direction.Axis.X ? essenceBasin.offset(0, 0, 1) : essenceBasin.offset(1, 0, 0);
            assertThat(world.get(beside)).isEqualTo(Part.RUBRUM_WALL);
            var contravoidBasin = orientation.basin(false);
            var besideOther = orientation.axis() == Direction.Axis.X ? contravoidBasin.offset(0, 0, -1) : contravoidBasin.offset(-1, 0, 0);
            assertThat(world.get(besideOther)).isEqualTo(Part.AZURIUM_WALL);
        }
    }

    @Test
    void bestFit_findsEachBuiltOrientation()
    {
        for (var orientation : Orientation.ALL)
        {
            var world = built(orientation);
            assertThat(VentricleLayout.bestFit(world::get)).isEqualTo(orientation);
            assertThat(VentricleLayout.missing(orientation, world::get)).isEmpty();
        }
    }

    @Test
    void missing_givenOneWallGone_thenListsOnlyIt()
    {
        var orientation = new Orientation(Direction.Axis.Z, false);
        var world = built(orientation);
        var gone = orientation.along(-4, 1);
        world.remove(gone);
        assertThat(VentricleLayout.bestFit(world::get)).isEqualTo(orientation);
        assertThat(VentricleLayout.missing(orientation, world::get)).extracting(VentricleLayout.Expected::offset).containsExactly(gone);
    }

    @Test
    void missing_givenWallsSwapped_thenEveryWallIsWrong()
    {
        var world = built(new Orientation(Direction.Axis.X, false));
        assertThat(VentricleLayout.missing(new Orientation(Direction.Axis.X, true), world::get)).hasSize(14);
    }
}
