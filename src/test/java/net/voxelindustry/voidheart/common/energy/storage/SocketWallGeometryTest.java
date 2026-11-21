package net.voxelindustry.voidheart.common.energy.storage;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SocketWallGeometryTest
{
    @Test
    void aNorthWallsTopLeftIsTowardEast()
    {
        // Seen from the north, looking south, east is on the viewer's left
        assertThat(SocketWallGeometry.slotAt(Direction.NORTH, new Vec3(0.8, 0.8, 0))).isEqualTo(0);
        assertThat(SocketWallGeometry.slotAt(Direction.NORTH, new Vec3(0.2, 0.8, 0))).isEqualTo(1);
        assertThat(SocketWallGeometry.slotAt(Direction.NORTH, new Vec3(0.8, 0.2, 0))).isEqualTo(2);
        assertThat(SocketWallGeometry.slotAt(Direction.NORTH, new Vec3(0.2, 0.2, 0))).isEqualTo(3);
    }

    @Test
    void eachSlotsCellIsFoundByAHitOnIt()
    {
        for (var facing : Direction.Plane.HORIZONTAL)
            for (var slot = 0; slot < SocketWallGeometry.SLOTS; slot++)
            {
                var center = SocketWallGeometry.cellCenter(facing, slot);
                assertThat(SocketWallGeometry.slotAt(facing, center)).as(facing + " slot " + slot).isEqualTo(slot);
            }
    }

    @Test
    void cellsStandInsideTheirNicheBehindTheFront()
    {
        for (var facing : Direction.Plane.HORIZONTAL)
            for (var slot = 0; slot < SocketWallGeometry.SLOTS; slot++)
            {
                var center = SocketWallGeometry.cellCenter(facing, slot).subtract(0.5, 0, 0.5);
                var out = center.x * facing.getStepX() + center.z * facing.getStepZ();
                assertThat(out).isCloseTo(0.5 - SocketWallGeometry.CELL_DEPTH / 16, within(1e-6));
                var across = Math.abs(center.x * facing.getStepZ()) + Math.abs(center.z * facing.getStepX());
                assertThat(across).isCloseTo(0.25, within(1e-6));
            }
    }

    @Test
    void aWallTurnedEastKeepsTopLeftOnTheViewersLeft()
    {
        // Facing east, the viewer stands east looking west, so their left is south (high z)
        var left = SocketWallGeometry.cellCenter(Direction.EAST, 0);
        var right = SocketWallGeometry.cellCenter(Direction.EAST, 1);
        assertThat(left.z).isGreaterThan(right.z);
        assertThat(left.y).isGreaterThan(SocketWallGeometry.cellCenter(Direction.EAST, 2).y);
    }
}
