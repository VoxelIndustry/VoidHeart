package net.voxelindustry.voidheart.common.content.shatterforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.voxelindustry.voidheart.VoidHeart;

/**
 * Runes of one monolith pair, alive as long as the forge keeps that pair lit.
 */
public record ShatterForgeRuneParticleEffect(Axis axis, BlockPos forge, int index) implements ParticleOptions
{
    public static final MapCodec<ShatterForgeRuneParticleEffect> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Axis.CODEC.fieldOf("axis").forGetter(ShatterForgeRuneParticleEffect::axis),
                    BlockPos.CODEC.fieldOf("forge").forGetter(ShatterForgeRuneParticleEffect::forge),
                    Codec.INT.fieldOf("index").forGetter(ShatterForgeRuneParticleEffect::index)
            ).apply(instance, ShatterForgeRuneParticleEffect::new));

    public static final StreamCodec<ByteBuf, ShatterForgeRuneParticleEffect> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(ordinal -> Axis.VALUES[ordinal], Axis::ordinal), ShatterForgeRuneParticleEffect::axis,
            BlockPos.STREAM_CODEC, ShatterForgeRuneParticleEffect::forge,
            ByteBufCodecs.VAR_INT, ShatterForgeRuneParticleEffect::index,
            ShatterForgeRuneParticleEffect::new);

    @Override
    public ParticleType<?> getType()
    {
        return VoidHeart.SHATTER_FORGE_RUNE_PARTICLE;
    }
}
