package net.voxelindustry.voidheart.common.content.mob;

import net.voxelindustry.voidheart.common.content.mob.DragoonAdaptation.Kind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DragoonAdaptationTest
{
    @Test
    void multiplier_dropsEightPercentPerHit()
    {
        int packed = 0;
        assertThat(DragoonAdaptation.multiplier(packed, Kind.BLADE)).isEqualTo(1);

        packed = DragoonAdaptation.adapt(packed, Kind.BLADE);
        assertThat(DragoonAdaptation.multiplier(packed, Kind.BLADE)).isCloseTo(0.92F, within(1e-6F));

        for (int i = 0; i < 4; i++)
            packed = DragoonAdaptation.adapt(packed, Kind.BLADE);
        assertThat(DragoonAdaptation.multiplier(packed, Kind.BLADE)).isCloseTo(0.6F, within(1e-6F));
    }

    @Test
    void multiplier_stopsAtEightyPercent()
    {
        int packed = 0;
        for (int i = 0; i < 25; i++)
            packed = DragoonAdaptation.adapt(packed, Kind.FIRE);

        assertThat(DragoonAdaptation.count(packed, Kind.FIRE)).isEqualTo(DragoonAdaptation.MAX_HITS);
        assertThat(DragoonAdaptation.multiplier(packed, Kind.FIRE)).isCloseTo(0.2F, within(1e-6F));
    }

    @Test
    void adapt_leavesTheOtherKindsAlone()
    {
        int packed = 0;
        for (int i = 0; i < 10; i++)
            packed = DragoonAdaptation.adapt(packed, Kind.MAGIC);
        packed = DragoonAdaptation.adapt(packed, Kind.BLADE);

        for (var kind : Kind.values())
        {
            int expected = kind == Kind.MAGIC ? 10 : kind == Kind.BLADE ? 1 : 0;
            assertThat(DragoonAdaptation.count(packed, kind)).as(kind.name()).isEqualTo(expected);
        }
    }

    @Test
    void packedCounts_fitEveryKindAtItsCap()
    {
        int packed = 0;
        for (var kind : Kind.values())
            for (int i = 0; i < DragoonAdaptation.MAX_HITS; i++)
                packed = DragoonAdaptation.adapt(packed, kind);

        for (var kind : Kind.values())
            assertThat(DragoonAdaptation.count(packed, kind)).isEqualTo(DragoonAdaptation.MAX_HITS);
    }

    @Test
    void tier_growsAtTheFirstFourthAndEighthHit()
    {
        assertThat(DragoonAdaptation.tier(0)).isZero();
        assertThat(DragoonAdaptation.tier(1)).isEqualTo(1);
        assertThat(DragoonAdaptation.tier(3)).isEqualTo(1);
        assertThat(DragoonAdaptation.tier(4)).isEqualTo(2);
        assertThat(DragoonAdaptation.tier(8)).isEqualTo(3);
        assertThat(DragoonAdaptation.tier(10)).isEqualTo(3);
        assertThat(DragoonAdaptation.grewTier(3, 4)).isTrue();
        assertThat(DragoonAdaptation.grewTier(4, 5)).isFalse();
    }
}
