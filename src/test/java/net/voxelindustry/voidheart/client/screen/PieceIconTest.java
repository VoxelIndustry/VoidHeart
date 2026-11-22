package net.voxelindustry.voidheart.client.screen;

import net.voxelindustry.voidheart.common.content.apparatus.ComponentShape;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PieceIconTest
{
    private static final int CELL = 20;
    private static final int ICON = 16;

    @Test
    void rectanglesCarryTheirItemInTheirMiddle()
    {
        assertThat(PieceIcon.offset(ComponentShape.of("##", "##"), CELL, ICON)).containsExactly(12, 12);
        assertThat(PieceIcon.offset(ComponentShape.of("##", "##", "##"), CELL, ICON)).containsExactly(12, 22);
        assertThat(PieceIcon.offset(ComponentShape.of("###", "###"), CELL, ICON)).containsExactly(22, 12);
        assertThat(PieceIcon.offset(ComponentShape.of("##"), CELL, ICON)).containsExactly(12, 2);
        assertThat(PieceIcon.offset(ComponentShape.of("#"), CELL, ICON)).containsExactly(2, 2);
    }

    @Test
    void shapesWithGapsKeepTheirItemOnACell()
    {
        // The middle of a U is its gap, so the item sits on the spine below it
        assertThat(PieceIcon.offset(ComponentShape.of("#.#", "###"), CELL, ICON)).containsExactly(22, 22);
        // An L's middle is its empty corner
        var lShape = PieceIcon.offset(ComponentShape.of("##", "#."), CELL, ICON);
        assertThat(lShape[0] % CELL).isEqualTo(2);
        assertThat(lShape[1] % CELL).isEqualTo(2);
        assertThat(lShape[0] / CELL == 1 && lShape[1] / CELL == 1).as("not on the empty cell").isFalse();
    }

    @Test
    void rotatedPiecesStayCentred()
    {
        var tall = ComponentShape.of("##", "##", "##");
        assertThat(PieceIcon.offset(tall.rotated(1), CELL, ICON)).containsExactly(22, 12);
    }
}
