package net.voxelindustry.voidheart.common.content.beam;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;

/**
 * A straight visible beam run, from the emitting block to the receiving block, carrying its composition on arrival.
 */
public record BeamSegment(BlockPos from, BlockPos to, BeamComposition composition)
{
    public static final Codec<BeamSegment> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BlockPos.CODEC.fieldOf("from").forGetter(BeamSegment::from),
                    BlockPos.CODEC.fieldOf("to").forGetter(BeamSegment::to),
                    BeamComposition.CODEC.fieldOf("composition").forGetter(BeamSegment::composition)
            ).apply(instance, BeamSegment::new));

    public static final StreamCodec<ByteBuf, BeamSegment> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BeamSegment::from,
            BlockPos.STREAM_CODEC, BeamSegment::to,
            BeamComposition.STREAM_CODEC, BeamSegment::composition,
            BeamSegment::new);
}
