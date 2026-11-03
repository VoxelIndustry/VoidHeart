package net.voxelindustry.voidheart.client.screen;

import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.research.Constellation;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * How a constellation is told apart without touching the colors of a research's state: the light behind its island, its sigil, and where its name sits on the map.
 *
 * @param rgb        the sigil and the name
 * @param lightRgb   the void lit around its researches, dark enough for the state colors to keep reading over it
 * @param sigilU     where its sigil is in the atlas
 * @param nameAnchor the research its name stays beside, however far the islands spread on a large screen
 * @param nameX      the middle of its name from that research, in screen units
 * @param nameY      likewise
 */
record ConstellationLook(int rgb, int lightRgb, int sigilU, Identifier nameAnchor, int nameX, int nameY)
{
    private static final ConstellationLook RITES     = new ConstellationLook(0xE08A5E, 0x5A2C1E, 0, research("shatter_forge"), 54, 11);
    private static final ConstellationLook ARTIFICES = new ConstellationLook(0xD880C8, 0x5C1F4E, 10, research("far_sight"), -2, 32);
    private static final ConstellationLook ARCHIVE   = new ConstellationLook(0xCDB36E, 0x4E4420, 20, research("archive_expansion"), 68, 0);
    private static final ConstellationLook TRAVEL    = new ConstellationLook(0x7FB2EA, 0x1E4060, 30, research("portal_flexion"), 54, 0);

    private static Identifier research(String path)
    {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    static ConstellationLook of(Constellation constellation)
    {
        return switch (constellation)
        {
            case RITES -> RITES;
            case ARTIFICES -> ARTIFICES;
            case ARCHIVE -> ARCHIVE;
            case TRAVEL -> TRAVEL;
        };
    }
}
