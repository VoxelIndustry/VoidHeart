package net.voxelindustry.voidheart.common.energy.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundles;
import net.voxelindustry.voidheart.common.content.crystal.CrystalHabit;
import net.voxelindustry.voidheart.common.content.crystal.CrystalKind;
import net.voxelindustry.voidheart.common.content.crystal.HabitEffects;
import net.voxelindustry.voidheart.common.content.crystal.HabitShapedRecipe;
import java.util.Arrays;
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
 * core or any frame block, a socket's cell, a socket wall's cells and a large cell's charge.
 */
public final class RubrumJade
{
    private static final Identifier CONSTRUCT   = Identifier.fromNamespaceAndPath(MODID, "rubrum_construct");
    private static final Identifier CELL_SOCKET = Identifier.fromNamespaceAndPath(MODID, "cell_socket");
    private static final Identifier SOCKET_WALL = Identifier.fromNamespaceAndPath(MODID, "socket_wall");
    private static final Identifier LARGE_CELL  = Identifier.fromNamespaceAndPath(MODID, "large_rubrum_cell");

    private RubrumJade()
    {
    }

    public static void register(IWailaCommonRegistration registration)
    {
        var construct = new ConstructData();
        registration.registerBlockDataProvider(construct, RubrumConstructTile.class);
        registration.registerBlockDataProvider(construct, ConstructFrameBlock.class);
        registration.registerBlockDataProvider(new SocketData(), CellSocketTile.class);
        registration.registerBlockDataProvider(new WallData(), SocketWallTile.class);
        // On the block, so the upper half reads the lower half's tile too
        registration.registerBlockDataProvider(new LargeCellData(), LargeRubrumCellBlock.class);
    }

    public static void registerClient(IWailaClientRegistration registration)
    {
        var construct = new ConstructTooltip();
        registration.registerBlockComponent(construct, RubrumConstructBlock.class);
        registration.registerBlockComponent(construct, ConstructFrameBlock.class);
        registration.registerBlockComponent(new SocketTooltip(), CellSocketBlock.class);
        registration.registerBlockComponent(new WallTooltip(), SocketWallBlock.class);
        registration.registerBlockComponent(new LargeCellTooltip(), LargeRubrumCellBlock.class);
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
            tag.putBoolean("leaking", core.isLeaking());
            boolean active = core.charge().isActive();
            tag.putLong("rateIn", active ? core.maxRateIn() : core.sockets().rateIn());
            tag.putLong("rateOut", active ? core.maxRateOut() : core.sockets().rateOut());
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
            long rateIn = data.getLongOr("rateIn", 0L), rateOut = data.getLongOr("rateOut", 0L);
            // Searing and Still azurium set what goes in apart from what comes out
            if (rateIn == rateOut)
                tooltip.add(Component.translatable(MODID + ".jade.rubrum_construct.rate", rateIn));
            else
                tooltip.add(Component.translatable(MODID + ".jade.rubrum_construct.rates", rateIn, rateOut));
            tooltip.add(Component.translatable(MODID + ".jade.rubrum_construct.bundles", data.getIntOr("rubrum", 0), data.getIntOr("azurium", 0)));

            var flow = data.getFloatOr("flow", 0F);
            if (flow > 0.02F)
                tooltip.add(Component.translatable(MODID + ".jade.rubrum_construct.charging"));
            else if (flow < -0.02F)
                tooltip.add(Component.translatable(MODID + ".jade.rubrum_construct.discharging"));

            var bud = data.getFloatOr("bud", 0F);
            if (bud > 0)
                tooltip.add(Component.translatable(MODID + ".jade.rubrum_construct.bud", Math.round(bud * 100)));
            if (data.getBooleanOr("leaking", false))
                tooltip.add(Component.translatable(MODID + ".jade.rubrum_construct.leaking").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
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
            tag.putString("habit", CrystalBundles.habitOf(socket.getCell()).getSerializedName());
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
            var habit = Arrays.stream(CrystalHabit.values()).filter(value -> value.getSerializedName().equals(data.getStringOr("habit", ""))).findFirst()
                    .orElse(CrystalHabit.NONE);
            // A Still cell charges whether powered or not
            var way = CellHabits.way(habit, data.getBooleanOr("powered", false), 0);
            tooltip.add(Component.translatable(way == CellHabits.Way.CHARGE ? MODID + ".jade.cell_socket.charging" : MODID + ".jade.cell_socket.discharging"));
            if (HabitShapedRecipe.CARRIED.contains(habit))
            {
                tooltip.add(HabitEffects.name(habit));
                HabitEffects.of(habit, CrystalKind.RUBRUM).forEach(effect -> tooltip.add(effect.onBlock()));
            }
        }

        @Override
        public Identifier getUid()
        {
            return CELL_SOCKET;
        }
    }

    private static class WallData implements IServerDataProvider<BlockAccessor>
    {
        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor)
        {
            if (!(accessor.getBlockEntity() instanceof SocketWallTile wall))
                return;
            tag.putInt("cells", wall.cellCount());
            tag.putLong("charge", wall.totalCharge());
            // Habits let one wall charge some cells while others discharge, so each way is told apart
            var charging = false;
            var discharging = false;
            for (var slot = 0; slot < SocketWallGeometry.SLOTS; slot++)
            {
                var activity = wall.activity(slot);
                charging |= activity == CellSocketTile.Activity.CHARGING;
                discharging |= activity == CellSocketTile.Activity.DISCHARGING || activity == CellSocketTile.Activity.BURSTING;
            }
            tag.putBoolean("charging", charging);
            tag.putBoolean("discharging", discharging);
        }

        @Override
        public Identifier getUid()
        {
            return SOCKET_WALL;
        }
    }

    private static class WallTooltip implements IBlockComponentProvider
    {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            var data = accessor.getServerData();
            if (!data.contains("cells"))
                return;
            var cells = data.getIntOr("cells", 0);
            if (cells == 0)
            {
                tooltip.add(Component.translatable(MODID + ".jade.socket_wall.empty"));
                return;
            }
            tooltip.add(Component.translatable(MODID + ".jade.socket_wall.cells", cells, SocketWallGeometry.SLOTS));
            tooltip.add(Component.translatable(MODID + ".rubrum_cell.tooltip",
                    String.format("%,d", data.getLongOr("charge", 0L)), String.format("%,d", RubrumBalance.CELL_CAPACITY * cells)));
            if (data.getBooleanOr("charging", false))
                tooltip.add(Component.translatable(MODID + ".jade.socket_wall.charging"));
            if (data.getBooleanOr("discharging", false))
                tooltip.add(Component.translatable(MODID + ".jade.socket_wall.discharging"));
        }

        @Override
        public Identifier getUid()
        {
            return SOCKET_WALL;
        }
    }

    private static class LargeCellData implements IServerDataProvider<BlockAccessor>
    {
        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor)
        {
            var cell = LargeRubrumCellBlock.tileOf(accessor.getLevel(), accessor.getPosition(), accessor.getBlockState());
            if (cell == null)
                return;
            tag.putLong("charge", cell.charge());
            tag.putString("activity", cell.activity().name());
        }

        @Override
        public Identifier getUid()
        {
            return LARGE_CELL;
        }
    }

    private static class LargeCellTooltip implements IBlockComponentProvider
    {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            var data = accessor.getServerData();
            if (!data.contains("charge"))
                return;
            var charge = data.getLongOr("charge", 0L);
            tooltip.add(Component.translatable(MODID + ".large_rubrum_cell.tooltip",
                    String.format("%,d", charge), String.format("%,d", RubrumBalance.LARGE_CELL_CAPACITY)));
            var activity = data.getStringOr("activity", "");
            if (activity.equals(CellSocketTile.Activity.CHARGING.name()))
                tooltip.add(Component.translatable(MODID + ".jade.large_rubrum_cell.charging"));
            else if (activity.equals(CellSocketTile.Activity.DISCHARGING.name()))
                tooltip.add(Component.translatable(MODID + ".jade.large_rubrum_cell.discharging"));
            // Anything but charging lets the charge fade
            if (charge > 0 && !activity.equals(CellSocketTile.Activity.CHARGING.name()))
                tooltip.add(Component.translatable(MODID + ".jade.large_rubrum_cell.fading", RubrumBalance.LARGE_CELL_DECAY_PERCENT)
                        .withStyle(net.minecraft.ChatFormatting.DARK_RED));
        }

        @Override
        public Identifier getUid()
        {
            return LARGE_CELL;
        }
    }
}
