package net.voxelindustry.voidheart.common.energy.pearl;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.energy.machine.MachinePower;
import net.voxelindustry.voidheart.common.energy.superfluid.SuperfluidBalance;
import net.voxelindustry.voidheart.common.energy.superfluid.SuperfluidRings;
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
 * Jade hand-off for the hanging pearl, called through EnergyJade. It says how far the pearl has grown in words, never a percentage, how many pearls
 * reinforce it, and what it lacks if it waits.
 */
public final class PearlJade
{
    private static final Identifier UID = Identifier.fromNamespaceAndPath(MODID, "hanging_rubrum_pearl");

    private PearlJade()
    {
    }

    public static void register(IWailaCommonRegistration registration)
    {
        registration.registerBlockDataProvider(new Server(), HangingPearlTile.class);
    }

    public static void registerClient(IWailaClientRegistration registration)
    {
        registration.registerBlockComponent(new Client(), HangingPearlBlock.class);
    }

    private static Component line(String key, Object... args)
    {
        return Component.translatable(MODID + ".jade.hanging_rubrum_pearl." + key, args);
    }

    public static class Server implements IServerDataProvider<BlockAccessor>
    {
        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor)
        {
            if (!(accessor.getBlockEntity() instanceof HangingPearlTile pearl))
                return;

            tag.putInt("stage", pearl.growth().stage().ordinal());
            tag.putInt("choir", pearl.choir().size());
            tag.putBoolean("fed", pearl.isFed());
            tag.putBoolean("thirsty", pearl.isFieryShort());
            tag.putInt("power", pearl.power().ordinal());
            tag.putInt("superfluid", pearl.superfluid());
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
            if (!data.contains("stage"))
                return;

            var stage = PearlGrowth.Stage.byOrdinal(data.getIntOr("stage", 0));
            if (stage == PearlGrowth.Stage.MATURE)
            {
                var fill = SuperfluidRings.fill(data.getIntOr("superfluid", 0), SuperfluidBalance.PEARL_CAPACITY);
                if (fill == SuperfluidRings.Fill.EMPTY)
                    tooltip.add(line("stage.mature"));
                else
                {
                    tooltip.add(line("rings." + fill.getSerializedName()).copy().withStyle(ChatFormatting.AQUA));
                    tooltip.add(line("ringed"));
                }
                return;
            }
            tooltip.add(line("stage." + stage.name().toLowerCase(Locale.ROOT)));

            var choir = data.getIntOr("choir", 0);
            if (choir <= 0)
                tooltip.add(line("alone"));
            else if (choir + 1 >= PearlBalance.MAX_CHOIR)
                tooltip.add(line("full_choir", choir));
            else
                tooltip.add(line("choir", choir));

            if (data.getBooleanOr("thirsty", false))
                tooltip.add(line("thirsty"));
            var power = MachinePower.byOrdinal(data.getIntOr("power", 0));
            if (power == MachinePower.UNPOWERED)
                tooltip.add(line("unpowered"));
            else if (power == MachinePower.WEAK)
                tooltip.add(line("weak"));
        }

        @Override
        public Identifier getUid()
        {
            return UID;
        }
    }
}
