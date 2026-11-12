package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;

import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.LIVING_FLOOR;

/**
 * Draws the galleries that lead the posts to their stations: a narrow track and a walkway on the living level, through the middle of every post on the way and under the chasms between. A station draws its own end of them.
 */
final class GalleryPainter
{
    static final int LOW = 62;
    static final int HIGH = 65;
    static final int RAIL = 63;
    static final int HEIGHT = 4;

    private static final int LAMP_ACROSS = 64;
    private static final int LAMP_EVERY = 32;
    private static final int LAMP_AT = 16;
    private static final int MIDDLE = DistrictPos.SIZE / 2;

    private GalleryPainter()
    {
    }

    static void paint(long seed, DistrictPos pos, boolean alongX, UnderwayCanvas canvas)
    {
        var brush = new UnderwayBrush(canvas, UnderwayFrame.ofLine(pos, alongX));
        var lamp = UnderwayNetwork.regionRuin(seed, pos) == UnderwayRuin.SEALED ? UnderwayMaterial.LAMP : UnderwayMaterial.EMERGENCY_LAMP;
        boolean post = !UnderwayNetwork.nodeAt(seed, pos).isOnLine();
        boolean onwards = UnderwayNetwork.galleryBetween(seed, pos, alongX ? Compass.EAST : Compass.SOUTH);
        if (post && UnderwayNetwork.galleryBetween(seed, pos, alongX ? Compass.WEST : Compass.NORTH))
            stretch(brush, 0, MIDDLE - 1, lamp);
        if (post && onwards)
            stretch(brush, MIDDLE, DistrictPos.SIZE - 1, lamp);
        if (onwards)
            stretch(brush, DistrictPos.SIZE, DistrictPos.PITCH - 1, lamp);
    }

    private static void stretch(UnderwayBrush brush, int from, int to, UnderwayMaterial lamp)
    {
        brush.room(from, LOW, to, HIGH, LIVING_FLOOR, HEIGHT, UnderwayMaterial.FLOOR);
        var rail = brush.feederRail(true);
        for (int along = from; along <= to; along++)
        {
            brush.setInAir(along, LIVING_FLOOR + 1, RAIL, rail);
            if (along % LAMP_EVERY == LAMP_AT)
                brush.setInAir(along, LIVING_FLOOR + HEIGHT, LAMP_ACROSS, lamp);
        }
    }
}
