package net.voxelindustry.voidheart.common.content.secondheart;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What the player's own client shows of their second heart: no number, only whether it is there and ready.
 */
public enum HeartShown
{
    NONE,
    HOLLOW,
    READY;

    public static final StreamCodec<ByteBuf, HeartShown> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], HeartShown::ordinal);
}
