package net.voxelindustry.voidheart.client.screen;

import net.voxelindustry.voidheart.common.content.research.Constellation;

/**
 * How a constellation is told apart without touching the colors of a research's state: the light behind its island and its sigil. Where its name sits is
 * worked out with the rest of the map, see {@link ResearchMapPlan}.
 *
 * @param rgb      the sigil and the name
 * @param lightRgb the void lit around its researches, dark enough for the state colors to keep reading over it
 * @param sigilU   where its sigil is in the atlas
 */
record ConstellationLook(int rgb, int lightRgb, int sigilU)
{
    private static final ConstellationLook RITES     = new ConstellationLook(0xE08A5E, 0x5A2C1E, 0);
    private static final ConstellationLook ARTIFICES = new ConstellationLook(0xD880C8, 0x5C1F4E, 10);
    private static final ConstellationLook ARCHIVE   = new ConstellationLook(0xCDB36E, 0x4E4420, 20);
    private static final ConstellationLook TRAVEL    = new ConstellationLook(0x7FB2EA, 0x1E4060, 30);
    private static final ConstellationLook DEITIES   = new ConstellationLook(0xD8586A, 0x4A0E16, 40);

    static ConstellationLook of(Constellation constellation)
    {
        return switch (constellation)
        {
            case RITES -> RITES;
            case ARTIFICES -> ARTIFICES;
            case ARCHIVE -> ARCHIVE;
            case TRAVEL -> TRAVEL;
            case DEITIES -> DEITIES;
        };
    }
}
