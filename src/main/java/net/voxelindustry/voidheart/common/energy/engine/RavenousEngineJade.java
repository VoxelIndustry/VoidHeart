package net.voxelindustry.voidheart.common.energy.engine;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Jade hand-off for W5 Ravenous engine, called through EnergyJade. The line says what burns and how much the network takes, or why nothing does, then
 * the essence held. Looking at the chamber shows the maw.
 */
public final class RavenousEngineJade
{
    private static final Identifier UID = Identifier.fromNamespaceAndPath(MODID, "ravenous_engine");

    private RavenousEngineJade()
    {
    }

    public static void register(IWailaCommonRegistration registration)
    {
        registration.registerBlockDataProvider(new Server(), RavenousEngineTile.class);
    }

    public static void registerClient(IWailaClientRegistration registration)
    {
        registration.registerBlockComponent(new Client(), RavenousEngineBlock.class);
        registration.addRayTraceCallback(new ChamberRedirect(registration));
    }

    public static class Server implements IServerDataProvider<BlockAccessor>
    {
        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor)
        {
            if (!(accessor.getBlockEntity() instanceof RavenousEngineTile engine))
                return;

            var amounts = engine.amounts();
            for (var fuel : EngineFuel.values())
                tag.putInt("held_" + fuel.name(), amounts[fuel.ordinal()]);
            tag.putInt("tank", RavenousEngineBalance.TANK);
            if (engine.shownFuel() != null)
                tag.putString("burning", engine.shownFuel().name());
            tag.putLong("output", Math.round(engine.averageOutput()));
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
            if (!data.contains("tank"))
                return;

            var empty = true;
            for (var fuel : EngineFuel.values())
                empty &= data.getIntOr("held_" + fuel.name(), 0) == 0;

            var burning = data.getStringOr("burning", "");
            if (!burning.isEmpty())
                tooltip.add(Component.translatable(MODID + ".jade.engine.burning", aspectName(EngineFuel.valueOf(burning)), data.getLongOr("output", 0)));
            else if (empty)
                tooltip.add(Component.translatable(MODID + ".jade.engine.empty"));
            else
                tooltip.add(Component.translatable(MODID + ".jade.engine.idle"));

            for (var fuel : EngineFuel.values())
            {
                var held = data.getIntOr("held_" + fuel.name(), 0);
                if (held > 0)
                    tooltip.add(aspectName(fuel).copy().append(Component.literal("  " + held + " / " + data.getIntOr("tank", 0))));
            }
        }

        private static Component aspectName(EngineFuel fuel)
        {
            return Component.translatable(RavenousEngineTile.essenceOf(fuel).translatableName());
        }

        @Override
        public Identifier getUid()
        {
            return UID;
        }
    }

    /**
     * The chamber holds nothing itself, so looking at it shows the maw below.
     */
    private record ChamberRedirect(IWailaClientRegistration registration) implements JadeRayTraceCallback
    {
        @Override
        public @Nullable Accessor<?> onRayTrace(HitResult hitResult, @Nullable Accessor<?> accessor, @Nullable Accessor<?> originalAccessor)
        {
            if (!(accessor instanceof BlockAccessor blockAccessor) || !(blockAccessor.getBlock() instanceof RavenousEngineBlock)
                    || blockAccessor.getBlockState().getValue(RavenousEngineBlock.PART) != RavenousEngineBlock.Part.CHAMBER)
                return accessor;

            var maw = blockAccessor.getPosition().below();
            var level = blockAccessor.getLevel();
            var state = level.getBlockState(maw);
            var tile = level.getBlockEntity(maw);
            if (!state.is(blockAccessor.getBlock()) || tile == null)
                return accessor;

            return registration.blockAccessor()
                    .from(blockAccessor)
                    .hit(new BlockHitResult(blockAccessor.getHitResult().getLocation(), blockAccessor.getSide(), maw, false))
                    .blockState(state)
                    .blockEntity(tile)
                    .serversideRep(new ItemStack(state.getBlock()))
                    .build();
        }
    }
}
