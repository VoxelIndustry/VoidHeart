package net.voxelindustry.voidheart.common.content.shatterforge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.VoidHeart;

public record ShatterForgeItemParticleEffect(ItemStack input, ItemStack output) implements ParticleOptions
{
    public static final MapCodec<ShatterForgeItemParticleEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ItemStack.CODEC.fieldOf("input").forGetter(ShatterForgeItemParticleEffect::input),
            ItemStack.CODEC.fieldOf("output").forGetter(ShatterForgeItemParticleEffect::output)
    ).apply(instance, ShatterForgeItemParticleEffect::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShatterForgeItemParticleEffect> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, ShatterForgeItemParticleEffect::input,
            ItemStack.OPTIONAL_STREAM_CODEC, ShatterForgeItemParticleEffect::output,
            ShatterForgeItemParticleEffect::new);

    @Override
    public ParticleType<?> getType()
    {
        return VoidHeart.SHATTER_FORGE_ITEM_PARTICLE;
    }
}
