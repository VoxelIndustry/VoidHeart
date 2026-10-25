package net.voxelindustry.voidheart.compat.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.beam.BeamComposition;
import net.voxelindustry.voidheart.common.content.beam.FormationError;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeTile;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.JadeUI;

import java.util.Map;

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
        if (accessor.getBlockEntity() instanceof ShatterForgeTile forge)
        {
            var crafting = forge.getRecipeState() != null;
            var error = forge.getFormationError();

            return new Data(crafting ? forge.getRecipeState().getInput(ItemStack.class, 0) : ItemStack.EMPTY,
                    crafting ? forge.getRecipeState().completionDelta() : -1F,
                    crafting ? forge.getRecipeState().getOutput(ItemStack.class, 0) : ItemStack.EMPTY,
                    forge.getBeamComposition(),
                    new BeamComposition(crafting ? forge.getRequiredAspects() : Map.of(), 0),
                    error == null ? "" : error.name());
        }
        return null;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec()
    {
        return Data.STREAM_CODEC;
    }

    /**
     * A negative progress means the forge is not crafting.
     */
    public record Data(ItemStack input, float progress, ItemStack output, BeamComposition beam, BeamComposition required, String error)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, Data::input,
                ByteBufCodecs.FLOAT, Data::progress,
                ItemStack.OPTIONAL_STREAM_CODEC, Data::output,
                BeamComposition.STREAM_CODEC, Data::beam,
                BeamComposition.STREAM_CODEC, Data::required,
                ByteBufCodecs.STRING_UTF8, Data::error,
                Data::new);

        public boolean crafting()
        {
            return progress >= 0;
        }
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
                if (data.crafting())
                {
                    tooltip.append(JadeUI.item(data.input()));
                    tooltip.append(JadeUI.progressArrow(data.progress()));
                    tooltip.append(JadeUI.item(data.output()));
                }
                else if (!data.error().isEmpty())
                {
                    tooltip.add(Component.translatable(FormationError.valueOf(data.error()).translationKey()).withStyle(ChatFormatting.RED));
                }

                if (data.crafting())
                    appendRequirements(tooltip, data);
                else
                    appendBeam(tooltip, data.beam());
            });
        }

        private static void appendBeam(ITooltip tooltip, BeamComposition beam)
        {
            beam.attuned().forEach((type, amount) ->
                    tooltip.add(Component.translatable(type.translatableName()).append(": " + amount)));

            if (beam.unattuned() > 0)
                tooltip.add(Component.translatable("voidheart.jade.beam.unattuned").append(": " + beam.unattuned()));
        }

        private static void appendRequirements(ITooltip tooltip, Data data)
        {
            data.required().attuned().forEach((type, required) ->
            {
                var supplied = data.beam().amount(type);
                tooltip.add(Component.translatable(type.translatableName())
                        .append(": " + supplied + "/" + required)
                        .withStyle(supplied >= required ? ChatFormatting.GREEN : ChatFormatting.RED));
            });

            if (data.beam().unattuned() > 0)
                tooltip.add(Component.translatable("voidheart.jade.beam.unattuned").append(": " + data.beam().unattuned()));
        }

        @Override
        public Identifier getUid()
        {
            return SHATTER_FORGE;
        }
    }
}
