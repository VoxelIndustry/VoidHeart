package net.voxelindustry.voidheart.common.energy.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundles;
import net.voxelindustry.voidheart.common.content.crystal.CrystalHabit;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Holds a rubrum cell and moves its energy: out into the field covering it, or in from that field while powered. The cell's habit changes how, as
 * {@link CellHabits} says, and a Wrung cell drinks the harm done to living things around it.
 */
public class CellSocketTile extends TileBase
{
    public enum Activity
    {
        IDLE,
        DISCHARGING,
        CHARGING,
        // A Searing cell letting go of its whole charge at once
        BURSTING
    }

    /** Sockets holding a Wrung cell on the server, which harm nearby is offered to. */
    private static final Set<CellSocketTile> DRINKERS = Collections.newSetFromMap(new WeakHashMap<>());

    private static final int WISPS_MAX = 6;

    private static final int    SYNC_PERIOD     = 10;
    private static final int    TARGET_RESCAN   = 100;
    private static final int    TARGET_REACH    = 16;
    private static final Identifier PYLON_ID    = Identifier.fromNamespaceAndPath(MODID, "azurium_pylon");
    private static final Identifier WELL_ID     = Identifier.fromNamespaceAndPath(MODID, "creative_void_well");

    private ItemStack cell = ItemStack.EMPTY;
    private Activity  activity = Activity.IDLE;
    private long      syncedCharge;
    private long      wrungBank;

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
        var habit = CrystalBundles.habitOf(cell);
        if (!cell.isEmpty() && habit == CrystalHabit.WRUNG)
            DRINKERS.add(this);
        else
        {
            DRINKERS.remove(this);
            wrungBank = 0;
        }

        if (!cell.isEmpty())
        {
            var charge = RubrumCellItem.charge(cell);
            var drunk = CellHabits.drink(wrungBank, charge);
            if (drunk > 0)
            {
                wrungBank -= drunk;
                charge += drunk;
                RubrumCellItem.setCharge(cell, charge);
                setChanged();
            }
            switch (CellHabits.way(habit, level.hasNeighborSignal(worldPosition), charge))
            {
                case CHARGE ->
                {
                    var room = RubrumBalance.CELL_CAPACITY - charge;
                    var drawn = VoidEnergy.draw(level, worldPosition, Math.min(RubrumBalance.CELL_SOCKET_RATE, room));
                    if (drawn > 0)
                    {
                        RubrumCellItem.setCharge(cell, charge + drawn);
                        next = Activity.CHARGING;
                    }
                }
                case DISCHARGE ->
                {
                    var offered = VoidEnergy.offer(level, worldPosition, Math.min(CellHabits.dischargeRate(habit), charge));
                    if (offered > 0)
                    {
                        RubrumCellItem.setCharge(cell, charge - offered);
                        next = habit == CrystalHabit.SEARING ? Activity.BURSTING : Activity.DISCHARGING;
                    }
                }
                case IDLE ->
                {
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

    /** Health a creature had before the hit being taken, so a killing blow is counted no deeper than it could go. */
    private static final java.util.Map<LivingEntity, Float> HEALTH_BEFORE = new WeakHashMap<>();
    /** Creatures whose killing blow was counted this tick, so a totem that saves them doesn't count the same hit again. */
    private static final java.util.Map<LivingEntity, Long>  KILL_COUNTED  = new WeakHashMap<>();

    /**
     * Before a hit lands: notes the health it may take, while any Wrung cell is there to drink it.
     */
    public static boolean beforeHarm(LivingEntity entity)
    {
        if (!DRINKERS.isEmpty())
            HEALTH_BEFORE.put(entity, entity.getHealth());
        return true;
    }

    /**
     * A hit the creature survived. One whose killing blow was already counted, as when a totem saves it, isn't counted again.
     */
    public static void afterHarm(LivingEntity entity, float damage)
    {
        HEALTH_BEFORE.remove(entity);
        var counted = KILL_COUNTED.remove(entity);
        if (counted != null && counted == entity.level().getGameTime())
            return;
        onHarm(entity, damage);
    }

    /**
     * A hit that would kill: counted up to the health the creature had, since AFTER_DAMAGE never hears of it.
     *
     * @return always true, never keeping anything alive
     */
    public static boolean killingBlow(LivingEntity entity, float damage)
    {
        var before = HEALTH_BEFORE.remove(entity);
        if (DRINKERS.isEmpty())
            return true;
        KILL_COUNTED.put(entity, entity.level().getGameTime());
        onHarm(entity, Math.min(damage, before != null ? before : entity.getMaxHealth()));
        return true;
    }

    /**
     * Offers harm done to a living thing to the Wrung cells around it. Each banks it and shows crimson wisps rising from the hurt into the cell.
     */
    public static void onHarm(LivingEntity entity, float damage)
    {
        if (!(damage > 0) || DRINKERS.isEmpty() || !(entity.level() instanceof ServerLevel level))
            return;
        var at = entity.position().add(0, entity.getBbHeight() * 0.6, 0);
        for (var socket : List.copyOf(DRINKERS))
        {
            if (socket.isRemoved() || socket.level != level || Vec3.atCenterOf(socket.worldPosition).distanceToSqr(at) > RubrumBalance.WRUNG_RANGE * RubrumBalance.WRUNG_RANGE)
                continue;
            socket.drinkHarm(level, at, damage, entity.getBbWidth());
        }
    }

    private void drinkHarm(ServerLevel level, Vec3 from, float damage, float width)
    {
        var banked = CellHabits.bank(wrungBank, damage);
        if (banked == wrungBank)
            return;
        wrungBank = banked;
        setChanged();
        var cellTop = Vec3.atBottomCenterOf(worldPosition).add(0, CellSocketGeometry.CELL_TOP, 0);
        var random = level.getRandom();
        int wisps = Mth.clamp(Mth.ceil(damage * 0.8F), 2, WISPS_MAX);
        for (int i = 0; i < wisps; i++)
        {
            double x = from.x + (random.nextDouble() - 0.5) * width, y = from.y + (random.nextDouble() - 0.5) * 0.4, z = from.z + (random.nextDouble() - 0.5) * width;
            // A count of 0 hands the target to the mote as its motion, unscaled
            level.sendParticles(RubrumContent.WRUNG_MOTE, false, true, x, y, z, 0, cellTop.x, cellTop.y, cellTop.z, 1);
        }
    }

    /**
     * Harm banked by a Wrung cell, not yet taken in.
     */
    public long wrungBank()
    {
        return wrungBank;
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
        // A Searing cell letting go streams its motes out
        nextMote = activity == Activity.BURSTING ? time + 1 : time + 5 + random.nextInt(6);
        var cellTop = Vec3.atBottomCenterOf(worldPosition).add(0, CellSocketGeometry.CELL_TOP, 0);
        var target = moteTarget != null ? moteTarget : cellTop.add(0, 3, 0);
        boolean out = activity == Activity.DISCHARGING || activity == Activity.BURSTING;
        var start = out ? cellTop : target;
        var end = out ? target : cellTop;
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
        wrungBank = 0;
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
    public void setRemoved()
    {
        super.setRemoved();
        DRINKERS.remove(this);
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
        wrungBank = Math.clamp(tag.getLongOr("wrungBank", 0L), 0, RubrumBalance.WRUNG_BANK);
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        tag.store("cell", ItemStack.OPTIONAL_CODEC, cell);
        tag.putLong("wrungBank", wrungBank);
        tag.putInt("activity", activity.ordinal());
        super.saveAdditional(tag);
    }
}
