package net.voxelindustry.voidheart.common.content.blight;

import io.netty.buffer.Unpooled;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WoundTest
{
    private static final Wound.Slit SLIT = new Wound.Slit(0.1F, 0.5F, -0.2F, 0.3F);

    private static Wound hits(int count, long from, long every)
    {
        var wound = Wound.NONE;
        for (int i = 0; i < count; i++)
            wound = wound.hit(SLIT, from + i * every);
        return wound;
    }

    @Test
    void hit_givenSteadyPressure_thenEverySlitStaysOpen()
    {
        var wound = hits(6, 0, 20);
        assertThat(wound.open(100)).isEqualTo(6);
        assertThat(wound.isReady(100)).isFalse();
    }

    @Test
    void hit_givenTheSeventh_thenReady()
    {
        assertThat(hits(Wound.HITS, 0, 10).isReady(60)).isTrue();
    }

    @Test
    void open_givenIdle_thenAllHoldThenCloseOneAtATime()
    {
        var wound = hits(5, 0, 10);
        long last = 40;
        assertThat(wound.open(last + Wound.HOLD - 1)).isEqualTo(5);
        assertThat(wound.open(last + Wound.HOLD)).isEqualTo(4);
        assertThat(wound.open(last + Wound.HOLD + Wound.CLOSE_EVERY)).isEqualTo(3);
        assertThat(wound.open(last + Wound.HOLD + Wound.CLOSE_EVERY * 10)).isZero();
        assertThat(wound.isSpent(last + Wound.HOLD + Wound.CLOSE_EVERY * 10)).isTrue();
    }

    @Test
    void hit_afterSomeClosed_thenCountsOnlyWhatWasStillOpen()
    {
        var wound = hits(5, 0, 10);
        long now = 40 + Wound.HOLD + Wound.CLOSE_EVERY;
        var after = wound.hit(SLIT, now);
        assertThat(after.open(now)).isEqualTo(4);
        assertThat(after.slits()).hasSize(4);
    }

    @Test
    void openSlits_closesTheOldestFirst()
    {
        var first = new Wound.Slit(0, 0.1F, 0, 0);
        var second = new Wound.Slit(0, 0.9F, 0, 0);
        var wound = Wound.NONE.hit(first, 0).hit(second, 5);
        assertThat(wound.openSlits(5 + Wound.HOLD)).containsExactly(second);
    }

    @Test
    void unseam_locksThenLetsPuncturesBackIn()
    {
        var wound = hits(Wound.HITS, 0, 10).unseam(60);
        assertThat(wound.open(60)).isZero();
        assertThat(wound.hit(SLIT, 61).slits()).isEmpty();
        assertThat(wound.isSpent(61)).isFalse();
        assertThat(wound.hit(SLIT, 60 + Wound.LOCKOUT).open(60 + Wound.LOCKOUT)).isEqualTo(1);
    }

    @Test
    void punctured_prunesSpentWoundsOnly()
    {
        var spent = new UUID(0, 1);
        var fresh = new UUID(0, 2);
        var punctured = Punctured.NONE.with(spent, hits(1, 0, 0)).with(fresh, hits(1, 200, 0));
        var pruned = punctured.pruned(200);
        assertThat(pruned.wounds()).containsOnlyKeys(fresh);
    }

    @Test
    void streamCodec_carriesWoundsAndFold()
    {
        var wielder = new UUID(3, 4);
        var fold = new Punctured.Fold(wielder, 120, new Vec3(1, 2, 3), List.of(SLIT, SLIT));
        var punctured = new Punctured(Map.of(wielder, hits(3, 100, 5)), Optional.of(fold));

        var buffer = Unpooled.buffer();
        Punctured.STREAM_CODEC.encode(buffer, punctured);
        assertThat(Punctured.STREAM_CODEC.decode(buffer)).isEqualTo(punctured);
    }

    @Test
    void unseamingDamage_addsAShareOfMaximumHealth()
    {
        assertThat(BlightBalance.unseamingDamage(100)).isCloseTo(BlightBalance.UNSEAMING_DAMAGE + 12, org.assertj.core.api.Assertions.within(1E-4F));
    }
}
