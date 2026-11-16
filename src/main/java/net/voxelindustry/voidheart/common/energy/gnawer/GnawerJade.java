package net.voxelindustry.voidheart.common.energy.gnawer;

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
 * Jade hand-off for the Gnawer, called through EnergyJade. It says whether it keeps or erases, how far down its box it has eaten in words, never a
 * percentage, why it waits if it does, and the pickaxe it was lent.
 */
public final class GnawerJade
{
    private static final Identifier UID = Identifier.fromNamespaceAndPath(MODID, "gnawer");

    /**
     * Progress at which each line starts, the first for a box just begun.
     */
    private static final float[] PROGRESS_STARTS = {0, 0.2F, 0.45F, 0.7F, 0.9F};

    private GnawerJade()
    {
    }

    public static void register(IWailaCommonRegistration registration)
    {
        registration.registerBlockDataProvider(new Server(), GnawerTile.class);
    }

    public static void registerClient(IWailaClientRegistration registration)
    {
        registration.registerBlockComponent(new Client(), GnawerBlock.class);
    }

    public static int progressLine(float progress)
    {
        var line = 0;
        for (int i = 1; i < PROGRESS_STARTS.length; i++)
        {
            if (progress >= PROGRESS_STARTS[i])
                line = i;
        }
        return line;
    }

    public static class Server implements IServerDataProvider<BlockAccessor>
    {
        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor)
        {
            if (!(accessor.getBlockEntity() instanceof GnawerTile gnawer))
                return;

            tag.putBoolean("box", gnawer.box() != null);
            tag.putBoolean("finished", gnawer.isFinished());
            tag.putFloat("progress", gnawer.progress());
            tag.putInt("power", gnawer.power().ordinal());
            tag.putBoolean("refused", GnawerTile.refusesToRun(gnawer.getLevel()));
            if (!gnawer.pickaxe().isEmpty())
                tag.putString("pickaxe", gnawer.pickaxe().getHoverName().getString());
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
            if (!data.contains("box"))
                return;

            var erase = accessor.getBlockState().getValue(GnawerBlock.ERASE);
            tooltip.add(Component.translatable(MODID + ".jade.gnawer." + (erase ? "erase" : "keep")));

            if (data.getBooleanOr("refused", false))
                tooltip.add(Component.translatable(MODID + ".jade.gnawer.refused"));
            else if (data.getBooleanOr("box", false))
            {
                tooltip.add(Component.translatable(MODID + ".jade.gnawer.progress." + progressLine(data.getFloatOr("progress", 0))));
                if (MachinePower.byOrdinal(data.getIntOr("power", 0)) == MachinePower.UNPOWERED)
                    tooltip.add(Component.translatable(MODID + ".jade.gnawer.unpowered"));
                else if (MachinePower.byOrdinal(data.getIntOr("power", 0)) == MachinePower.WEAK)
                    tooltip.add(Component.translatable(MODID + ".jade.gnawer.weak"));
            }
            else if (data.getBooleanOr("finished", false))
                tooltip.add(Component.translatable(MODID + ".jade.gnawer.finished"));
            else
                tooltip.add(Component.translatable(MODID + ".jade.gnawer.no_box"));

            var pickaxe = data.getStringOr("pickaxe", "");
            if (!pickaxe.isEmpty())
                tooltip.add(Component.translatable(MODID + ".jade.gnawer.pickaxe", pickaxe));
        }

        @Override
        public Identifier getUid()
        {
            return UID;
        }
    }
}
