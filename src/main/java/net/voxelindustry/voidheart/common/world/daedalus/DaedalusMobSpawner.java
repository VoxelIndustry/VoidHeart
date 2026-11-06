package net.voxelindustry.voidheart.common.world.daedalus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.voxelindustry.voidheart.common.content.mob.CorruptedBarnacle;
import net.voxelindustry.voidheart.common.content.mob.KernacianDragoon;
import net.voxelindustry.voidheart.common.content.mob.Legionary;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Creates a chunk's planned mobs at the spawn step, once, as the chunk first generates. Their nests were grown earlier, by {@link DaedalusChunkWriter}. Neighbouring chunks may not be built yet then, which is why every spot keeps to its own cell.
 */
final class DaedalusMobSpawner
{
    private static final int STAGES = CorruptedBarnacle.GROWN + 1;
    private static final ThreadLocal<DaedalusCanvas> CANVAS = ThreadLocal.withInitial(() -> new DaedalusCanvas(16, 16));

    private DaedalusMobSpawner()
    {
    }

    static void spawn(WorldGenRegion region)
    {
        var chunk = region.getCenter();
        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();
        if (!DistrictPos.isInsideDistrict(minX, minZ))
            return;

        var plan = DistrictPlans.get(region.getSeed(), DistrictPos.at(minX, minZ));
        var placements = plan.mobs().inChunk(chunk.x(), chunk.z());
        if (placements.isEmpty())
            return;

        var canvas = CANVAS.get();
        canvas.reset(minX, minZ);
        DistrictPainter.paint(plan, canvas);

        // Nests grew at the terrain step, found first in their cells just as here, so mobs are kept off them
        var taken = new HashMap<Long, Set<Long>>();
        for (var placement : placements)
        {
            if (placement.mob() == DaedalusMob.BARNACLE_NEST)
                DaedalusMobSpots.find(canvas, plan.pos, placement, taken.computeIfAbsent(cellKey(placement), key -> new HashSet<>()));
        }

        Legionary waiting = null;
        int waitingGroup = -1;
        for (var placement : placements)
        {
            if (!placement.mob().isMob())
                continue;
            var spot = DaedalusMobSpots.find(canvas, plan.pos, placement, taken.computeIfAbsent(cellKey(placement), key -> new HashSet<>()));
            if (spot == null)
                continue;
            var pos = new BlockPos(spot.x(), spot.y(), spot.z());

            var mob = create(region, placement, pos);
            if (mob == null)
                continue;

            // Each half of a pair is placed by the plan, as the one a Legionary brings along could land where nothing is built yet
            if (mob instanceof Legionary legionary)
            {
                if (waiting != null && waitingGroup == placement.group())
                {
                    legionary.pairWith(waiting);
                    finish(region, waiting, pos);
                    finish(region, legionary, pos);
                    waiting = null;
                    continue;
                }
                waiting = legionary;
                waitingGroup = placement.group();
                continue;
            }
            finish(region, mob, pos);
        }
    }

    private static Mob create(WorldGenRegion region, DaedalusMobPlan.Placement placement, BlockPos pos)
    {
        var mob = type(placement.mob()).create(region.getLevel(), EntitySpawnReason.STRUCTURE);
        if (mob == null)
            return null;

        float yaw = placement.facing() == DaedalusMobPlan.ANYWHERE ? (float) Math.floorMod(placement.salt(), 360) : Compass.yaw(placement.facing());
        // A spot may be on a carpet, which they stand on rather than in
        var underfoot = region.getBlockState(pos).getCollisionShape(region, pos);
        double y = pos.getY() + (underfoot.isEmpty() ? 0 : underfoot.max(Direction.Axis.Y));
        mob.snapTo(pos.getX() + 0.5, y, pos.getZ() + 0.5, yaw, 0);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
        if (mob instanceof CorruptedBarnacle barnacle)
            barnacle.setStage(Math.floorMod(pos.getX() + pos.getY() + pos.getZ(), STAGES));
        if (mob instanceof KernacianDragoon dragoon)
            dragoon.guard(pos, yaw);
        return mob;
    }

    private static void finish(WorldGenRegion region, Mob mob, BlockPos pos)
    {
        mob.setPersistenceRequired();
        mob.finalizeSpawn(region, region.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
        region.addFreshEntityWithPassengers(mob);
    }

    private static long cellKey(DaedalusMobPlan.Placement placement)
    {
        return DistrictPlan.index(placement.i(), placement.k(), placement.j());
    }

    private static EntityType<? extends Mob> type(DaedalusMob mob)
    {
        return switch (mob)
        {
            case VOID_SKELETON -> VoidHeartEntities.VOID_SKELETON;
            case VOID_GHOUL -> VoidHeartEntities.VOID_GHOUL;
            case WRAITH -> VoidHeartEntities.WRAITH;
            case STALKING_WRAITH -> VoidHeartEntities.STALKING_WRAITH;
            case FALLEN_BISHOP -> VoidHeartEntities.FALLEN_BISHOP;
            case MALIGNANT_BOULDER -> VoidHeartEntities.MALIGNANT_BOULDER;
            case LEGIONARY -> VoidHeartEntities.LEGIONARY;
            case CENTURION -> VoidHeartEntities.CENTURION;
            case KERNACIAN_DRAGOON -> VoidHeartEntities.KERNACIAN_DRAGOON;
            case FALLEN_NOVICE -> VoidHeartEntities.FALLEN_NOVICE;
            case FALLEN_ADEPT -> VoidHeartEntities.FALLEN_ADEPT;
            case FALLEN_ELDER -> VoidHeartEntities.FALLEN_ELDER;
            case CRAB_SMALL -> VoidHeartEntities.CORRUPTED_CRAB_SMALL;
            case CRAB_MEDIUM -> VoidHeartEntities.CORRUPTED_CRAB_MEDIUM;
            case CRAB_LARGE -> VoidHeartEntities.CORRUPTED_CRAB_LARGE;
            case BARNACLE -> VoidHeartEntities.CORRUPTED_BARNACLE;
            case EYE_MAW -> VoidHeartEntities.EYE_MAW;
            case ARCANE_EYE -> VoidHeartEntities.ARCANE_EYE;
            case BARNACLE_NEST -> throw new IllegalArgumentException("A nest is a block");
        };
    }
}
