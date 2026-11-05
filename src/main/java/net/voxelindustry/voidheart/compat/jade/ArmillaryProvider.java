package net.voxelindustry.voidheart.compat.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryChart;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryTile;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class ArmillaryProvider implements IServerDataProvider<BlockAccessor>
{
    private static final Identifier ARMILLARY = Identifier.fromNamespaceAndPath(MODID, "armillary");

    @Override
    public Identifier getUid()
    {
        return ARMILLARY;
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor)
    {
        if (!(accessor.getBlockEntity() instanceof ArmillaryTile armillary))
            return;

        var chart = armillary.getChart();
        tag.putBoolean("formed", armillary.isFormed());
        tag.putInt("phase", chart.getPhase().ordinal());
        tag.putInt("bubbles", chart.liveBubbles());
        tag.putInt("percent", chart.getSearchDuration() == 0 ? 0 : 100 * chart.getSearchTicks() / chart.getSearchDuration());
    }

    public static class Client implements IBlockComponentProvider
    {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            var data = accessor.getServerData();
            if (!data.contains("formed"))
                return;

            if (!data.getBooleanOr("formed", false))
            {
                tooltip.add(Component.translatable(MODID + ".jade.armillary.incomplete"));
                return;
            }

            var phase = ArmillaryChart.Phase.values()[data.getIntOr("phase", 0)];
            tooltip.add(switch (phase)
            {
                case SCANNING -> Component.translatable(MODID + ".jade.armillary.scanning", data.getIntOr("bubbles", 0));
                case SEARCHING -> Component.translatable(MODID + ".jade.armillary.searching", data.getIntOr("percent", 0));
                case REACHED -> Component.translatable(MODID + ".jade.armillary.reached");
                case CONNECTED -> Component.translatable(MODID + ".jade.armillary.connected");
            });
        }

        @Override
        public Identifier getUid()
        {
            return ARMILLARY;
        }
    }
}
