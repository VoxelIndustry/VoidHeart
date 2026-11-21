package net.voxelindustry.voidheart.common.content.hoard;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.config.IPluginConfig;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * What a Fold gives back, and the room a coffer gives and how full its hoard is, all read from what the blocks sync.
 */
public final class HoardJade
{
    private HoardJade()
    {
    }

    public static void registerClient(IWailaClientRegistration registration)
    {
        registration.registerBlockComponent(new FoldClient(), FoldBlock.class);
        registration.registerBlockComponent(new CofferClient(), HoardCofferBlock.class);
    }

    private static final class FoldClient implements IBlockComponentProvider
    {
        private static final Identifier UID = Identifier.fromNamespaceAndPath(MODID, "fold");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            if (!(accessor.getBlockEntity() instanceof FoldTile fold))
                return;
            var attuned = fold.getAttuned();
            if (attuned == null)
                tooltip.add(Component.translatable(MODID + ".jade.fold.only_takes").withStyle(ChatFormatting.GRAY));
            else
                tooltip.add(Component.translatable(MODID + ".jade.fold.gives", attuned.toStack().getHoverName()).withStyle(ChatFormatting.GRAY));
        }

        @Override
        public Identifier getUid()
        {
            return UID;
        }
    }

    private static final class CofferClient implements IBlockComponentProvider
    {
        private static final Identifier UID = Identifier.fromNamespaceAndPath(MODID, "hoard_coffer");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            if (!(accessor.getBlockEntity() instanceof HoardCofferTile coffer))
                return;
            if (coffer.getBundle().isEmpty())
            {
                tooltip.add(Component.translatable(MODID + ".jade.hoard_coffer.empty").withStyle(ChatFormatting.YELLOW));
                return;
            }
            tooltip.add(Component.translatable(MODID + ".jade.hoard_coffer.room", coffer.capacity()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(MODID + ".jade.hoard_coffer.fill", Math.round(coffer.getFill() * 100)).withStyle(ChatFormatting.GRAY));
        }

        @Override
        public Identifier getUid()
        {
            return UID;
        }
    }
}
