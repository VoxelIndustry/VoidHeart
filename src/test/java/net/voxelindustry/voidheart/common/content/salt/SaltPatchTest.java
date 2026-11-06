package net.voxelindustry.voidheart.common.content.salt;

import net.voxelindustry.voidheart.common.content.salt.SaltPatch.Cell;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaltPatchTest
{
    @Test
    void freshIsTheThreeByThreeMiddleFirst()
    {
        var cells = SaltPatch.fresh(new Cell(4, -2));
        assertEquals(9, cells.size());
        assertEquals(new Cell(4, -2), cells.getFirst());
        assertEquals(9, new HashSet<>(cells).size());
        for (var cell : cells)
            assertTrue(Math.abs(cell.x() - 4) <= 1 && Math.abs(cell.z() + 2) <= 1);
    }

    @Test
    void ringAroundThreeByThreeIsSixteen()
    {
        var ring = SaltPatch.ring(SaltPatch.fresh(new Cell(0, 0)), new Cell(0, 0));
        assertEquals(16, ring.size());
        for (var cell : ring)
            assertEquals(2, Math.max(Math.abs(cell.x()), Math.abs(cell.z())));
    }

    @Test
    void ringStartsNearestTheClick()
    {
        var ring = SaltPatch.ring(SaltPatch.fresh(new Cell(0, 0)), new Cell(1, 0));
        assertEquals(new Cell(2, 0), ring.getFirst());
        assertTrue(ring.indexOf(new Cell(-2, 0)) > ring.indexOf(new Cell(2, 1)));
    }

    @Test
    void ringFollowsAnIrregularPatch()
    {
        var patch = List.of(new Cell(0, 0), new Cell(1, 0), new Cell(2, 0));
        var ring = SaltPatch.ring(patch, new Cell(0, 0));
        assertEquals(12, ring.size());
        assertFalse(ring.contains(new Cell(1, 0)));
    }

    @Test
    void neverGrowsPastTheCap()
    {
        Set<Cell> patch = new HashSet<>();
        for (int x = 0; x < SaltPatch.MAX_SIZE - 1; x++)
        {
            for (int z = 0; z < 3; z++)
                patch.add(new Cell(x, z));
        }
        // One column short of the cap grows only on the side clicked
        var ring = SaltPatch.ring(patch, new Cell(0, 1));
        assertTrue(ring.stream().anyMatch(cell -> cell.x() == -1));
        assertTrue(ring.stream().noneMatch(cell -> cell.x() == SaltPatch.MAX_SIZE - 1));

        for (int z = 0; z < 3; z++)
            patch.add(new Cell(-1, z));
        var full = SaltPatch.ring(patch, new Cell(0, 1));
        assertTrue(full.stream().allMatch(cell -> cell.x() >= -1 && cell.x() <= SaltPatch.MAX_SIZE - 2));
        assertFalse(full.isEmpty(), "It can still widen the other way");
    }

    @Test
    void costIsOneSaltPerNineCells()
    {
        assertEquals(0, SaltPatch.cost(0));
        assertEquals(1, SaltPatch.cost(1));
        assertEquals(1, SaltPatch.cost(9));
        assertEquals(2, SaltPatch.cost(10));
        assertEquals(2, SaltPatch.cost(16));
    }
}
