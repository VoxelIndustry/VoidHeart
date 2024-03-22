package net.voxelindustry.voidheart.common.content.shatterforge;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.ItemStackArgument;
import net.minecraft.command.argument.ItemStringReader;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.voxelindustry.voidheart.VoidHeart;

public record ShatterForgeItemParticleEffect(ItemStack input, ItemStack output) implements ParticleEffect
{
    @Override
    public ParticleType<?> getType()
    {
        return VoidHeart.SHATTER_FORGE_ITEM_PARTICLE;
    }

    @Override
    public void write(PacketByteBuf buffer)
    {
        buffer.writeItemStack(input);
        buffer.writeItemStack(output);
    }

    @Override
    public String asString()
    {
        return Registries.PARTICLE_TYPE.getId(getType()) + " " + (new ItemStackArgument(input.getRegistryEntry(), input.getNbt())).asString() + " " + (new ItemStackArgument(output.getRegistryEntry(), output.getNbt())).asString();
    }

    public static final Factory<ShatterForgeItemParticleEffect> PARAMETERS_FACTORY = new Factory<>()
    {
        @Override
        public ShatterForgeItemParticleEffect read(ParticleType<ShatterForgeItemParticleEffect> particleType, StringReader stringReader) throws CommandSyntaxException
        {
            stringReader.expect(' ');
            var inputItemResult = ItemStringReader.item(Registries.ITEM.getReadOnlyWrapper(), stringReader);
            var inputStack = (new ItemStackArgument(inputItemResult.item(), inputItemResult.nbt()))
                    .createStack(1, false);
            stringReader.expect(' ');

            var outputItemResult = ItemStringReader.item(Registries.ITEM.getReadOnlyWrapper(), stringReader);
            var outputStack = (new ItemStackArgument(outputItemResult.item(), outputItemResult.nbt()))
                    .createStack(1, false);
            return new ShatterForgeItemParticleEffect(inputStack, outputStack);
        }

        @Override
        public ShatterForgeItemParticleEffect read(ParticleType<ShatterForgeItemParticleEffect> particleType, PacketByteBuf buffer)
        {
            return new ShatterForgeItemParticleEffect(
                    buffer.readItemStack(),
                    buffer.readItemStack()
            );
        }
    };
}
