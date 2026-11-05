package net.voxelindustry.voidheart.common.content.mob;

import net.voxelindustry.voidheart.common.content.mob.DragoonClimb.Probe;
import net.voxelindustry.voidheart.common.content.mob.DragoonClimb.Step;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Climbs a dragoon through slices of the world taken along the way it faces, with forward to the right and up, so whole climbs can be checked without a game.
 */
class DragoonClimbTest
{
    private static final double WIDTH = 1;
    private static final double HEIGHT = 2.6;
    private static final int SUB_STEPS = 50;

    @Test
    void wantsToClimb_onlyAfterWhatItCantWalkTo()
    {
        assertThat(DragoonClimb.wantsToClimb(3, false)).isTrue();
        assertThat(DragoonClimb.wantsToClimb(3, true)).isFalse();
        assertThat(DragoonClimb.wantsToClimb(1.2, false)).isFalse();
    }

    @Test
    void keepsClimbing_untilItsTargetDropsBelowIt()
    {
        assertThat(DragoonClimb.keepsClimbing(2, 10)).isTrue();
        assertThat(DragoonClimb.keepsClimbing(-0.5, 10)).isTrue();
        assertThat(DragoonClimb.keepsClimbing(-1.5, 10)).isFalse();
        assertThat(DragoonClimb.keepsClimbing(2, DragoonClimb.MAX_TICKS)).isFalse();
    }

    @Test
    void canSwipe_upToItsShoulders()
    {
        assertThat(DragoonClimb.canSwipe(1.7)).isTrue();
        assertThat(DragoonClimb.canSwipe(2.5)).isFalse();
    }

    @Test
    void climb_overASmallPile()
    {
        var slice = ground().fill(1, 0, 1, 1);
        var body = new Body(0, 0);

        var steps = climb(slice, body);

        assertThat(steps.getLast()).isEqualTo(Step.DONE);
        assertThat(body.y).isCloseTo(2, within(0.01));
        assertThat(standsOn(body, 1)).isTrue();
    }

    @Test
    void climb_upAPillar()
    {
        var slice = ground().fill(1, 0, 1, 4);
        var body = new Body(0, 0);

        var steps = climb(slice, body);

        assertThat(steps).doesNotContain(Step.LIP_OUT);
        assertThat(steps.getLast()).isEqualTo(Step.DONE);
        assertThat(body.y).isCloseTo(5, within(0.01));
        assertThat(standsOn(body, 1)).isTrue();
    }

    @Test
    void climb_outFromUnderALip()
    {
        // A block jutting toward it at the top of the pillar, right above the face it climbs
        var slice = ground().fill(1, 0, 1, 4).fill(0, 4, 0, 4);
        var body = new Body(0, 0);

        var steps = climb(slice, body);

        assertThat(steps).contains(Step.LIP_OUT);
        assertThat(steps.getLast()).isEqualTo(Step.DONE);
        assertThat(body.y).isCloseTo(5, within(0.01));
        assertThat(standsOn(body, 0)).isTrue();
    }

    @Test
    void climb_outFromUnderALipJustOverItsHead()
    {
        // Close enough to the ground that edging out leaves its feet near it, which mustn't end the climb
        var slice = ground().fill(1, 0, 1, 3).fill(0, 3, 0, 3);
        var body = new Body(0, 0);

        var steps = climb(slice, body);

        assertThat(steps.getLast()).isEqualTo(Step.DONE);
        assertThat(body.y).isCloseTo(4, within(0.01));
    }

    @Test
    void climb_givesUpUnderAWideOverhang()
    {
        var slice = ground().fill(1, 0, 1, 4).fill(-3, 4, 0, 4);
        var body = new Body(0, 0);

        assertThat(climb(slice, body).getLast()).isEqualTo(Step.STUCK);
    }

    @Test
    void climb_givesUpInAShaftWithACeiling()
    {
        var slice = ground().fill(1, 0, 1, 6).fill(-1, 0, -1, 6).fill(-1, 4, 1, 4);
        var body = new Body(0, 0);

        assertThat(climb(slice, body).getLast()).isEqualTo(Step.STUCK);
    }

    @Test
    void next_letsGoWithNothingToHold()
    {
        var probe = new Probe(false, false, false, true, true);

        assertThat(DragoonClimb.next(probe, Step.RISE, 0)).isEqualTo(Step.LET_GO);
    }

    // ---------------------------------------------------------------------------------------------

    private static final class Slice
    {
        private final Set<Long> solid = new HashSet<>();

        Slice fill(int fromForward, int fromY, int toForward, int toY)
        {
            for (int f = fromForward; f <= toForward; f++)
                for (int y = fromY; y <= toY; y++)
                    solid.add(key(f, y));
            return this;
        }

        boolean blocked(double minForward, double minY, double maxForward, double maxY)
        {
            for (int f = (int) Math.floor(minForward); f < maxForward; f++)
                for (int y = (int) Math.floor(minY); y < maxY; y++)
                    if (solid.contains(key(f, y)) && f + 1 > minForward && y + 1 > minY)
                        return true;
            return false;
        }

        private static long key(int f, int y)
        {
            return ((long) f << 32) ^ (y & 0xFFFFFFFFL);
        }
    }

    /**
     * Its back edge and its feet.
     */
    private static final class Body
    {
        double forward;
        double y;

        Body(double forward, double y)
        {
            this.forward = forward;
            this.y = y;
        }
    }

    private static Slice ground()
    {
        return new Slice().fill(-10, -1, 10, -1);
    }

    private static boolean blocked(Slice slice, double forward, double y)
    {
        return slice.blocked(forward, y, forward + WIDTH, y + HEIGHT);
    }

    private static Probe probe(Slice slice, Body body)
    {
        return new Probe(
                blocked(slice, body.forward + DragoonClimb.PROBE, body.y),
                blocked(slice, body.forward + DragoonClimb.PROBE, body.y - DragoonClimb.LEDGE_PROBE),
                slice.blocked(body.forward + DragoonClimb.FOOTING, body.y - DragoonClimb.LEDGE_PROBE, body.forward + WIDTH - DragoonClimb.FOOTING, body.y + HEIGHT - DragoonClimb.LEDGE_PROBE),
                !blocked(slice, body.forward, body.y + DragoonClimb.SPEED),
                !blocked(slice, body.forward - DragoonClimb.SPEED, body.y));
    }

    /**
     * Moves up or down first, then along, stopping each short of what it would run into, as entities do.
     */
    private static void move(Slice slice, Body body, double forward, double up)
    {
        for (int i = 0; i < SUB_STEPS && !blocked(slice, body.forward, body.y + up / SUB_STEPS); i++)
            body.y += up / SUB_STEPS;
        for (int i = 0; i < SUB_STEPS && !blocked(slice, body.forward + forward / SUB_STEPS, body.y); i++)
            body.forward += forward / SUB_STEPS;
    }

    /**
     * Climbs until it is done or drops, then lets it fall to whatever is under it.
     */
    private static List<Step> climb(Slice slice, Body body)
    {
        var steps = new ArrayList<Step>();
        var last = Step.RISE;
        int lipTicks = 0;
        for (int tick = 0; tick < DragoonClimb.MAX_TICKS; tick++)
        {
            var step = DragoonClimb.next(probe(slice, body), last, lipTicks);
            steps.add(step);
            if (step == Step.DONE || step == Step.LET_GO || step == Step.STUCK)
                break;
            lipTicks = step == Step.LIP_OUT ? lipTicks + 1 : 0;
            var motion = DragoonClimb.motion(step);
            move(slice, body, motion[0], motion[1]);
            last = step;
        }
        for (int i = 0; i < 200; i++)
            move(slice, body, 0, -0.1);
        return steps;
    }

    private static boolean standsOn(Body body, int column)
    {
        return body.forward + WIDTH - DragoonClimb.FOOTING > column && body.forward + DragoonClimb.FOOTING < column + 1;
    }
}
