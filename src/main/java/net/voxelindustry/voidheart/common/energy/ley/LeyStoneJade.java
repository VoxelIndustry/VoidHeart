package net.voxelindustry.voidheart.common.energy.ley;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.config.IPluginConfig;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Jade hand-off for W4 Standing stones and wards, called through EnergyJade. A stone tells where its spur comes from and what its machines draw; a ward and
 * a grower tell their supply, and a ward what weakens it.
 */
public final class LeyStoneJade
{
    private static final Identifier STONE  = Identifier.fromNamespaceAndPath(MODID, "ley_stone");
    private static final Identifier WARD   = Identifier.fromNamespaceAndPath(MODID, "ley_ward");
    private static final Identifier GROWER = Identifier.fromNamespaceAndPath(MODID, "ley_grower");

    private LeyStoneJade()
    {
    }

    public static void register(IWailaCommonRegistration registration)
    {
        registration.registerBlockDataProvider(new StoneData(), LeyStoneTile.class);
        registration.registerBlockDataProvider(new MachineData(WARD), LeyWardTile.class);
        registration.registerBlockDataProvider(new MachineData(GROWER), LeyGrowerTile.class);
    }

    public static void registerClient(IWailaClientRegistration registration)
    {
        registration.registerBlockComponent(new StoneTooltip(), LeyStoneBlock.class);
        registration.registerBlockComponent(new MachineTooltip(WARD), LeyWardBlock.class);
        registration.registerBlockComponent(new MachineTooltip(GROWER), LeyGrowerBlock.class);
    }

    private static final class StoneData implements IServerDataProvider<BlockAccessor>
    {
        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor)
        {
            if (!(accessor.getLevel() instanceof ServerLevel level))
                return;
            var spurs = LeySpurs.get(level);
            var link = spurs.plan().link(accessor.getPosition());
            if (link == null)
                return;
            var network = spurs.network();
            tag.putInt("consumers", network.consumers(link.root()));
            tag.putDouble("claimed", network.claimed(link.root()));
            tag.putDouble("granted", network.granted(link.root()));
        }

        @Override
        public Identifier getUid()
        {
            return STONE;
        }
    }

    /**
     * Reads the synced lower half, so the upper half of the menhir shows the same lines.
     */
    private static final class StoneTooltip implements IBlockComponentProvider
    {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            var pos = accessor.getBlockState().getValue(LeyStoneBlock.HALF) == DoubleBlockHalf.UPPER ? accessor.getPosition().below() : accessor.getPosition();
            if (!(accessor.getLevel().getBlockEntity(pos) instanceof LeyStoneTile stone))
                return;

            if (!stone.isConnected())
            {
                tooltip.add(Component.translatable(MODID + ".jade.ley_stone.dark", LeyStoneBalance.STONE_REACH));
                return;
            }
            if (stone.isRoot())
                tooltip.add(Component.translatable(MODID + ".jade.ley_stone.tap", String.format("%.1f", stone.flow())));
            else
                tooltip.add(Component.translatable(MODID + ".jade.ley_stone.chained",
                        String.format("%.0f", Math.hypot(stone.upstreamX() - pos.getX() - 0.5, stone.upstreamZ() - pos.getZ() - 0.5))));
            if (stone.taint() >= 1)
                tooltip.add(Component.translatable(MODID + ".jade.ley_stone.taint", String.format("%.0f", stone.taint())));

            var data = accessor.getServerData();
            if (data.contains("consumers"))
                tooltip.add(Component.translatable(MODID + ".jade.ley_stone.spur", data.getIntOr("consumers", 0),
                        String.format("%.1f", data.getDoubleOr("claimed", 0)), String.format("%.1f", data.getDoubleOr("granted", 0))));
        }

        @Override
        public Identifier getUid()
        {
            return STONE;
        }
    }

    private record MachineData(Identifier uid) implements IServerDataProvider<BlockAccessor>
    {
        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor)
        {
            if (!(accessor.getLevel() instanceof ServerLevel level))
                return;
            tag.putBoolean("stoneNear", LeySpurs.get(level).network().stoneFor(accessor.getPosition()) != null);
            switch (accessor.getBlockEntity())
            {
                case LeyWardTile ward ->
                {
                    tag.putFloat("supply", ward.supply());
                    tag.putFloat("strength", ward.strength());
                    tag.putFloat("taint", ward.taint());
                    tag.putFloat("seep", ward.seep());
                }
                case LeyGrowerTile grower -> tag.putFloat("supply", grower.supply());
                default ->
                {
                }
            }
        }

        @Override
        public Identifier getUid()
        {
            return uid;
        }
    }

    private record MachineTooltip(Identifier uid) implements IBlockComponentProvider
    {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            var data = accessor.getServerData();
            if (!data.contains("supply"))
                return;

            float supply = data.getFloatOr("supply", 0);
            tooltip.add(Component.translatable(MODID + ".jade.ley.supply", Math.round(supply * 100)));
            if (supply <= 0 && !data.getBooleanOr("stoneNear", false))
                tooltip.add(Component.translatable(MODID + ".jade.ley.no_stone", LeyStoneBalance.STONE_SUPPLY_RADIUS));

            if (!data.contains("strength"))
                return;
            tooltip.add(Component.translatable(MODID + ".jade.ley_ward.strength", Math.round(data.getFloatOr("strength", 0) * 100)));
            if (data.getFloatOr("taint", 0) >= 1)
                tooltip.add(Component.translatable(MODID + ".jade.ley_ward.taint"));
            if (data.getFloatOr("seep", 0) > 0.01F)
                tooltip.add(Component.translatable(MODID + ".jade.ley_ward.seep"));
        }

        @Override
        public Identifier getUid()
        {
            return uid;
        }
    }
}
