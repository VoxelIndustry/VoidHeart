package net.voxelindustry.voidheart.common.energy.fissure;

import net.voxelindustry.voidheart.common.ley.TaintStage;
import net.voxelindustry.voidheart.common.ley.taint.LeyTaintMath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SeepMathTest
{
    @Test
    void outputAndRadiusFollowTheNumbersByWidth()
    {
        assertThat(new long[] { SeepMath.output(1), SeepMath.output(2), SeepMath.output(3), SeepMath.output(4), SeepMath.output(5) })
                .containsExactly(16, 32, 48, 64, 88);
        assertThat(new float[] { SeepMath.targetRadius(1), SeepMath.targetRadius(2), SeepMath.targetRadius(3), SeepMath.targetRadius(4), SeepMath.targetRadius(5) })
                .containsExactly(6, 10, 16, 24, 32);
        assertThat(SeepMath.output(0)).isEqualTo(16);
        assertThat(SeepMath.output(9)).isEqualTo(88);
    }

    @Test
    void aFractionalWoundSeepsBetweenTheWidthsAroundIt()
    {
        assertThat(SeepMath.targetRadius(3.5F)).isEqualTo(20);
        assertThat(SeepMath.targetRadius(0.2F)).isEqualTo(6);
        assertThat(SeepMath.targetRadius(7)).isEqualTo(32);
        assertThat(SeepMath.targetRadius(Float.NaN)).isEqualTo(6);
    }

    @Test
    void theSeepSpreadsABlockAMinuteUntilItsWidthsRadius()
    {
        assertThat(SeepMath.radiusAt(0, 1000, 1000, 6)).isEqualTo(0);
        assertThat(SeepMath.radiusAt(0, 1000, 1000 + 3 * 1200, 6)).isEqualTo(3, within(1e-4F));
        assertThat(SeepMath.radiusAt(0, 1000, 1000 + 60 * 1200, 6)).isEqualTo(6);
        // Widened at 4 blocks toward 10: it carries on from where it stood
        assertThat(SeepMath.radiusAt(4, 0, 2 * 1200, 10)).isEqualTo(6, within(1e-4F));
        // A clock running backward never shrinks it below where it stood
        assertThat(SeepMath.radiusAt(4, 5000, 0, 10)).isEqualTo(4);
        assertThat(SeepMath.radiusAt(12, 0, 0, 10)).isEqualTo(10);
    }

    @Test
    void strengthFallsLinearlyToTheEdge()
    {
        assertThat(SeepMath.strength(0, 16)).isEqualTo(1);
        assertThat(SeepMath.strength(4, 16)).isEqualTo(0.75F);
        assertThat(SeepMath.strength(16, 16)).isEqualTo(0);
        assertThat(SeepMath.strength(30, 16)).isEqualTo(0);
        assertThat(SeepMath.strength(0, 0)).isEqualTo(0);
    }

    @Test
    void overlappingSeepsAddUpWithoutPassingOne()
    {
        assertThat(SeepMath.combine(0.5F, 0.5F)).isEqualTo(0.75F);
        assertThat(SeepMath.combine(0, 0.3F)).isEqualTo(0.3F, within(1e-6F));
        assertThat(SeepMath.combine(1, 0.3F)).isEqualTo(1);
        assertThat(SeepMath.combine(0.9F, 0.9F)).isLessThanOrEqualTo(1);
    }

    @Test
    void corruptionRisesFasterAndWardsWeakenInsideTheSeep()
    {
        assertThat(SeepMath.corruptionMultiplier(0)).isEqualTo(1);
        assertThat(SeepMath.corruptionMultiplier(1)).isEqualTo(2.5F);
        assertThat(SeepMath.corruptionMultiplier(0.5F)).isEqualTo(1.75F);
        assertThat(SeepMath.wardFactor(0)).isEqualTo(1);
        assertThat(SeepMath.wardFactor(0.25F)).isEqualTo(0.75F);
        assertThat(SeepMath.wardFactor(1)).isEqualTo(0);
    }

    @Test
    void theSeepsOwnRiseComesToAPointAMinuteAtItsHeart()
    {
        float perMinute = SeepMath.risePerSecond(1) * 60 * SeepMath.corruptionMultiplier(1);
        assertThat(perMinute).isEqualTo(1, within(1e-5F));
        assertThat(SeepMath.risePerSecond(0)).isEqualTo(0);
        // Halfway out, it is under half as fast, since the boost falls too
        assertThat(SeepMath.risePerSecond(0.5F) * 60 * SeepMath.corruptionMultiplier(0.5F)).isLessThan(0.5F);
    }

    @Test
    void taintFallsLinearlyFromTheLineAndScalesWithWidth()
    {
        assertThat(SeepMath.taintPerMinute(1, 0)).isEqualTo(0.15F, within(1e-6F));
        assertThat(SeepMath.taintPerMinute(5, 0)).isEqualTo(0.75F, within(1e-6F));
        assertThat(SeepMath.taintPerMinute(5, 24)).isEqualTo(0.375F, within(1e-6F));
        assertThat(SeepMath.taintPerMinute(5, 48)).isEqualTo(0);
        assertThat(SeepMath.taintPerMinute(5, 100)).isEqualTo(0);
    }

    /**
     * N0: a width 5 fissure on a line drives its node toward Breached, and 24 blocks away it leaves it Tainted, against W3's healing.
     */
    @Test
    void aWideFissureOnALineBreachesItsNodeAndOneFartherOffOnlyTaintsIt()
    {
        assertThat(LeyTaintMath.stage(settle(SeepMath.taintPerMinute(5, 0)))).isEqualTo(TaintStage.BREACHED);
        assertThat(LeyTaintMath.stage(settle(SeepMath.taintPerMinute(5, 24)))).isEqualTo(TaintStage.TAINTED);
        assertThat(LeyTaintMath.stage(settle(SeepMath.taintPerMinute(1, 40)))).isEqualTo(TaintStage.CLEAN);
    }

    private static float settle(float gainPerMinute)
    {
        float taint = 0;
        for (int minute = 0; minute < 24 * 60; minute++)
            taint = LeyTaintMath.step(taint, gainPerMinute, 1);
        return taint;
    }

    @Test
    void creaturesComeFasterAndMoreAtWiderFissures()
    {
        assertThat(SeepMath.spawnIntervalTicks(1)).isEqualTo(1800);
        assertThat(SeepMath.spawnIntervalTicks(5)).isEqualTo(360);
        assertThat(SeepMath.maxAlive(1)).isEqualTo(2);
        assertThat(SeepMath.maxAlive(4)).isEqualTo(8);
        assertThat(SeepMath.maxAlive(5)).isEqualTo(10);
    }

    @Test
    void closingCostsContravoidByWidthAndAWoundCostsLikeWidthFour()
    {
        assertThat(SeepMath.closeCost(1)).isEqualTo(2000);
        assertThat(SeepMath.closeCost(5)).isEqualTo(10000);
        assertThat(SeepMath.closeCost(VoidFissureBalance.WOUND_SIZE)).isEqualTo(8000);
    }

    @Test
    void widerFissuresTakeLargerBundles()
    {
        assertThat(new int[] { SeepMath.widenBundle(1), SeepMath.widenBundle(2), SeepMath.widenBundle(3), SeepMath.widenBundle(4) }).containsExactly(1, 1, 2, 3);
        assertThat(SeepMath.widenBundle(5)).isZero();
        assertThat(SeepMath.widenBundle(0)).isZero();
    }

    @Test
    void closingAndTearingRunOverTheirTicks()
    {
        assertThat(SeepMath.closing(-1, 500)).isZero();
        assertThat(SeepMath.closing(100, 120)).isEqualTo(0.5F);
        assertThat(SeepMath.closing(100, 500)).isEqualTo(1);
        assertThat(SeepMath.tearing(100, 110)).isEqualTo(0.5F);
        assertThat(SeepMath.tearing(Long.MIN_VALUE, 0)).isEqualTo(1);
    }
}
