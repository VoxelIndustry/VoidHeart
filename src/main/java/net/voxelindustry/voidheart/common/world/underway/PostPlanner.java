package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;

import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.ENTRY_FLOOR;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.LIVING_FLOOR;

/**
 * Plans the small fort under a district no line passes: an entry level behind its hatch, and a few bays where the gallery to its station runs through, closed by a door on each side that leads on.
 */
final class PostPlanner
{
    private static final int CORE_ALONG = 64;
    private static final int CORE_ACROSS = 72;
    private static final int BAYS_START = 36;
    private static final int BAYS_END = 92;
    private static final int[] DOORS = { BAYS_START - 3, BAYS_END + 3 };
    private static final UnderwayRoomKind[] PROGRAM = {
            UnderwayRoomKind.BARRACKS, UnderwayRoomKind.STORES, UnderwayRoomKind.WORKSHOP, UnderwayRoomKind.WASHROOM, UnderwayRoomKind.CISTERN,
            UnderwayRoomKind.FILTERS, UnderwayRoomKind.STORES, UnderwayRoomKind.EXCHANGE, UnderwayRoomKind.KITCHEN
    };
    private static final long SALT = 0x554E44455250L;

    private PostPlanner()
    {
    }

    static UnderwayFort plan(long seed, DistrictPos pos)
    {
        int direction = UnderwayNetwork.galleryDirection(seed, pos);
        var builder = new FortBuilder(seed, pos, direction == Compass.EAST || direction == Compass.WEST, SALT, PROGRAM);

        builder.room(UnderwayRoomKind.GALLERY, DOORS[0] - 1, GalleryPainter.LOW, DOORS[1] + 1, GalleryPainter.HIGH, LIVING_FLOOR, GalleryPainter.HEIGHT);
        builder.rail(false, true, DOORS[0] - 1, DOORS[1] + 1, builder.frame.flipAcross() ? GalleryPainter.HIGH - 1 : GalleryPainter.RAIL, LIVING_FLOOR);
        builder.lamps(true, BAYS_START, BAYS_END, GalleryPainter.HIGH - 1, LIVING_FLOOR + GalleryPainter.HEIGHT);
        for (int end = 0; end < DOORS.length; end++)
        {
            if (!UnderwayNetwork.galleryBetween(seed, pos, builder.frame.compass(end == 0 ? -1 : 1, 0)))
                continue;
            builder.fill(UnderwayMaterial.STEEL, DOORS[end], LIVING_FLOOR + 1, GalleryPainter.LOW, DOORS[end], LIVING_FLOOR + GalleryPainter.HEIGHT, GalleryPainter.HIGH);
            builder.fill(UnderwayMaterial.DOOR, DOORS[end], LIVING_FLOOR + 1, GalleryPainter.LOW + 1, DOORS[end], LIVING_FLOOR + 2, GalleryPainter.HIGH - 1);
        }

        var core = builder.shaft(CORE_ALONG, CORE_ACROSS, LIVING_FLOOR, ENTRY_FLOOR, false, false, true, LIVING_FLOOR);
        builder.opening(CORE_ALONG, GalleryPainter.HIGH + 1, CORE_ALONG + 1, CORE_ACROSS - 1, LIVING_FLOOR, FortBuilder.DOOR_HEIGHT);
        builder.bays(false, BAYS_START, BAYS_END, GalleryPainter.LOW - 1, -1);
        builder.bays(false, BAYS_START, BAYS_END, GalleryPainter.HIGH + 1, 1, new int[] { CORE_ALONG - 2, CORE_ALONG + StairShaft.SIZE + 1 });
        builder.entry(core, -1, 0);
        return builder.build(UnderwayNode.POST);
    }
}
