package net.voxelindustry.voidheart.common.energy.spike;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.voxelindustry.voidheart.common.ley.Ley;
import net.voxelindustry.voidheart.common.ley.TaintStage;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.callback.JadeRayTraceCallback;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Jade hand-off for W8 Spike, called through EnergyJade. The line says how much ley the spike bleeds into void energy, or why it bleeds none, then its
 * setting, the node's taint, and the Contravoid it gives. Looking at the tip shows the base.
 */
public final class LeySpikeJade
{
    private static final Identifier UID = Identifier.fromNamespaceAndPath(MODID, "azurium_spike");

    private LeySpikeJade()
    {
    }

    public static void register(IWailaCommonRegistration registration)
    {
        registration.registerBlockDataProvider(new Server(), AzuriumSpikeTile.class);
    }

    public static void registerClient(IWailaClientRegistration registration)
    {
        registration.registerBlockComponent(new Client(), AzuriumSpikeBlock.class);
        registration.addRayTraceCallback(new TipRedirect(registration));
    }

    public static class Server implements IServerDataProvider<BlockAccessor>
    {
        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor)
        {
            if (!(accessor.getBlockEntity() instanceof AzuriumSpikeTile spike) || !(accessor.getLevel() instanceof ServerLevel level))
                return;

            tag.putInt("setting", Math.round((float) LeySpikeMath.share(spike.setting()) * 100));
            tag.putDouble("flow", spike.lastFlow());
            tag.putDouble("ley", spike.averageLey());
            tag.putLong("output", Math.round(spike.averageOutput()));
            tag.putBoolean("idle", spike.isIdle());
            tag.putDouble("contravoid", spike.averageContravoid() * 20);
            var node = spike.node(level);
            if (node.isPresent())
            {
                tag.putFloat("taint", Ley.taint(level, node.getAsLong()));
                tag.putString("stage", Ley.stage(level, node.getAsLong()).name());
            }
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
            if (!data.contains("setting"))
                return;

            var onLattice = data.contains("stage");
            if (!onLattice)
                tooltip.add(Component.translatable(MODID + ".jade.spike.off_lattice"));
            else if (!data.getBooleanOr("idle", true))
                tooltip.add(Component.translatable(MODID + ".jade.spike.converting", format(data.getDoubleOr("ley", 0)), data.getLongOr("output", 0)));
            else
                tooltip.add(Component.translatable(MODID + ".jade.spike.idle"));

            tooltip.add(Component.translatable(MODID + ".jade.spike.setting", data.getIntOr("setting", 25), format(data.getDoubleOr("flow", 0))));
            if (!onLattice)
                return;

            var stage = TaintStage.valueOf(data.getStringOr("stage", TaintStage.CLEAN.name()));
            tooltip.add(Component.translatable(MODID + ".jade.spike.taint",
                    Component.translatable(MODID + ".jade.spike.stage." + stage.name().toLowerCase(Locale.ROOT)), String.format(Locale.ROOT, "%.0f", data.getFloatOr("taint", 0))));
            var contravoid = data.getDoubleOr("contravoid", 0);
            if (contravoid >= 0.05)
                tooltip.add(Component.translatable(MODID + ".jade.spike.contravoid", format(contravoid)));
        }

        private static String format(double value)
        {
            return String.format(Locale.ROOT, "%.1f", value);
        }

        @Override
        public Identifier getUid()
        {
            return UID;
        }
    }

    /**
     * The tip holds nothing itself, so looking at it shows the base below.
     */
    private record TipRedirect(IWailaClientRegistration registration) implements JadeRayTraceCallback
    {
        @Override
        public @Nullable Accessor<?> onRayTrace(HitResult hitResult, @Nullable Accessor<?> accessor, @Nullable Accessor<?> originalAccessor)
        {
            if (!(accessor instanceof BlockAccessor blockAccessor) || !(blockAccessor.getBlock() instanceof AzuriumSpikeBlock)
                    || blockAccessor.getBlockState().getValue(AzuriumSpikeBlock.PART) != AzuriumSpikeBlock.Part.TIP)
                return accessor;

            var base = blockAccessor.getPosition().below();
            var level = blockAccessor.getLevel();
            var state = level.getBlockState(base);
            var tile = level.getBlockEntity(base);
            if (!state.is(blockAccessor.getBlock()) || tile == null)
                return accessor;

            return registration.blockAccessor()
                    .from(blockAccessor)
                    .hit(new BlockHitResult(blockAccessor.getHitResult().getLocation(), blockAccessor.getSide(), base, false))
                    .blockState(state)
                    .blockEntity(tile)
                    .serversideRep(new ItemStack(state.getBlock()))
                    .build();
        }
    }
}
