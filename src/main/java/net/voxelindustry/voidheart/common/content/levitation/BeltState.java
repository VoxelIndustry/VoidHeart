package net.voxelindustry.voidheart.common.content.levitation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What the belt does for its wearer, synced to everyone so the motes show on other players too.
 *
 * @param granted   the belt gave the wearer flight, and must take it back
 * @param flying    flying on the belt this tick, drawing from a field
 * @param fadeSince game time the lift started fading after the field was lost in flight, or -1
 */
public record BeltState(boolean granted, boolean flying, long fadeSince)
{
    public static final BeltState NONE = new BeltState(false, false, -1);

    public static final Codec<BeltState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("granted").forGetter(BeltState::granted),
            Codec.BOOL.optionalFieldOf("flying", false).forGetter(BeltState::flying),
            Codec.LONG.optionalFieldOf("fade_since", -1L).forGetter(BeltState::fadeSince)
    ).apply(instance, BeltState::new));

    public static final StreamCodec<ByteBuf, BeltState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BeltState::granted,
            ByteBufCodecs.BOOL, BeltState::flying,
            ByteBufCodecs.VAR_LONG, BeltState::fadeSince,
            BeltState::new);

    public boolean fading()
    {
        return fadeSince >= 0;
    }

    public long elapsed(long now)
    {
        return fading() ? now - fadeSince : Long.MAX_VALUE;
    }
}
