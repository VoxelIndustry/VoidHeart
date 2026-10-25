package net.voxelindustry.voidheart.common.content.shatterforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.voxelindustry.voidheart.common.content.beam.BeamNodeKind;
import net.voxelindustry.voidheart.common.content.beam.BeamResolver;
import net.voxelindustry.voidheart.common.content.beam.BeamWorldView;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ShatterForgeLayoutTest
{
    private static final BlockPos FORGE = new BlockPos(0, 64, 0);

    private FakeView view;

    @BeforeEach
    void setUp()
    {
        view = new FakeView();
        view.kinds.put(FORGE, BeamNodeKind.FORGE);
    }

    private void build(Direction d, int pairs, boolean withPillar)
    {
        for (var block : ShatterForgeLayout.expected(FORGE, d, pairs))
        {
            if (block.part() != ShatterForgeLayout.Part.PILLAR || withPillar)
                view.kinds.put(block.pos(), block.part().kind());
        }
    }

    @Test
    void expected_givenBuiltLayout_thenResolverAcceptsIt()
    {
        build(Direction.SOUTH, 3, true);

        var resolution = BeamResolver.resolve(view, FORGE);

        assertThat(resolution.error()).isEmpty();
        assertThat(resolution.totalPairs()).isEqualTo(3);
        assertThat(resolution.itemSource()).contains(ShatterForgeLayout.endSlot(FORGE, Direction.SOUTH, 3));
    }

    @Test
    void inferDirection_givenNothingBuilt_thenFront()
    {
        assertThat(ShatterForgeLayout.inferDirection(view, FORGE, Direction.Axis.Z)).isEqualTo(Direction.NORTH);
    }

    @Test
    void inferDirection_givenPartialTrackBehind_thenBack()
    {
        view.kinds.put(FORGE.below().south(), BeamNodeKind.CONDUIT);
        view.kinds.put(FORGE.below().south(2), BeamNodeKind.CONDUIT);

        assertThat(ShatterForgeLayout.inferDirection(view, FORGE, Direction.Axis.Z)).isEqualTo(Direction.SOUTH);
    }

    @Test
    void completeSegments_givenTwoPairsOpen_thenTwoAndNotClosed()
    {
        build(Direction.NORTH, 2, false);

        assertThat(ShatterForgeLayout.completeSegments(view, FORGE, Direction.NORTH)).isEqualTo(2);
        assertThat(ShatterForgeLayout.isEmitter(view, ShatterForgeLayout.endSlot(FORGE, Direction.NORTH, 2))).isFalse();
    }

    @Test
    void isEmitter_givenCollectorAboveSlot_thenClosed()
    {
        build(Direction.NORTH, 1, false);
        view.kinds.put(ShatterForgeLayout.endSlot(FORGE, Direction.NORTH, 1).above(), BeamNodeKind.COLLECTOR);

        assertThat(ShatterForgeLayout.isEmitter(view, ShatterForgeLayout.endSlot(FORGE, Direction.NORTH, 1))).isTrue();
    }

    private static class FakeView implements BeamWorldView
    {
        final Map<BlockPos, BeamNodeKind> kinds = new HashMap<>();

        @Override
        public BeamNodeKind kindAt(BlockPos pos)
        {
            return kinds.getOrDefault(pos, BeamNodeKind.OTHER);
        }

        @Override
        public EssenceType collectorAspect(BlockPos pos)
        {
            return null;
        }

        @Override
        public Direction focusFacing(BlockPos pos)
        {
            return Direction.NORTH;
        }

        @Override
        public Direction.Axis forgeAxis(BlockPos pos)
        {
            return Direction.Axis.Z;
        }
    }
}
