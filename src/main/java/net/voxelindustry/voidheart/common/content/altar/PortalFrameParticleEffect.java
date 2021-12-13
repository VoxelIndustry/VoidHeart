package net.voxelindustry.voidheart.common.content.altar;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.Registry;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.command.DirectionArgumentType;

public record PortalFrameParticleEffect(Direction direction,
                                        int width,
                                        int height) implements ParticleEffect
{
    @Override
    public ParticleType<?> getType()
    {
        return VoidHeart.PORTAL_FRAME_PARTICLE;
    }

    @Override
    public void write(PacketByteBuf buffer)
    {
        buffer.writeInt(direction.ordinal());
        buffer.writeInt(width);
        buffer.writeInt(height);
    }

    @Override
    public String asString()
    {
        return Registry.PARTICLE_TYPE.getId(getType()) + " " + (DoubleArgumentType.doubleArg(0));
    }

    public static final Factory<PortalFrameParticleEffect> PARAMETERS_FACTORY = new Factory<>()
    {
        @Override
        public PortalFrameParticleEffect read(ParticleType<PortalFrameParticleEffect> particleType, StringReader stringReader) throws CommandSyntaxException
        {
            stringReader.expect(' ');
            var direction = DirectionArgumentType.direction().parse(stringReader);
            stringReader.expect(' ');
            var width = IntegerArgumentType.integer(0).parse(stringReader);
            stringReader.expect(' ');
            var height = IntegerArgumentType.integer(0).parse(stringReader);
            return new PortalFrameParticleEffect(direction, width, height);
        }

        @Override
        public PortalFrameParticleEffect read(ParticleType<PortalFrameParticleEffect> particleType, PacketByteBuf buffer)
        {
            return new PortalFrameParticleEffect(Direction.byId(buffer.readInt()), buffer.readInt(), buffer.readInt());
        }
    };
}
