package net.voxelindustry.voidheart.common.content.crystal;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.config.IPluginConfig;

import java.util.Arrays;
import java.util.Locale;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Jade for the Crystal Cradle: what it grows, the habit it would come out with now, and what feeds it. No progress number: the crystal shows how far it is.
 */
public final class CrystalJade
{
    private static final Identifier CRADLE = Identifier.fromNamespaceAndPath(MODID, "crystal_cradle");
    private static final Identifier BUNDLE = Identifier.fromNamespaceAndPath(MODID, "crystal_bundle");

    private CrystalJade()
    {
    }

    public static void register(IWailaCommonRegistration registration)
    {
        registration.registerBlockDataProvider(new Server(), CrystalCradleTile.class);
    }

    public static void registerClient(IWailaClientRegistration registration)
    {
        registration.registerBlockComponent(new Client(), CrystalCradleBlock.class);
        registration.registerBlockComponent(new BundleClient(), CrystalBundleBlock.class);
    }

    /**
     * A placed bundle's habit and what it changes there, and whether Scoured rubrum has drawn any taint in yet. All of it is in the block state.
     */
    private static final class BundleClient implements IBlockComponentProvider
    {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            var state = accessor.getBlockState();
            var habit = CrystalBundles.habitOf(state);
            var kind = CrystalBundles.kindOfBundle(state.getBlock());
            if (kind == null)
                return;
            if (habit == CrystalHabit.PLAIN)
            {
                tooltip.add(Component.translatable(habit.translationKey()).withStyle(ChatFormatting.DARK_GRAY));
                return;
            }
            if (!habit.isHabit())
                return;
            tooltip.add(HabitEffects.name(habit));
            HabitEffects.of(habit, kind).forEach(effect -> tooltip.add(effect.onBlock()));
            if (CrystalBundleBlock.soaks(state) && CrystalBundles.soakOf(state) > 0)
                tooltip.add(Component.translatable(MODID + ".habit.scoured.soaked").withStyle(ChatFormatting.GRAY));
        }

        @Override
        public Identifier getUid()
        {
            return BUNDLE;
        }
    }

    private static final class Server implements IServerDataProvider<BlockAccessor>
    {
        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor)
        {
            if (!(accessor.getBlockEntity() instanceof CrystalCradleTile cradle) || !(accessor.getLevel() instanceof ServerLevel level))
                return;
            var growth = cradle.growth();
            var target = growth.target();
            if (growth.kind() == null || target == null)
            {
                tag.putBoolean("empty", true);
                return;
            }
            tag.putString("kind", growth.kind().getSerializedName());
            tag.putString("size", target.name().toLowerCase(Locale.ROOT));
            tag.putInt("waiting", growth.worth() - target.worth());
            tag.putBoolean("growing", growth.isGrowing());
            tag.putString("habit", growth.habit(CradleResearch.settles(level.getServer(), cradle.owner())).getSerializedName());
            tag.putFloat("supply", cradle.supply());
            tag.putString("feed", cradle.feed().getSerializedName());
            tag.putString("source", cradle.source().getSerializedName());
        }

        @Override
        public Identifier getUid()
        {
            return CRADLE;
        }
    }

    private static final class Client implements IBlockComponentProvider
    {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            var data = accessor.getServerData();
            if (data.getBooleanOr("empty", false))
            {
                tooltip.add(Component.translatable(MODID + ".jade.cradle.empty"));
                return;
            }
            if (!data.contains("kind"))
                return;

            var bundle = Component.translatable(MODID + ".crystal." + data.getStringOr("size", "small"),
                    Component.translatable(MODID + ".crystal." + data.getStringOr("kind", "azurium")));
            boolean growing = data.getBooleanOr("growing", false);
            tooltip.add(Component.translatable(MODID + (growing ? ".jade.cradle.growing" : ".jade.cradle.grown"), bundle));
            int waiting = data.getIntOr("waiting", 0);
            if (waiting > 0)
                tooltip.add(Component.translatable(MODID + ".jade.cradle.waiting", waiting).withStyle(ChatFormatting.GRAY));

            var habit = habit(data.getStringOr("habit", ""));
            tooltip.add(Component.translatable(MODID + ".jade.cradle.habit",
                    habit.isHabit() ? HabitEffects.name(habit) : Component.translatable(MODID + ".jade.cradle.plain")));

            if (!growing)
                return;
            switch (data.getStringOr("source", "idle"))
            {
                case "seep" -> tooltip.add(Component.translatable(MODID + ".jade.cradle.fed.seep"));
                case "scouring" -> tooltip.add(Component.translatable(MODID + ".jade.cradle.scouring"));
                case "breached" -> tooltip.add(Component.translatable(MODID + ".jade.cradle.breached").withStyle(ChatFormatting.RED));
                case "void" ->
                {
                    var feed = habit(data.getStringOr("feed", "plain"));
                    tooltip.add(Component.translatable(MODID + switch (feed)
                    {
                        case DEEP -> ".jade.cradle.fed.void_deep";
                        case FOULED -> ".jade.cradle.fed.void_fouled";
                        default -> ".jade.cradle.fed.void_plain";
                    }));
                }
                default ->
                {
                    if (data.getFloatOr("supply", 0) <= 0)
                        tooltip.add(Component.translatable(MODID + ".jade.cradle.unfed").withStyle(ChatFormatting.RED));
                    else
                        tooltip.add(Component.translatable(MODID + ".jade.cradle.fed." + habit(data.getStringOr("feed", "plain")).getSerializedName()));
                }
            }
        }

        private static CrystalHabit habit(String name)
        {
            return Arrays.stream(CrystalHabit.values()).filter(value -> value.getSerializedName().equals(name)).findFirst().orElse(CrystalHabit.PLAIN);
        }

        @Override
        public Identifier getUid()
        {
            return CRADLE;
        }
    }
}
