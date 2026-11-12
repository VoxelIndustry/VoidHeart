package net.voxelindustry.voidheart.common.content.underway;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.mob.Legionary;
import net.voxelindustry.voidheart.common.energy.siphon.DeepSiphonTile;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusMob;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Brings a wave's mobs into the world around a charging hatch. Walkers come from the rooms and corridors beyond the hatch room's walls when they can find a way in from there,
 * so they arrive through its doors; the rest, and any walker with no way in, appear along the room's walls.
 */
final class HatchWaveSpawner
{
    static final String WAVE_TAG = "voidheart_hatch_wave";

    // Blocks from the key, as the larger of the two offsets: the hatch room reaches 12 from it
    private static final int INSIDE_MIN = 8;
    private static final int INSIDE_MAX = 10;
    private static final int OUTSIDE_MIN = 14;
    private static final int OUTSIDE_MAX = 19;
    private static final int OUTSIDE_TRIES = 6;
    private static final int INSIDE_TRIES = 24;

    record Arrival(Mob mob, DaedalusMob kind)
    {
    }

    private HatchWaveSpawner()
    {
    }

    static List<Arrival> spawn(ServerLevel level, UnderwayKeyEntity key, List<DaedalusMob> kinds)
    {
        var arrivals = new ArrayList<Arrival>();
        Legionary waiting = null;
        for (var kind : kinds)
        {
            var near = waiting != null && kind == DaedalusMob.LEGIONARY ? waiting.position() : null;
            var mob = place(level, key, kind, near);
            if (mob == null)
                continue;
            if (mob instanceof Legionary legionary)
            {
                if (waiting == null)
                {
                    waiting = legionary;
                    continue;
                }
                legionary.pairWith(waiting);
                arrivals.add(arrive(level, waiting, kind));
                waiting = null;
            }
            arrivals.add(arrive(level, mob, kind));
        }
        return arrivals;
    }

    private static @Nullable Mob place(ServerLevel level, UnderwayKeyEntity key, DaedalusMob kind, @Nullable Vec3 near)
    {
        var type = DeepSiphonTile.typeOf(kind);
        var random = level.getRandom();
        if (near != null)
        {
            for (int attempt = 0; attempt < INSIDE_TRIES; attempt++)
            {
                var mob = tryAt(level, key, type, kind, near.x + random.nextInt(5) - 2, near.z + random.nextInt(5) - 2, false);
                if (mob != null)
                    return mob;
            }
        }
        if (comesThroughDoors(kind))
        {
            for (int attempt = 0; attempt < OUTSIDE_TRIES; attempt++)
            {
                var mob = tryRing(level, key, type, kind, random, OUTSIDE_MIN, OUTSIDE_MAX, walks(kind));
                if (mob != null)
                    return mob;
            }
        }
        for (int attempt = 0; attempt < INSIDE_TRIES; attempt++)
        {
            var mob = tryRing(level, key, type, kind, random, INSIDE_MIN, INSIDE_MAX, false);
            if (mob != null)
                return mob;
        }
        return null;
    }

    /** Walkers and the wraiths, which cross walls. Crawlers, hoppers and eyes find their own way poorly through doors. */
    private static boolean comesThroughDoors(DaedalusMob kind)
    {
        return walks(kind) || kind == DaedalusMob.WRAITH || kind == DaedalusMob.STALKING_WRAITH;
    }

    private static boolean walks(DaedalusMob kind)
    {
        return switch (kind)
        {
            case VOID_SKELETON, VOID_GHOUL, FALLEN_NOVICE, FALLEN_ADEPT, FALLEN_ELDER, FALLEN_BISHOP, LEGIONARY, CENTURION, MALIGNANT_BOULDER -> true;
            default -> false;
        };
    }

    private static @Nullable Mob tryRing(ServerLevel level, UnderwayKeyEntity key, EntityType<? extends Mob> type, DaedalusMob kind, RandomSource random, int min, int max, boolean needsPath)
    {
        int far = min + random.nextInt(max - min + 1);
        int along = random.nextInt(far * 2 + 1) - far;
        boolean onX = random.nextBoolean();
        int side = random.nextBoolean() ? far : -far;
        return tryAt(level, key, type, kind, key.getX() + (onX ? side : along), key.getZ() + (onX ? along : side), needsPath);
    }

    private static @Nullable Mob tryAt(ServerLevel level, UnderwayKeyEntity key, EntityType<? extends Mob> type, DaedalusMob kind, double x, double z, boolean needsPath)
    {
        var feet = BlockPos.containing(x, key.getY(), z);
        var district = DistrictPos.at(key.getBlockX(), key.getBlockZ());
        if (!DistrictPos.isInsideDistrict(feet.getX(), feet.getZ()) || !DistrictPos.at(feet.getX(), feet.getZ()).equals(district))
            return null;
        if (!level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP))
            return null;
        double y = feet.getY() + (kind.placing == DaedalusMob.Placing.AIR ? 1.5 : 0);
        if (!level.noCollision(type.getSpawnAABB(feet.getX() + 0.5, y, feet.getZ() + 0.5)))
            return null;
        var mob = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (mob == null)
            return null;
        float yaw = (float) (Math.toDegrees(Math.atan2(key.getZ() - feet.getZ() - 0.5, key.getX() - feet.getX() - 0.5)) - 90);
        mob.snapTo(feet.getX() + 0.5, y, feet.getZ() + 0.5, yaw, 0);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
        if (needsPath)
        {
            mob.setOnGround(true);
            var path = mob.getNavigation().createPath(key, 0);
            if (path == null || !path.canReach())
                return null;
        }
        return mob;
    }

    private static Arrival arrive(ServerLevel level, Mob mob, DaedalusMob kind)
    {
        mob.setPersistenceRequired();
        mob.addTag(WAVE_TAG);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
        level.addFreshEntity(mob);
        level.sendParticles(ParticleTypes.SCULK_SOUL, mob.getX(), mob.getY() + 0.2, mob.getZ(), 8, 0.35, 0.4, 0.35, 0.03);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 0.35F, 1.4F + level.getRandom().nextFloat() * 0.2F);
        return new Arrival(mob, kind);
    }
}
