package net.voxelindustry.voidheart.common.world.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.voxelindustry.voidheart.common.content.mob.CorruptedBarnacle;
import net.voxelindustry.voidheart.common.content.mob.CrawlerMonster;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;

/**
 * Creates the mobs a plan holds in one chunk, only once as that chunk generates. A cleared dungeon therefore stays cleared.
 */
final class DungeonMobSpawner
{
    private static final int STAGES = CorruptedBarnacle.GROWN + 1;

    private DungeonMobSpawner()
    {
    }

    static void spawn(WorldGenLevel level, DungeonPlan plan, BlockPos origin, int minX, int minZ)
    {
        for (var placement : plan.mobs())
        {
            int x = origin.getX() + placement.x();
            int z = origin.getZ() + placement.z();
            if (x < minX || x >= minX + 16 || z < minZ || z >= minZ + 16)
                continue;

            var pos = new BlockPos(x, origin.getY() + placement.y(), z);
            if (placement.scanHeight() > 0)
                pos = findFloor(level, pos, placement.scanHeight(), placement.mob());
            if (pos != null && isSafe(level, pos))
                spawnAt(level, placement, pos);
        }
    }

    private static BlockPos findFloor(WorldGenLevel level, BlockPos from, int height, DungeonMob mob)
    {
        var pos = new BlockPos.MutableBlockPos();
        for (int up = 0; up < height; up++)
        {
            pos.set(from.getX(), from.getY() + up, from.getZ());
            if (hasRoom(level, pos, mob) && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP) && isSafe(level, pos))
                return pos.immutable();
        }
        return null;
    }

    private static boolean hasRoom(WorldGenLevel level, BlockPos pos, DungeonMob mob)
    {
        for (int up = 0; up < mob.height; up++)
        {
            for (int dz = -mob.halfWidth; dz <= mob.halfWidth; dz++)
            {
                for (int dx = -mob.halfWidth; dx <= mob.halfWidth; dx++)
                {
                    if (!level.getBlockState(pos.offset(dx, up, dz)).isAir())
                        return false;
                }
            }
        }
        return true;
    }

    /**
     * Prepared structures hold fire and magma the plan knows nothing of, and a mob left there would be dead before anyone came.
     */
    private static boolean isSafe(WorldGenLevel level, BlockPos pos)
    {
        var ground = level.getBlockState(pos.below());
        return !ground.is(Blocks.MAGMA_BLOCK) && !ground.is(BlockTags.CAMPFIRES) && level.getFluidState(pos).isEmpty() && level.getFluidState(pos.above()).isEmpty();
    }

    private static void spawnAt(WorldGenLevel level, DungeonMobTable.Placement placement, BlockPos pos)
    {
        var mob = type(placement.mob()).create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (mob == null)
            return;

        float yaw = Math.floorMod(pos.getX() * 31 + pos.getZ() * 17, 360);
        mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
        if (mob instanceof CorruptedBarnacle barnacle)
            barnacle.setStage(Math.floorMod(pos.getX() + pos.getY() + pos.getZ(), STAGES));
        if (mob instanceof CrawlerMonster crawler)
            crawler.settleOn(pos, placement.surface());

        mob.setPersistenceRequired();
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
        level.addFreshEntityWithPassengers(mob);
    }

    private static EntityType<? extends Mob> type(DungeonMob mob)
    {
        return switch (mob)
        {
            case VOID_SKELETON -> VoidHeartEntities.VOID_SKELETON;
            case VOID_GHOUL -> VoidHeartEntities.VOID_GHOUL;
            case WRAITH -> VoidHeartEntities.WRAITH;
            case CRAB_SMALL -> VoidHeartEntities.CORRUPTED_CRAB_SMALL;
            case CRAB_MEDIUM -> VoidHeartEntities.CORRUPTED_CRAB_MEDIUM;
            case CRAB_LARGE -> VoidHeartEntities.CORRUPTED_CRAB_LARGE;
            case BARNACLE -> VoidHeartEntities.CORRUPTED_BARNACLE;
            case FALLEN_NOVICE -> VoidHeartEntities.FALLEN_NOVICE;
            case FALLEN_ADEPT -> VoidHeartEntities.FALLEN_ADEPT;
            case FALLEN_ELDER -> VoidHeartEntities.FALLEN_ELDER;
        };
    }
}
