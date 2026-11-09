package net.voxelindustry.voidheart.compat.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.client.BrewTooltip;
import net.voxelindustry.voidheart.common.content.athanor.AthanorTile;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.oil.Brews;
import net.voxelindustry.voidheart.common.energy.machine.MachinePower;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.JadeUI;

import java.util.LinkedHashMap;
import java.util.Map;

public class AthanorProvider implements StreamServerDataProvider<BlockAccessor, AthanorProvider.Data>
{
    private static final Identifier ATHANOR = Identifier.fromNamespaceAndPath(VoidHeart.MODID, "athanor");

    @Override
    public Identifier getUid()
    {
        return ATHANOR;
    }

    @Override
    public Data streamData(BlockAccessor accessor)
    {
        if (!(accessor.getBlockEntity() instanceof AthanorTile athanor))
            return null;

        var recipe = athanor.getCurrentRecipe();
        var required = new LinkedHashMap<Identifier, Integer>();
        var gathered = new LinkedHashMap<Identifier, Integer>();
        if (recipe != null)
        {
            recipe.getAspects().forEach((type, amount) ->
            {
                required.put(type.identifier(), amount);
                gathered.put(type.identifier(), Math.min(amount, athanor.getGathered().getOrDefault(type, 0)));
            });
        }

        return new Data(athanor.getStack(),
                recipe == null ? ItemStack.EMPTY : Brews.apply(recipe.getOutput().copy(), athanor.getBrew()),
                athanor.isBrewing() ? athanor.brewProgress() : -1F,
                athanor.getLayout().alembic(),
                athanor.getLayout().intake(),
                required,
                gathered,
                athanor.getPower().ordinal(),
                athanor.drawRate());
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec()
    {
        return Data.STREAM_CODEC;
    }

    /**
     * A negative progress means the athanor is not brewing.
     */
    public record Data(ItemStack base, ItemStack output, float progress, boolean alembic, boolean intake, Map<Identifier, Integer> required,
                       Map<Identifier, Integer> gathered, int power, long draw)
    {
        private static final StreamCodec<RegistryFriendlyByteBuf, Map<Identifier, Integer>> ASPECTS =
                ByteBufCodecs.map(LinkedHashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_INT);

        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, Data::base,
                ItemStack.OPTIONAL_STREAM_CODEC, Data::output,
                ByteBufCodecs.FLOAT, Data::progress,
                ByteBufCodecs.BOOL, Data::alembic,
                ByteBufCodecs.BOOL, Data::intake,
                ASPECTS, Data::required,
                ASPECTS, Data::gathered,
                ByteBufCodecs.VAR_INT, Data::power,
                ByteBufCodecs.VAR_LONG, Data::draw,
                Data::new);
    }

    public static class Client implements IBlockComponentProvider
    {
        private final AthanorProvider provider;

        public Client(AthanorProvider provider)
        {
            this.provider = provider;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            provider.decodeFromData(accessor).ifPresent(data ->
            {
                if (!data.base().isEmpty() && !data.output().isEmpty())
                {
                    tooltip.append(JadeUI.item(data.base()));
                    tooltip.append(JadeUI.progressArrow(Math.max(0, data.progress())));
                    tooltip.append(JadeUI.item(data.output()));
                    BrewTooltip.lines(Brews.brewOf(data.output())).forEach(tooltip::add);
                }

                if (data.progress() >= 0)
                {
                    var power = MachinePower.byOrdinal(data.power());
                    if (power == MachinePower.UNPOWERED)
                        tooltip.add(Component.translatable("voidheart.jade.athanor.unpowered").withStyle(ChatFormatting.RED));
                    else if (power == MachinePower.WEAK)
                        tooltip.add(Component.translatable("voidheart.jade.athanor.weak").withStyle(ChatFormatting.YELLOW));
                    else
                        tooltip.add(Component.translatable("voidheart.jade.machine.draw", data.draw()).withStyle(ChatFormatting.GRAY));
                }

                if (!data.alembic())
                    tooltip.add(Component.translatable("voidheart.jade.athanor.no_alembic").withStyle(ChatFormatting.RED));

                if (data.progress() < 0 && !data.required().isEmpty())
                {
                    if (!data.intake())
                        tooltip.add(Component.translatable("voidheart.jade.athanor.no_intake").withStyle(ChatFormatting.RED));
                    else
                        tooltip.add(Component.translatable("voidheart.jade.athanor.gathering"));

                    data.required().forEach((id, required) ->
                    {
                        if (!EssenceRegistry.ESSENCE_REGISTRY.containsKey(id))
                            return;
                        var type = EssenceRegistry.ESSENCE_REGISTRY.getValue(id);
                        int gathered = data.gathered().getOrDefault(id, 0);
                        tooltip.add(Component.translatable(type.translatableName())
                                .append(": " + gathered + "/" + required)
                                .withStyle(gathered >= required ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
                    });
                }
            });
        }

        @Override
        public Identifier getUid()
        {
            return ATHANOR;
        }
    }
}
