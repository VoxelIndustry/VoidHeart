package net.voxelindustry.voidheart.common.content.tormenter;

import net.voxelindustry.voidheart.common.content.tormenter.TormenterDropletShape.Cell;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TormenterDropletShapeTest
{
    @Test
    void droplet_keepsItsTipOutOfTheNeighboringBlocks()
    {
        assertThat(TormenterDropletShape.CELLS).noneMatch(cell -> cell.y() < 1);
    }

    @Test
    void droplet_runsUnbrokenToItsTop()
    {
        for (var y = 1; y < TormenterDropletShape.HEIGHT; y++)
            assertThat(TormenterDropletShape.CELLS).contains(new Cell(0, y, 0));
    }

    @Test
    void droplet_staysWithinThreeBlocksAcross()
    {
        assertThat(TormenterDropletShape.CELLS).allMatch(cell -> Math.abs(cell.x()) <= 1 && Math.abs(cell.z()) <= 1);
        assertThat(TormenterDropletShape.CELLS).contains(new Cell(1, 2, 0), new Cell(-1, 2, 0), new Cell(0, 2, 1), new Cell(0, 2, -1));
    }

    @Test
    void droplet_isSymmetricAroundItsAxis()
    {
        for (var cell : TormenterDropletShape.CELLS)
        {
            assertThat(TormenterDropletShape.CELLS).contains(new Cell(-cell.x(), cell.y(), cell.z()));
            assertThat(TormenterDropletShape.CELLS).contains(new Cell(cell.z(), cell.y(), cell.x()));
        }
    }

    @Test
    void boxes_stayInsideTheirBlock()
    {
        for (var cell : TormenterDropletShape.CELLS)
        {
            var boxes = TormenterDropletShape.boxes(cell.x(), cell.y(), cell.z());
            assertThat(boxes).isNotEmpty();
            assertThat(boxes).allMatch(box -> box.minX() >= 0 && box.minZ() >= 0 && box.maxX() <= 1 && box.maxZ() <= 1 && box.minX() < box.maxX() && box.minZ() < box.maxZ());
        }
    }

    @Test
    void boxes_followTheWidthOfTheDroplet()
    {
        var middle = TormenterDropletShape.boxes(0, 2, 0);
        assertThat(middle).anyMatch(box -> box.minX() == 0 && box.maxX() == 1);

        // The belly only spills a little into the block beside it, starting from the side it touches
        var beside = TormenterDropletShape.boxes(1, 2, 0);
        assertThat(beside).allMatch(box -> box.minX() == 0 && box.maxX() < 1);
    }
}
