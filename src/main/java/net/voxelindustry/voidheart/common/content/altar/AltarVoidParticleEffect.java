package net.voxelindustry.voidheart.common.content.altar;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.voxelindustry.voidheart.VoidHeart;

@RequiredArgsConstructor
public class AltarVoidParticleEffect implements ParticleOptions
{
    @Getter
    private final double speed;

    @Override
    public ParticleType<?> getType()
    {
        return VoidHeart.ALTAR_VOID_PARTICLE;
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf)
    {
        buf.writeDouble(speed);
    }

    @Override
    public String writeToString()
    {
        return BuiltInRegistries.PARTICLE_TYPE.getKey(getType()) + " " + (DoubleArgumentType.doubleArg(0));
    }

    public static final Deserializer<AltarVoidParticleEffect> PARAMETERS_FACTORY = new Deserializer<>()
    {
        @Override
        public AltarVoidParticleEffect fromCommand(ParticleType<AltarVoidParticleEffect> particleType, StringReader stringReader) throws CommandSyntaxException
        {
            stringReader.expect(' ');
            double speed = DoubleArgumentType.doubleArg(0).parse(stringReader);
            return new AltarVoidParticleEffect(speed);
        }

        @Override
        public AltarVoidParticleEffect fromNetwork(ParticleType<AltarVoidParticleEffect> particleType, FriendlyByteBuf packetByteBuf)
        {
            return new AltarVoidParticleEffect(packetByteBuf.readDouble());
        }
    };
}
