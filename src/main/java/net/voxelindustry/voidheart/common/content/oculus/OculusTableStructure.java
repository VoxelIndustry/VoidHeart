package net.voxelindustry.voidheart.common.content.oculus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
     * Everyone who placed a block of the table: their beholders all see for it.
     */
    public static Set<Optional<UUID>> owners(BlockGetter level, BlockPos start)
    {
        var owners = new HashSet<Optional<UUID>>();
        for (var member : collect(level, start))
        {
            var tile = getTile(level, member);
            if (tile != null)
                owners.add(tile.getOwner());
        }
        return owners;
    }

    /**
     * A placed block takes the view of the first table it touches, which then spreads to every table it joined.
     */
    public static void onBlockAdded(Level level, BlockPos pos)
    {
        Predicate<BlockPos> isTable = member -> level.getBlockState(member).is(VoidHeartBlocks.OCULUS_TABLE);
        var view = OculusView.DEFAULT;
        boolean found = false;
        // One memory per table the block joins, even copies of one recording: the first is kept, and every other comes back out as its bundle
        var held = new ArrayList<Optional<OculusMemory>>();
        for (var table : joinedTables(pos, isTable))
        {
            var side = getTile(level, table.iterator().next());
            if (side == null)
                continue;
            if (!found)
            {
                view = side.getView();
                found = true;
            }
            held.add(side.getView().memory());
        }
        var reconciled = reconcile(held);

        var adopted = view.remembering(reconciled.kept());
        updateView(level, pos, current -> adopted);
        remember(level, pos, reconciled.kept());
        for (var memory : reconciled.returned())
            net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, memory.toStack());
        updatePower(level, pos);
    }

    /**
     * What a block joining tables does with their memories, one per table it joins in the order met.
     *
     * @param kept     what the joined table shows
     * @param returned the bundles that come back out, one per other table that held a memory
     */
    record Reconciled(Optional<OculusMemory> kept, List<OculusMemory> returned)
    {
    }

    static Reconciled reconcile(List<Optional<OculusMemory>> held)
    {
        Optional<OculusMemory> kept = Optional.empty();
        var returned = new ArrayList<OculusMemory>();
        for (var memory : held)
        {
            if (memory.isEmpty())
                continue;
            if (kept.isEmpty())
                kept = memory;
            else
                returned.add(memory.get());
        }
        return new Reconciled(kept, List.copyOf(returned));
    }

    /**
     * The separate tables a block at joined touches, each as every block of it, in the order its sides are met. Two sides of one table are one table.
     */
    static List<Set<BlockPos>> joinedTables(BlockPos joined, Predicate<BlockPos> isTable)
    {
        Predicate<BlockPos> without = member -> !member.equals(joined) && isTable.test(member);
        var tables = new ArrayList<Set<BlockPos>>();
        for (var direction : Direction.Plane.HORIZONTAL)
        {
            var side = joined.relative(direction);
            if (!without.test(side) || tables.stream().anyMatch(table -> table.contains(side)))
                continue;
            tables.add(connected(side, without, MEMORY_WALK));
        }
        return tables;
    }

    /**
     * Every table block connected to start, the first being start, up to the limit given.
     */
    static Set<BlockPos> connected(BlockPos start, Predicate<BlockPos> isTable, int limit)
    {
        var members = new LinkedHashSet<BlockPos>();
        if (!isTable.test(start))
            return members;
        var frontier = new ArrayDeque<BlockPos>();
        members.add(start);
        frontier.add(start);
        while (!frontier.isEmpty() && members.size() < limit)
        {
            var current = frontier.poll();
            for (var direction : Direction.Plane.HORIZONTAL)
            {
                var adjacent = current.relative(direction);
                if (members.size() < limit && !members.contains(adjacent) && isTable.test(adjacent))
                {
                    members.add(adjacent);
                    frontier.add(adjacent);
                }
            }
        }
        return members;
    }

    /**
     * Sets the memory on every table block connected to start, past the size a table projects at, so no block keeps a memory that went elsewhere.
     */
    public static void remember(Level level, BlockPos start, Optional<OculusMemory> memory)
    {
        for (var member : connected(start, pos -> level.getBlockState(pos).is(VoidHeartBlocks.OCULUS_TABLE), MEMORY_WALK))
        {
            var tile = getTile(level, member);
            if (tile != null && !tile.getView().memory().equals(memory))
                tile.setView(tile.getView().remembering(memory));
        }
    }

    /** How many connected table blocks a memory is settled across, well past any table that projects. */
    static final int MEMORY_WALK = 4096;

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
