package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EyeFlockTest
{
    private static final Vec3 HERE = new Vec3(10, 64, 10);

    @Test
    void pull_isNothingWithoutNeighbours()
    {
        assertThat(EyeFlock.pull(HERE, List.of())).isEqualTo(Vec3.ZERO);
    }

    @Test
    void pull_keepsOffAVeryCloseNeighbour()
    {
        var pull = EyeFlock.pull(HERE, List.of(new EyeFlock.Member(HERE.add(0.3, 0, 0), Vec3.ZERO)));
        assertThat(pull.x).isNegative();
    }

    @Test
    void pull_driftsTowardAFlockFarAway()
    {
        var flock = List.of(
                new EyeFlock.Member(HERE.add(6, 0, 1), Vec3.ZERO),
                new EyeFlock.Member(HERE.add(6, 0, -1), Vec3.ZERO));
        var pull = EyeFlock.pull(HERE, flock);
        assertThat(pull.x).isPositive();
        assertThat(pull.z).isCloseTo(0, within(1e-6));
    }

    @Test
    void pull_followsTheFlocksHeading()
    {
        var flock = List.of(
                new EyeFlock.Member(HERE.add(2, 0, 2), new Vec3(0, 0.3, 0)),
                new EyeFlock.Member(HERE.add(-2, 0, -2), new Vec3(0, 0.3, 0)));
        assertThat(EyeFlock.pull(HERE, flock).y).isPositive();
    }

    @Test
    void velocity_holdsTheGivenSpeedWhateverTheWeights()
    {
        var velocity = EyeFlock.velocity(new Vec3(1, 0, 0), new Vec3(0, 3, 0), new Vec3(0, 0, -2), 0.4);
        assertThat(velocity.length()).isCloseTo(0.4, within(1e-9));
    }

    @Test
    void velocity_isStillWithNothingToDo()
    {
        assertThat(EyeFlock.velocity(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0.4)).isEqualTo(Vec3.ZERO);
    }

    @Test
    void velocity_letsWallsOverruleAWeakWish()
    {
        var velocity = EyeFlock.velocity(new Vec3(1, 0, 0), Vec3.ZERO, new Vec3(-2.4, 0, 0), 0.4);
        assertThat(velocity.x).isNegative();
    }
}
