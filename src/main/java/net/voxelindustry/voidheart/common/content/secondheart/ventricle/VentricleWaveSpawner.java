package net.voxelindustry.voidheart.common.content.secondheart.ventricle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.energy.siphon.DeepSiphonTile;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusMob;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonMobSpawner;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTheme;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

import static net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleBalance.*;

/**
 * Opens a wave's tears on open ground around the cradle, each bringing one creature, or a Legionary pair together.
 */
final class VentricleWaveSpawner
{
    private static final int TRIES        = 24;
    private static final int GROUND_REACH = 5;

    private VentricleWaveSpawner()
    {
    }

    static int callWave(ServerLevel level, BlockPos cradle, int wave, UUID celebrant)
    {
        if (level.getDifficulty() == Difficulty.PEACEFUL)
            return 0;
        var random = new SplittableRandom(level.getSeed() ^ cradle.asLong() * 31 + level.getGameTime());
        var groups = new ArrayList<List<EntityType<? extends Mob>>>();
        if (VentricleWaves.fromDaedalus(wave))
        {
            var district = new DistrictPos(random.nextInt(-4096, 4096), random.nextInt(-4096, 4096));
            var mobs = VentricleWaves.daedalusWave(wave, district, random);
            for (int i = 0; i < mobs.size(); i++)
            {
                var kind = mobs.get(i);
                if (kind == DaedalusMob.LEGIONARY && i + 1 < mobs.size() && mobs.get(i + 1) == DaedalusMob.LEGIONARY)
                {
                    groups.add(List.of(DeepSiphonTile.typeOf(kind), DeepSiphonTile.typeOf(kind)));
                    i++;
                }
                else
                    groups.add(List.of(DeepSiphonTile.typeOf(kind)));
            }
        }
        else
        {
            var themes = DungeonTheme.values();
            var theme = themes[random.nextInt(themes.length)];
            for (var mob : VentricleWaves.dungeonWave(wave, theme, level.getRandom()))
                groups.add(List.of(DungeonMobSpawner.type(mob)));
        }

        int opened = 0;
        for (var group : groups)
        {
            var spot = findSpot(level, cradle, group.getFirst(), random);
            if (spot == null)
                continue;
            var tear = TwinVentricleContent.VENTRICLE_TEAR.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
            if (tear == null)
                continue;
            tear.snapTo(spot.x, spot.y, spot.z, 0, 0);
            tear.bring(group, celebrant);
            level.addFreshEntity(tear);
            opened++;
        }
        return opened;
    }

    private static @Nullable Vec3 findSpot(ServerLevel level, BlockPos cradle, EntityType<? extends Mob> type, SplittableRandom random)
    {
        for (int attempt = 0; attempt < TRIES; attempt++)
        {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double distance = TEAR_MIN_RANGE + random.nextDouble() * (TEAR_MAX_RANGE - TEAR_MIN_RANGE);
            int x = Mth.floor(cradle.getX() + 0.5 + Math.cos(angle) * distance);
            int z = Mth.floor(cradle.getZ() + 0.5 + Math.sin(angle) * distance);
            for (int dy = GROUND_REACH; dy >= -GROUND_REACH; dy--)
            {
                var feet = new BlockPos(x, cradle.getY() + dy, z);
                var below = feet.below();
                if (level.getBlockState(below).getCollisionShape(level, below).isEmpty())
                    continue;
                var at = new Vec3(x + 0.5, feet.getY(), z + 0.5);
                if (level.noCollision(type.getSpawnAABB(at.x, at.y, at.z)) && level.getFluidState(feet).isEmpty())
                    return at;
            }
        }
        return null;
    }
}
