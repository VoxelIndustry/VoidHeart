package net.voxelindustry.voidheart.common.content.altar;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.command.argument.ItemStackArgument;
import net.minecraft.command.argument.ItemStringReader;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Direction;
import net.voxelindustry.voidheart.VoidHeart;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@RequiredArgsConstructor
public class AltarItemParticleEffect implements ParticleEffect
{
    @Getter
    private final ItemStack stack;

    @Getter
    private final Vector3fc firstPointBezier;
    @Getter
    private final Vector3fc secondPointBezier;

    @Override
    public ParticleType<?> getType()
    {
        return VoidHeart.ALTAR_ITEM_PARTICLE;
    }

    @Override
    public void write(PacketByteBuf buf)
    {
        buf.writeItemStack(stack);

        buf.writeFloat(firstPointBezier.x());
        buf.writeFloat(firstPointBezier.y());
        buf.writeFloat(firstPointBezier.z());

        buf.writeFloat(secondPointBezier.x());
        buf.writeFloat(secondPointBezier.y());
        buf.writeFloat(secondPointBezier.z());
    }

    @Override
    public String asString()
    {
        return Registries.PARTICLE_TYPE.getId(getType()) + " " + (new ItemStackArgument(stack.getRegistryEntry(), stack.getNbt())).asString();
    }

    public static final ParticleEffect.Factory<AltarItemParticleEffect> PARAMETERS_FACTORY = new ParticleEffect.Factory<>()
    {
        @Override
        public AltarItemParticleEffect read(ParticleType<AltarItemParticleEffect> particleType, StringReader stringReader) throws CommandSyntaxException
        {
            stringReader.expect(' ');

            var itemResult = ItemStringReader.item(Registries.ITEM.getReadOnlyWrapper(), stringReader);
            var itemStack = (new ItemStackArgument(itemResult.item(), itemResult.nbt()))
                    .createStack(1, false);

            var upVec = Direction.UP.getUnitVector();
            return new AltarItemParticleEffect(itemStack, upVec, upVec);
        }

        @Override
        public AltarItemParticleEffect read(ParticleType<AltarItemParticleEffect> particleType, PacketByteBuf buffer)
        {
            return new AltarItemParticleEffect(
                    buffer.readItemStack(),
                    new Vector3f(buffer.readFloat(), buffer.readFloat(), buffer.readFloat()),
                    new Vector3f(buffer.readFloat(), buffer.readFloat(), buffer.readFloat())
            );
        }
    };
}
