package net.voxelindustry.voidheart.compat.jade;

import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.RavenousRelayTile;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class RavenousRelayProvider implements IServerDataProvider<BlockAccessor>
{
    private static final Identifier RAVENOUS_RELAY = Identifier.fromNamespaceAndPath(MODID, "ravenous_relay");

    @Override
    public Identifier getUid()
    {
        return RAVENOUS_RELAY;
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor)
    {
        if (accessor.getBlockEntity() instanceof RavenousRelayTile relay)
        {
            if (relay.transmitted() != EssenceRegistry.EMPTY)
                tag.putString("transmittedName", relay.transmitted().translatableName());
            tag.putInt("linkedCollectors", relay.linkedCollectors().size());
            tag.putInt("linkedStorages", relay.linkedReceivers().size());
            tag.putInt("linkedRelays", relay.linkedRelays().size());
        }
    }

    public static class Client implements IBlockComponentProvider
    {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            var data = accessor.getServerData();
            if (!data.contains("linkedRelays"))
                return;

            if (data.contains("transmittedName"))
                tooltip.add(Component.translatable(MODID + ".jade.relay.transmitting", Component.translatable(data.getStringOr("transmittedName", ""))));
            else
                tooltip.add(Component.translatable(MODID + ".jade.relay.idle"));

            tooltip.add(Component.translatable(MODID + ".jade.relay.links",
                    data.getIntOr("linkedCollectors", 0), data.getIntOr("linkedStorages", 0), data.getIntOr("linkedRelays", 0)));
        }

        @Override
        public Identifier getUid()
        {
            return RAVENOUS_RELAY;
        }
    }
}
