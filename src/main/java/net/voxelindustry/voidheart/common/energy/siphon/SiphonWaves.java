package net.voxelindustry.voidheart.common.energy.siphon;

import net.voxelindustry.voidheart.common.world.daedalus.DaedalusMob;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusRoster;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusTheme;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Which mobs a wave brings: picks from the rosters of the two districts the siphon's bridge joins, with the same share of each theme's older family as the
 * districts themselves. A Legionary always comes with its partner and counts as two. Pure, so it is unit tested.
 */
public final class SiphonWaves
{
    // Rerolls for a pick that doesn't fit what is left of the wave or can't climb onto a bridge
    private static final int TRIES = 16;

    private SiphonWaves()
    {
    }

    /**
     * Mobs that can climb out of the chasm onto a deck: not the Dragoon, which guards its district, nor barnacles, which grow where nests are.
     */
    public static boolean climbs(DaedalusMob mob)
    {
        return mob.isMob() && mob != DaedalusMob.KERNACIAN_DRAGOON && mob != DaedalusMob.BARNACLE;
    }

    /**
     * How much of a wave a mob takes.
     */
    public static int weight(DaedalusMob mob)
    {
        return mob == DaedalusMob.LEGIONARY ? 2 : 1;
    }

    /**
     * @param count how many mobs the wave brings, a Legionary pair counting as two
     * @return the mobs in the order they climb out, a Legionary appearing twice for its pair, never weighing more than {@code count}
     */
    public static List<DaedalusMob> compose(DistrictPos from, DistrictPos to, int count, SplittableRandom random)
    {
        var rosters = new DaedalusRoster[] { DaedalusRoster.of(DaedalusTheme.at(from)), DaedalusRoster.of(DaedalusTheme.at(to)) };
        var wave = new ArrayList<DaedalusMob>(count);
        int left = count;
        while (left > 0)
        {
            DaedalusMob picked = null;
            for (int attempt = 0; attempt < TRIES && picked == null; attempt++)
            {
                var roster = rosters[random.nextInt(rosters.length)];
                // Below the share, a pick comes from the family, so a uniform roll gives the family that share of picks
                var mob = roster.pick(random, random.nextDouble());
                if (climbs(mob) && weight(mob) <= left)
                    picked = mob;
            }
            if (picked == null)
                break;
            for (int i = 0; i < weight(picked); i++)
                wave.add(picked);
            left -= weight(picked);
        }
        return wave;
    }
}
