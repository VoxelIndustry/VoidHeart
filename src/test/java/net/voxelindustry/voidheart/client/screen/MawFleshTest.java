package net.voxelindustry.voidheart.client.screen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ravine behind the research map grows with each stage and always looks the same for the same player.
 */
class MawFleshTest
{
    @Test
    void everyStageIsLargerThanTheLast()
    {
        for (int stage = 1; stage < MawFlesh.STAGES; stage++)
        {
            var before = MawFlesh.shape(stage - 1, 7L);
            var after = MawFlesh.shape(stage, 7L);
            assertThat(after.radiusX()).as("stage %d across", stage).isGreaterThanOrEqualTo(before.radiusX());
            assertThat(after.radiusY()).as("stage %d down", stage).isGreaterThan(before.radiusY());
            assertThat(after.eyeRadius()).as("stage %d eye", stage).isGreaterThanOrEqualTo(before.eyeRadius());
            assertThat(after.opening()).as("stage %d gape", stage).isGreaterThanOrEqualTo(before.opening());
            assertThat(after.teeth().size()).as("stage %d teeth", stage).isGreaterThanOrEqualTo(before.teeth().size());
        }
    }

    @Test
    void theSameSeedGivesTheSameMouth()
    {
        for (int stage = 0; stage < MawFlesh.STAGES; stage++)
        {
            assertThat(MawFlesh.shape(stage, 99L)).as("stage %d", stage).isEqualTo(MawFlesh.shape(stage, 99L));
            assertThat(MawFlesh.shape(stage, 99L).cells()).isNotEqualTo(MawFlesh.shape(stage, 100L).cells());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 3, 4 })
    void theEyeStaysDownTheGullet(int stage)
    {
        var shape = MawFlesh.shape(stage, 7L);
        assertThat(shape.eyeRadius()).isPositive();
        assertThat(shape.eyeRadius() * 1.4F).isLessThanOrEqualTo(shape.opening());
    }

    @ParameterizedTest
    @ValueSource(ints = { 3, 4 })
    void tendrilsLeaveFromTheLips(int stage)
    {
        var shape = MawFlesh.shape(stage, 7L);
        assertThat(shape.roots()).isNotEmpty();
        for (var root : shape.roots())
        {
            assertThat(Math.abs(root.x())).isLessThanOrEqualTo(shape.radiusX());
            assertThat(Math.abs(root.y())).isLessThanOrEqualTo(shape.radiusY());
        }
    }

    @Test
    void aSealedMouthIsShutAndStitched()
    {
        var sealed = MawFlesh.sealed(7L);
        var open = MawFlesh.shape(MawFlesh.STAGES - 1, 7L);
        assertThat(sealed.opening()).isZero();
        assertThat(sealed.eyeRadius()).isZero();
        assertThat(sealed.roots()).isEmpty();
        assertThat(sealed.radiusX()).isEqualTo(open.radiusX());
        assertThat(sealed.cells()).anyMatch(cell -> cell.argb() == MawFlesh.THREAD);
        assertThat(sealed).isEqualTo(MawFlesh.sealed(7L));
    }

    @Test
    void anEyeShutHasNothingToDraw()
    {
        assertThat(MawFlesh.eye(6, 0, 0, 0, 1L)).isEmpty();
        assertThat(MawFlesh.eye(6, 0, 0, 1, 1L)).isNotEmpty();
    }
}
