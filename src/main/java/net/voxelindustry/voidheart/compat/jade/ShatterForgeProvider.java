package net.voxelindustry.voidheart.compat.jade;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeTile;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.JadeUI;

public class ShatterForgeProvider implements StreamServerDataProvider<BlockAccessor, ShatterForgeProvider.Data>
{
    private static final Identifier SHATTER_FORGE = Identifier.fromNamespaceAndPath(VoidHeart.MODID, "shatter_forge");

    @Override
    public Identifier getUid()
    {
        return SHATTER_FORGE;
    }

    @Override
    public Data streamData(BlockAccessor accessor)
    {
        if (accessor.getBlockEntity() instanceof ShatterForgeTile forge && forge.getRecipeState() != null)
        {
            return new Data(forge.getRecipeState().getInput(ItemStack.class, 0),
                    forge.getRecipeState().completionDelta(),
                    forge.getRecipeState().getOutput(ItemStack.class, 0));
        }
        return null;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec()
    {
        return Data.STREAM_CODEC;
    }

    public record Data(ItemStack input, float progress, ItemStack output)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, Data::input,
                ByteBufCodecs.FLOAT, Data::progress,
                ItemStack.OPTIONAL_STREAM_CODEC, Data::output,
                Data::new);
    }

    public static class Client implements IBlockComponentProvider
    {
        private final ShatterForgeProvider provider;

        public Client(ShatterForgeProvider provider)
        {
            this.provider = provider;
        }

        @Override
        public void appendTooltip(
                ITooltip tooltip,
                BlockAccessor accessor,
                IPluginConfig config
        )
        {
            provider.decodeFromData(accessor).ifPresent(data ->
            {
                tooltip.append(JadeUI.item(data.input()));
                tooltip.append(JadeUI.progressArrow(data.progress()));
                tooltip.append(JadeUI.item(data.output()));
            });
        }

        @Override
        public Identifier getUid()
        {
            return SHATTER_FORGE;
        }
    }
}
