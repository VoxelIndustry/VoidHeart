package net.voxelindustry.voidheart.common.content.works;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.voxelindustry.voidheart.common.content.hoard.PocketHoard;
import net.voxelindustry.voidheart.common.util.ProfileUtil;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Who an Officiant works for, the station it serves, and what it is doing: idle, laying a craft out, or waiting on its station.
 */
public final class WorksJade
{
    private static final Identifier UID = Identifier.fromNamespaceAndPath(MODID, "officiant");

    private WorksJade()
    {
    }

    public static void register(IWailaCommonRegistration registration)
    {
        registration.registerBlockDataProvider(new Server(), OfficiantTile.class);
    }

    public static void registerClient(IWailaClientRegistration registration)
    {
        registration.registerBlockComponent(new Client(), OfficiantBlock.class);
    }

    private static final class Server implements IServerDataProvider<BlockAccessor>
    {
        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor)
        {
            if (!(accessor.getBlockEntity() instanceof OfficiantTile officiant) || !(accessor.getLevel() instanceof ServerLevel level))
                return;
            var owner = officiant.getOwner();
            if (owner != null)
                ProfileUtil.cachedName(level.getServer(), owner).ifPresent(name -> tag.putString("owner", name));

            var kind = officiant.stationKind();
            tag.putString("station", kind == null ? "" : kind.name().toLowerCase(Locale.ROOT));
            var claim = officiant.getClaim();
            if (claim == null || owner == null)
                return;
            var recipes = WorksCatalogues.of(level.getServer(), owner, PocketHoard.get(level.getServer()).of(owner)).byId(claim.recipe());
            tag.putString("making", recipes.isEmpty() ? claim.recipe() : recipes.getFirst().outputs().getFirst().item().toStack().getHoverName().getString());
            if (officiant.isLaying())
            {
                tag.putInt("step", officiant.layingStep());
                tag.putInt("steps", officiant.layingSteps());
            }
        }

        @Override
        public Identifier getUid()
        {
            return UID;
        }
    }

    private static final class Client implements IBlockComponentProvider
    {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            var data = accessor.getServerData();
            if (!data.contains("station"))
                return;
            data.getString("owner").ifPresent(name -> tooltip.add(Component.translatable(MODID + ".jade.officiant.owner", name).withStyle(ChatFormatting.GRAY)));
            var station = data.getStringOr("station", "");
            if (station.isEmpty())
            {
                tooltip.add(Component.translatable(MODID + ".officiant.no_station").withStyle(ChatFormatting.YELLOW));
                return;
            }
            var stationName = Component.translatable(MODID + ".works.station." + station);
            var making = data.getString("making");
            if (making.isEmpty())
                tooltip.add(Component.translatable(MODID + ".jade.officiant.idle", stationName).withStyle(ChatFormatting.GRAY));
            else if (data.contains("step"))
                tooltip.add(Component.translatable(MODID + ".jade.officiant.laying", making.get(), data.getIntOr("step", 0), data.getIntOr("steps", 0)));
            else
                tooltip.add(Component.translatable(MODID + ".jade.officiant.working", making.get(), stationName));
        }

        @Override
        public Identifier getUid()
        {
            return UID;
        }
    }
}
