package net.voxelindustry.voidheart.common.energy.storage;

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
 * Jade hand-off for W2 Rubrum storage, called through EnergyJade: a construct's charge, rate, bundles and bud from its
 * core or any frame block, and a socket's cell.
 */
public final class RubrumJade
{
    private static final Identifier CONSTRUCT   = Identifier.fromNamespaceAndPath(MODID, "rubrum_construct");
    private static final Identifier CELL_SOCKET = Identifier.fromNamespaceAndPath(MODID, "cell_socket");

    private RubrumJade()
    {
    }

    public static void register(IWailaCommonRegistration registration)
    {
        var construct = new ConstructData();
        registration.registerBlockDataProvider(construct, RubrumConstructTile.class);
        registration.registerBlockDataProvider(construct, ConstructFrameBlock.class);
        registration.registerBlockDataProvider(new SocketData(), CellSocketTile.class);
    }

    public static void registerClient(IWailaClientRegistration registration)
    {
        var construct = new ConstructTooltip();
        registration.registerBlockComponent(construct, RubrumConstructBlock.class);
        registration.registerBlockComponent(construct, ConstructFrameBlock.class);
        registration.registerBlockComponent(new SocketTooltip(), CellSocketBlock.class);
    }

    private static class ConstructData implements IServerDataProvider<BlockAccessor>
    {
        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor)
        {
            var core = accessor.getBlockEntity() instanceof RubrumConstructTile tile ? tile : ConstructInteraction.coreOf(accessor.getLevel(), accessor.getPosition());
            if (core == null)
                return;
            tag.putBoolean("formed", core.isFormed());
            tag.putBoolean("tooManyBundles", core.problem() == RubrumConstructTile.Problem.TOO_MANY_BUNDLES);
            tag.putLong("stored", core.stored());
            tag.putLong("capacity", core.capacity());
            tag.putLong("rate", core.charge().isActive() ? core.maxRate() : core.sockets().rate());
            tag.putInt("rubrum", core.sockets().count(true));
            tag.putInt("azurium", core.sockets().count(false));
            tag.putFloat("flow", core.flow());
            tag.putFloat("bud", core.budding().share());
        }

        @Override
        public Identifier getUid()
        {
            return CONSTRUCT;
        }
    }

    private static class ConstructTooltip implements IBlockComponentProvider
    {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            var data = accessor.getServerData();
            if (!data.contains("stored"))
                return;

            if (!data.getBooleanOr("formed", false))
                tooltip.add(Component.translatable(data.getBooleanOr("tooManyBundles", false)
                        ? MODID + ".rubrum_construct.too_many_bundles" : MODID + ".jade.rubrum_construct.loose"));
            tooltip.add(Component.translatable(MODID + ".jade.rubrum_construct.charge",
                    String.format("%,d", data.getLongOr("stored", 0L)), String.format("%,d", data.getLongOr("capacity", 0L))));
            tooltip.add(Component.translatable(MODID + ".jade.rubrum_construct.rate", data.getLongOr("rate", 0L)));
            tooltip.add(Component.translatable(MODID + ".jade.rubrum_construct.bundles", data.getIntOr("rubrum", 0), data.getIntOr("azurium", 0)));

            var flow = data.getFloatOr("flow", 0F);
            if (flow > 0.02F)
                tooltip.add(Component.translatable(MODID + ".jade.rubrum_construct.charging"));
            else if (flow < -0.02F)
                tooltip.add(Component.translatable(MODID + ".jade.rubrum_construct.discharging"));

            var bud = data.getFloatOr("bud", 0F);
            if (bud > 0)
                tooltip.add(Component.translatable(MODID + ".jade.rubrum_construct.bud", Math.round(bud * 100)));
        }

        @Override
        public Identifier getUid()
        {
            return CONSTRUCT;
        }
    }

    private static class SocketData implements IServerDataProvider<BlockAccessor>
    {
        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor)
        {
            if (!(accessor.getBlockEntity() instanceof CellSocketTile socket))
                return;
            tag.putBoolean("hasCell", !socket.getCell().isEmpty());
            tag.putLong("charge", RubrumCellItem.charge(socket.getCell()));
            tag.putBoolean("powered", accessor.getLevel().hasNeighborSignal(accessor.getPosition()));
            tag.putString("activity", socket.activity().name());
        }

        @Override
        public Identifier getUid()
        {
            return CELL_SOCKET;
        }
    }

    private static class SocketTooltip implements IBlockComponentProvider
    {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            var data = accessor.getServerData();
            if (!data.contains("hasCell"))
                return;
            if (!data.getBooleanOr("hasCell", false))
            {
                tooltip.add(Component.translatable(MODID + ".jade.cell_socket.empty"));
                return;
            }
            tooltip.add(Component.translatable(MODID + ".rubrum_cell.tooltip",
                    String.format("%,d", data.getLongOr("charge", 0L)), String.format("%,d", RubrumBalance.CELL_CAPACITY)));
            tooltip.add(Component.translatable(data.getBooleanOr("powered", false)
                    ? MODID + ".jade.cell_socket.charging" : MODID + ".jade.cell_socket.discharging"));
        }

        @Override
        public Identifier getUid()
        {
            return CELL_SOCKET;
        }
    }
}
