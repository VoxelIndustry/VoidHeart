package net.voxelindustry.voidheart.common.content.altar;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.command.arguments.ItemStackArgument;
import net.minecraft.command.arguments.ItemStringReader;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.util.registry.Registry;
import net.voxelindustry.steamlayer.math.Vec3f;
import net.voxelindustry.voidheart.VoidHeart;

@RequiredArgsConstructor
public class AltarItemParticleEffect implements ParticleEffect
{
    @Getter
    private final ItemStack stack;

    @Getter
    private final Vec3f firstPointBezier;
    @Getter
    private final Vec3f secondPointBezier;

    @Override
    public ParticleType<?> getType()
    {
        return VoidHeart.ALTAR_ITEM_PARTICLE;
    }

    @Override
    public void write(PacketByteBuf buf)
    {
        buf.writeItemStack(stack);

        buf.writeFloat(firstPointBezier.getX());
        buf.writeFloat(firstPointBezier.getY());
        buf.writeFloat(firstPointBezier.getZ());

        buf.writeFloat(secondPointBezier.getX());
        buf.writeFloat(secondPointBezier.getY());
        buf.writeFloat(secondPointBezier.getZ());
    }

    @Override
    public String asString()
    {
        return Registry.PARTICLE_TYPE.getId(getType()) + " " + (new ItemStackArgument(stack.getItem(), stack.getTag())).asString();
    }

    public static final ParticleEffect.Factory<AltarItemParticleEffect> PARAMETERS_FACTORY = new ParticleEffect.Factory<AltarItemParticleEffect>()
    {
        @Override
        public AltarItemParticleEffect read(ParticleType<AltarItemParticleEffect> particleType, StringReader stringReader) throws CommandSyntaxException
        {
            stringReader.expect(' ');
            ItemStringReader itemStringReader = (new ItemStringReader(stringReader, false)).consume();
            ItemStack itemStack = (new ItemStackArgument(itemStringReader.getItem(), itemStringReader.getTag())).createStack(1, false);

            return new AltarItemParticleEffect(itemStack, Vec3f.UP, Vec3f.UP);
        }

        @Override
        public AltarItemParticleEffect read(ParticleType<AltarItemParticleEffect> particleType, PacketByteBuf buffer)
        {
            return new AltarItemParticleEffect(
                    buffer.readItemStack(),
                    new Vec3f(buffer.readFloat(), buffer.readFloat(), buffer.readFloat()),
                    new Vec3f(buffer.readFloat(), buffer.readFloat(), buffer.readFloat())
            );
        }
    };
}
