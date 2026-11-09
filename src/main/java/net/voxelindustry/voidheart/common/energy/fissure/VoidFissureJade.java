package net.voxelindustry.voidheart.common.energy.fissure;

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
 * Jade hand-off for W9 Void Fissure, called through EnergyJade: a fissure's width and what the network takes from it, the seep's reach, and the
 * Contravoid paid toward closing it.
 */
public final class VoidFissureJade
{
    private static final Identifier UID = Identifier.fromNamespaceAndPath(MODID, "void_fissure");

    private VoidFissureJade()
    {
    }

    public static void register(IWailaCommonRegistration registration)
    {
        registration.registerBlockDataProvider(new Server(), VoidFissureTile.class);
    }

    public static void registerClient(IWailaClientRegistration registration)
    {
        registration.registerBlockComponent(new Client(), VoidFissureBlock.class);
    }

    public static class Server implements IServerDataProvider<BlockAccessor>
    {
        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor)
        {
            if (!(accessor.getBlockEntity() instanceof VoidFissureTile fissure))
                return;
            long now = accessor.getLevel().getGameTime();
            tag.putBoolean("wound", fissure.isWound());
            tag.putInt("width", fissure.width());
            tag.putLong("output", Math.round(fissure.averageOutput()));
            tag.putInt("seep", Math.round(fissure.seepRadius(now)));
            tag.putInt("seepTarget", Math.round(SeepMath.targetRadius(fissure.size())));
            tag.putInt("paid", fissure.paid());
            tag.putInt("cost", fissure.cost());
            tag.putBoolean("closing", fissure.isClosing());
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
            if (!data.contains("cost"))
                return;

            if (data.getBooleanOr("closing", false))
            {
                tooltip.add(Component.translatable(MODID + ".jade.void_fissure.closing"));
                return;
            }
            if (!data.getBooleanOr("wound", false))
                tooltip.add(Component.translatable(MODID + ".jade.void_fissure.width", data.getIntOr("width", 1), data.getLongOr("output", 0)));
            else
                tooltip.add(Component.translatable(MODID + ".jade.void_wound.dead"));
            tooltip.add(Component.translatable(MODID + ".jade.void_fissure.seep", data.getIntOr("seep", 0), data.getIntOr("seepTarget", 0)));
            tooltip.add(Component.translatable(MODID + ".jade.void_fissure.contravoid", data.getIntOr("paid", 0), data.getIntOr("cost", 0)));
        }

        @Override
        public Identifier getUid()
        {
            return UID;
        }
    }
}
