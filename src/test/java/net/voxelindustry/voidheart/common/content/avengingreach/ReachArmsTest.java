package net.voxelindustry.voidheart.common.content.avengingreach;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ReachArmsTest
{
    @Test
    void nextReady_takesTheFirstRechargedArmAmongThoseOwned()
    {
        var readyAt = new long[ReachArms.MAX_ARMS];
        assertThat(ReachArms.nextReady(readyAt, 0, 100)).isEqualTo(-1);
        assertThat(ReachArms.nextReady(readyAt, 1, 100)).isZero();

        readyAt[0] = 160;
        assertThat(ReachArms.nextReady(readyAt, 1, 100)).isEqualTo(-1);
        assertThat(ReachArms.nextReady(readyAt, 2, 100)).isEqualTo(1);
        assertThat(ReachArms.nextReady(readyAt, 1, 160)).isZero();

        readyAt[1] = 170;
        readyAt[2] = 180;
        readyAt[3] = 190;
        assertThat(ReachArms.nextReady(readyAt, 4, 100)).isEqualTo(-1);
        assertThat(ReachArms.nextReady(readyAt, 4, 175)).isZero();
    }

    @Test
    void nextReady_prefersAnArmOnTheAggressorsSide()
    {
        var readyAt = new long[ReachArms.MAX_ARMS];
        int left = -ReachArms.side(0);
        assertThat(ReachArms.nextReady(readyAt, 4, 0, left)).isEqualTo(1);
        assertThat(ReachArms.nextReady(readyAt, 4, 0, -left)).isZero();
        // With only one arm, it answers whatever side the blow came from
        assertThat(ReachArms.nextReady(readyAt, 1, 0, left)).isZero();
        readyAt[1] = 100;
        readyAt[3] = 100;
        assertThat(ReachArms.nextReady(readyAt, 4, 0, left)).isZero();
    }

    @Test
    void sideOf_tellsRightFromLeftAsTheBodyTurns()
    {
        // Facing south (+z), the right hand is to the west (-x)
        assertThat(ReachArms.sideOf(0, -1, 0)).isEqualTo(1);
        assertThat(ReachArms.sideOf(0, 1, 0)).isEqualTo(-1);
        // Facing west (-x), the right hand is to the north (-z)
        assertThat(ReachArms.sideOf(90, 0, -1)).isEqualTo(1);
        assertThat(ReachArms.sideOf(90, 0, 1)).isEqualTo(-1);
        assertThat(ReachArms.side(0)).isEqualTo(1);
    }

    @Test
    void canTake_onlyTheNextArmInTurn()
    {
        assertThat(ReachArms.canTake(0, 1)).isTrue();
        assertThat(ReachArms.canTake(0, 2)).isFalse();
        assertThat(ReachArms.canTake(1, 1)).isFalse();
        assertThat(ReachArms.canTake(3, 4)).isTrue();
        assertThat(ReachArms.canTake(4, 4)).isFalse();
    }

    @Test
    void inReach_measuresToTheNearestSideOfTheAggressor()
    {
        var chest = new Vec3(0, 1.3, 0);
        // A zombie-sized box whose near side is 2.7 blocks away
        assertThat(ReachArms.inReach(chest, new AABB(2.7, 0, -0.3, 3.3, 1.95, 0.3))).isTrue();
        assertThat(ReachArms.inReach(chest, new AABB(3.1, 0, -0.3, 3.7, 1.95, 0.3))).isFalse();
        // Diagonally, both axes count
        assertThat(ReachArms.inReach(chest, new AABB(2.2, 0, 2.2, 2.8, 1.95, 2.8))).isFalse();
        // Far above, out of reach though right overhead
        assertThat(ReachArms.inReach(chest, new AABB(-0.3, 5, -0.3, 0.3, 6, 0.3))).isFalse();
        assertThat(ReachArms.nearestPoint(chest, new AABB(2, 0, -1, 3, 2, 1))).isEqualTo(new Vec3(2, 1.3, 0));
    }

    @Test
    void lash_reachesTheTargetExactlyWhenTheBlowLands()
    {
        assertThat(ReachArms.extent(0)).isZero();
        assertThat(ReachArms.extent(ReachArms.EMERGE_TICKS)).isLessThan(0.5F);
        assertThat(ReachArms.extent(ReachArms.IMPACT_TICK - 0.5F)).isLessThan(1);
        assertThat(ReachArms.extent(ReachArms.IMPACT_TICK)).isEqualTo(1);
        assertThat(ReachArms.extent(ReachArms.IMPACT_TICK + ReachArms.HOLD_TICKS)).isEqualTo(1);
        assertThat(ReachArms.extent(ReachArms.TOTAL_TICKS)).isZero();

        assertThat(ReachArms.straighten(ReachArms.EMERGE_TICKS)).isZero();
        assertThat(ReachArms.straighten(ReachArms.IMPACT_TICK)).isEqualTo(1);
        assertThat(ReachArms.clawOpen(ReachArms.EMERGE_TICKS)).isLessThan(0.5F);
        assertThat(ReachArms.clawOpen(ReachArms.IMPACT_TICK - 0.01F)).isGreaterThan(0.95F);
        assertThat(ReachArms.clawOpen(ReachArms.IMPACT_TICK + 2)).isZero();
    }

    @Test
    void lash_neverJumpsBetweenTicks()
    {
        float previous = 0;
        for (float ticks = 0; ticks <= ReachArms.TOTAL_TICKS; ticks += 0.1F)
        {
            float extent = ReachArms.extent(ticks);
            assertThat(extent).isBetween(0F, 1F);
            assertThat(extent).isCloseTo(previous, within(0.12F));
            previous = extent;
        }
    }

    @Test
    void anchors_allLeaveTheMiddleOfTheBackAndFanOutToBothSides()
    {
        var first = ReachArms.anchor(0, 1.8);
        assertThat(first.x).isZero();
        assertThat(first.z).isPositive();
        assertThat(ReachArms.anchor(0, 1.5).y).isLessThan(first.y);
        for (int arm = 1; arm < ReachArms.MAX_ARMS; arm++)
            assertThat(ReachArms.anchor(arm, 1.8)).isEqualTo(first);
        assertThat(ReachArms.side(0)).isEqualTo(-ReachArms.side(1));
        assertThat(ReachArms.side(2)).isEqualTo(-ReachArms.side(3));
        assertThat(ReachArms.coilRise(2)).isLessThan(ReachArms.coilRise(0));
    }
}
