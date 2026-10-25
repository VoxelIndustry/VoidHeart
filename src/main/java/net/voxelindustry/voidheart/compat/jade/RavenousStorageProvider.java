package net.voxelindustry.voidheart.compat.jade;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageTile;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class RavenousStorageProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider
{
    private static final Identifier RAVENOUS_STORAGE = new Identifier(MODID, "ravenous_storage");

    @Override
    public void appendTooltip(
            ITooltip tooltip,
            BlockAccessor accessor,
            IPluginConfig config
    )
    {
        if (!accessor.getServerData().contains("essenceTypeName"))
            return;

        tooltip.add(Text.translatable(accessor.getServerData().getString("essenceTypeName")));
        tooltip.append(Text.literal("  "+accessor.getServerData().getInt("essenceAmount")+" / "+accessor.getServerData().getInt("essenceMaxAmount")));
    }

    @Override
    public Identifier getUid()
    {
        return RAVENOUS_STORAGE;
    }

    @Override
    public void appendServerData(NbtCompound tag, BlockAccessor accessor)
    {
        var blockEntity = accessor.getBlockEntity();

        if (blockEntity instanceof RavenousStorageTile storage)
        {
            tag.putString("essenceTypeName", storage.storage().type().translatableName());
            tag.putInt("essenceAmount", storage.storage().amount());
            tag.putInt("essenceMaxAmount", storage.storage().maximumAmount());
        }
    }
}
