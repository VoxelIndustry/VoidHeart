package net.voxelindustry.voidheart.common.content.portalframe;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.command.DirectionArgumentType;

public record PortalFrameParticleEffect(Direction direction,
                                        int width,
                                        int height) implements ParticleOptions
{
    @Override
    public ParticleType<?> getType()
    {
        return VoidHeart.PORTAL_FRAME_PARTICLE;
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buffer)
    {
        buffer.writeInt(direction.ordinal());
        buffer.writeInt(width);
        buffer.writeInt(height);
    }

    @Override
    public String writeToString()
    {
        return BuiltInRegistries.PARTICLE_TYPE.getKey(getType()) + " " + (DoubleArgumentType.doubleArg(0));
    }

    public static final Deserializer<PortalFrameParticleEffect> PARAMETERS_FACTORY = new Deserializer<>()
    {
        @Override
        public PortalFrameParticleEffect fromCommand(ParticleType<PortalFrameParticleEffect> particleType, StringReader stringReader) throws CommandSyntaxException
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
        public PortalFrameParticleEffect fromNetwork(ParticleType<PortalFrameParticleEffect> particleType, FriendlyByteBuf buffer)
        {
            return new PortalFrameParticleEffect(Direction.from3DDataValue(buffer.readInt()), buffer.readInt(), buffer.readInt());
        }
    };
}
