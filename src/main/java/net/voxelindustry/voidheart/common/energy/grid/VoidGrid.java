package net.voxelindustry.voidheart.common.energy.grid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.energy.IVoidHolder;
import net.voxelindustry.voidheart.common.energy.VoidNetworkProvider;
import net.voxelindustry.voidheart.common.energy.creative.CreativeEnergyContent;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The void grid of one level: its loaded pylons, the arcs between them and the holders in their fields. It rebuilds its layout when a pylon or bundle
 * changes and every few seconds for line of sight, and settles its ledger before anything else in the level ticks.
 */
public final class VoidGrid
{
    private static final Map<ServerLevel, VoidGrid> GRIDS = new WeakHashMap<>();

    public static final VoidNetworkProvider PROVIDER = new VoidNetworkProvider()
    {
        @Override
        public long available(ServerLevel level, BlockPos pos)
        {
            var grid = GRIDS.get(level);
            return grid == null ? 0 : grid.ledger.available(pos);
        }

        @Override
        public long draw(ServerLevel level, BlockPos pos, long amount)
        {
            var grid = GRIDS.get(level);
            return grid == null ? 0 : grid.ledger.draw(pos, amount);
        }

        @Override
        public long offer(ServerLevel level, BlockPos pos, long amount)
        {
            var grid = GRIDS.get(level);
            return grid == null ? 0 : grid.ledger.offer(pos, amount);
        }
    };

    private final Map<BlockPos, AzuriumPylonTile> pylons = new HashMap<>();
    private final VoidGridLedger                  ledger = new VoidGridLedger();

    private boolean dirty = true;
    private long    lastRebuild;

    private VoidGrid()
    {
    }

    public static @Nullable VoidGrid get(ServerLevel level)
    {
        return GRIDS.get(level);
    }

    static void join(ServerLevel level, AzuriumPylonTile pylon)
    {
        var grid = GRIDS.computeIfAbsent(level, key -> new VoidGrid());
        grid.pylons.put(pylon.getBlockPos().immutable(), pylon);
        grid.dirty = true;
    }

    static void leave(ServerLevel level, AzuriumPylonTile pylon)
    {
        var grid = GRIDS.get(level);
        if (grid != null && grid.pylons.remove(pylon.getBlockPos(), pylon))
            grid.dirty = true;
    }

    public static void markDirty(ServerLevel level)
    {
        var grid = GRIDS.get(level);
        if (grid != null)
            grid.dirty = true;
    }

    public static void clear()
    {
        GRIDS.clear();
    }

    /**
     * Called at the start of every level tick, before block entities.
     */
    public static void tick(ServerLevel level)
    {
        var grid = GRIDS.get(level);
        if (grid == null)
            return;

        long time = level.getGameTime();
        if (grid.dirty || time - grid.lastRebuild >= VoidGridBalance.REBUILD_TICKS)
            grid.rebuild(level, time);
        grid.ledger.startTick(time);
        if (time % VoidGridBalance.DISPLAY_TICKS == 0)
            grid.display();
    }

    /**
     * Pylons with a bundle whose fields cover the position.
     */
    public static List<GridLayout.Pylon> pylonsCovering(ServerLevel level, BlockPos pos)
    {
        var grid = GRIDS.get(level);
        if (grid == null)
            return List.of();
        var layout = grid.ledger.layout();
        var covering = new ArrayList<GridLayout.Pylon>();
        for (int index : layout.covering(pos))
            covering.add(layout.pylons().get(index));
        return covering;
    }

    public VoidGridLedger ledger()
    {
        return ledger;
    }

    public GridLayout layout()
    {
        return ledger.layout();
    }

    private void rebuild(ServerLevel level, long time)
    {
        dirty = false;
        lastRebuild = time;

        var found = new ArrayList<GridLayout.Pylon>();
        var sorted = new ArrayList<>(pylons.keySet());
        sorted.sort(Comparator.comparingLong(BlockPos::asLong));
        for (var pos : sorted)
        {
            var bundle = PylonBundle.above(level, pos);
            if (bundle != PylonBundle.NONE)
                found.add(new GridLayout.Pylon(pos, pos.above(), bundle.radius(), bundle.cap()));
        }
        var layout = GridLayout.link(found, (a, b) -> inSight(level, a, b));

        var holders = new HashMap<BlockPos, int[]>();
        for (var pylon : found)
            scanHolders(level, pylon, layout, holders);

        var levelRef = new WeakReference<>(level);
        ledger.setLayout(layout, holders, pos -> holderAt(levelRef.get(), pos));
    }

    private static void scanHolders(ServerLevel level, GridLayout.Pylon pylon, GridLayout layout, Map<BlockPos, int[]> holders)
    {
        var center = pylon.bundle();
        int radius = pylon.radius();
        for (int chunkX = (center.getX() - radius) >> 4; chunkX <= (center.getX() + radius) >> 4; chunkX++)
        {
            for (int chunkZ = (center.getZ() - radius) >> 4; chunkZ <= (center.getZ() + radius) >> 4; chunkZ++)
            {
                var chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null)
                    continue;
                for (var entry : chunk.getBlockEntities().entrySet())
                {
                    var pos = entry.getKey();
                    if (!holders.containsKey(pos) && pylon.covers(pos) && holderAt(level, pos) != null)
                        holders.put(pos.immutable(), layout.covering(pos));
                }
            }
        }
    }

    private static @Nullable IVoidHolder holderAt(@Nullable ServerLevel level, BlockPos pos)
    {
        if (level == null || !level.isLoaded(pos))
            return null;
        if (level.getBlockState(pos).is(CreativeEnergyContent.CREATIVE_VOID_WELL))
            return EndlessHolder.INSTANCE;
        return level.getBlockEntity(pos) instanceof IVoidHolder holder ? holder : null;
    }

    /**
     * From bundle to bundle: any block with a collision shape between them breaks the arc, the two pylons and their bundles excepted.
     */
    static boolean inSight(ServerLevel level, GridLayout.Pylon a, GridLayout.Pylon b)
    {
        var from = Vec3.atCenterOf(a.bundle());
        var to = Vec3.atCenterOf(b.bundle());
        int steps = Mth.ceil(from.distanceTo(to) * 4);
        var cursor = new BlockPos.MutableBlockPos();
        for (int i = 1; i < steps; i++)
        {
            var point = from.lerp(to, i / (double) steps);
            cursor.set(point.x, point.y, point.z);
            if (cursor.equals(a.bundle()) || cursor.equals(b.bundle()) || cursor.equals(a.pos()) || cursor.equals(b.pos()))
                continue;
            if (!level.isLoaded(cursor))
                return false;
            var state = level.getBlockState(cursor);
            if (!state.getCollisionShape(level, cursor).isEmpty())
                return false;
        }
        return true;
    }

    private void display()
    {
        var layout = ledger.layout();
        var displays = ledger.takeDisplay();
        var shown = new HashMap<BlockPos, VoidGridLedger.PylonDisplay>();
        for (int i = 0; i < layout.pylons().size(); i++)
            shown.put(layout.pylons().get(i).pos(), displays.get(i));
        for (var pylon : pylons.values())
            if (!pylon.isRemoved())
                pylon.setDisplay(shown.getOrDefault(pylon.getBlockPos(), VoidGridLedger.PylonDisplay.IDLE));
    }

    /**
     * A creative void well inside a field: endless energy, and room for any amount.
     */
    static final class EndlessHolder implements IVoidHolder
    {
        static final  EndlessHolder INSTANCE = new EndlessHolder();
        private static final long   ENDLESS  = 1L << 40;

        @Override
        public long stored()
        {
            return ENDLESS;
        }

        @Override
        public long capacity()
        {
            return ENDLESS;
        }

        @Override
        public long maxRate()
        {
            return ENDLESS;
        }

        @Override
        public long insert(long amount, boolean simulate)
        {
            return Math.max(0, amount);
        }

        @Override
        public long extract(long amount, boolean simulate)
        {
            return Math.max(0, amount);
        }
    }
}
