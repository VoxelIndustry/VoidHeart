package net.voxelindustry.voidheart.common.content.altar;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.util.registry.Registry;
import net.voxelindustry.voidheart.VoidHeart;

@RequiredArgsConstructor
public class AltarVoidParticleEffect implements ParticleEffect
{
    @Getter
    private final double speed;

    @Override
    public ParticleType<?> getType()
    {
        return VoidHeart.ALTAR_VOID_PARTICLE;
    }

    @Override
    public void write(PacketByteBuf buf)
    {
        buf.writeDouble(speed);
    }

    @Override
    public String asString()
    {
        return Registry.PARTICLE_TYPE.getId(getType()) + " " + (DoubleArgumentType.doubleArg(0));
    }

    public static final Factory<AltarVoidParticleEffect> PARAMETERS_FACTORY = new Factory<AltarVoidParticleEffect>()
    {
        @Override
        public AltarVoidParticleEffect read(ParticleType<AltarVoidParticleEffect> particleType, StringReader stringReader) throws CommandSyntaxException
        {
            stringReader.expect(' ');
            double speed = DoubleArgumentType.doubleArg(0).parse(stringReader);
            return new AltarVoidParticleEffect(speed);
        }

        @Override
        public AltarVoidParticleEffect read(ParticleType<AltarVoidParticleEffect> particleType, PacketByteBuf packetByteBuf)
        {
            return new AltarVoidParticleEffect(packetByteBuf.readDouble());
        }
    };
}
