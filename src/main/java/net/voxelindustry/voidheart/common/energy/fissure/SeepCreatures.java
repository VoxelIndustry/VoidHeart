package net.voxelindustry.voidheart.common.energy.fissure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.common.content.mob.CrawlerMonster;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;

import java.util.List;

/**
 * Weak void creatures coming through a seep: Void Skeletons, Void Ghouls and small Corrupted Crabs, on open ground inside the seep's current radius,
 * never right next to the crack or a player. They are ordinary monsters once through and despawn like any other.
 */
public final class SeepCreatures
{
    // Nothing comes through within this many blocks of the crack, or of a player
    private static final double MIN_DISTANCE   = 3;
    private static final double PLAYER_BUFFER  = 4;
    private static final int    TRIES          = 10;
    private static final int    SEARCH_ABOVE   = 4;
    private static final int    SEARCH_BELOW   = 6;

    private SeepCreatures()
    {
    }

    public static List<EntityType<? extends Mob>> types()
    {
        return List.of(VoidHeartEntities.VOID_SKELETON, VoidHeartEntities.VOID_GHOUL, VoidHeartEntities.CORRUPTED_CRAB_SMALL);
    }

    /**
     * Seep creatures alive within the seep's radius of the crack.
     */
    public static int alive(ServerLevel level, VoidFissureTile tile, float radius)
    {
        var center = net.minecraft.world.phys.Vec3.atCenterOf(tile.getBlockPos());
        var types = types();
        return level.getEntitiesOfClass(Mob.class, new AABB(center, center).inflate(radius),
                mob -> mob.isAlive() && types.contains(mob.getType()) && mob.distanceToSqr(center) <= radius * radius).size();
    }

    /**
     * One attempt, made every {@link SeepMath#spawnIntervalTicks} by the tile.
     *
     * @return the creature that came through, or null
     */
    public static Mob tryBringThrough(ServerLevel level, VoidFissureTile tile, long now)
    {
        if (tile.isClosing() || level.getDifficulty() == Difficulty.PEACEFUL || !level.isSpawningMonsters())
            return null;
        var radius = tile.seepRadius(now);
        if (radius <= MIN_DISTANCE + 1 || alive(level, tile, radius) >= SeepMath.maxAlive(tile.size()))
            return null;

        var random = level.getRandom();
        var origin = tile.getBlockPos();
        for (int attempt = 0; attempt < TRIES; attempt++)
        {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = MIN_DISTANCE + random.nextDouble() * (radius - MIN_DISTANCE);
            var column = BlockPos.containing(origin.getX() + 0.5 + Math.cos(angle) * distance, origin.getY(), origin.getZ() + 0.5 + Math.sin(angle) * distance);
            // Reading a column at the seep's edge must never load or generate its chunk
            if (!level.isPositionEntityTicking(column))
                continue;
            var feet = floor(level, column);
            if (feet == null || level.getNearestPlayer(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, PLAYER_BUFFER, false) != null)
                continue;

            var types = types();
            var mob = types.get(random.nextInt(types.size())).create(level, EntitySpawnReason.EVENT);
            if (mob == null)
                return null;
            mob.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, random.nextFloat() * 360, 0);
            if (mob instanceof CrawlerMonster crawler)
                crawler.settleOn(feet, Direction.UP);
            if (!level.noCollision(mob) || level.containsAnyLiquid(mob.getBoundingBox()))
            {
                mob.discard();
                continue;
            }

            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(feet), EntitySpawnReason.EVENT, null);
            level.addFreshEntity(mob);
            level.sendParticles(ParticleTypes.SQUID_INK, mob.getX(), mob.getY() + 0.2, mob.getZ(), 10, 0.3, 0.1, 0.3, 0.02);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getY() + 0.5, mob.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
            return mob;
        }
        return null;
    }

    /**
     * Open ground near the crack's height in this column: two free blocks over a sturdy top.
     */
    private static BlockPos floor(ServerLevel level, BlockPos column)
    {
        var cursor = column.mutable();
        for (int dy = SEARCH_ABOVE; dy >= -SEARCH_BELOW; dy--)
        {
            cursor.setY(column.getY() + dy);
            var below = cursor.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                    && level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()
                    && level.getBlockState(cursor.above()).getCollisionShape(level, cursor.above()).isEmpty()
                    && level.getFluidState(cursor).isEmpty())
                return cursor.immutable();
        }
        return null;
    }
}
