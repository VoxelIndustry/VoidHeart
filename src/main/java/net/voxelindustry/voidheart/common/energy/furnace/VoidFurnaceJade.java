package net.voxelindustry.voidheart.common.energy.furnace;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.energy.machine.MachinePower;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.config.IPluginConfig;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Jade hand-off for the Void Furnace, called through EnergyJade: its pace, why it waits if it does, and how much Fiery it holds in words.
 */
public final class VoidFurnaceJade
{
    private static final Identifier UID = Identifier.fromNamespaceAndPath(MODID, "void_furnace");

    private VoidFurnaceJade()
    {
    }

    public static void register(IWailaCommonRegistration registration)
    {
        registration.registerBlockDataProvider(new Server(), VoidFurnaceTile.class);
    }

    public static void registerClient(IWailaClientRegistration registration)
    {
        registration.registerBlockComponent(new Client(), VoidFurnaceBlock.class);
    }

    /**
     * @return 0 for none, 1 for under a tenth of its tank, 2 for under half, 3 for more
     */
    public static int fieryLine(int fiery)
    {
        if (fiery < VoidFurnaceBalance.FIERY_PER_ITEM)
            return 0;
        if (fiery * 10 < VoidFurnaceBalance.FIERY_TANK)
            return 1;
        return fiery * 2 < VoidFurnaceBalance.FIERY_TANK ? 2 : 3;
    }

    public static class Server implements IServerDataProvider<BlockAccessor>
    {
        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor)
        {
            if (!(accessor.getBlockEntity() instanceof VoidFurnaceTile furnace))
                return;
            tag.putBoolean("cooking", furnace.isCooking());
            tag.putBoolean("fast", furnace.isFast());
            tag.putInt("power", furnace.power().ordinal());
            tag.putInt("fiery", furnace.fiery());
            tag.putDouble("heat", furnace.heat());
            tag.putBoolean("officiant", furnace.getTicket() != null);
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
            if (!data.contains("cooking"))
                return;

            var power = MachinePower.byOrdinal(data.getIntOr("power", 0));
            if (!data.getBooleanOr("cooking", false))
                tooltip.add(Component.translatable(MODID + ".jade.void_furnace.idle"));
            else if (power == MachinePower.UNPOWERED)
                tooltip.add(Component.translatable(MODID + ".jade.void_furnace.unpowered"));
            else
            {
                var fast = data.getBooleanOr("fast", false);
                tooltip.add(Component.translatable(MODID + ".jade.void_furnace." + (fast ? "fiery" : "void"),
                        VoidFurnaceBalance.paceText(VoidFurnaceBalance.pace(data.getDoubleOr("heat", 0), fast))));
                if (power == MachinePower.WEAK)
                    tooltip.add(Component.translatable(MODID + ".jade.void_furnace.weak"));
            }
            tooltip.add(Component.translatable(MODID + ".jade.void_furnace.heat." + VoidFurnaceBalance.heatStage(data.getDoubleOr("heat", 0))));
            tooltip.add(Component.translatable(MODID + ".jade.void_furnace.fiery_held." + fieryLine(data.getIntOr("fiery", 0))));
            if (data.getBooleanOr("officiant", false))
                tooltip.add(Component.translatable(MODID + ".jade.void_furnace.officiant"));
        }

        @Override
        public Identifier getUid()
        {
            return UID;
        }
    }
}
