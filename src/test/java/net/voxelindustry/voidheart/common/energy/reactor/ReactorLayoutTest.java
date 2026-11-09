package net.voxelindustry.voidheart.common.energy.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorLayout.Part;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorLayout.Slit;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReactorLayoutTest
{
    @Test
    void theShellIsTheCubeAroundTheCoreWithTwoIntakes()
    {
        for (var axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z})
        {
            var shell = ReactorLayout.shell(axis);
            assertThat(shell).hasSize(26);
            assertThat(new HashSet<>(shell.stream().map(ReactorLayout.Expected::offset).toList())).hasSize(26).doesNotContain(BlockPos.ZERO);
            assertThat(shell.stream().filter(block -> block.part() == Part.INTAKE).map(ReactorLayout.Expected::offset))
                    .containsExactlyInAnyOrderElementsOf(ReactorLayout.intakes(axis));
            shell.forEach(block -> assertThat(ReactorLayout.inShell(block.offset())).isTrue());
        }
        assertThat(ReactorLayout.inShell(BlockPos.ZERO)).isFalse();
        assertThat(ReactorLayout.inShell(new BlockPos(2, 0, 0))).isFalse();
    }

    @Test
    void intakesFaceEachOtherAcrossTheCore()
    {
        assertThat(ReactorLayout.intakes(Direction.Axis.X)).containsExactly(new BlockPos(-1, 0, 0), new BlockPos(1, 0, 0));
        assertThat(ReactorLayout.intakes(Direction.Axis.Z)).containsExactly(new BlockPos(0, 0, -1), new BlockPos(0, 0, 1));
    }

    @Test
    void theFourOtherFaceCentersCarrySlitsAlongTheAxis()
    {
        var alongX = Map.of(
                new BlockPos(0, 1, 0), Slit.Y_ALONG_X, new BlockPos(0, -1, 0), Slit.Y_ALONG_X,
                new BlockPos(0, 0, 1), Slit.Z_ALONG_X, new BlockPos(0, 0, -1), Slit.Z_ALONG_X);
        var alongZ = Map.of(
                new BlockPos(0, 1, 0), Slit.Y_ALONG_Z, new BlockPos(0, -1, 0), Slit.Y_ALONG_Z,
                new BlockPos(1, 0, 0), Slit.X_ALONG_Z, new BlockPos(-1, 0, 0), Slit.X_ALONG_Z);

        for (var block : ReactorLayout.shell(Direction.Axis.X))
            assertThat(block.slit()).as(block.offset().toShortString()).isEqualTo(alongX.getOrDefault(block.offset(), Slit.NONE));
        for (var block : ReactorLayout.shell(Direction.Axis.Z))
            assertThat(block.slit()).as(block.offset().toShortString()).isEqualTo(alongZ.getOrDefault(block.offset(), Slit.NONE));

        for (var slit : Slit.values())
        {
            if (slit != Slit.NONE)
                assertThat(slit.along() != slit.through() && slit.along() != Direction.Axis.Y).as(slit.name()).isTrue();
        }
    }

    @Test
    void theAxisFollowsTheIntakesAlreadyPlaced()
    {
        assertThat((Object) ReactorLayout.inferAxis(offset -> false)).isEqualTo(Direction.Axis.X);
        assertThat((Object) ReactorLayout.inferAxis(offset -> offset.equals(new BlockPos(0, 0, 1)))).isEqualTo(Direction.Axis.Z);
        assertThat((Object) ReactorLayout.inferAxis(offset -> offset.equals(new BlockPos(-1, 0, 0)))).isEqualTo(Direction.Axis.X);
        assertThat((Object) ReactorLayout.inferAxis(offset -> offset.getY() == 0 && Math.abs(offset.getX() + offset.getZ()) == 1 && offset.getX() * offset.getZ() == 0))
                .isEqualTo(Direction.Axis.X);
    }

    @Test
    void missingListsWhatIsAbsentOrWrong()
    {
        assertThat(ReactorLayout.missing(Direction.Axis.X, offset -> null)).hasSize(26);

        var whole = ReactorLayout.missing(Direction.Axis.X, offset -> ReactorLayout.isIntake(Direction.Axis.X, offset) ? Part.INTAKE : Part.CONTAINMENT);
        assertThat(whole).isEmpty();

        // Containment where an intake belongs, and an intake in a corner
        var wrong = ReactorLayout.missing(Direction.Axis.X, offset -> offset.equals(new BlockPos(1, 0, 0)) || offset.equals(new BlockPos(1, 1, 1))
                ? (offset.getY() == 0 ? Part.CONTAINMENT : Part.INTAKE)
                : ReactorLayout.isIntake(Direction.Axis.X, offset) ? Part.INTAKE : Part.CONTAINMENT);
        assertThat(wrong).extracting(ReactorLayout.Expected::offset).containsExactlyInAnyOrder(new BlockPos(1, 0, 0), new BlockPos(1, 1, 1));
    }
}
