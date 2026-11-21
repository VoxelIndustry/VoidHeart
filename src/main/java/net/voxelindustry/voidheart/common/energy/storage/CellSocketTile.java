package net.voxelindustry.voidheart.common.energy.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.steamlayer.tile.TileBase;
import org.jspecify.annotations.Nullable;

/**
 * Holds a rubrum cell and moves its energy: out into the field covering it, or in from that field while powered.
 */
public class CellSocketTile extends TileBase
{
    public enum Activity
    {
        IDLE,
        DISCHARGING,
        CHARGING
    }

    private static final int    SYNC_PERIOD     = 10;
    private static final int    TARGET_RESCAN   = 100;

    private ItemStack cell = ItemStack.EMPTY;
    private Activity  activity = Activity.IDLE;
    private long      syncedCharge;

    // Client only
    private @Nullable Vec3 moteTarget;
    private long           targetScannedAt = Long.MIN_VALUE;
    private long           nextMote;

    public CellSocketTile(BlockPos pos, BlockState state)
    {
        super(RubrumContent.CELL_SOCKET_TILE, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CellSocketTile tile)
    {
        if (level.isClientSide())
            tile.clientTick();
        else
            tile.serverTick((ServerLevel) level);
    }

    private void serverTick(ServerLevel level)
    {
        var next = CellTransfer.step(level, worldPosition, cell, level.hasNeighborSignal(worldPosition), RubrumBalance.CELL_SOCKET_RATE);

        var charge = RubrumCellItem.charge(cell);
        if (charge != syncedCharge)
            setChanged();
        if (next != activity || (charge != syncedCharge && level.getGameTime() % SYNC_PERIOD == 0))
        {
            activity = next;
            syncedCharge = charge;
            sync();
        }
    }

    private void clientTick()
    {
        if (activity == Activity.IDLE)
            return;

        var time = level.getGameTime();
        if (time - targetScannedAt >= TARGET_RESCAN)
        {
            targetScannedAt = time;
            moteTarget = CellTransfer.moteTarget(level, worldPosition);
        }
        if (time < nextMote)
            return;

        nextMote = time + 5 + level.getRandom().nextInt(6);
        CellTransfer.mote(level, Vec3.atBottomCenterOf(worldPosition).add(0, CellSocketGeometry.CELL_TOP, 0), moteTarget, activity);
    }

    public ItemStack getCell()
    {
        return cell;
    }

    public void setCell(ItemStack cell)
    {
        this.cell = cell.copy();
        syncedCharge = RubrumCellItem.charge(this.cell);
        activity = Activity.IDLE;
        setChanged();
        sync();
    }

    public Activity activity()
    {
        return activity;
    }

    public float fill()
    {
        return cell.isEmpty() ? 0 : Mth.clamp(RubrumCellItem.fill(cell), 0, 1);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        if (level != null && !cell.isEmpty())
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, cell);
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);
        cell = tag.read("cell", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        activity = Activity.values()[Mth.clamp(tag.getIntOr("activity", 0), 0, Activity.values().length - 1)];
        syncedCharge = RubrumCellItem.charge(cell);
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        tag.store("cell", ItemStack.OPTIONAL_CODEC, cell);
        tag.putInt("activity", activity.ordinal());
        super.saveAdditional(tag);
    }
}
