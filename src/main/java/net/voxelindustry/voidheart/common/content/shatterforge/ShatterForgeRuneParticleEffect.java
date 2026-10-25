package net.voxelindustry.voidheart.common.content.shatterforge;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.voxelindustry.voidheart.VoidHeart;

public record ShatterForgeRuneParticleEffect(Axis axis) implements ParticleOptions
{
    @Override
    public ParticleType<?> getType()
    {
        return VoidHeart.SHATTER_FORGE_RUNE_PARTICLE;
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buffer)
    {
        buffer.writeInt(axis.ordinal());
    }

    @Override
    public String writeToString()
    {
        return BuiltInRegistries.PARTICLE_TYPE.getKey(getType()) + " " + (DoubleArgumentType.doubleArg(0));
    }

    public static final Deserializer<ShatterForgeRuneParticleEffect> PARAMETERS_FACTORY = new Deserializer<>()
    {
        @Override
        public ShatterForgeRuneParticleEffect fromCommand(ParticleType<ShatterForgeRuneParticleEffect> particleType, StringReader stringReader) throws CommandSyntaxException
        {
            stringReader.expect(' ');
            var axis = Axis.byName(stringReader.readUnquotedString());
            if (axis == null)
                throw new SimpleCommandExceptionType(Component.literal("Invalid axis")).createWithContext(stringReader);
            return new ShatterForgeRuneParticleEffect(axis);
        }

        @Override
        public ShatterForgeRuneParticleEffect fromNetwork(ParticleType<ShatterForgeRuneParticleEffect> particleType, FriendlyByteBuf buffer)
        {
            return new ShatterForgeRuneParticleEffect(Axis.VALUES[buffer.readInt()]);
        }
    };
}
