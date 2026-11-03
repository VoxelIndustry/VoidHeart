package net.voxelindustry.voidheart.client.scene;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SceneViewTest
{
    private static final SceneView LEVEL = new SceneView(0, 0, 10, 2, 3, 4);

    @Test
    void project_putsWhatItLooksAtInTheMiddle()
    {
        var at = LEVEL.project(2, 3, 4, 300, 200);

        assertThat(at[0]).isCloseTo(150, within(0.01F));
        assertThat(at[1]).isCloseTo(100, within(0.01F));
    }

    @Test
    void project_keepsUpUp()
    {
        assertThat(LEVEL.project(2, 4, 4, 300, 200)[1]).isLessThan(100);
    }

    @Test
    void project_scalesToTheShorterSide()
    {
        // 10 blocks across the 200 of the shorter side
        assertThat(LEVEL.project(2, 4, 4, 300, 200)[1]).isCloseTo(80, within(0.01F));
    }

    /**
     * The stage's depth runs negative towards the viewer, so looking down what is higher has to come out nearer, or the floor would hide all that stands on it.
     */
    @Test
    void matrix_bringsWhatIsHigherNearerWhenLookingDown()
    {
        var above = new SceneView(0, 30, 10, 0, 0, 0);

        assertThat(above.matrix().transformPosition(new org.joml.Vector3f(0, 1, 0)).z).isLessThan(0);
    }

    @Test
    void lerp_reachesBothEnds()
    {
        var far = new SceneView(90, 30, 20, 0, 0, 0);

        assertThat(LEVEL.lerp(far, 0)).isEqualTo(LEVEL);
        assertThat(LEVEL.lerp(far, 1)).isEqualTo(far);
    }
}
