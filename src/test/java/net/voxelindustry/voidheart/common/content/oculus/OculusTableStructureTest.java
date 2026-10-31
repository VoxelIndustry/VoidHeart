package net.voxelindustry.voidheart.common.content.oculus;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OculusTableStructureTest
{
    private static Set<BlockPos> rectangle(int width, int depth)
    {
        var blocks = new HashSet<BlockPos>();
        for (int x = 0; x < width; x++)
        {
            for (int z = 0; z < depth; z++)
                blocks.add(new BlockPos(x, 64, z));
        }
        return blocks;
    }

    @Test
    void lShapeKeepsOnlyItsCells()
    {
        var blocks = Set.of(new BlockPos(10, 64, 20), new BlockPos(11, 64, 20), new BlockPos(10, 64, 21));

        var footprint = OculusTableStructure.footprint(new BlockPos(11, 64, 20), blocks::contains).orElseThrow();

        assertThat(footprint.width()).isEqualTo(2);
        assertThat(footprint.depth()).isEqualTo(2);
        assertThat(footprint.size()).isEqualTo(3);
        assertThat(footprint.contains(10, 21)).isTrue();
        assertThat(footprint.contains(11, 21)).isFalse();
        assertThat(footprint.anchor()).isEqualTo(new BlockPos(10, 64, 20));
    }

    @Test
    void anchorIsTheSameFromEveryBlock()
    {
        var blocks = Set.of(new BlockPos(5, 64, 3), new BlockPos(6, 64, 3), new BlockPos(6, 64, 2), new BlockPos(5, 64, 4));

        var anchors = blocks.stream()
                .map(start -> OculusTableStructure.footprint(start, blocks::contains).orElseThrow().anchor())
                .distinct()
                .toList();

        assertThat(anchors).containsExactly(new BlockPos(5, 64, 3));
    }

    @Test
    void blocksAtAnotherHeightOrOnlyTouchingByCornerAreSeparateTables()
    {
        var blocks = Set.of(new BlockPos(0, 64, 0), new BlockPos(0, 65, 0), new BlockPos(1, 64, 1));

        assertThat(OculusTableStructure.collect(new BlockPos(0, 64, 0), blocks::contains)).containsExactly(new BlockPos(0, 64, 0));
    }

    @Test
    void largestAllowedTableProjects()
    {
        var blocks = rectangle(8, 8);

        assertThat(OculusTableStructure.footprint(BlockPos.ZERO.atY(64), blocks::contains)).isPresent();
    }

    @Test
    void tableOverTheBlockLimitDoesNotProject()
    {
        var blocks = rectangle(13, 5);

        assertThat(OculusTableStructure.collect(BlockPos.ZERO.atY(64), blocks::contains)).hasSize(OculusTableStructure.MAX_SIZE + 1);
        assertThat(OculusTableStructure.footprint(BlockPos.ZERO.atY(64), blocks::contains)).isEmpty();
    }

    @Test
    void tableWiderThanTheSpanLimitDoesNotProject()
    {
        var blocks = rectangle(OculusTableStructure.MAX_SPAN + 1, 1);

        assertThat(OculusTableStructure.footprint(BlockPos.ZERO.atY(64), blocks::contains)).isEmpty();
    }

    @Test
    void followCyclesThroughCandidatesThenNoOne()
    {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var candidates = List.of(first, second);

        assertThat(OculusControlPayload.nextTarget(candidates, Optional.empty())).contains(first);
        assertThat(OculusControlPayload.nextTarget(candidates, Optional.of(first))).contains(second);
        assertThat(OculusControlPayload.nextTarget(candidates, Optional.of(second))).isEmpty();
    }

    @Test
    void followRestartsWhenTheTargetLeftRange()
    {
        var first = UUID.randomUUID();

        assertThat(OculusControlPayload.nextTarget(List.of(first), Optional.of(UUID.randomUUID()))).contains(first);
        assertThat(OculusControlPayload.nextTarget(List.of(), Optional.of(first))).isEmpty();
    }

    @Test
    void zoomStaysWithinItsSteps()
    {
        assertThat(OculusView.DEFAULT.zoomOut().scale()).isEqualTo(32);
        var view = OculusView.DEFAULT;
        for (int step = 0; step < OculusView.ZOOM_STEPS + 2; step++)
            view = view.zoomIn();
        assertThat(view.scale()).isEqualTo(4);
        assertThat(OculusView.DEFAULT.zoomIn().scale()).as("intermediate step").isEqualTo(24);
    }
}
