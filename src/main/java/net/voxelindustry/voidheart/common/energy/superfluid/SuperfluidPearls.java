package net.voxelindustry.voidheart.common.energy.superfluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.voxelindustry.voidheart.common.energy.pearl.HangingPearlTile;
import org.jspecify.annotations.Nullable;

/**
 * Finds the mature pearl a cradle pours into: the nearest in reach that still has room, ties going to the lowest position so two scans agree.
 */
public final class SuperfluidPearls
{
    private SuperfluidPearls()
    {
    }

    public static @Nullable BlockPos nearestWithRoom(ServerLevel level, BlockPos from)
    {
        var reach = SuperfluidBalance.REACH;
        var reachSqr = reach * reach;
        var span = (int) Math.ceil(reach);
        BlockPos best = null;
        var bestSqr = Double.MAX_VALUE;

        for (var chunkX = SectionPos.blockToSectionCoord(from.getX() - span); chunkX <= SectionPos.blockToSectionCoord(from.getX() + span); chunkX++)
        {
            for (var chunkZ = SectionPos.blockToSectionCoord(from.getZ() - span); chunkZ <= SectionPos.blockToSectionCoord(from.getZ() + span); chunkZ++)
            {
                if (!level.hasChunk(chunkX, chunkZ))
                    continue;
                for (var entity : level.getChunk(chunkX, chunkZ).getBlockEntities().values())
                {
                    if (!(entity instanceof HangingPearlTile pearl) || pearl.isRemoved() || pearl.superfluidRoom() <= 0)
                        continue;
                    var pos = pearl.getBlockPos();
                    var distSqr = pos.distSqr(from);
                    if (distSqr > reachSqr)
                        continue;
                    if (distSqr < bestSqr || distSqr == bestSqr && best != null && pos.asLong() < best.asLong())
                    {
                        best = pos.immutable();
                        bestSqr = distSqr;
                    }
                }
            }
        }
        return best;
    }

    public static @Nullable HangingPearlTile at(ServerLevel level, BlockPos pos)
    {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof HangingPearlTile pearl ? pearl : null;
    }
}
