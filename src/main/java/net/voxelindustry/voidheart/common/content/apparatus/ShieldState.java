package net.voxelindustry.voidheart.common.content.apparatus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

/**
 * The shield a player carries, worked out each tick from the apparatus they wear.
 *
 * @param points   what is left to absorb
 * @param capacity what it holds when full, kept so the client can draw it without knowing the apparatus
 * @param delay     ticks left before it starts refilling
 */
public record ShieldState(float points, float capacity, int delay)
{
    public static final ShieldState NONE = new ShieldState(0, 0, 0);

    public static final Codec<ShieldState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("points").forGetter(ShieldState::points),
            Codec.FLOAT.fieldOf("capacity").forGetter(ShieldState::capacity),
            Codec.INT.fieldOf("delay").forGetter(ShieldState::delay)
    ).apply(instance, ShieldState::new));

    public static final StreamCodec<ByteBuf, ShieldState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ShieldState::points,
            ByteBufCodecs.FLOAT, ShieldState::capacity,
            ByteBufCodecs.VAR_INT, ShieldState::delay,
            ShieldState::new);

    public boolean isFull()
    {
        return points >= capacity;
    }

    /**
     * A new apparatus starts empty, so swapping one in can't refill a shield in the middle of a fight, and a smaller capacity cuts the points down to it.
     */
    public ShieldState fitTo(@Nullable ShieldStats stats)
    {
        if (stats == null)
            return NONE;
        if (capacity == 0)
            return new ShieldState(0, stats.capacity(), stats.delayTicks());
        return new ShieldState(Math.min(points, stats.capacity()), stats.capacity(), delay);
    }

    public ShieldState tick(ShieldStats stats)
    {
        if (delay > 0)
            return new ShieldState(points, capacity, delay - 1);
        if (isFull())
            return this;
        return new ShieldState(Math.min(capacity, points + stats.regenPerTick()), capacity, 0);
    }

    /**
     * @param absorbed damage taken off the hit
     * @param broke    whether this hit emptied a shield that held something
     */
    public record Absorption(ShieldState state, float absorbed, boolean broke)
    {
    }

    public Absorption absorb(ShieldStats stats, float damage)
    {
        return absorb(stats, damage, false);
    }

    /**
     * Takes what it can of a hit. A warded hit costs half its points, so the same shield stops twice as much of it.
     */
    public Absorption absorb(ShieldStats stats, float damage, boolean warded)
    {
        if (damage <= 0)
            return new Absorption(this, 0, false);

        float cost = warded ? ShieldStats.WARD_COST : 1;
        float absorbed = Math.min(damage, points / cost);
        float left = Math.max(0, points - absorbed * cost);
        if (left < 0.001F)
            left = 0;

        boolean broke = points > 0 && left == 0;
        int nextDelay = broke ? stats.brokenDelayTicks() : stats.delayTicks();
        return new Absorption(new ShieldState(left, capacity, Math.max(delay, nextDelay)), absorbed, broke);
    }
}
