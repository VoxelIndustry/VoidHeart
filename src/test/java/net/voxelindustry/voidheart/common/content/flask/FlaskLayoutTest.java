package net.voxelindustry.voidheart.common.content.flask;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlaskLayoutTest
{
    /**
     * Half the side of the square a flask's base can sweep, in block pixels, when turned by the full tilt.
     */
    private static final double REACH = FlaskLayout.WIDEST_HALF * FlaskLayout.PLACED_SCALE
            * (Math.cos(Math.toRadians(FlaskLayout.MAX_TURN)) + Math.sin(Math.toRadians(FlaskLayout.MAX_TURN)));

    @Test
    void spots_stayOnTheBlock()
    {
        for (int count = 1; count <= FlaskLayout.MAX_FLASKS; count++)
        {
            var spots = FlaskLayout.spots(count);
            assertThat(spots.length).isEqualTo(count);
            for (var spot : spots)
            {
                assertThat(spot[0] - REACH).isGreaterThanOrEqualTo(0);
                assertThat(spot[0] + REACH).isLessThanOrEqualTo(16);
                assertThat(spot[1] - REACH).isGreaterThanOrEqualTo(0);
                assertThat(spot[1] + REACH).isLessThanOrEqualTo(16);
            }
        }
    }

    @Test
    void spots_keepNeighboursApart()
    {
        for (int count = 2; count <= FlaskLayout.MAX_FLASKS; count++)
        {
            var spots = FlaskLayout.spots(count);
            for (int i = 0; i < count; i++)
            {
                for (int j = i + 1; j < count; j++)
                {
                    double apart = Math.max(Math.abs(spots[i][0] - spots[j][0]), Math.abs(spots[i][1] - spots[j][1]));
                    assertThat(apart).as("flasks %d and %d of %d", i, j, count).isGreaterThanOrEqualTo(2 * REACH);
                }
            }
        }
    }

    @Test
    void spots_refuseCountsThatDoNotFit()
    {
        assertThatThrownBy(() -> FlaskLayout.spots(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FlaskLayout.spots(FlaskLayout.MAX_FLASKS + 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void turn_isSteadyAndWithinTilt()
    {
        for (long seed = -50; seed < 50; seed++)
        {
            for (int index = 0; index < FlaskLayout.MAX_FLASKS; index++)
            {
                float turn = FlaskLayout.turn(seed * 7919, index, 4);
                assertThat(turn).isEqualTo(FlaskLayout.turn(seed * 7919, index, 4));
                assertThat(Math.abs(turn)).isLessThanOrEqualTo(FlaskLayout.MAX_TURN);
                assertThat(Math.abs(FlaskLayout.turn(seed * 7919, index, 1))).isLessThanOrEqualTo(180);
            }
        }
    }

    @Test
    void turn_variesBetweenFlasks()
    {
        assertThat(FlaskLayout.turn(1234, 0, 4)).isNotEqualTo(FlaskLayout.turn(1234, 1, 4));
        assertThat(FlaskLayout.turn(1234, 0, 4)).isNotEqualTo(FlaskLayout.turn(1235, 0, 4));
    }
}
