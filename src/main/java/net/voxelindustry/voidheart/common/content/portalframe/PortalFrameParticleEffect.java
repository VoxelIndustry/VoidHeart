package net.voxelindustry.voidheart.common.content.portalframe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.voxelindustry.voidheart.VoidHeart;

public record PortalFrameParticleEffect(Direction direction,
                                        int width,
                                        int height) implements ParticleOptions
{
    public static final MapCodec<PortalFrameParticleEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Direction.CODEC.fieldOf("direction").forGetter(PortalFrameParticleEffect::direction),
            Codec.INT.fieldOf("width").forGetter(PortalFrameParticleEffect::width),
            Codec.INT.fieldOf("height").forGetter(PortalFrameParticleEffect::height)
    ).apply(instance, PortalFrameParticleEffect::new));

    public static final StreamCodec<ByteBuf, PortalFrameParticleEffect> STREAM_CODEC = StreamCodec.composite(
            Direction.STREAM_CODEC, PortalFrameParticleEffect::direction,
            ByteBufCodecs.VAR_INT, PortalFrameParticleEffect::width,
            ByteBufCodecs.VAR_INT, PortalFrameParticleEffect::height,
            PortalFrameParticleEffect::new);

    @Override
    public ParticleType<?> getType()
    {
        return VoidHeart.PORTAL_FRAME_PARTICLE;
    }
}
