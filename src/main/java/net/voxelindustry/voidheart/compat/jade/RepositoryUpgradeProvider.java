package net.voxelindustry.voidheart.compat.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.research.MeatProxyTile;
import net.voxelindustry.voidheart.common.content.research.RepositoryBoundTile;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Which repository a Cogitant, Void Loom or Meat Proxy works for, worded like the columns, and how long a proxy can still bear strain.
 */
public class RepositoryUpgradeProvider implements IServerDataProvider<BlockAccessor>
{
    private static final Identifier REPOSITORY_UPGRADE = Identifier.fromNamespaceAndPath(MODID, "repository_upgrade");

    @Override
    public Identifier getUid()
    {
        return REPOSITORY_UPGRADE;
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor)
    {
        if (!(accessor.getBlockEntity() instanceof RepositoryBoundTile tile))
            return;

        tag.putBoolean("linked", tile.getLink().isPresent());
        tag.putBoolean("active", tile.isActive());
        tile.getLink().flatMap(link -> KnowledgeColumnProvider.nameOf(accessor.getLevel().getServer(), link.owner())).ifPresent(name -> tag.putString("owner", name));
        if (tile instanceof MeatProxyTile proxy)
        {
            tag.putDouble("left", proxy.getLeft());
            tag.putBoolean("bearing", proxy.isBearing());
        }
    }

    public static class Client implements IBlockComponentProvider
    {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            var data = accessor.getServerData();
            if (!data.contains("linked"))
                return;

            if (!data.getBooleanOr("linked", false))
                tooltip.add(Component.translatable(MODID + ".jade.upgrade.idle"));
            else
            {
                boolean active = data.getBooleanOr("active", false);
                if (data.contains("owner"))
                    tooltip.add(Component.translatable(MODID + (active ? ".jade.column.linked" : ".jade.column.pending"), data.getStringOr("owner", "")));
                else
                    tooltip.add(Component.translatable(MODID + (active ? ".jade.column.linked_unknown" : ".jade.column.pending_unknown")));
                if (!active)
                    tooltip.add(Component.translatable(MODID + ".jade.column.unusable").withStyle(ChatFormatting.ITALIC));
            }

            if (data.contains("left"))
            {
                tooltip.add(Component.translatable(MODID + ".meat_proxy.endurance", (int) Math.ceil(data.getDoubleOr("left", 0) / 60)));
                if (data.getBooleanOr("bearing", false))
                    tooltip.add(Component.translatable(MODID + ".jade.proxy.bearing").withStyle(ChatFormatting.DARK_RED));
            }
        }

        @Override
        public Identifier getUid()
        {
            return REPOSITORY_UPGRADE;
        }
    }
}
