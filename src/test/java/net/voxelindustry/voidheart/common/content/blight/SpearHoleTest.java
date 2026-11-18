package net.voxelindustry.voidheart.common.content.blight;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SpearHoleTest
{
    @Test
    void openness_opensFromAPointHoldsThenCloses()
    {
        assertThat(SpearHole.openness(0)).isZero();
        assertThat(SpearHole.openness(BlightBalance.HOLE_OPEN_TICKS / 2F)).isStrictlyBetween(0.5F, 1F);
        assertThat(SpearHole.openness(BlightBalance.HOLE_OPEN_TICKS)).isEqualTo(1);
        assertThat(SpearHole.openness(BlightBalance.HOLE_TICKS / 2F)).isEqualTo(1);
        assertThat(SpearHole.openness(BlightBalance.HOLE_TICKS - BlightBalance.HOLE_CLOSE_TICKS / 2F)).isStrictlyBetween(0F, 0.5F);
        assertThat(SpearHole.openness(BlightBalance.HOLE_TICKS)).isZero();
        assertThat(SpearHole.isClosed(BlightBalance.HOLE_TICKS - 1)).isFalse();
        assertThat(SpearHole.isClosed(BlightBalance.HOLE_TICKS)).isTrue();
    }

    @Test
    void crushesAt_crushesEveryHalfSecondWhileOpen()
    {
        var crushes = IntStream.range(0, BlightBalance.HOLE_TICKS + 20).filter(SpearHole::crushesAt).boxed().toList();

        assertThat(crushes).first().isEqualTo(BlightBalance.HOLE_OPEN_TICKS);
        assertThat(crushes).hasSize((BlightBalance.HOLE_TICKS - BlightBalance.HOLE_OPEN_TICKS - 1) / BlightBalance.HOLE_CRUSH_EVERY + 1);
        for (int i = 1; i < crushes.size(); i++)
            assertThat(crushes.get(i) - crushes.get(i - 1)).isEqualTo(BlightBalance.HOLE_CRUSH_EVERY);
    }

    @Test
    void scale_pinchesAfterACrushThenOpensBack()
    {
        int crush = BlightBalance.HOLE_OPEN_TICKS + BlightBalance.HOLE_CRUSH_EVERY;

        assertThat(SpearHole.scale(crush)).isCloseTo(1 - BlightBalance.HOLE_PINCH, within(1.0E-4F));
        assertThat(SpearHole.scale(crush + BlightBalance.HOLE_PINCH_TICKS)).isEqualTo(1);
    }

    @Test
    void pulled_drawsInHarderCloserAndNotPastItsReach()
    {
        var still = Vec3.ZERO;
        var far = SpearHole.pulled(new Vec3(BlightBalance.HOLE_PULL_RANGE - 0.5, 0, 0), still);
        var near = SpearHole.pulled(new Vec3(2, 0, 0), still);

        assertThat(far.x).isPositive();
        assertThat(near.x).isGreaterThan(far.x);
        assertThat(far.y).isZero();
        assertThat(SpearHole.pulled(new Vec3(BlightBalance.HOLE_PULL_RANGE + 1, 0, 0), new Vec3(0, 0, 0.3))).isEqualTo(new Vec3(0, 0, 0.3));
    }

    @Test
    void pulled_takesAwayMostSidewaysSpeed()
    {
        var pulled = SpearHole.pulled(new Vec3(4, 0, 0), new Vec3(0, 0, 1));

        assertThat(pulled.z).isCloseTo(BlightBalance.HOLE_DRAG, within(1.0E-9));
        assertThat(pulled.x).isPositive();
    }

    @Test
    void pulled_holdsWhatIsInsideNearTheMiddle()
    {
        var held = SpearHole.pulled(new Vec3(0.5, 0, 0), new Vec3(-1, 0, 0));
        var still = SpearHole.pulled(new Vec3(0.5, 0, 0), Vec3.ZERO);

        assertThat(Math.abs(held.x)).isLessThan(1);
        assertThat(still.x).isPositive();
        assertThat(SpearHole.isInside(BlightBalance.HOLE_RADIUS)).isTrue();
        assertThat(SpearHole.isInside(BlightBalance.HOLE_RADIUS + 0.01)).isFalse();
    }
}
