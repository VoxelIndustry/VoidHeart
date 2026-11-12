package net.voxelindustry.voidheart.common.content.underway.security;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.underway.UnderwayFort;
import net.voxelindustry.voidheart.common.world.underway.UnderwayPlans;
import net.voxelindustry.voidheart.common.world.underway.UnderwayUnit;
import net.voxelindustry.voidheart.common.world.underway.UnderwayUnitKind;
import org.jspecify.annotations.Nullable;

/**
 * Posts a chunk's share of its fort's security at the spawn step, once, as the chunk first generates. Nothing takes the place of a unit that was destroyed, which is what keeps a cleared section safe: only a fitter of the same section, while it still works, stands a wreck up again.
 */
public final class UnderwayUnitSpawner
{
    private UnderwayUnitSpawner()
    {
    }

    public static void spawn(WorldGenRegion region)
    {
        var chunk = region.getCenter();
        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();
        if (!DistrictPos.isInsideDistrict(minX, minZ))
            return;

        var district = DistrictPos.at(minX, minZ);
        var fort = UnderwayPlans.fort(region.getSeed(), district);
        var frame = fort.frame();
        var units = fort.units();
        for (int index = 0; index < units.size(); index++)
        {
            var unit = units.get(index);
            int x = frame.x(unit.along(), unit.across());
            int z = frame.z(unit.along(), unit.across());
            if (x >> 4 != chunk.x() || z >> 4 != chunk.z())
                continue;
            var mob = create(region, region.getLevel(), district, fort, index);
            if (mob != null)
                region.addFreshEntityWithPassengers(mob);
        }
    }

    /** Stands a unit of a fort where it was posted, in a world that already holds the fort. */
    public static void post(ServerLevel level, DistrictPos district, int index)
    {
        var fort = UnderwayPlans.fort(level.getSeed(), district);
        if (index < 0 || index >= fort.units().size())
            return;
        var mob = create(level, level, district, fort, index);
        if (mob != null)
            level.addFreshEntityWithPassengers(mob);
    }

    private static @Nullable Mob create(ServerLevelAccessor world, ServerLevel level, DistrictPos district, UnderwayFort fort, int index)
    {
        var unit = fort.units().get(index);
        var frame = fort.frame();
        int x = frame.x(unit.along(), unit.across());
        int z = frame.z(unit.along(), unit.across());
        var mob = type(unit.kind()).create(level, EntitySpawnReason.STRUCTURE);
        if (mob == null)
            return null;
        int facing = frame.compass(unit.faceAlong(), unit.faceAcross());
        float yaw = Compass.yaw(facing);
        double back = backInBlock(unit.kind());
        mob.snapTo(x + 0.5 - Compass.dx(facing) * back, unit.y() + heightInBlock(unit), z + 0.5 - Compass.dz(facing) * back, yaw, 0);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
        if (mob instanceof MountedUnit mounted)
            mounted.link().post(district, index, unit);
        else if (mob instanceof SecurityMob walker)
        {
            walker.link().post(district, index, unit);
            walker.postHere();
        }
        mob.setPersistenceRequired();
        mob.finalizeSpawn(world, world.getCurrentDifficultyAt(new BlockPos(x, unit.y(), z)), EntitySpawnReason.STRUCTURE, null);
        return mob;
    }

    /** @return how far over the foot of its block a unit's feet are */
    public static double heightInBlock(UnderwayUnit unit)
    {
        return switch (unit.kind())
        {
            case SENTINEL_EYE -> (1 - SentinelEye.HEIGHT) / 2;
            case TURRET_CUPOLA -> -TurretCupola.HANG;
            case EMBRASURE_GUN -> 0.05;
            case TRIPWIRE -> Tripwire.LENS_HEIGHT - Tripwire.SIZE / 2;
            // A sapper hangs folded right under the grille of its duct, which reaches seven sixteenths into the block above
            case SAPPER -> 1 + 7 / 16.0 - Sapper.HEIGHT - 0.03;
            case AUTOMATON, TURRET_WELL, SEARCHLIGHT, MANTLET, RAM_CART, FITTER -> 0;
        };
    }

    /** @return how far behind the middle of its block a unit sits, against the wall it hangs on */
    public static double backInBlock(UnderwayUnitKind kind)
    {
        return kind == UnderwayUnitKind.TRIPWIRE ? Tripwire.BACK : 0;
    }

    public static EntityType<? extends Mob> type(UnderwayUnitKind kind)
    {
        return switch (kind)
        {
            case SENTINEL_EYE -> VoidHeartEntities.SENTINEL_EYE;
            case TURRET_CUPOLA -> VoidHeartEntities.TURRET_CUPOLA;
            case EMBRASURE_GUN -> VoidHeartEntities.EMBRASURE_GUN;
            case AUTOMATON -> VoidHeartEntities.AUTOMATON;
            case TRIPWIRE -> VoidHeartEntities.TRIPWIRE;
            case TURRET_WELL -> VoidHeartEntities.TURRET_WELL;
            case SEARCHLIGHT -> VoidHeartEntities.SEARCHLIGHT;
            case MANTLET -> VoidHeartEntities.MANTLET;
            case RAM_CART -> VoidHeartEntities.RAM_CART;
            case SAPPER -> VoidHeartEntities.SAPPER;
            case FITTER -> VoidHeartEntities.FITTER;
        };
    }
}
