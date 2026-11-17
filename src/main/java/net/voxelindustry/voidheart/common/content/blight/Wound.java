package net.voxelindustry.voidheart.common.content.blight;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * The punctures one rapier's wielder left in one target. Nothing ticks it: how many are still open is worked out from the time of the last hit, so the
 * server and every client agree on when each one closes without being told.
 *
 * @param slits      oldest first
 * @param lastHit    game time of the last puncture
 * @param lockedTill game time until which no puncture lands, after an Unseaming
 */
public record Wound(List<Slit> slits, long lastHit, long lockedTill)
{
    /** How many counted hits unseam the target. */
    public static final int  HITS        = 7;
    /** How long they all stay open after the last hit. */
    public static final long HOLD        = 60;
    /** Then one closes this often, oldest first. */
    public static final long CLOSE_EVERY = 10;
    /** How long after an Unseaming the same wielder can't puncture that target. */
    public static final long LOCKOUT     = 100;

    public static final Wound NONE = new Wound(List.of(), 0, 0);

    public static final StreamCodec<ByteBuf, Wound> STREAM_CODEC = StreamCodec.composite(
            Slit.STREAM_CODEC.apply(ByteBufCodecs.list(HITS)), Wound::slits,
            ByteBufCodecs.VAR_LONG, Wound::lastHit,
            ByteBufCodecs.VAR_LONG, Wound::lockedTill,
            Wound::new);

    /**
     * Where a puncture went, in the target's own frame at the time: across its body, up from its feet as a share of its height, and how the slit is turned.
     */
    public record Slit(float side, float height, float depth, float angle)
    {
        public static final StreamCodec<ByteBuf, Slit> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Slit::side,
                ByteBufCodecs.FLOAT, Slit::height,
                ByteBufCodecs.FLOAT, Slit::depth,
                ByteBufCodecs.FLOAT, Slit::angle,
                Slit::new);
    }

    /** How many have closed by then. */
    public int closed(long now)
    {
        long idle = now - lastHit;
        if (idle < HOLD)
            return 0;
        return (int) Math.min(slits.size(), 1 + (idle - HOLD) / CLOSE_EVERY);
    }

    public int open(long now)
    {
        return slits.size() - closed(now);
    }

    /** Those still open, oldest first. */
    public List<Slit> openSlits(long now)
    {
        return slits.subList(closed(now), slits.size());
    }

    public boolean isLocked(long now)
    {
        return now < lockedTill;
    }

    /** Whether nothing is left of it worth keeping. */
    public boolean isSpent(long now)
    {
        return open(now) == 0 && !isLocked(now);
    }

    /**
     * @return the wound with one more puncture, or the same one while it is locked
     */
    public Wound hit(Slit slit, long now)
    {
        if (isLocked(now))
            return this;
        var kept = new ArrayList<>(openSlits(now));
        kept.add(slit);
        return new Wound(List.copyOf(kept), now, lockedTill);
    }

    public boolean isReady(long now)
    {
        return !isLocked(now) && open(now) >= HITS;
    }

    /** Every slit spent at once, and the target closed to this wielder for a while. */
    public Wound unseam(long now)
    {
        return new Wound(List.of(), now, now + LOCKOUT);
    }
}
