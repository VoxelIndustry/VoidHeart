package net.voxelindustry.voidheart.common.world.daedalus;

import java.util.EnumMap;
import java.util.Map;
import java.util.SplittableRandom;

import static net.voxelindustry.voidheart.common.world.daedalus.DaedalusMob.*;

/**
 * Which mobs live in each theme. Generics and the Daedalus' own mobs are the bulk everywhere, each theme favouring some of them,
 * and about a third of every district comes from one older family that suits it.
 */
public final class DaedalusRoster
{
    public static final double FAMILY_SHARE = 0.3;
    private static final int FAVOURED = 3;

    private static final Map<DaedalusMob, Integer> CORE = weights(
            VOID_SKELETON, 10,
            VOID_GHOUL, 10,
            WRAITH, 6,
            WRAITH_SCARAB, 4,
            STALKING_WRAITH, 3,
            FALLEN_BISHOP, 3,
            MALIGNANT_BOULDER, 2,
            LEGIONARY, 3,
            CENTURION, 3);

    private final Map<DaedalusMob, Integer> core;
    private final Map<DaedalusMob, Integer> family;
    private final boolean nests;

    private DaedalusRoster(Map<DaedalusMob, Integer> family, boolean nests, DaedalusMob... favoured)
    {
        core = new EnumMap<>(CORE);
        for (var mob : favoured)
            core.computeIfPresent(mob, (key, weight) -> weight * FAVOURED);
        this.family = family;
        this.nests = nests;
    }

    public static DaedalusRoster of(DaedalusTheme theme)
    {
        return switch (theme)
        {
            case ARCHIVE -> new DaedalusRoster(weights(FALLEN_NOVICE, 14, FALLEN_ADEPT, 6, FALLEN_ELDER, 3), false, FALLEN_BISHOP, STALKING_WRAITH);
            case WARREN -> new DaedalusRoster(weights(CRAB_SMALL, 8, CRAB_MEDIUM, 8, CRAB_LARGE, 3), false, VOID_GHOUL, STALKING_WRAITH);
            case GALLERY -> new DaedalusRoster(weights(FALLEN_ADEPT, 1), false, LEGIONARY, CENTURION);
            case CISTERN -> new DaedalusRoster(weights(CRAB_SMALL, 8, CRAB_MEDIUM, 8, CRAB_LARGE, 3, BARNACLE, 6), true, MALIGNANT_BOULDER);
            case STAIRWELL -> new DaedalusRoster(weights(ARCANE_EYE, 1), false, LEGIONARY, MALIGNANT_BOULDER);
            case WRITHING -> new DaedalusRoster(weights(BARNACLE, 6, EYE_MAW, 3), true, WRAITH, WRAITH_SCARAB, VOID_GHOUL);
        };
    }

    public boolean hasNests()
    {
        return nests;
    }

    public boolean holds(DaedalusMob mob)
    {
        return core.containsKey(mob) || family.containsKey(mob);
    }

    /**
     * @param familyShare how much of the district the family holds so far, which keeps it close to {@link #FAMILY_SHARE}
     */
    public DaedalusMob pick(SplittableRandom random, double familyShare)
    {
        return pick(familyShare < FAMILY_SHARE ? family : core, random);
    }

    public boolean isFamily(DaedalusMob mob)
    {
        return family.containsKey(mob);
    }

    private static DaedalusMob pick(Map<DaedalusMob, Integer> weights, SplittableRandom random)
    {
        int roll = random.nextInt(weights.values().stream().mapToInt(Integer::intValue).sum());
        for (var entry : weights.entrySet())
        {
            roll -= entry.getValue();
            if (roll < 0)
                return entry.getKey();
        }
        throw new IllegalStateException("Empty roster");
    }

    private static Map<DaedalusMob, Integer> weights(Object... pairs)
    {
        var weights = new EnumMap<DaedalusMob, Integer>(DaedalusMob.class);
        for (int i = 0; i < pairs.length; i += 2)
            weights.put((DaedalusMob) pairs[i], (Integer) pairs[i + 1]);
        return weights;
    }
}
