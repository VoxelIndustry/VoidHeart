package net.voxelindustry.voidheart.common.content.secondheart.ventricle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleCeremony.Ingredient;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleCeremony.Phase;
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
 * Jade for the Twin Ventricle: the cradle says what the rite waits for, the basins what they hold. No progress is shown, since the rite tells
 * its stage by sound and sight.
 */
public final class TwinVentricleJade
{
    private static final Identifier UID = Identifier.fromNamespaceAndPath(MODID, "twin_ventricle");

    private TwinVentricleJade()
    {
    }

    public static void register(IWailaCommonRegistration registration)
    {
        var server = new Server();
        registration.registerBlockDataProvider(server, HeartCradleTile.class);
        registration.registerBlockDataProvider(server, VentricleBasinTile.class);
    }

    public static void registerClient(IWailaClientRegistration registration)
    {
        var client = new Client();
        registration.registerBlockComponent(client, HeartCradleBlock.class);
        registration.registerBlockComponent(client, VentricleBasinBlock.class);
    }

    public static class Server implements IServerDataProvider<BlockAccessor>
    {
        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor)
        {
            if (accessor.getBlockEntity() instanceof VentricleBasinTile basin)
            {
                var called = basin.calledFor();
                tag.putString("calls", called == null ? "" : called.identifier().toString());
                tag.putString("held", basin.held() == EssenceRegistry.EMPTY ? "" : basin.held().identifier().toString());
                tag.putInt("amount", basin.amount());
                return;
            }
            if (!(accessor.getBlockEntity() instanceof HeartCradleTile cradle))
                return;
            var ceremony = cradle.ceremony();
            tag.putString("phase", ceremony.phase().name());
            tag.putBoolean("complete", cradle.isComplete());
            tag.putBoolean("running", cradle.isRunning());
            tag.putBoolean("itemsIn", ceremony.itemsComplete());
            for (var ingredient : Ingredient.values())
                tag.putInt("need_" + ingredient.name(), ceremony.stillNeeded(ingredient));
            var celebrant = ceremony.celebrant() == null ? null : accessor.getLevel().getPlayerByUUID(ceremony.celebrant());
            if (celebrant != null)
                tag.putString("celebrant", celebrant.getGameProfile().name());
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
            if (data.contains("calls"))
            {
                var calls = data.getStringOr("calls", "");
                var held = data.getStringOr("held", "");
                if (calls.isEmpty())
                    tooltip.add(Component.translatable(MODID + ".jade.ventricle_basin.no_walls"));
                else if (held.isEmpty())
                    tooltip.add(Component.translatable(MODID + ".jade.ventricle_basin.takes", aspect(calls)));
                else
                    tooltip.add(aspect(held).copy().append(Component.literal("  " + data.getIntOr("amount", 0) + " / " + VentricleBalance.BASIN_TANK)));
                return;
            }
            if (!data.contains("phase"))
                return;
            if (!data.getBooleanOr("complete", false))
                tooltip.add(Component.translatable(MODID + ".jade.heart_cradle.incomplete"));
            var phase = parse(data.getStringOr("phase", "IDLE"));
            boolean running = data.getBooleanOr("running", false);
            tooltip.add(switch (phase)
            {
                case IDLE -> Component.translatable(MODID + ".heart_cradle.idle");
                case OFFERING -> data.getBooleanOr("itemsIn", false) ? Component.translatable(MODID + ".heart_cradle.drinking")
                        : Component.translatable(MODID + ".jade.heart_cradle.offering");
                case KINDLING -> Component.translatable(MODID + (running ? ".heart_cradle.kindling" : ".heart_cradle.starved"));
                case BEATING -> Component.translatable(MODID + (running ? ".heart_cradle.beating" : ".heart_cradle.waiting_celebrant"));
            });
            if (phase == Phase.OFFERING)
                for (var ingredient : Ingredient.values())
                {
                    int needed = data.getIntOr("need_" + ingredient.name(), 0);
                    if (needed > 0)
                        tooltip.add(Component.translatable(MODID + ".heart_cradle.ingredient." + ingredient.name().toLowerCase(Locale.ROOT), needed));
                }
            if (data.contains("celebrant"))
                tooltip.add(Component.translatable(MODID + ".jade.heart_cradle.celebrant", data.getStringOr("celebrant", "")));
        }

        private static Phase parse(String name)
        {
            try
            {
                return Phase.valueOf(name);
            } catch (IllegalArgumentException unknown)
            {
                return Phase.IDLE;
            }
        }

        private static Component aspect(String id)
        {
            var type = EssenceRegistry.ESSENCE_REGISTRY.getValue(Identifier.parse(id));
            return type == null ? Component.literal(id) : Component.translatable(type.translatableName());
        }

        @Override
        public Identifier getUid()
        {
            return UID;
        }
    }
}
