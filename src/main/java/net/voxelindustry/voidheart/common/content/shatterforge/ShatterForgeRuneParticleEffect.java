package net.voxelindustry.voidheart.common.content.shatterforge;

import com.mojang.serialization.MapCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.voxelindustry.voidheart.VoidHeart;

public record ShatterForgeRuneParticleEffect(Axis axis) implements ParticleOptions
{
    public static final MapCodec<ShatterForgeRuneParticleEffect> CODEC = Axis.CODEC.fieldOf("axis").xmap(ShatterForgeRuneParticleEffect::new, ShatterForgeRuneParticleEffect::axis);

    public static final StreamCodec<ByteBuf, ShatterForgeRuneParticleEffect> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            ordinal -> new ShatterForgeRuneParticleEffect(Axis.VALUES[ordinal]),
            effect -> effect.axis().ordinal());

    @Override
    public ParticleType<?> getType()
    {
        return VoidHeart.SHATTER_FORGE_RUNE_PARTICLE;
    }
}
