package net.voxelindustry.voidheart.common.content.oculus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Table blocks side by side at the same height form one table sharing a single view.
 * Tables past the size limits keep their view but do not project.
 */
public class OculusTableStructure
{
    public static final int MAX_SIZE = 64;
    public static final int MAX_SPAN = 16;

    /**
     * Breadth-first walk of the table, stopping one block past the size limit so oversized tables can be told apart.
     */
    public static Set<BlockPos> collect(BlockGetter level, BlockPos start)
    {
        return collect(start, pos -> level.getBlockState(pos).is(VoidHeartBlocks.OCULUS_TABLE));
    }

    static Set<BlockPos> collect(BlockPos start, Predicate<BlockPos> isTable)
    {
        var members = new LinkedHashSet<BlockPos>();
        if (!isTable.test(start))
            return members;

        var frontier = new ArrayDeque<BlockPos>();
        members.add(start);
        frontier.add(start);

        while (!frontier.isEmpty() && members.size() <= MAX_SIZE)
        {
            var current = frontier.poll();
            for (var direction : Direction.Plane.HORIZONTAL)
            {
                var adjacent = current.relative(direction);
                if (!members.contains(adjacent) && isTable.test(adjacent))
                {
                    members.add(adjacent);
                    frontier.add(adjacent);
                }
            }
        }
        return members;
    }

    /**
     * The table around this block, if it is small enough to project.
     */
    public static Optional<OculusFootprint> footprint(BlockGetter level, BlockPos start)
    {
        return footprint(start, pos -> level.getBlockState(pos).is(VoidHeartBlocks.OCULUS_TABLE));
    }

    static Optional<OculusFootprint> footprint(BlockPos start, Predicate<BlockPos> isTable)
    {
        var members = collect(start, isTable);
        if (members.isEmpty() || members.size() > MAX_SIZE)
            return Optional.empty();

        var footprint = OculusFootprint.of(members);
        if (footprint.width() > MAX_SPAN || footprint.depth() > MAX_SPAN)
            return Optional.empty();
        return Optional.of(footprint);
    }

    public static @Nullable OculusTableTile getTile(BlockGetter level, BlockPos pos)
    {
        return level.getBlockEntity(pos) instanceof OculusTableTile tile ? tile : null;
    }

    /**
     * A placed block takes the view of the first table it touches, which then spreads to every table it joined.
     */
    public static void onBlockAdded(Level level, BlockPos pos)
    {
        var view = OculusView.DEFAULT;
        for (var direction : Direction.Plane.HORIZONTAL)
        {
            var neighbor = getTile(level, pos.relative(direction));
            if (neighbor != null)
            {
                view = neighbor.getView();
                break;
            }
        }

        var adopted = view;
        updateView(level, pos, current -> adopted);
        updatePower(level, pos);
    }

    /**
     * A table sleeps while any of its blocks is powered.
     */
    public static void updatePower(Level level, BlockPos start)
    {
        var members = collect(level, start);
        if (members.isEmpty())
            return;
        boolean powered = members.stream().anyMatch(level::hasNeighborSignal);
        updateView(level, start, view -> view.sleeping(powered));
    }

    public static void updateView(Level level, BlockPos start, UnaryOperator<OculusView> change)
    {
        var startTile = getTile(level, start);
        if (startTile == null)
            return;

        var view = change.apply(startTile.getView());
        for (var member : collect(level, start))
        {
            var tile = getTile(level, member);
            if (tile != null)
                tile.setView(view);
        }
    }
}
