package net.voxelindustry.voidheart.common.content.secondheart.ventricle;

import net.minecraft.util.RandomSource;
import net.voxelindustry.voidheart.common.energy.siphon.SiphonWaves;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusMob;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonAddress;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonMob;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonMobTable;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import static net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleBalance.*;

/**
 * Which creatures each beat of the Twin Ventricle calls, in rising waves: the first from the void dungeons' rosters at rising difficulty, the last
 * from the Daedalus, never its Dragoon or barnacles, as the Deep Siphon picks them. Nothing that clings to a wall comes, since it couldn't reach
 * the cradle.
 */
public final class VentricleWaves
{
    private VentricleWaves()
    {
    }

    /**
     * @return how many waves are owed after this many beats
     */
    public static int due(int beats)
    {
        if (beats < FIRST_WAVE_BEAT)
            return 0;
        return Math.min(WAVES, 1 + (beats - FIRST_WAVE_BEAT) / BEATS_PER_WAVE);
    }

    public static int size(int wave)
    {
        return WAVE_SIZE + wave / WAVE_GROWTH_EVERY;
    }

    public static boolean fromDaedalus(int wave)
    {
        return wave >= DUNGEON_WAVES;
    }

    public static int difficulty(int wave)
    {
        return Math.clamp(DungeonAddress.MIN_DIFFICULTY + wave, DungeonAddress.MIN_DIFFICULTY, DungeonAddress.MAX_DIFFICULTY);
    }

    public static boolean walksIn(DungeonMob mob)
    {
        return !mob.clings && mob != DungeonMob.BARNACLE;
    }

    public static List<DungeonMob> dungeonWave(int wave, DungeonTheme theme, RandomSource random)
    {
        var roster = DungeonMobTable.roster(theme, difficulty(wave)).stream().filter(VentricleWaves::walksIn).toList();
        var mobs = new ArrayList<DungeonMob>(size(wave));
        if (roster.isEmpty())
            return mobs;
        for (int i = 0; i < size(wave); i++)
            mobs.add(DungeonMobTable.pick(roster, random));
        return mobs;
    }

    public static List<DaedalusMob> daedalusWave(int wave, DistrictPos district, SplittableRandom random)
    {
        return SiphonWaves.compose(district, district, size(wave), random);
    }
}
