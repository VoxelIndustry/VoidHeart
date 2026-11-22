package net.voxelindustry.voidheart.common.energy.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.energy.storage.CellSocketTile.Activity;
import org.jspecify.annotations.Nullable;

/**
 * The charge of a Large Rubrum Cell, held by its lower half. Each tick it charges from the field while powered, or
 * discharges into it otherwise, and every tick it isn't charging loses a little, as {@link LargeCellCharge#decay} says.
 * Ticks passed while its chunk was unloaded count as not charging, so a forgotten cell drains away. A full cell on a
 * signal draws just what it would lose, so a field holds it full.
 */
public class LargeRubrumCellTile extends TileBase
{
    private static final int  SYNC_PERIOD   = 10;
    private static final int  TARGET_RESCAN = 100;
    private static final long NEVER         = Long.MIN_VALUE;

    private long     charge;
    private long     decayCarry;
    /** Game time of the last tick, to count the ticks passed while unloaded. */
    private long     lastTick = NEVER;
    private Activity activity = Activity.IDLE;
    private long     syncedCharge;
    private int      lastSignal;

    // Client only
    private @Nullable Vec3 moteTarget;
    private long           targetScannedAt = NEVER;
    private long           nextMote;

    public LargeRubrumCellTile(BlockPos pos, BlockState state)
    {
        super(RubrumContent.LARGE_RUBRUM_CELL_TILE, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, LargeRubrumCellTile tile)
    {
        if (level.isClientSide())
            tile.clientTick();
        else
            tile.serverTick((ServerLevel) level);
    }

    private void serverTick(ServerLevel level)
    {
        var now = level.getGameTime();
        var capacity = RubrumBalance.LARGE_CELL_CAPACITY;
        // Ticks missed while unloaded fade first: the cell wasn't charging through them, and what it draws now isn't lost to them
        if (lastTick != NEVER && now - lastTick > 1)
            fade(now - lastTick - 1, capacity);
        lastTick = now;

        var next = Activity.IDLE;
        if (level.hasNeighborSignal(worldPosition) || level.hasNeighborSignal(worldPosition.above()))
        {
            // A full cell draws only what this tick would fade, which holds it full while the field can feed it
            var room = charge >= capacity ? LargeCellCharge.decay(decayCarry, 1, capacity).loss() : capacity - charge;
            var drawn = room > 0 ? VoidEnergy.draw(level, worldPosition, Math.min(RubrumBalance.LARGE_CELL_RATE, room)) : 0;
            if (drawn > 0)
            {
                charge = Math.min(capacity, charge + drawn);
                next = Activity.CHARGING;
            }
        }
        else if (charge > 0)
        {
            var offered = VoidEnergy.offer(level, worldPosition, Math.min(RubrumBalance.LARGE_CELL_RATE, charge));
            if (offered > 0)
            {
                charge -= offered;
                next = Activity.DISCHARGING;
            }
        }
        if (next != Activity.CHARGING)
            fade(1, capacity);
        setChanged();

        showCharge(level);
        if (next != activity || (charge != syncedCharge && now % SYNC_PERIOD == 0))
        {
            activity = next;
            syncedCharge = charge;
            sync();
        }
    }

    private void fade(long ticks, long capacity)
    {
        var decay = LargeCellCharge.decay(decayCarry, ticks, capacity);
        decayCarry = decay.carry();
        charge = Math.clamp(charge - decay.loss(), 0, capacity);
    }

    /**
     * Lights the column's segments on both halves and tells comparators when what they read changes.
     */
    private void showCharge(ServerLevel level)
    {
        var segments = LargeCellCharge.segments(charge, RubrumBalance.LARGE_CELL_CAPACITY);
        var state = getBlockState();
        if (state.getValue(LargeRubrumCellBlock.CHARGE) != segments)
        {
            level.setBlock(worldPosition, state.setValue(LargeRubrumCellBlock.CHARGE, segments), Block.UPDATE_CLIENTS);
            var above = worldPosition.above();
            var upper = level.getBlockState(above);
            if (upper.is(state.getBlock()))
                level.setBlock(above, upper.setValue(LargeRubrumCellBlock.CHARGE, segments), Block.UPDATE_CLIENTS);
        }

        var signal = comparatorSignal();
        if (signal != lastSignal)
        {
            lastSignal = signal;
            level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
            level.updateNeighbourForOutputSignal(worldPosition.above(), state.getBlock());
        }
    }

    private void clientTick()
    {
        var time = level.getGameTime();
        if (activity == Activity.IDLE || time < nextMote)
            return;
        if (targetScannedAt == NEVER || time - targetScannedAt >= TARGET_RESCAN)
        {
            targetScannedAt = time;
            moteTarget = CellTransfer.moteTarget(level, worldPosition);
        }
        nextMote = time + 2 + level.getRandom().nextInt(4);
        // Motes leave and enter anywhere along the column
        var column = Vec3.atBottomCenterOf(worldPosition).add(0, 0.3 + level.getRandom().nextDouble() * 1.4, 0);
        CellTransfer.mote(level, column, moteTarget, activity);
    }

    public long charge()
    {
        return charge;
    }

    public Activity activity()
    {
        return activity;
    }

    /**
     * Sets the charge outright, for review commands.
     */
    public void setCharge(long charge)
    {
        this.charge = Math.clamp(charge, 0, RubrumBalance.LARGE_CELL_CAPACITY);
        setChanged();
        sync();
    }

    /**
     * Ages the cell as if it had stood uncharged for that many ticks, for review commands.
     */
    public void age(long ticks)
    {
        var decay = LargeCellCharge.decay(decayCarry, ticks, RubrumBalance.LARGE_CELL_CAPACITY);
        decayCarry = decay.carry();
        setCharge(charge - decay.loss());
    }

    public int comparatorSignal()
    {
        return LargeCellCharge.comparatorSignal(charge, RubrumBalance.LARGE_CELL_CAPACITY);
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);
        charge = Math.clamp(tag.getLongOr("charge", 0L), 0, RubrumBalance.LARGE_CELL_CAPACITY);
        decayCarry = Math.max(0, tag.getLongOr("decayCarry", 0L));
        lastTick = tag.getLongOr("lastTick", NEVER);
        activity = Activity.values()[Mth.clamp(tag.getIntOr("activity", 0), 0, Activity.values().length - 1)];
        syncedCharge = charge;
        lastSignal = comparatorSignal();
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        tag.putLong("charge", charge);
        tag.putLong("decayCarry", decayCarry);
        if (lastTick != NEVER)
            tag.putLong("lastTick", lastTick);
        tag.putInt("activity", activity.ordinal());
        super.saveAdditional(tag);
    }
}
