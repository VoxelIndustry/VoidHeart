package net.voxelindustry.voidheart.common.content.shatterforge;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.VoidHeart;

public record ShatterForgeItemParticleEffect(ItemStack input, ItemStack output) implements ParticleOptions
{
    @Override
    public ParticleType<?> getType()
    {
        return VoidHeart.SHATTER_FORGE_ITEM_PARTICLE;
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buffer)
    {
        buffer.writeItem(input);
        buffer.writeItem(output);
    }

    @Override
    public String writeToString()
    {
        return BuiltInRegistries.PARTICLE_TYPE.getKey(getType()) + " " + (new ItemInput(input.getItemHolder(), input.getTag())).serialize() + " " + (new ItemInput(output.getItemHolder(), output.getTag())).serialize();
    }

    public static final Deserializer<ShatterForgeItemParticleEffect> PARAMETERS_FACTORY = new Deserializer<>()
    {
        @Override
        public ShatterForgeItemParticleEffect fromCommand(ParticleType<ShatterForgeItemParticleEffect> particleType, StringReader stringReader) throws CommandSyntaxException
        {
            stringReader.expect(' ');
            var inputItemResult = ItemParser.parseForItem(BuiltInRegistries.ITEM.asLookup(), stringReader);
            var inputStack = (new ItemInput(inputItemResult.item(), inputItemResult.nbt()))
                    .createItemStack(1, false);
            stringReader.expect(' ');

            var outputItemResult = ItemParser.parseForItem(BuiltInRegistries.ITEM.asLookup(), stringReader);
            var outputStack = (new ItemInput(outputItemResult.item(), outputItemResult.nbt()))
                    .createItemStack(1, false);
            return new ShatterForgeItemParticleEffect(inputStack, outputStack);
        }

        @Override
        public ShatterForgeItemParticleEffect fromNetwork(ParticleType<ShatterForgeItemParticleEffect> particleType, FriendlyByteBuf buffer)
        {
            return new ShatterForgeItemParticleEffect(
                    buffer.readItem(),
                    buffer.readItem()
            );
        }
    };
}
