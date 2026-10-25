package net.voxelindustry.voidheart.compat.jade;

import net.voxelindustry.voidheart.common.content.essence.RavenousStorageTile;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class RavenousStorageProvider implements IServerDataProvider<BlockAccessor>
{
    private static final Identifier RAVENOUS_STORAGE = Identifier.fromNamespaceAndPath(MODID, "ravenous_storage");

    @Override
    public Identifier getUid()
    {
        return RAVENOUS_STORAGE;
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor)
    {
        var blockEntity = accessor.getBlockEntity();

        if (blockEntity instanceof RavenousStorageTile storage)
        {
            tag.putString("essenceTypeName", storage.storage().type().translatableName());
            tag.putInt("essenceAmount", storage.storage().amount());
            tag.putInt("essenceMaxAmount", storage.storage().maximumAmount());
        }
    }

    public static class Client implements IBlockComponentProvider
    {

        @Override
        public void appendTooltip(
                ITooltip tooltip,
                BlockAccessor accessor,
                IPluginConfig config
        )
        {
            if (!accessor.getServerData().contains("essenceTypeName"))
                return;
    
            tooltip.add(Component.translatable(accessor.getServerData().getStringOr("essenceTypeName", "")));
            tooltip.append(Component.literal("  "+accessor.getServerData().getIntOr("essenceAmount", 0)+" / "+accessor.getServerData().getIntOr("essenceMaxAmount", 0)));
        }

        @Override
        public Identifier getUid()
        {
            return RAVENOUS_STORAGE;
        }
    }
}
