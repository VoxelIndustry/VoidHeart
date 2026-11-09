package net.voxelindustry.voidheart.common.energy.spike;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import net.voxelindustry.voidheart.common.content.essence.RelayNetwork;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * Contravoid, gathered by collectors attuned to it with an azurium shard from a spike bleeding a tainted node. Modelled on SufferingHarvest.
 */
public final class ContravoidHarvest
{
    private ContravoidHarvest()
    {
    }

    /**
     * Fills the nearest Contravoid collectors in range first, what one has no room for goes to the next. What none takes is lost.
     *
     * @return amount the collectors took in
     */
    public static int emit(ServerLevel level, BlockPos source, int amount)
    {
        if (amount <= 0)
            return 0;

        var collectors = new ArrayList<RavenousCollectorTile>();
        RelayNetwork.forEachTileInRange(level, source, LeySpikeBalance.CONTRAVOID_RANGE, tile ->
        {
            if (tile instanceof RavenousCollectorTile collector && collector.storage().type() == EssenceRegistry.CONTRAVOID
                    && collector.storage().spaceLeft() > 0)
                collectors.add(collector);
        });
        if (collectors.isEmpty())
            return 0;

        var center = Vec3.atCenterOf(source);
        collectors.sort(Comparator.comparingDouble(collector -> Vec3.atCenterOf(collector.getBlockPos()).distanceToSqr(center)));

        var given = 0;
        for (var collector : collectors)
        {
            if (given >= amount)
                break;
            given += collector.draw(EssenceRegistry.CONTRAVOID, amount - given, source);
        }
        return given;
    }
}
