package net.voxelindustry.voidheart.common.content.altar;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.voxelindustry.voidheart.VoidHeart;
import org.joml.Vector3fc;

public record AltarExperienceParticleEffect(Vector3fc firstPointBezier, Vector3fc secondPointBezier) implements ParticleOptions
{
    public static final MapCodec<AltarExperienceParticleEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ExtraCodecs.VECTOR3F.fieldOf("first_point").forGetter(AltarExperienceParticleEffect::firstPointBezier),
            ExtraCodecs.VECTOR3F.fieldOf("second_point").forGetter(AltarExperienceParticleEffect::secondPointBezier)
    ).apply(instance, AltarExperienceParticleEffect::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AltarExperienceParticleEffect> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VECTOR3F, AltarExperienceParticleEffect::firstPointBezier,
            ByteBufCodecs.VECTOR3F, AltarExperienceParticleEffect::secondPointBezier,
            AltarExperienceParticleEffect::new);

    @Override
    public ParticleType<?> getType()
    {
        return VoidHeart.ALTAR_EXPERIENCE_PARTICLE;
    }
}
