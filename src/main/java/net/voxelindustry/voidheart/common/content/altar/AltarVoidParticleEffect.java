package net.voxelindustry.voidheart.common.content.altar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.voxelindustry.voidheart.VoidHeart;

public record AltarVoidParticleEffect(double speed) implements ParticleOptions
{
    public static final MapCodec<AltarVoidParticleEffect> CODEC = Codec.DOUBLE.fieldOf("speed").xmap(AltarVoidParticleEffect::new, AltarVoidParticleEffect::speed);

    public static final StreamCodec<ByteBuf, AltarVoidParticleEffect> STREAM_CODEC = ByteBufCodecs.DOUBLE.map(AltarVoidParticleEffect::new, AltarVoidParticleEffect::speed);

    @Override
    public ParticleType<?> getType()
    {
        return VoidHeart.ALTAR_VOID_PARTICLE;
    }

    public double getSpeed()
    {
        return speed;
    }
}
