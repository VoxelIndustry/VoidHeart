package net.voxelindustry.voidheart.common.content.secondheart;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * A dissolve and reform under way, synced to everyone who can see the player so they all draw it.
 *
 * @param start  game time the heart fired
 * @param anchor where the player is held, and reforms
 * @param wound  where the dissolve starts, from the player's feet
 */
public record HeartSequence(long start, Vec3 anchor, Vec3 wound)
{
    public static final StreamCodec<ByteBuf, HeartSequence> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, HeartSequence::start,
            Vec3.STREAM_CODEC, HeartSequence::anchor,
            Vec3.STREAM_CODEC, HeartSequence::wound,
            HeartSequence::new);

    public float elapsed(long gameTime, float partialTick)
    {
        return gameTime - start + partialTick;
    }

    public HeartSequence movedTo(Vec3 anchor)
    {
        return new HeartSequence(start, anchor, wound);
    }
}
