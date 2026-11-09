package net.voxelindustry.voidheart.compat.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryChart;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryRiftTile;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryTile;
import net.voxelindustry.voidheart.common.energy.machine.MachineEnergy;
import net.voxelindustry.voidheart.common.energy.machine.MachinePower;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.common.energy.machine.MachineEnergyBalance.*;

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
        tag.putBoolean("daedalus", chart.targetsStrata());
        tag.putInt("percent", chart.getSearchDuration() == 0 ? 0 : 100 * chart.getSearchTicks() / chart.getSearchDuration());
        tag.putInt("power", armillary.getPower().ordinal());
        tag.putInt("charge", MachineEnergy.percent(armillary.getCharge(), armillary.openingCost()));
        tag.putBoolean("holds", armillary.getDaedalusConnection() != ArmillaryRiftTile.NONE);
        tag.putInt("severedIn", armillary.secondsBeforeSevered());
        tag.putBoolean("siphon", armillary.isSiphonFlowing());
    }

    /**
     * Why the rings wait, or what they draw.
     */
    private static void appendPower(ITooltip tooltip, CompoundTag data, ArmillaryChart.Phase phase)
    {
        var power = MachinePower.byOrdinal(data.getIntOr("power", 0));
        switch (phase)
        {
            case SEARCHING ->
            {
                if (power == MachinePower.UNPOWERED)
                    tooltip.add(Component.translatable(MODID + ".jade.armillary.search_unpowered").withStyle(ChatFormatting.RED));
                else if (power == MachinePower.WEAK)
                    tooltip.add(Component.translatable(MODID + ".jade.armillary.search_weak").withStyle(ChatFormatting.YELLOW));
                else
                    tooltip.add(Component.translatable(MODID + ".jade.machine.draw", data.getBooleanOr("daedalus", false) ? DAEDALUS_SEARCH : ARMILLARY_SEARCH)
                            .withStyle(ChatFormatting.GRAY));
            }
            case REACHED ->
            {
                int charge = data.getIntOr("charge", 0);
                if (charge >= 100)
                    tooltip.add(Component.translatable(MODID + ".jade.armillary.charged").withStyle(ChatFormatting.GREEN));
                else if (power == MachinePower.UNPOWERED)
                    tooltip.add(Component.translatable(MODID + ".jade.armillary.charge_unpowered", charge).withStyle(ChatFormatting.RED));
                else
                    tooltip.add(Component.translatable(MODID + ".jade.armillary.charging", charge).withStyle(ChatFormatting.YELLOW));
            }
            case CONNECTED ->
            {
                if (!data.getBooleanOr("holds", false))
                    return;
                int severedIn = data.getIntOr("severedIn", -1);
                if (severedIn >= 0)
                    tooltip.add(Component.translatable(MODID + ".jade.armillary.hold_starved", severedIn).withStyle(ChatFormatting.RED));
                else
                    tooltip.add(Component.translatable(MODID + ".jade.machine.draw", DAEDALUS_HOLD).withStyle(ChatFormatting.GRAY));
                if (data.getBooleanOr("siphon", false))
                    tooltip.add(Component.translatable(MODID + ".jade.armillary.siphon").withStyle(ChatFormatting.AQUA));
            }
            default ->
            {
            }
        }
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
                case CONNECTED -> Component.translatable(MODID + (data.getBooleanOr("daedalus", false) ? ".jade.armillary.connected_daedalus" : ".jade.armillary.connected"));
            });
            appendPower(tooltip, data, phase);
        }

        @Override
        public Identifier getUid()
        {
            return ARMILLARY;
        }
    }
}
