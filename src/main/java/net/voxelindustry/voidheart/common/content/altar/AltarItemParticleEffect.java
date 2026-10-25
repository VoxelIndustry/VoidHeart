package net.voxelindustry.voidheart.common.content.altar;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.VoidHeart;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@RequiredArgsConstructor
public class AltarItemParticleEffect implements ParticleOptions
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
    public void writeToNetwork(FriendlyByteBuf buf)
    {
        buf.writeItem(stack);

        buf.writeFloat(firstPointBezier.x());
        buf.writeFloat(firstPointBezier.y());
        buf.writeFloat(firstPointBezier.z());

        buf.writeFloat(secondPointBezier.x());
        buf.writeFloat(secondPointBezier.y());
        buf.writeFloat(secondPointBezier.z());
    }

    @Override
    public String writeToString()
    {
        return BuiltInRegistries.PARTICLE_TYPE.getKey(getType()) + " " + (new ItemInput(stack.getItemHolder(), stack.getTag())).serialize();
    }

    public static final ParticleOptions.Deserializer<AltarItemParticleEffect> PARAMETERS_FACTORY = new ParticleOptions.Deserializer<>()
    {
        @Override
        public AltarItemParticleEffect fromCommand(ParticleType<AltarItemParticleEffect> particleType, StringReader stringReader) throws CommandSyntaxException
        {
            stringReader.expect(' ');

            var itemResult = ItemParser.parseForItem(BuiltInRegistries.ITEM.asLookup(), stringReader);
            var itemStack = (new ItemInput(itemResult.item(), itemResult.nbt()))
                    .createItemStack(1, false);

            var upVec = Direction.UP.step();
            return new AltarItemParticleEffect(itemStack, upVec, upVec);
        }

        @Override
        public AltarItemParticleEffect fromNetwork(ParticleType<AltarItemParticleEffect> particleType, FriendlyByteBuf buffer)
        {
            return new AltarItemParticleEffect(
                    buffer.readItem(),
                    new Vector3f(buffer.readFloat(), buffer.readFloat(), buffer.readFloat()),
                    new Vector3f(buffer.readFloat(), buffer.readFloat(), buffer.readFloat())
            );
        }
    };
}
