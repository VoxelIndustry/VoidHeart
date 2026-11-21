package net.voxelindustry.voidheart.common.energy.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.energy.storage.CellSocketTile.Activity;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * Four rubrum cells set in a wall, each moving its energy like a cell socket's: out into the field covering it, or in
 * from that field while the wall is powered.
 */
public class SocketWallTile extends TileBase
{
    private static final int SYNC_PERIOD   = 10;
    private static final int TARGET_RESCAN = 100;

    private final ItemStack[] cells      = new ItemStack[SocketWallGeometry.SLOTS];
    private final Activity[]  activities = new Activity[SocketWallGeometry.SLOTS];
    /** Each cell's charge as last sent to clients: cells can charge and discharge at once and leave the total unchanged. */
    private final long[]      syncedCharges = new long[SocketWallGeometry.SLOTS];

    // Client only
    private @Nullable Vec3 moteTarget;
    private long           targetScannedAt = Long.MIN_VALUE;
    private final long[]   nextMotes       = new long[SocketWallGeometry.SLOTS];

    public SocketWallTile(BlockPos pos, BlockState state)
    {
        super(RubrumContent.SOCKET_WALL_TILE, pos, state);
        Arrays.fill(cells, ItemStack.EMPTY);
        Arrays.fill(activities, Activity.IDLE);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SocketWallTile tile)
    {
        if (level.isClientSide())
            tile.clientTick();
        else
            tile.serverTick((ServerLevel) level);
    }

    private void serverTick(ServerLevel level)
    {
        var powered = level.hasNeighborSignal(worldPosition);
        var changedActivity = false;
        for (var slot = 0; slot < cells.length; slot++)
        {
            var next = CellTransfer.step(level, worldPosition, cells[slot], powered, RubrumBalance.SOCKET_WALL_RATE);
            changedActivity |= next != activities[slot];
            activities[slot] = next;
        }

        var changedCharge = false;
        for (var slot = 0; slot < cells.length; slot++)
            changedCharge |= RubrumCellItem.charge(cells[slot]) != syncedCharges[slot];
        if (changedCharge)
            setChanged();
        if (changedActivity || (changedCharge && level.getGameTime() % SYNC_PERIOD == 0))
        {
            noteSyncedCharges();
            sync();
        }
    }

    private void clientTick()
    {
        var time = level.getGameTime();
        for (var slot = 0; slot < cells.length; slot++)
        {
            var activity = activities[slot];
            if (activity == Activity.IDLE || time < nextMotes[slot])
                continue;
            if (targetScannedAt == Long.MIN_VALUE || time - targetScannedAt >= TARGET_RESCAN)
            {
                targetScannedAt = time;
                moteTarget = CellTransfer.moteTarget(level, worldPosition);
            }
            // A Searing cell letting go streams its motes out
            nextMotes[slot] = activity == Activity.BURSTING ? time + 1 : time + 5 + level.getRandom().nextInt(6);
            var cell = Vec3.atLowerCornerOf(worldPosition).add(SocketWallGeometry.cellCenter(facing(), slot));
            CellTransfer.mote(level, cell, moteTarget, activity);
        }
    }

    public Direction facing()
    {
        return getBlockState().getValue(SocketWallBlock.FACING);
    }

    public ItemStack getCell(int slot)
    {
        return cells[slot];
    }

    /**
     * @return the first slot holding no cell, or -1 when all four do
     */
    public int firstEmpty()
    {
        for (var slot = 0; slot < cells.length; slot++)
            if (cells[slot].isEmpty())
                return slot;
        return -1;
    }

    public int cellCount()
    {
        var count = 0;
        for (var cell : cells)
            if (!cell.isEmpty())
                count++;
        return count;
    }

    public void setCell(int slot, ItemStack cell)
    {
        cells[slot] = cell.copy();
        activities[slot] = Activity.IDLE;
        noteSyncedCharges();
        setChanged();
        sync();
    }

    public Activity activity(int slot)
    {
        return activities[slot];
    }

    public float fill(int slot)
    {
        return cells[slot].isEmpty() ? 0 : Mth.clamp(RubrumCellItem.fill(cells[slot]), 0, 1);
    }

    public long totalCharge()
    {
        var total = 0L;
        for (var cell : cells)
            total += RubrumCellItem.charge(cell);
        return total;
    }

    /**
     * A comparator reads how full the wall is out of four full cells, and at least 1 while any charge is held.
     */
    public int comparatorSignal()
    {
        var total = totalCharge();
        if (total <= 0)
            return 0;
        var capacity = RubrumBalance.CELL_CAPACITY * SocketWallGeometry.SLOTS;
        return 1 + (int) Math.floor(14D * Math.min(total, capacity) / capacity);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        if (level != null)
            for (var cell : cells)
                if (!cell.isEmpty())
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, cell);
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);
        for (var slot = 0; slot < cells.length; slot++)
        {
            var cell = tag.read("cell" + slot, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
            // Only cells belong here, whatever a stale save says
            cells[slot] = cell.is(RubrumContent.RUBRUM_CELL) ? cell : ItemStack.EMPTY;
            activities[slot] = Activity.values()[Mth.clamp(tag.getIntOr("activity" + slot, 0), 0, Activity.values().length - 1)];
        }
        noteSyncedCharges();
    }

    private void noteSyncedCharges()
    {
        for (var slot = 0; slot < cells.length; slot++)
            syncedCharges[slot] = RubrumCellItem.charge(cells[slot]);
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        for (var slot = 0; slot < cells.length; slot++)
        {
            tag.store("cell" + slot, ItemStack.OPTIONAL_CODEC, cells[slot]);
            tag.putInt("activity" + slot, activities[slot].ordinal());
        }
        super.saveAdditional(tag);
    }
}
