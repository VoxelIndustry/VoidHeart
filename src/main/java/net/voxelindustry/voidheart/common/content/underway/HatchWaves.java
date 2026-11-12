package net.voxelindustry.voidheart.common.content.underway;

import net.voxelindustry.voidheart.common.energy.siphon.SiphonWaves;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusMob;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;

import java.util.List;
import java.util.SplittableRandom;

/**
 * The waves a charging hatch draws: when they come, how many mobs each brings, and which of them go for the key rather than for the players defending it.
 * They are picked from the roster of the hatch's own district, like the waves a Deep Siphon draws onto its bridge. Pure, so it is unit tested.
 */
public final class HatchWaves
{
    private static final int TRIES = 8;

    private HatchWaves()
    {
    }

    /** @return how many waves should have come once the hatch has charged for that many ticks */
    public static int due(int charge)
    {
        if (charge < HatchBalance.WAVE_FIRST_TICKS)
            return 0;
        return 1 + (Math.min(charge, HatchBalance.CHARGE_TICKS - 1) - HatchBalance.WAVE_FIRST_TICKS) / HatchBalance.WAVE_INTERVAL_TICKS;
    }

    /** @return how many mobs that wave brings, the first being wave 0 and a Legionary pair counting as two */
    public static int size(int wave)
    {
        return HatchBalance.WAVE_SIZE + wave / HatchBalance.WAVE_GROWTH_EVERY;
    }

    /**
     * Mobs whose blows land on anything go for the key. Those whose kit is made against a player, the stalker's stare, lances, orbs and bolts, hunt its defenders instead.
     */
    public static boolean goesForKey(DaedalusMob mob)
    {
        return switch (mob)
        {
            case STALKING_WRAITH, FALLEN_BISHOP, FALLEN_ELDER, ARCANE_EYE -> false;
            default -> SiphonWaves.climbs(mob);
        };
    }

    /**
     * @return the mobs of that wave, a Legionary appearing twice for its pair, at least one of them going for the key
     */
    public static List<DaedalusMob> compose(DistrictPos district, int wave, SplittableRandom random)
    {
        List<DaedalusMob> mobs = List.of();
        for (int attempt = 0; attempt < TRIES; attempt++)
        {
            mobs = SiphonWaves.compose(district, district, size(wave), random);
            if (mobs.stream().anyMatch(HatchWaves::goesForKey))
                return mobs;
        }
        var forced = new java.util.ArrayList<>(mobs);
        if (forced.isEmpty() || forced.getFirst() == DaedalusMob.LEGIONARY)
            forced.add(DaedalusMob.VOID_GHOUL);
        else
            forced.set(0, DaedalusMob.VOID_GHOUL);
        return forced;
    }
}
