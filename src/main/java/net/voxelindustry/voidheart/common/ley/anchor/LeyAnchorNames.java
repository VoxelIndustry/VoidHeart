package net.voxelindustry.voidheart.common.ley.anchor;

import net.voxelindustry.voidheart.common.ley.LeyHash;

/**
 * Place names for Old Anchors, built from syllables so the same node always carries the same name.
 */
public final class LeyAnchorNames
{
    private static final String[] ROOTS = {
            "Ael", "Bryn", "Cael", "Dun", "Eth", "Fal", "Gal", "Hal", "Ith", "Kel", "Lor", "Mor",
            "Nyth", "Or", "Pel", "Quel", "Rhun", "Sel", "Thal", "Ul", "Vael", "Wyr", "Ys", "Zar",
            "Ash", "Brae", "Cor", "Drav", "Ervy", "Gwen", "Har", "Lun", "Mael", "Nor", "Ost", "Tir"
    };

    private static final String[] JOINS = { "", "", "", "a", "e", "i", "o", "an", "en", "ar", "ith", "ul" };

    private static final String[] ENDINGS = {
            "dor", "mere", "wyn", "gard", "hollow", "reach", "moor", "fell", "stead", "thorn",
            "vale", "crest", "spire", "mark", "haven", "wick", "holm", "barrow", "ford", "keld"
    };

    private static final String[] SITES = { "Crossing", "Stones", "Ring", "Gate", "Cairn", "Circle", "Threshold", "Wellspring" };

    private static final long SALT_NAME = 11;

    private LeyAnchorNames()
    {
    }

    public static String name(long seed, long nodeId)
    {
        long hash = LeyHash.hash(seed, nodeId, 0, SALT_NAME);

        String root = ROOTS[pick(hash, 0, ROOTS.length)];
        String join = JOINS[pick(hash, 8, JOINS.length)];
        String ending = ENDINGS[pick(hash, 16, ENDINGS.length)];
        String name = root + join + ending;

        if (pick(hash, 24, 10) < 3)
            name += " " + SITES[pick(hash, 32, SITES.length)];
        return name;
    }

    private static int pick(long hash, int shift, int bound)
    {
        return (int) ((hash >>> shift & 0xFF) % bound);
    }
}
