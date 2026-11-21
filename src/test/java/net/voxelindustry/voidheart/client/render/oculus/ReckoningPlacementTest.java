package net.voxelindustry.voidheart.client.render.oculus;

import net.voxelindustry.voidheart.common.content.works.WorksLayout;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class ReckoningPlacementTest
{
    /**
     * A layout of the given column sizes, the target alone in the last column, rows centred as {@link WorksLayout} centres them.
     */
    private static WorksLayout<Integer> layout(int... columnSizes)
    {
        var spots = new LinkedHashMap<Integer, WorksLayout.Spot>();
        int node = 1;
        int rows = 1;
        for (int column = 0; column < columnSizes.length; column++)
        {
            rows = Math.max(rows, columnSizes[column]);
            for (int row = 0; row < columnSizes[column]; row++)
            {
                int key = column == columnSizes.length - 1 ? 0 : node++;
                spots.put(key, new WorksLayout.Spot(column, row, column, row - (columnSizes[column] - 1) / 2F));
            }
        }
        return new WorksLayout<>(spots, columnSizes.length, rows);
    }

    @Test
    void of_givenASmallPlanOnAThreeByThreeTable_thenKeepsItInsideTheMarginAtFullSize()
    {
        var place = ReckoningPlacement.of(layout(2, 2, 1), 5, 3, 3);

        float limit = 1.5F - ReckoningPlacement.MARGIN;
        for (int node = 0; node < 5; node++)
        {
            assertThat(Math.abs(place.along()[node])).isLessThanOrEqualTo(limit);
            assertThat(Math.abs(place.across()[node])).isLessThanOrEqualTo(limit);
        }
        assertThat(place.item()).isEqualTo(ReckoningPlacement.MAX_ITEM);
        assertThat(place.along()[0]).isPositive();
        assertThat(place.along()[1]).isNegative();
    }

    @Test
    void of_givenALongChain_thenShrinksTheSpacingButNotTheItemsBelowTheirFloor()
    {
        var place = ReckoningPlacement.of(layout(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1), 12, 3, 3);

        assertThat(place.item()).isEqualTo(ReckoningPlacement.MIN_ITEM);
        assertThat(place.along()[0]).isEqualTo(1.5F - ReckoningPlacement.MARGIN, org.assertj.core.data.Offset.offset(1e-4F));
        assertThat(place.along()[1]).isEqualTo(-(1.5F - ReckoningPlacement.MARGIN), org.assertj.core.data.Offset.offset(1e-4F));
    }

    @Test
    void of_givenOneNode_thenStandsItInTheMiddle()
    {
        var place = ReckoningPlacement.of(layout(1), 1, 3, 3);

        assertThat(place.along()[0]).isZero();
        assertThat(place.across()[0]).isZero();
        assertThat(place.plate()).isGreaterThan(place.item());
    }
}
