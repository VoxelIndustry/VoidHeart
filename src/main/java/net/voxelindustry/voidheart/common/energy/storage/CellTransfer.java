package net.voxelindustry.voidheart.common.energy.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.energy.storage.CellSocketTile.Activity;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * What a held rubrum cell does each tick, shared by the cell socket and the socket wall: it fills from the field while
 * powered and empties into it otherwise, and its motes run to the nearest pylon.
 */
public final class CellTransfer
{
    private static final int        TARGET_REACH = 16;
    private static final Identifier PYLON_ID     = Identifier.fromNamespaceAndPath(MODID, "azurium_pylon");
    private static final Identifier WELL_ID      = Identifier.fromNamespaceAndPath(MODID, "creative_void_well");

    private CellTransfer()
    {
    }

    /**
     * Moves up to rate between the cell and the field at pos, changing the cell's charge in place.
     *
     * @return what the cell did this tick
     */
    public static Activity step(ServerLevel level, BlockPos pos, ItemStack cell, boolean powered, long rate)
    {
        if (cell.isEmpty())
            return Activity.IDLE;

        var charge = RubrumCellItem.charge(cell);
        if (powered)
        {
            var room = RubrumBalance.CELL_CAPACITY - charge;
            var drawn = room > 0 ? VoidEnergy.draw(level, pos, Math.min(rate, room)) : 0;
            if (drawn <= 0)
                return Activity.IDLE;
            RubrumCellItem.setCharge(cell, charge + drawn);
            return Activity.CHARGING;
        }
        if (charge <= 0)
            return Activity.IDLE;
        var offered = VoidEnergy.offer(level, pos, Math.min(rate, charge));
        if (offered <= 0)
            return Activity.IDLE;
        RubrumCellItem.setCharge(cell, charge - offered);
        return Activity.DISCHARGING;
    }

    /**
     * Sends one mote between a cell and its target: out of the cell while it discharges, into it while it charges.
     *
     * @param target where motes run to, or null to rise straight up
     */
    public static void mote(Level level, Vec3 cell, @Nullable Vec3 target, Activity activity)
    {
        var end = target != null ? target : cell.add(0, 3, 0);
        var from = activity == Activity.DISCHARGING ? cell : end;
        var to = activity == Activity.DISCHARGING ? end : cell;
        level.addParticle(RubrumContent.RUBRUM_MOTE, from.x, from.y, from.z, to.x, to.y, to.z);
    }

    /**
     * The bundle on the nearest pylon, else the nearest creative well, which the motes run toward.
     */
    public static @Nullable Vec3 moteTarget(Level level, BlockPos origin)
    {
        var pylon = BuiltInRegistries.BLOCK.getOptional(PYLON_ID).orElse(Blocks.AIR);
        var well = BuiltInRegistries.BLOCK.getOptional(WELL_ID).orElse(Blocks.AIR);
        BlockPos bestPylon = null;
        BlockPos bestWell = null;
        double pylonDistance = Double.MAX_VALUE, wellDistance = Double.MAX_VALUE;
        for (var pos : BlockPos.betweenClosed(origin.offset(-TARGET_REACH, -TARGET_REACH / 2, -TARGET_REACH),
                origin.offset(TARGET_REACH, TARGET_REACH / 2, TARGET_REACH)))
        {
            var state = level.getBlockState(pos);
            if (state.isAir())
                continue;
            var distance = pos.distSqr(origin);
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
}
