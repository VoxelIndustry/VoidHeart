package net.voxelindustry.voidheart.common.content.secondheart;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A player's second heart, saved with the player.
 *
 * @param owned    the rite was performed
 * @param recharge online ticks left before the life comes back, 0 when ready
 */
public record HeartState(boolean owned, int recharge)
{
    public static final HeartState NONE = new HeartState(false, 0);

    public static final Codec<HeartState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("owned", false).forGetter(HeartState::owned),
            Codec.INT.optionalFieldOf("recharge", 0).forGetter(HeartState::recharge)
    ).apply(instance, HeartState::new));

    public boolean ready()
    {
        return owned && recharge <= 0;
    }

    public HeartShown shown()
    {
        if (!owned)
            return HeartShown.NONE;
        return recharge > 0 ? HeartShown.HOLLOW : HeartShown.READY;
    }
}
