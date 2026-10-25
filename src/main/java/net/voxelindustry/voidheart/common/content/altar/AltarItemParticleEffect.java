package net.voxelindustry.voidheart.common.content.altar;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.VoidHeart;
import org.joml.Vector3fc;

public record AltarItemParticleEffect(ItemStack stack,
                                      Vector3fc firstPointBezier,
                                      Vector3fc secondPointBezier) implements ParticleOptions
{
    public static final MapCodec<AltarItemParticleEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ItemStack.CODEC.fieldOf("item").forGetter(AltarItemParticleEffect::stack),
            ExtraCodecs.VECTOR3F.fieldOf("first_point").forGetter(AltarItemParticleEffect::firstPointBezier),
            ExtraCodecs.VECTOR3F.fieldOf("second_point").forGetter(AltarItemParticleEffect::secondPointBezier)
    ).apply(instance, AltarItemParticleEffect::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AltarItemParticleEffect> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, AltarItemParticleEffect::stack,
            ByteBufCodecs.VECTOR3F, AltarItemParticleEffect::firstPointBezier,
            ByteBufCodecs.VECTOR3F, AltarItemParticleEffect::secondPointBezier,
            AltarItemParticleEffect::new);

    @Override
    public ParticleType<?> getType()
    {
        return VoidHeart.ALTAR_ITEM_PARTICLE;
    }

    public ItemStack getStack()
    {
        return stack;
    }

    public Vector3fc getFirstPointBezier()
    {
        return firstPointBezier;
    }

    public Vector3fc getSecondPointBezier()
    {
        return secondPointBezier;
    }
}
