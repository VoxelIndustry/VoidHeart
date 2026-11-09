package net.voxelindustry.voidheart.common.energy.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

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
    private static final int    TARGET_REACH    = 16;
    private static final Identifier PYLON_ID    = Identifier.fromNamespaceAndPath(MODID, "azurium_pylon");
    private static final Identifier WELL_ID     = Identifier.fromNamespaceAndPath(MODID, "creative_void_well");

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
        var next = Activity.IDLE;
        if (!cell.isEmpty())
        {
            var charge = RubrumCellItem.charge(cell);
            if (level.hasNeighborSignal(worldPosition))
            {
                var room = RubrumBalance.CELL_CAPACITY - charge;
                var drawn = room > 0 ? VoidEnergy.draw(level, worldPosition, Math.min(RubrumBalance.CELL_SOCKET_RATE, room)) : 0;
                if (drawn > 0)
                {
                    RubrumCellItem.setCharge(cell, charge + drawn);
                    next = Activity.CHARGING;
                }
            }
            else if (charge > 0)
            {
                var offered = VoidEnergy.offer(level, worldPosition, Math.min(RubrumBalance.CELL_SOCKET_RATE, charge));
                if (offered > 0)
                {
                    RubrumCellItem.setCharge(cell, charge - offered);
                    next = Activity.DISCHARGING;
                }
            }
        }

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
            moteTarget = findTarget();
        }
        if (time < nextMote)
            return;

        var random = level.getRandom();
        nextMote = time + 5 + random.nextInt(6);
        var cellTop = Vec3.atBottomCenterOf(worldPosition).add(0, CellSocketGeometry.CELL_TOP, 0);
        var target = moteTarget != null ? moteTarget : cellTop.add(0, 3, 0);
        var start = activity == Activity.DISCHARGING ? cellTop : target;
        var end = activity == Activity.DISCHARGING ? target : cellTop;
        level.addParticle(RubrumContent.RUBRUM_MOTE, start.x, start.y, start.z, end.x, end.y, end.z);
    }

    /**
     * The bundle on the nearest pylon, else the nearest creative well, which the motes run toward.
     */
    private @Nullable Vec3 findTarget()
    {
        var pylon = BuiltInRegistries.BLOCK.getOptional(PYLON_ID).orElse(Blocks.AIR);
        var well = BuiltInRegistries.BLOCK.getOptional(WELL_ID).orElse(Blocks.AIR);
        BlockPos bestPylon = null;
        BlockPos bestWell = null;
        double pylonDistance = Double.MAX_VALUE, wellDistance = Double.MAX_VALUE;
        for (var pos : BlockPos.betweenClosed(worldPosition.offset(-TARGET_REACH, -TARGET_REACH / 2, -TARGET_REACH),
                worldPosition.offset(TARGET_REACH, TARGET_REACH / 2, TARGET_REACH)))
        {
            var state = level.getBlockState(pos);
            if (state.isAir())
                continue;
            var distance = pos.distSqr(worldPosition);
            if (pylon != Blocks.AIR && state.is(pylon) && distance < pylonDistance)
            {
                pylonDistance = distance;
                bestPylon = pos.immutable();
            }
            else if (well != Blocks.AIR && state.is(well) && distance < wellDistance)
            {
                wellDistance = distance;
                bestWell = pos.immutable();
            }
        }
        if (bestPylon != null)
            return Vec3.atCenterOf(bestPylon.above());
        return bestWell != null ? Vec3.atCenterOf(bestWell) : null;
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
