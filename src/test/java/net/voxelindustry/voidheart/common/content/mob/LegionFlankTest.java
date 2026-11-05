package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LegionFlankTest
{
    private static final Vec3 TARGET = new Vec3(0, 64, 0);
    private static final Vec3 LOOKING_SOUTH = new Vec3(0, -0.3, 1);

    @Test
    void spot_putsThePairOnOppositeSides()
    {
        var right = LegionFlank.spot(TARGET, LOOKING_SOUTH, 1);
        var left = LegionFlank.spot(TARGET, LOOKING_SOUTH, -1);

        assertThat(right.subtract(TARGET).dot(left.subtract(TARGET))).isNegative();
    }

    @Test
    void spot_isBesideTheTargetNotInFrontOrBehind()
    {
        var spot = LegionFlank.spot(TARGET, LOOKING_SOUTH, 1);

        assertThat(spot.subtract(TARGET).dot(new Vec3(0, 0, 1))).isCloseTo(0, within(1e-9));
        assertThat(spot.distanceTo(TARGET)).isCloseTo(LegionFlank.WIDTH, within(1e-9));
    }

    @Test
    void spot_rightIsWestWhenFacingSouth()
    {
        // Facing south, along +z, the right hand side is west
        assertThat(LegionFlank.spot(TARGET, LOOKING_SOUTH, 1).x).isNegative();
    }

    @Test
    void spot_staysOnTheHeightOfTheTarget()
    {
        assertThat(LegionFlank.spot(TARGET, new Vec3(0.3, 0.9, 0.1), 1).y).isEqualTo(TARGET.y);
    }

    @Test
    void shouldEngage_onlyOnceAtItsFlank()
    {
        var spot = new Vec3(3, 64, 0);

        assertThat(LegionFlank.shouldEngage(TARGET, new Vec3(2.8, 64, 0.5), spot, false)).isTrue();
        assertThat(LegionFlank.shouldEngage(TARGET, new Vec3(-2, 64, 1), spot, false)).isFalse();
        // Close to the target but in front of it rather than beside it
        assertThat(LegionFlank.shouldEngage(TARGET, new Vec3(0.5, 64, 2.5), spot, false)).isFalse();
    }

    @Test
    void shouldEngage_staysEngagedWhenTheTargetTurns()
    {
        var spot = new Vec3(3, 64, 0);
        var wrongSide = new Vec3(-2, 64, 0);

        assertThat(LegionFlank.shouldEngage(TARGET, wrongSide, spot, true)).isTrue();
        assertThat(LegionFlank.shouldEngage(TARGET, new Vec3(-6, 64, 0), spot, true)).isFalse();
    }
}
