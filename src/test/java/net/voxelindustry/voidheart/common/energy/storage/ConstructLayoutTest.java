package net.voxelindustry.voidheart.common.energy.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout.FramePart;
import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout.Size;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class ConstructLayoutTest
{
    @Test
    void frameStandsOnlyOnEdges()
    {
        assertThat(ConstructLayout.frame(Size.SMALL)).hasSize(20);
        assertThat(ConstructLayout.frame(Size.LARGE)).hasSize(44);

        for (var size : Size.values())
        {
            for (var offset : ConstructLayout.frame(size))
                assertThat(ConstructLayout.partAt(size, offset)).isNotNull();
        }
        assertThat(ConstructLayout.partAt(Size.SMALL, BlockPos.ZERO)).isNull();
        assertThat(ConstructLayout.partAt(Size.SMALL, new BlockPos(1, 0, 0))).isNull();
    }

    @Test
    void partsHugTheEdgeTheyStandOn()
    {
        assertThat(ConstructLayout.partAt(Size.SMALL, new BlockPos(-1, -1, -1))).isEqualTo(FramePart.CORNER_NNN);
        assertThat(ConstructLayout.partAt(Size.SMALL, new BlockPos(1, 1, -1))).isEqualTo(FramePart.CORNER_PPN);
        assertThat(ConstructLayout.partAt(Size.SMALL, new BlockPos(1, 0, 1))).isEqualTo(FramePart.PILLAR_PP);
        assertThat(ConstructLayout.partAt(Size.SMALL, new BlockPos(0, 1, 1))).isEqualTo(FramePart.BEAM_X_PP);
        assertThat(ConstructLayout.partAt(Size.SMALL, new BlockPos(-1, 1, 0))).isEqualTo(FramePart.BEAM_Z_NP);
        assertThat(ConstructLayout.partAt(Size.LARGE, new BlockPos(-2, 1, 2))).isEqualTo(FramePart.PILLAR_NP);
        assertThat(ConstructLayout.partAt(Size.LARGE, new BlockPos(1, -2, 2))).isEqualTo(FramePart.BEAM_X_NP);
        assertThat(ConstructLayout.partAt(Size.LARGE, new BlockPos(-1, -2, 2))).isEqualTo(FramePart.BEAM_X_NP);
    }

    @Test
    void everyFormedPartIsUsedOnce()
    {
        var parts = new HashSet<FramePart>();
        for (var offset : ConstructLayout.frame(Size.SMALL))
            assertThat(parts.add(ConstructLayout.partAt(Size.SMALL, offset))).isTrue();
        assertThat(parts).hasSize(FramePart.values().length - 1).doesNotContain(FramePart.LOOSE);
        assertThat(parts.stream().filter(FramePart::isCorner)).hasSize(8);
        assertThat(parts.stream().filter(FramePart::isPillar)).hasSize(4);
    }

    @Test
    void socketsAreOpenFaceCellsWithCentersFirst()
    {
        assertThat(ConstructLayout.sockets(Size.SMALL)).hasSize(6);
        assertThat(ConstructLayout.sockets(Size.LARGE)).hasSize(30).doesNotHaveDuplicates();
        assertThat(ConstructLayout.sockets(Size.LARGE).subList(0, 6)).containsExactlyElementsOf(
                ConstructLayout.sockets(Size.SMALL).stream().map(pos -> pos.multiply(2)).toList());

        var frame = new HashSet<>(ConstructLayout.frame(Size.LARGE));
        for (var socket : ConstructLayout.sockets(Size.LARGE))
        {
            assertThat(frame).doesNotContain(socket);
            assertThat(ConstructLayout.isInside(Size.LARGE, socket)).isTrue();
        }
        // Face corners stay open
        assertThat(ConstructLayout.socketIndex(Size.LARGE, new BlockPos(2, 1, 1))).isEqualTo(-1);
        assertThat(ConstructLayout.socketIndex(Size.LARGE, new BlockPos(2, 1, 0))).isNotNegative();
    }

    @Test
    void bundlesPointIntoTheCube()
    {
        assertThat(ConstructLayout.inward(Size.SMALL, new BlockPos(1, 0, 0))).isEqualTo(Direction.WEST);
        assertThat(ConstructLayout.inward(Size.SMALL, new BlockPos(0, -1, 0))).isEqualTo(Direction.UP);
        assertThat(ConstructLayout.inward(Size.LARGE, new BlockPos(1, 0, -2))).isEqualTo(Direction.SOUTH);
    }
}
