package net.voxelindustry.voidheart.common.content.shatterforge;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Direction.Axis;
import net.voxelindustry.voidheart.VoidHeart;
import qouteall.imm_ptl.core.commands.AxisArgumentType;

public record ShatterForgeRuneParticleEffect(Axis axis) implements ParticleEffect
{
    @Override
    public ParticleType<?> getType()
    {
        return VoidHeart.SHATTER_FORGE_RUNE_PARTICLE;
    }

    @Override
    public void write(PacketByteBuf buffer)
    {
        buffer.writeInt(axis.ordinal());
    }

    @Override
    public String asString()
    {
        return Registries.PARTICLE_TYPE.getId(getType()) + " " + (DoubleArgumentType.doubleArg(0));
    }

    public static final Factory<ShatterForgeRuneParticleEffect> PARAMETERS_FACTORY = new Factory<>()
    {
        @Override
        public ShatterForgeRuneParticleEffect read(ParticleType<ShatterForgeRuneParticleEffect> particleType, StringReader stringReader) throws CommandSyntaxException
        {
            stringReader.expect(' ');
            var direction = AxisArgumentType.instance.parse(stringReader);
            return new ShatterForgeRuneParticleEffect(direction);
        }

        @Override
        public ShatterForgeRuneParticleEffect read(ParticleType<ShatterForgeRuneParticleEffect> particleType, PacketByteBuf buffer)
        {
            return new ShatterForgeRuneParticleEffect(Axis.VALUES[buffer.readInt()]);
        }
    };
}
