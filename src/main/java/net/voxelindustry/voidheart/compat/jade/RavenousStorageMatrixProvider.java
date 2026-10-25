package net.voxelindustry.voidheart.compat.jade;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageMatrixTile;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class RavenousStorageMatrixProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider
{
    private static final Identifier RAVENOUS_STORAGE_MATRIX = new Identifier(MODID, "ravenous_storage_matrix");

    @Override
    public void appendTooltip(
            ITooltip tooltip,
            BlockAccessor accessor,
            IPluginConfig config
    )
    {
        if (!accessor.getServerData().contains("essenceMaxAmount"))
            return;

        var storageCount = accessor.getServerData().getInt("storageCount");

        for (int index = 0; index < storageCount; index++)
        {
            tooltip.add(Text.literal(index + ": "));
            tooltip.append(Text.translatable(accessor.getServerData().getString("essenceTypeName" + index)));
            tooltip.append(Text.literal("  " + accessor.getServerData().getInt("essenceAmount" + index) + " / " + accessor.getServerData().getInt("essenceMaxAmount")));
        }
    }

    @Override
    public Identifier getUid()
    {
        return RAVENOUS_STORAGE_MATRIX;
    }

    @Override
    public void appendServerData(NbtCompound tag, BlockAccessor accessor)
    {
        var blockEntity = accessor.getBlockEntity();

        if (blockEntity instanceof RavenousStorageMatrixTile storage)
        {
            tag.putInt("storageCount", storage.storage().storageCount());

            for (int index = 0; index < storage.storage().storageCount(); index++)
            {
                tag.putString("essenceTypeName" + index, storage.storage().types()[index].translatableName());
                tag.putInt("essenceAmount" + index, storage.storage().amounts()[index]);
            }
            tag.putInt("essenceMaxAmount", storage.storage().maximumAmount());
        }
    }
}
