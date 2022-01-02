package net.voxelindustry.voidheart.common.content.portalframe;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortalFormerStateTest
{
    @Test
    void areShapeIncompatible_givenSameSizeSameFacing_thenShouldReturnFalse()
    {
        var firstState = new PortalFormerState(new BlockPos(0, 0, 0), new BlockPos(4, 4, 0), Direction.NORTH);
        var secondState = new PortalFormerState(new BlockPos(0, 0, 4), new BlockPos(4, 4, 4), Direction.NORTH);

        assertThat(firstState.areShapeIncompatible(secondState))
                .isEqualTo(secondState.areShapeIncompatible(firstState))
                .isFalse();
    }

    @Test
    void areShapeIncompatible_givenSameSizePerpendicularFacing_thenShouldReturnFalse()
    {
        var firstState = new PortalFormerState(new BlockPos(0, 0, 0), new BlockPos(4, 4, 0), Direction.NORTH);
        var secondState = new PortalFormerState(new BlockPos(4, 0, 0), new BlockPos(4, 4, 4), Direction.EAST);

        assertThat(firstState.areShapeIncompatible(secondState))
                .isEqualTo(secondState.areShapeIncompatible(firstState))
                .isFalse();
    }

    @Test
    void areShapeIncompatible_givenSameSizeOppositeFacing_thenShouldReturnFalse()
    {
        var firstState = new PortalFormerState(new BlockPos(0, 0, 0), new BlockPos(4, 4, 0), Direction.NORTH);
        var secondState = new PortalFormerState(new BlockPos(0, 0, 4), new BlockPos(4, 4, 4), Direction.SOUTH);

        assertThat(firstState.areShapeIncompatible(secondState))
                .isEqualTo(secondState.areShapeIncompatible(firstState))
                .isFalse();
    }

    @Test
    void areShapeIncompatible_givenSameSizeOppositeVerticalFacing_thenShouldReturnFalse()
    {
        var firstState = new PortalFormerState(new BlockPos(0, 0, 0), new BlockPos(4, 0, 4), Direction.UP);
        var secondState = new PortalFormerState(new BlockPos(0, 4, 0), new BlockPos(4, 4, 4), Direction.DOWN);

        assertThat(firstState.areShapeIncompatible(secondState))
                .isEqualTo(secondState.areShapeIncompatible(firstState))
                .isFalse();
    }

    @Test
    void areShapeIncompatible_givenSameSizeSameVerticalFacing_thenShouldReturnTrue()
    {
        var firstState = new PortalFormerState(new BlockPos(0, 0, 0), new BlockPos(4, 0, 4), Direction.UP);
        var secondState = new PortalFormerState(new BlockPos(0, 4, 0), new BlockPos(4, 4, 4), Direction.UP);

        assertThat(firstState.areShapeIncompatible(secondState))
                .isEqualTo(secondState.areShapeIncompatible(firstState))
                .isTrue();
    }

    @Test
    void areShapeIncompatible_givenSameSizeOneVerticalAndOneHorizontalFacing_thenShouldReturnTrue()
    {
        var firstState = new PortalFormerState(new BlockPos(0, 0, 0), new BlockPos(4, 4, 0), Direction.NORTH);
        var secondState = new PortalFormerState(new BlockPos(0, 4, 0), new BlockPos(4, 4, 4), Direction.UP);

        assertThat(firstState.areShapeIncompatible(secondState))
                .isEqualTo(secondState.areShapeIncompatible(firstState))
                .isTrue();
    }

    @Test
    void areShapeIncompatible_givenDifferentWidthSameFacing_thenShouldReturnTrue()
    {
        var firstState = new PortalFormerState(new BlockPos(0, 0, 0), new BlockPos(4, 4, 0), Direction.NORTH);
        var secondState = new PortalFormerState(new BlockPos(0, 0, 4), new BlockPos(5, 4, 4), Direction.NORTH);

        assertThat(firstState.areShapeIncompatible(secondState))
                .isEqualTo(secondState.areShapeIncompatible(firstState))
                .isTrue();
    }

    @Test
    void areShapeIncompatible_givenDifferentHeightSameFacing_thenShouldReturnTrue()
    {
        var firstState = new PortalFormerState(new BlockPos(0, 0, 0), new BlockPos(4, 4, 0), Direction.NORTH);
        var secondState = new PortalFormerState(new BlockPos(0, 0, 4), new BlockPos(4, 5, 4), Direction.NORTH);

        assertThat(firstState.areShapeIncompatible(secondState))
                .isEqualTo(secondState.areShapeIncompatible(firstState))
                .isTrue();
    }

    @Test
    void areShapeIncompatible_givenInvertedSizeVerticalFacing_thenShouldReturnFalse()
    {
        var firstState = new PortalFormerState(new BlockPos(0, 0, 0), new BlockPos(5, 0, 4), Direction.UP);
        var secondState = new PortalFormerState(new BlockPos(0, 4, 0), new BlockPos(4, 4, 5), Direction.DOWN);

        assertThat(firstState.areShapeIncompatible(secondState))
                .isEqualTo(secondState.areShapeIncompatible(firstState))
                .isFalse();
    }

    @Test
    void areShapeIncompatible_givenDifferentWidthVerticalFacing_thenShouldReturnTrue()
    {
        var firstState = new PortalFormerState(new BlockPos(0, 0, 0), new BlockPos(5, 0, 4), Direction.UP);
        var secondState = new PortalFormerState(new BlockPos(0, 4, 0), new BlockPos(4, 4, 4), Direction.DOWN);

        assertThat(firstState.areShapeIncompatible(secondState))
                .isEqualTo(secondState.areShapeIncompatible(firstState))
                .isTrue();
    }

    @Test
    void areShapeIncompatible_givenDifferentLengthVerticalFacing_thenShouldReturnTrue()
    {
        var firstState = new PortalFormerState(new BlockPos(0, 0, 0), new BlockPos(4, 0, 4), Direction.UP);
        var secondState = new PortalFormerState(new BlockPos(0, 4, 0), new BlockPos(5, 4, 4), Direction.DOWN);

        assertThat(firstState.areShapeIncompatible(secondState))
                .isEqualTo(secondState.areShapeIncompatible(firstState))
                .isTrue();
    }
}