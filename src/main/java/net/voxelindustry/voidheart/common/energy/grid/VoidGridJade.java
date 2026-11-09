package net.voxelindustry.voidheart.common.energy.grid;

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
 * Jade hand-off for W1 Void grid, called through EnergyJade: a pylon's load against its cap, its field and its arcs.
 */
public final class VoidGridJade
{
    private static final Identifier AZURIUM_PYLON = Identifier.fromNamespaceAndPath(MODID, "azurium_pylon");

    private VoidGridJade()
    {
    }

    public static void register(IWailaCommonRegistration registration)
    {
        registration.registerBlockDataProvider(new Server(), AzuriumPylonTile.class);
    }

    public static void registerClient(IWailaClientRegistration registration)
    {
        registration.registerBlockComponent(new Client(), AzuriumPylonBlock.class);
    }

    private static final class Server implements IServerDataProvider<BlockAccessor>
    {
        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor)
        {
            if (!(accessor.getBlockEntity() instanceof AzuriumPylonTile pylon))
                return;
            var bundle = pylon.bundle();
            tag.putLong("cap", bundle.cap());
            tag.putInt("radius", bundle.radius());
            tag.putLong("throughput", pylon.throughput());
            tag.putInt("arcs", pylon.arcs().size());
            tag.putInt("fed", pylon.fed().size());
        }

        @Override
        public Identifier getUid()
        {
            return AZURIUM_PYLON;
        }
    }

    private static final class Client implements IBlockComponentProvider
    {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            var data = accessor.getServerData();
            if (!data.contains("cap"))
                return;

            long cap = data.getLongOr("cap", 0);
            if (cap <= 0)
            {
                tooltip.add(Component.translatable(MODID + ".jade.pylon.empty"));
                return;
            }
            tooltip.add(Component.translatable(MODID + ".jade.pylon.load", data.getLongOr("throughput", 0), cap));
            tooltip.add(Component.translatable(MODID + ".jade.pylon.field", data.getIntOr("radius", 0), data.getIntOr("arcs", 0), data.getIntOr("fed", 0)));
        }

        @Override
        public Identifier getUid()
        {
            return AZURIUM_PYLON;
        }
    }
}
