package net.voxelindustry.voidheart.compat.jade;

import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class RavenousCollectorProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider
{
    private static final Identifier RAVENOUS_COLLECTOR = Identifier.fromNamespaceAndPath(MODID, "ravenous_collector");

    @Override
    public void appendTooltip(
            ITooltip tooltip,
            BlockAccessor accessor,
            IPluginConfig config
    )
    {
        if (!accessor.getServerData().contains("essenceTypeName"))
            return;

        tooltip.add(Component.translatable(accessor.getServerData().getString("essenceTypeName")));
        tooltip.append(Component.literal("  "+accessor.getServerData().getInt("essenceAmount")+" / "+accessor.getServerData().getInt("essenceMaxAmount")));
    }

    @Override
    public Identifier getUid()
    {
        return RAVENOUS_COLLECTOR;
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor)
    {
        var blockEntity = accessor.getBlockEntity();

        if (blockEntity instanceof RavenousCollectorTile collector)
        {
            tag.putString("essenceTypeName", collector.storage().type().translatableName());
            tag.putInt("essenceAmount", collector.storage().amount());
            tag.putInt("essenceMaxAmount", collector.storage().maximumAmount());
        }
    }
}
