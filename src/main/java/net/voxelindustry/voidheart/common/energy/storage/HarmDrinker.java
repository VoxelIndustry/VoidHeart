package net.voxelindustry.voidheart.common.energy.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A block entity holding Wrung cells, offered the harm done to living things around it by {@link CellSocketTile#onHarm}.
 */
public interface HarmDrinker
{
    int WISPS_MAX = 6;

    /**
     * Banks harm done at from into this holder's Wrung cells.
     */
    void drinkHarm(ServerLevel level, Vec3 from, float damage, float width);

    /**
     * Whether harm done at a point in a level is near enough to be drunk by a holder at pos.
     */
    static boolean reaches(boolean removed, @Nullable Level own, BlockPos pos, ServerLevel level, Vec3 at)
    {
        return !removed && own == level && Vec3.atCenterOf(pos).distanceToSqr(at) <= RubrumBalance.WRUNG_RANGE * RubrumBalance.WRUNG_RANGE;
    }

    /**
     * Crimson wisps rising from what was hurt into the cell that drank it, more for a deeper hit.
     */
    static void sendWisps(ServerLevel level, Vec3 from, float damage, float width, Vec3 cell)
    {
        var random = level.getRandom();
        int wisps = Mth.clamp(Mth.ceil(damage * 0.8F), 2, WISPS_MAX);
        for (int i = 0; i < wisps; i++)
        {
            double x = from.x + (random.nextDouble() - 0.5) * width, y = from.y + (random.nextDouble() - 0.5) * 0.4, z = from.z + (random.nextDouble() - 0.5) * width;
            // A count of 0 hands the target to the mote as its motion, unscaled
            level.sendParticles(RubrumContent.WRUNG_MOTE, false, true, x, y, z, 0, cell.x, cell.y, cell.z, 1);
        }
    }

    /**
     * Whether harm at a point reaches this holder.
     */
    boolean reaches(ServerLevel level, Vec3 at);
}
