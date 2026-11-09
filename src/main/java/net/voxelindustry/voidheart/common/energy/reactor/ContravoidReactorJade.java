package net.voxelindustry.voidheart.common.energy.reactor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorCycle.Cause;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorCycle.Phase;
import org.jspecify.annotations.Nullable;
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
 * Jade hand-off for W10 Contravoid reactor, called through EnergyJade. The core and each intake say what the reactor is doing, or why it won't light,
 * then what fuel each intake holds. Looking at the containment shows the core.
 */
public final class ContravoidReactorJade
{
    private static final Identifier UID = Identifier.fromNamespaceAndPath(MODID, "contravoid_reactor");

    private ContravoidReactorJade()
    {
    }

    public static void register(IWailaCommonRegistration registration)
    {
        // One instance for both, since Jade keys providers by id
        var server = new Server();
        registration.registerBlockDataProvider(server, ContravoidReactorTile.class);
        registration.registerBlockDataProvider(server, ReactorIntakeTile.class);
    }

    public static void registerClient(IWailaClientRegistration registration)
    {
        var client = new Client();
        registration.registerBlockComponent(client, ContravoidReactorCoreBlock.class);
        registration.registerBlockComponent(client, ReactorIntakeBlock.class);
        registration.addRayTraceCallback(new ContainmentRedirect(registration));
    }

    public static class Server implements IServerDataProvider<BlockAccessor>
    {
        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor)
        {
            var level = accessor.getLevel();
            ContravoidReactorTile core;
            if (accessor.getBlockEntity() instanceof ReactorIntakeTile intake)
            {
                tag.putString("own", intake.held() == EssenceRegistry.EMPTY ? "" : intake.held().identifier().toString());
                tag.putInt("ownAmount", intake.amount());
                var wound = intake.woundBeside();
                if (wound != null)
                {
                    tag.putInt("woundPaid", wound.paid());
                    tag.putInt("woundCost", wound.cost());
                    tag.putBoolean("woundClosing", wound.isClosing());
                }
                core = ContravoidReactorTile.coreAround(level, accessor.getPosition());
            }
            else
                core = accessor.getBlockEntity() instanceof ContravoidReactorTile tile ? tile : null;

            tag.putInt("tank", ReactorBalance.INTAKE_TANK);
            if (core == null)
                return;

            var phase = core.phase();
            tag.putString("phase", phase.name());
            tag.putInt("missing", phase == Phase.COLD ? core.missing(level).size() : 0);
            tag.putBoolean("powered", core.isPowered());
            tag.putInt("charge", (int) (core.cycle().charge() * 100 / ReactorBalance.IGNITION));
            tag.putLong("output", Math.round(core.averageOutput()));
            tag.putDouble("burn", core.averageBurn());
            tag.putInt("countdown", core.cycle().countdown());
            tag.putInt("causes", core.cycle().causes());
            var intakes = core.intakes();
            for (var side = 0; side < intakes.size(); side++)
            {
                var intake = intakes.get(side);
                if (intake != null && intake.amount() > 0)
                {
                    tag.putString("intake" + side, intake.held().identifier().toString());
                    tag.putInt("intake" + side + "Amount", intake.amount());
                }
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
            if (!data.contains("tank"))
                return;
            var tank = data.getIntOr("tank", 0);

            if (data.contains("woundCost"))
            {
                tooltip.add(Component.translatable(MODID + ".jade.reactor.feeds_wound"));
                tooltip.add(data.getBooleanOr("woundClosing", false) ? Component.translatable(MODID + ".jade.void_fissure.closing")
                        : Component.translatable(MODID + ".jade.void_fissure.contravoid", data.getIntOr("woundPaid", 0), data.getIntOr("woundCost", 0)));
                return;
            }
            if (data.contains("own"))
            {
                var own = data.getStringOr("own", "");
                tooltip.add(own.isEmpty() ? Component.translatable(MODID + ".jade.reactor.intake_empty")
                        : aspect(own).copy().append(Component.literal("  " + data.getIntOr("ownAmount", 0) + " / " + tank)));
            }
            if (!data.contains("phase"))
            {
                if (data.contains("own"))
                    tooltip.add(Component.translatable(MODID + ".jade.reactor.no_core"));
                return;
            }

            var phase = parse(data.getStringOr("phase", "COLD"));
            tooltip.add(switch (phase)
            {
                case COLD -> data.getIntOr("missing", 0) > 0
                        ? Component.translatable(MODID + ".jade.reactor.incomplete", data.getIntOr("missing", 0))
                        : Component.translatable(MODID + ".jade.reactor.cold");
                case IGNITING -> Component.translatable(MODID + ".jade.reactor.igniting", data.getIntOr("charge", 0));
                case LIT -> Component.translatable(MODID + ".jade.reactor.lit", data.getLongOr("output", 0),
                        String.format(Locale.ROOT, "%.2f", data.getDoubleOr("burn", 0)));
                case FAILING -> Component.translatable(MODID + ".jade.reactor.failing." + cause(data.getIntOr("causes", 0)),
                        Math.max(1, (data.getIntOr("countdown", 0) + 19) / 20));
                case COLLAPSING, FAILED -> Component.translatable(MODID + ".jade.reactor.collapsing");
            });
            if (data.getBooleanOr("powered", false))
                tooltip.add(Component.translatable(MODID + ".jade.reactor.powered"));

            if (data.contains("own"))
                return;
            for (var side = 0; side < 2; side++)
            {
                var held = data.getStringOr("intake" + side, "");
                if (!held.isEmpty())
                    tooltip.add(aspect(held).copy().append(Component.literal("  " + data.getIntOr("intake" + side + "Amount", 0) + " / " + tank)));
            }
        }

        private static String cause(int causes)
        {
            if (Cause.BREACH.in(causes))
                return "breach";
            if (Cause.CONTRAVOID_DRY.in(causes))
                return "contravoid";
            return "essence";
        }

        private static Phase parse(String name)
        {
            try
            {
                return Phase.valueOf(name);
            } catch (IllegalArgumentException unknown)
            {
                return Phase.COLD;
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

    /**
     * The containment holds nothing itself, so looking at it shows the core it closes in.
     */
    private record ContainmentRedirect(IWailaClientRegistration registration) implements JadeRayTraceCallback
    {
        @Override
        public @Nullable Accessor<?> onRayTrace(HitResult hitResult, @Nullable Accessor<?> accessor, @Nullable Accessor<?> originalAccessor)
        {
            if (!(accessor instanceof BlockAccessor blockAccessor) || !(blockAccessor.getBlock() instanceof ReactorContainmentBlock))
                return accessor;

            var level = blockAccessor.getLevel();
            var core = ContravoidReactorTile.coreAround(level, blockAccessor.getPosition());
            if (core == null)
                return accessor;

            var corePos = core.getBlockPos();
            var state = level.getBlockState(corePos);
            return registration.blockAccessor()
                    .from(blockAccessor)
                    .hit(new BlockHitResult(blockAccessor.getHitResult().getLocation(), blockAccessor.getSide(), corePos, false))
                    .blockState(state)
                    .blockEntity(core)
                    .serversideRep(new ItemStack(state.getBlock()))
                    .build();
        }
    }
}
