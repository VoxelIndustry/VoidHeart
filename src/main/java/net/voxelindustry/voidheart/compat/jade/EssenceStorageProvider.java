package net.voxelindustry.voidheart.compat.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.voxelindustry.voidheart.common.content.essence.EssenceStorage;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.function.Function;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Shows the essence held by a block with a single {@link EssenceStorage}, such as the Ravenous Collector and Storage.
 */
public class EssenceStorageProvider implements IServerDataProvider<BlockAccessor>
{
    private static final String TYPE_NAME = "essenceTypeName";
    private static final String AMOUNT = "essenceAmount";
    private static final String MAX_AMOUNT = "essenceMaxAmount";

    private final Identifier uid;
    private final Function<BlockEntity, @Nullable EssenceStorage> storage;

    /**
     * @param storage the storage of a block entity, or null when it has none
     */
    public EssenceStorageProvider(String name, Function<BlockEntity, @Nullable EssenceStorage> storage)
    {
        this.uid = Identifier.fromNamespaceAndPath(MODID, name);
        this.storage = storage;
    }

    @Override
    public Identifier getUid()
    {
        return uid;
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor)
    {
        var held = storage.apply(accessor.getBlockEntity());
        if (held == null)
            return;

        tag.putString(TYPE_NAME, held.type().translatableName());
        tag.putInt(AMOUNT, held.amount());
        tag.putInt(MAX_AMOUNT, held.maximumAmount());
    }

    public IBlockComponentProvider client()
    {
        return new Client(uid);
    }

    private record Client(Identifier getUid) implements IBlockComponentProvider
    {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            var data = accessor.getServerData();
            if (!data.contains(TYPE_NAME))
                return;

            tooltip.add(Component.translatable(data.getStringOr(TYPE_NAME, "")));
            tooltip.append(Component.literal("  " + data.getIntOr(AMOUNT, 0) + " / " + data.getIntOr(MAX_AMOUNT, 0)));
        }
    }
}
