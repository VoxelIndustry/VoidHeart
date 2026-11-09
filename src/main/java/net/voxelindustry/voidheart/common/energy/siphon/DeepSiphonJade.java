package net.voxelindustry.voidheart.common.energy.siphon;

import net.minecraft.nbt.CompoundTag;
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
 * Jade hand-off for W11 Deep siphon, called through EnergyJade. The line says what it draws, or why it draws nothing, then how many mobs its notice
 * would bring.
 */
public final class DeepSiphonJade
{
    private static final Identifier UID = Identifier.fromNamespaceAndPath(MODID, "deep_siphon");

    private DeepSiphonJade()
    {
    }

    public static void register(IWailaCommonRegistration registration)
    {
        registration.registerBlockDataProvider(new Server(), DeepSiphonTile.class);
    }

    public static void registerClient(IWailaClientRegistration registration)
    {
        registration.registerBlockComponent(new Client(), DeepSiphonBlock.class);
    }

    public static class Server implements IServerDataProvider<BlockAccessor>
    {
        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor)
        {
            if (!(accessor.getBlockEntity() instanceof DeepSiphonTile siphon))
                return;

            tag.putInt("status", siphon.status().ordinal());
            tag.putLong("output", Math.round(siphon.averageOutput()));
            tag.putInt("notice", siphon.notice().mobs());
        }

        @Override
        public Identifier getUid()
        {
            return UID;
        }
    }

    public static class Client implements IBlockComponentProvider
    {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            var data = accessor.getServerData();
            if (!data.contains("status"))
                return;

            var status = SiphonStatus.byOrdinal(data.getIntOr("status", 0));
            if (status == SiphonStatus.DRAWING)
                tooltip.add(Component.translatable(MODID + ".jade.siphon.drawing", data.getLongOr("output", 0)));
            else
                tooltip.add(Component.translatable(MODID + ".jade.siphon." + status.key()));

            int notice = data.getIntOr("notice", 0);
            if (notice > 0)
                tooltip.add(Component.translatable(MODID + ".jade.siphon.notice", notice));
        }

        @Override
        public Identifier getUid()
        {
            return UID;
        }
    }
}
