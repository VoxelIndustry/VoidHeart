package net.voxelindustry.voidheart.compat.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameCoreTile;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameTile;
import net.voxelindustry.voidheart.common.energy.machine.MachineEnergyBalance;
import net.voxelindustry.voidheart.common.energy.machine.MachinePower;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Whether a linked portal is open or dark, read from its core whether the core or one of its frames is looked at.
 */
public class PortalCoreProvider implements IServerDataProvider<BlockAccessor>
{
    private static final Identifier PORTAL_CORE = Identifier.fromNamespaceAndPath(MODID, "portal_core");

    @Override
    public Identifier getUid()
    {
        return PORTAL_CORE;
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor)
    {
        var core = coreOf(accessor);
        if (core == null || !core.isLinked())
            return;

        tag.putBoolean("dark", core.isDark());
        tag.putInt("power", core.getPower().ordinal());
    }

    private static PortalFrameCoreTile coreOf(BlockAccessor accessor)
    {
        if (accessor.getBlockEntity() instanceof PortalFrameCoreTile core)
            return core;
        if (!(accessor.getBlockEntity() instanceof PortalFrameTile frame))
            return null;
        for (var pos : frame.getLinkedCores())
        {
            var core = accessor.getLevel().getBlockEntity(pos, VoidHeartTiles.PORTAL_FRAME_CORE);
            if (core.isPresent() && core.get().isLinked())
                return core.get();
        }
        return null;
    }

    public static class Client implements IBlockComponentProvider
    {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            var data = accessor.getServerData();
            if (!data.contains("dark"))
                return;

            if (data.getBooleanOr("dark", false))
                tooltip.add(Component.translatable(MODID + ".jade.portal.dark").withStyle(ChatFormatting.RED));
            else if (MachinePower.byOrdinal(data.getIntOr("power", 0)).isStarved())
                tooltip.add(Component.translatable(MODID + ".jade.portal.failing").withStyle(ChatFormatting.YELLOW));
            else
                tooltip.add(Component.translatable(MODID + ".jade.portal.open", MachineEnergyBalance.PORTAL_HOLD).withStyle(ChatFormatting.GRAY));
        }

        @Override
        public Identifier getUid()
        {
            return PORTAL_CORE;
        }
    }
}
