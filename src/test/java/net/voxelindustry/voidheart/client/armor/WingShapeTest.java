package net.voxelindustry.voidheart.client.armor;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class WingShapeTest
{
    private static Vector3f[] points()
    {
        var points = new Vector3f[WingShape.NODES];
        for (int i = 0; i < points.length; i++)
            points[i] = new Vector3f();
        return points;
    }

    @Test
    void theBeatSweepsDownFastAndRecoversSlowly()
    {
        assertThat(WingShape.beat(0)).isCloseTo(-1, within(1.0E-4F));
        // Find where it is fully down: well before half the cycle
        float down = 0;
        for (float t = 0; t < 1; t += 0.01F)
            if (WingShape.beat(t) > WingShape.beat(down))
                down = t;
        assertThat(down).isBetween(0.25F, 0.4F);
        assertThat(WingShape.beat(0.999F)).isCloseTo(-1, within(0.01F));
        assertThat(WingShape.beat(1.3F)).isCloseTo(WingShape.beat(0.3F), within(1.0E-4F));
    }

    @Test
    void wingsAreMirroredAcrossTheSpine()
    {
        var left = points();
        var right = points();
        WingShape.rest(-1, 4, 7, 1, 0.3F, 0, left);
        WingShape.rest(1, 4, 7, 1, 0.3F, 0, right);
        for (int j = 0; j < WingShape.NODES; j++)
        {
            assertThat(left[j].x).isCloseTo(-right[j].x, within(1.0E-5F));
            assertThat(left[j].y).isCloseTo(right[j].y, within(1.0E-5F));
        }
    }

    @Test
    void theTopFilamentsRiseAndOpenFirst()
    {
        var top = points();
        var bottom = points();
        WingShape.rest(1, 0, 3, 1, 0, 0, top);
        WingShape.rest(1, WingShape.FILAMENTS - 1, 3, 1, 0, 0, bottom);
        // y is down in model space
        assertThat(top[WingShape.NODES - 1].y).isLessThan(top[0].y - 1);
        assertThat(bottom[WingShape.NODES - 1].x).isGreaterThan(bottom[0].x + 0.8F);
        assertThat(WingShape.growth(0, 0.4F)).isGreaterThan(WingShape.growth(WingShape.FILAMENTS - 1, 0.4F));
        assertThat(WingShape.growth(WingShape.FILAMENTS - 1, 1)).isEqualTo(1);
    }

    @Test
    void aClosedFilamentFoldsIntoItsRoot()
    {
        var closed = points();
        WingShape.rest(1, 5, 3, 0, 0, 0, closed);
        for (var point : closed)
            assertThat(point.distance(closed[0])).isZero();
    }

    @Test
    void theFrameMapsTheModelOntoTheWorldAndBack()
    {
        var frame = new WingFrame(37, 1.5F);
        var model = new Vector3f(0.3F, -0.4F, 0.27F);
        var world = frame.toWorld(model, new Vector3f());
        var back = frame.toModel(world, new Vector3f());
        assertThat(back.distance(model)).isLessThan(1.0E-5F);

        // Facing south, the back of the frame is north, and its neck stands 1.5 model units over the feet at the frame's scale
        var south = new WingFrame(0, 1.5F);
        var neck = south.toWorld(new Vector3f(0, 0, 0), new Vector3f());
        assertThat(neck.y).isCloseTo(1.501F * WingFrame.PLAYER_SCALE * 1.5F, within(1.0E-4F));
        var behind = south.toWorld(new Vector3f(0, 0, 1), new Vector3f()).sub(neck);
        assertThat(behind.z).isLessThan(-0.9F);
    }
}
