package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * Suffering essence, drawn by collectors attuned to it from the mobs dying around them. A Malignant Tormenter feeds
 * them straight, each time it torments its souls.
 */
public final class SufferingHarvest
{
    public static final int RANGE      = 6;
    public static final int PER_HEALTH = 10;

    private SufferingHarvest()
    {
    }

    public static void onDeath(LivingEntity killed, DamageSource source)
    {
        if (!(killed instanceof Mob) || !(killed.level() instanceof ServerLevel level))
            return;

        emit(level, BlockPos.containing(killed.getX(), killed.getY(0.5), killed.getZ()), yieldOf(killed.getMaxHealth()));
    }

    /**
     * @return Suffering a death of a mob with this much health gives
     */
    public static int yieldOf(float maxHealth)
    {
        return Math.max(1, Math.round(maxHealth * PER_HEALTH));
    }

    /**
     * Fills the nearest Suffering collectors in range first, what one has no room for goes to the next.
     *
     * @return amount the collectors took in
     */
    public static int emit(ServerLevel level, BlockPos source, int amount)
    {
        var collectors = new ArrayList<RavenousCollectorTile>();
        RelayNetwork.forEachTileInRange(level, source, RANGE, tile ->
        {
            if (tile instanceof RavenousCollectorTile collector && collector.storage().type() == EssenceRegistry.SUFFERING
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
            given += collector.draw(EssenceRegistry.SUFFERING, amount - given, source);
        }
        return given;
    }
}
