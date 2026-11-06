package net.voxelindustry.voidheart.common.world.daedalus;

import net.voxelindustry.voidheart.common.world.daedalus.DaedalusBridge.BridgeStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Bridges come from the seed and the side they cross alone, which lets two districts agree on them without knowing anything about each other.
 */
public final class DaedalusBridges
{
    private static final long EAST_SALT = 0x6272696467654CL;
    private static final long SOUTH_SALT = 0x6272696467655AL;
    private static final int MIN_BRIDGES = 3;
    private static final int EXTRA_BRIDGES = 3;
    private static final int FIRST_CELL = 1;
    private static final int LAST_CELL = DistrictPos.CELLS - 2;
    private static final int MIN_CELL_SPACING = 2;
    private static final BridgeStyle[] STYLES = BridgeStyle.values();
    private static final int[] NARROW = { 3, 3, 5 };
    private static final int[] WIDE = { 5, 5, 7 };

    private DaedalusBridges()
    {
    }

    /**
     * @param direction {@link Compass#EAST} or {@link Compass#SOUTH}
     */
    public static List<DaedalusBridge> between(long seed, DistrictPos from, int direction)
    {
        var random = new SplittableRandom(DaedalusHash.mix(seed, from.x(), from.z(), direction == Compass.EAST ? EAST_SALT : SOUTH_SALT));
        int[] tiers = shuffledRange(0, DistrictPos.TIERS - 1, random);
        int[] cells = shuffledRange(FIRST_CELL, LAST_CELL, random);
        int wanted = MIN_BRIDGES + random.nextInt(EXTRA_BRIDGES);

        var bridges = new ArrayList<DaedalusBridge>(wanted);
        var taken = new ArrayList<Integer>(wanted);
        for (int cell : cells)
        {
            if (bridges.size() == wanted)
                break;
            if (taken.stream().anyMatch(other -> Math.abs(other - cell) < MIN_CELL_SPACING))
                continue;

            taken.add(cell);
            bridges.add(bridge(from, direction, cell, tiers[bridges.size()], random));
        }
        return bridges;
    }

    private static DaedalusBridge bridge(DistrictPos from, int direction, int cell, int tier, SplittableRandom random)
    {
        var style = STYLES[random.nextInt(STYLES.length)];
        int toTier = tier;
        if (style == BridgeStyle.RAMP)
            toTier = tier == 0 || (tier < DistrictPos.TIERS - 1 && random.nextBoolean()) ? tier + 1 : tier - 1;

        int[] widths = style == BridgeStyle.ARCADE || style == BridgeStyle.PIER ? WIDE : NARROW;
        return new DaedalusBridge(from, direction, cell, tier, toTier, widths[random.nextInt(widths.length)], style, random.nextInt());
    }

    private static int[] shuffledRange(int first, int last, SplittableRandom random)
    {
        int[] values = new int[last - first + 1];
        for (int index = 0; index < values.length; index++)
            values[index] = first + index;
        for (int index = values.length - 1; index > 0; index--)
        {
            int other = random.nextInt(index + 1);
            int value = values[index];
            values[index] = values[other];
            values[other] = value;
        }
        return values;
    }
}
