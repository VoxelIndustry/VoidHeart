package net.voxelindustry.voidheart.common.content.hoard;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.config.IPluginConfig;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * What a Fold gives back, and the room a coffer gives and how full its hoard is, all read from what the blocks sync; and what an Aspect Fold's
 * reserve holds, read on the server.
 */
public final class HoardJade
{
    private HoardJade()
    {
    }

    public static void register(IWailaCommonRegistration registration)
    {
        registration.registerBlockDataProvider(new AspectFoldServer(), AspectFoldTile.class);
    }

    public static void registerClient(IWailaClientRegistration registration)
    {
        registration.registerBlockComponent(new AspectFoldClient(), AspectFoldBlock.class);
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

    private static final Identifier ASPECT_FOLD_UID = Identifier.fromNamespaceAndPath(MODID, "aspect_fold");

    private static final class AspectFoldServer implements IServerDataProvider<BlockAccessor>
    {
        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor)
        {
            if (!(accessor.getBlockEntity() instanceof AspectFoldTile fold))
                return;
            var hoard = fold.hoard();
            if (hoard == null)
                return;
            tag.putBoolean("open", fold.isOpen());
            var aspects = new ListTag();
            hoard.reserve().amounts().forEach((aspect, amount) ->
            {
                var entry = new CompoundTag();
                entry.putString("name", aspect.translatableName());
                entry.putLong("amount", amount);
                entry.putLong("capacity", hoard.reserve().capacityOf(aspect));
                aspects.add(entry);
            });
            tag.put("aspects", aspects);
            tag.putLong("free", hoard.reserve().freeCapacity());
        }

        @Override
        public Identifier getUid()
        {
            return ASPECT_FOLD_UID;
        }
    }

    private static final class AspectFoldClient implements IBlockComponentProvider
    {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            var data = accessor.getServerData();
            if (!data.contains("open"))
                return;
            if (!data.getBooleanOr("open", false))
                tooltip.add(Component.translatable(MODID + ".jade.aspect_fold.closed").withStyle(ChatFormatting.YELLOW));
            var aspects = data.getListOrEmpty("aspects");
            if (aspects.isEmpty())
                tooltip.add(Component.translatable(MODID + ".jade.aspect_fold.empty").withStyle(ChatFormatting.GRAY));
            for (int index = 0; index < aspects.size(); index++)
            {
                var entry = aspects.getCompoundOrEmpty(index);
                tooltip.add(Component.translatable(entry.getStringOr("name", "")));
                tooltip.append(Component.literal("  " + entry.getLongOr("amount", 0) + " / " + entry.getLongOr("capacity", 0)));
            }
            var free = data.getLongOr("free", 0);
            if (free > 0)
                tooltip.add(Component.translatable(MODID + ".jade.aspect_fold.free", free).withStyle(ChatFormatting.GRAY));
        }

        @Override
        public Identifier getUid()
        {
            return ASPECT_FOLD_UID;
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
