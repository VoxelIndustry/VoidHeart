package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class WraithStalkTest
{
    private static final Vec3 FEET = new Vec3(10, 64, -3);
    private static final Vec3 LOOKING_EAST = new Vec3(1, 0, 0);

    @Test
    void spotBehind_liesInTheHoldingRangeOppositeTheLook()
    {
        var spot = WraithStalk.spotBehind(FEET, LOOKING_EAST);

        assertThat(spot.distanceTo(FEET)).isBetween(WraithStalk.HOLD_NEAR, WraithStalk.HOLD_FAR);
        assertThat(spot.x).isLessThan(FEET.x);
        assertThat(spot.z).isEqualTo(FEET.z);
        assertThat(WraithStalk.isBehind(FEET, LOOKING_EAST, spot)).isTrue();
    }

    @Test
    void spotBehind_ignoresLookingUpAndDown()
    {
        var spot = WraithStalk.spotBehind(FEET, new Vec3(0.5, -0.8, 0));

        assertThat(spot.y).isEqualTo(FEET.y);
        assertThat(spot.distanceTo(FEET)).isCloseTo((WraithStalk.HOLD_NEAR + WraithStalk.HOLD_FAR) / 2, within(1e-9));
    }

    @Test
    void spotBehind_staysOnTheFeetWhenLookingStraightDown()
    {
        assertThat(WraithStalk.spotBehind(FEET, new Vec3(0, -1, 0))).isEqualTo(FEET);
    }

    @Test
    void isHolding_needsBothDistanceAndBeingBehind()
    {
        assertThat(WraithStalk.isHolding(5.5, true)).isTrue();
        assertThat(WraithStalk.isHolding(5.5, false)).isFalse();
        assertThat(WraithStalk.isHolding(4.9, true)).isFalse();
        assertThat(WraithStalk.isHolding(6.1, true)).isFalse();
    }

    @Test
    void contact_takesOneAndAHalfSecondsOfStaring()
    {
        int contact = 0;
        for (int tick = 0; tick < 29; tick++)
            contact = WraithStalk.nextContact(contact, true);
        assertThat(WraithStalk.rushes(contact)).isFalse();

        assertThat(WraithStalk.rushes(WraithStalk.nextContact(contact, true))).isTrue();
    }

    @Test
    void contact_drainsWhenLookingAwayAndStopsAtZero()
    {
        assertThat(WraithStalk.nextContact(10, false)).isEqualTo(9);
        assertThat(WraithStalk.nextContact(0, false)).isZero();
    }

    @Test
    void contact_neverGoesPastItsCap()
    {
        assertThat(WraithStalk.nextContact(WraithStalk.CONTACT_TICKS, true)).isEqualTo(WraithStalk.CONTACT_TICKS);
    }

    @Test
    void creeps_afterTwentySecondsUnnoticed()
    {
        assertThat(WraithStalk.creeps(399)).isFalse();
        assertThat(WraithStalk.creeps(400)).isTrue();
    }
}
