package net.voxelindustry.voidheart.common.content.permeablebarrier;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Barrier blocks connected by face adjacency form one structure sharing a single config and powered state.
 */
public class PermeableBarrierStructure
{
    public static final int MAX_SIZE = 4096;

    /**
     * Breadth-first walk of the structure, mapping each block to its distance from the start.
     */
    public static Map<BlockPos, Integer> collect(BlockGetter level, BlockPos start)
    {
        var distances = new LinkedHashMap<BlockPos, Integer>();
        if (!level.getBlockState(start).is(VoidHeartBlocks.PERMEABLE_BARRIER))
            return distances;

        var frontier = new ArrayDeque<BlockPos>();
        distances.put(start, 0);
        frontier.add(start);

        while (!frontier.isEmpty() && distances.size() < MAX_SIZE)
        {
            var current = frontier.poll();
            int distance = distances.get(current);
            for (var direction : Direction.values())
            {
                var adjacent = current.relative(direction);
                if (distances.containsKey(adjacent) || !level.getBlockState(adjacent).is(VoidHeartBlocks.PERMEABLE_BARRIER))
                    continue;
                distances.put(adjacent, distance + 1);
                frontier.add(adjacent);
            }
        }
        return distances;
    }

    public static @Nullable PermeableBarrierTile getTile(BlockGetter level, BlockPos pos)
    {
        return level.getBlockEntity(pos) instanceof PermeableBarrierTile tile ? tile : null;
    }

    /**
     * A player cannot attach a block to a structure that belongs to someone else.
     */
    public static boolean canExtend(BlockGetter level, BlockPos pos, @Nullable Player player)
    {
        for (var direction : Direction.values())
        {
            var neighbor = getTile(level, pos.relative(direction));
            if (neighbor != null && (player == null || !neighbor.getConfig().isAlly(player)))
                return false;
        }
        return true;
    }

    /**
     * Joins a freshly placed block to its structure, adopting the config of an adjacent barrier
     * or becoming a new unconfigured structure owned by the placer.
     */
    public static void onBlockAdded(ServerLevel level, BlockPos pos, @Nullable UUID placer)
    {
        BarrierConfig config = null;
        boolean joined = false;
        for (var direction : Direction.values())
        {
            var neighbor = getTile(level, pos.relative(direction));
            if (neighbor != null)
            {
                config = neighbor.getConfig();
                joined = true;
                break;
            }
        }
        if (config == null)
            config = placer != null ? BarrierConfig.ownedBy(placer) : BarrierConfig.EMPTY;

        var finalConfig = config;
        var structure = collect(level, pos);
        structure.keySet().forEach(member ->
        {
            var tile = getTile(level, member);
            if (tile != null)
                tile.setConfig(finalConfig);
        });
        updatePower(level, structure);

        if (joined)
        {
            var payload = new BarrierWavePayload(pos);
            PlayerLookup.tracking(level, pos).forEach(player -> ServerPlayNetworking.send(player, payload));
        }
    }

    public static void updateConfig(Level level, BlockPos start, UnaryOperator<BarrierConfig> change)
    {
        var startTile = getTile(level, start);
        if (startTile == null)
            return;

        var config = change.apply(startTile.getConfig());
        for (var member : collect(level, start).keySet())
        {
            var tile = getTile(level, member);
            if (tile != null)
                tile.setConfig(config);
        }
    }

    public static void updatePower(Level level, BlockPos start)
    {
        updatePower(level, collect(level, start));
    }

    private static void updatePower(Level level, Map<BlockPos, Integer> structure)
    {
        boolean powered = structure.keySet().stream().anyMatch(level::hasNeighborSignal);

        for (var member : structure.keySet())
        {
            var state = level.getBlockState(member);
            if (state.getValue(BlockStateProperties.POWERED) != powered)
                level.setBlock(member, state.setValue(BlockStateProperties.POWERED, powered), Block.UPDATE_CLIENTS);
        }
    }
}
