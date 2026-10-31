package net.voxelindustry.voidheart.client.render.oculus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.oculus.OculusControlPanel.Button;
import net.voxelindustry.voidheart.common.content.oculus.OculusFootprint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class OculusControlPanelTest
{
    private static OculusFootprint square(int size)
    {
        var blocks = new ArrayList<BlockPos>();
        for (int x = 0; x < size; x++)
        {
            for (int z = 0; z < size; z++)
                blocks.add(new BlockPos(x, 64, z));
        }
        return OculusFootprint.of(blocks);
    }

    @Test
    void standsOnTheEdgeFacingTheViewer()
    {
        var panel = OculusControlPanel.place(square(3), null, new Vec3(1.5, 66, 8));

        assertThat(panel.facing()).isEqualTo(Direction.SOUTH);
        assertThat(panel.cell()).isEqualTo(new BlockPos(1, 64, 2));
        assertThat(panel.center().z).isGreaterThan(3);
    }

    @Test
    void neverStandsBetweenJoinedBlocks()
    {
        var footprint = square(3);
        // Right above the middle block, every outer edge is equally far: any of them is fine, but never an inner one
        var panel = OculusControlPanel.place(footprint, null, new Vec3(1.5, 66, 1.5));

        var cell = panel.cell();
        assertThat(footprint.contains(cell.getX() + panel.facing().getStepX(), cell.getZ() + panel.facing().getStepZ())).isFalse();
    }

    @Test
    void staysPutUntilAnotherEdgeIsClearlyCloser()
    {
        var footprint = square(3);
        var south = OculusControlPanel.place(footprint, null, new Vec3(1.5, 66, 8));

        var slightlyEast = OculusControlPanel.place(footprint, south, new Vec3(2.1, 66, 8));
        assertThat(slightlyEast).isEqualTo(south);

        var farEast = OculusControlPanel.place(footprint, south, new Vec3(9, 66, 1.5));
        assertThat(farEast.facing()).isEqualTo(Direction.EAST);
    }

    @Test
    void buttonsReadLeftToRightFromTheViewer()
    {
        var panel = OculusControlPanel.place(square(3), null, new Vec3(1.5, 66, 8));
        var eye = new Vec3(1.5, panel.center().y, 6);

        // Looking north, the viewer's left is west
        var left = panel.hit(eye, panel.center().add(panel.right().scale(Button.ZOOM_OUT.offset())).subtract(eye).normalize(), 6);
        var right = panel.hit(eye, panel.center().add(panel.right().scale(Button.LEY_LINES.offset())).subtract(eye).normalize(), 6);

        assertThat(panel.right().x).isPositive();
        assertThat(left.button()).isEqualTo(Button.ZOOM_OUT);
        assertThat(right.button()).isEqualTo(Button.LEY_LINES);
    }

    @Test
    void missesFromBehindBetweenButtonsAndOutOfReach()
    {
        var panel = OculusControlPanel.place(square(3), null, new Vec3(1.5, 66, 8));
        var center = panel.center();

        var behind = new Vec3(center.x, center.y, center.z - 2);
        assertThat(panel.hit(behind, new Vec3(0, 0, 1), 6)).isNull();

        var gapPoint = center.add(panel.right().scale(Button.ZOOM_IN.offset() + OculusControlPanel.BUTTON / 2 + OculusControlPanel.GAP / 2));
        var front = new Vec3(gapPoint.x, gapPoint.y, gapPoint.z + 2);
        assertThat(panel.hit(front, new Vec3(0, 0, -1), 6)).isNull();

        var far = new Vec3(center.x, center.y, center.z + 10);
        assertThat(panel.hit(far, new Vec3(0, 0, -1), 6)).isNull();
    }
}
