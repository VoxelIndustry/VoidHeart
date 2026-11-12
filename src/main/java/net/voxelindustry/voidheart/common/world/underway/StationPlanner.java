package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;

import static net.voxelindustry.voidheart.common.world.underway.FortBuilder.DOOR_HEIGHT;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.ENTRY_FLOOR;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.LIVING_FLOOR;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.LIVING_HEIGHT;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.PLATFORM_FLOOR;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.RAIL_HEIGHT;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.TRUNK_TOP;

/**
 * Plans the fort under a district that one trunk line passes. It is laid out along the line with the living side at low across, then turned and mirrored by its frame.
 * <p>
 * The rail level has a platform on each side of the trunk, a magazine whose feeder track loops out and back, a depot, a pump room, a freight hall and a signal cabin. The living level runs one gallery of bays beside the trunk, and by seed a second one further out joined by rungs, and a wing across the trunk reached by galleries over the platforms. Three shafts join the levels: the core with its lift, the depot's stairs, and the wing's.
 */
final class StationPlanner
{
    static final int HALL_START = 24;
    static final int HALL_END = 103;
    private static final int LOW_PLATFORM = 53;
    private static final int HIGH_PLATFORM = 70;
    private static final int PLATFORM_WIDTH = 5;
    private static final int PLATFORM_HEIGHT = 7;
    private static final int[] COLUMN_ROWS = { 55, 72 };
    private static final int COLUMN_EVERY = 8;
    private static final int COLUMN_FIRST = 28;

    private static final int CORE_ALONG = 72;
    private static final int CORE_ACROSS = 40;
    private static final int GALLERY_START = 6;
    private static final int GALLERY_END = 121;
    private static final int BAYS_START = 8;
    private static final int BAYS_END = 119;
    private static final int MAIN_LOW = 34;
    private static final int MAIN_HIGH = 38;
    private static final int OUTER_LOW = 15;
    private static final int OUTER_HIGH = 19;
    private static final int WING_LOW = 76;
    private static final int WING_HIGH = 80;
    private static final int[] MIDDLE = { 61, 66 };
    private static final int[][] RUNGS = { { 29, 32 }, { 86, 89 } };
    private static final int[][] CROSSINGS = { { 18, 21 }, { 106, 109 } };
    private static final int WING_PERCENT = 60;
    private static final int OUTER_PERCENT = 60;
    private static final int PERCENT = 100;

    private static final int MAGAZINE_ROWS = 6;
    private static final int MAGAZINE_ROW_PITCH = 7;
    private static final int MAGAZINE_DEPTH = 6;
    private static final int[] MAGAZINE_LEGS = { 30, 56 };
    private static final int LEG_WIDTH = 4;

    private static final UnderwayRoomKind[] PROGRAM = {
            UnderwayRoomKind.BARRACKS, UnderwayRoomKind.SHELTER, UnderwayRoomKind.MESS, UnderwayRoomKind.KITCHEN, UnderwayRoomKind.INFIRMARY, UnderwayRoomKind.POWER_PLANT,
            UnderwayRoomKind.FILTERS, UnderwayRoomKind.CISTERN, UnderwayRoomKind.COMMAND_POST, UnderwayRoomKind.EXCHANGE, UnderwayRoomKind.STORES, UnderwayRoomKind.WORKSHOP,
            UnderwayRoomKind.WASHROOM, UnderwayRoomKind.LABORATORY, UnderwayRoomKind.BRIG, UnderwayRoomKind.ARCHIVE, UnderwayRoomKind.MORGUE, UnderwayRoomKind.BARRACKS,
            UnderwayRoomKind.SHELTER, UnderwayRoomKind.STORES
    };
    private static final long SALT = 0x554E44455253L;

    private StationPlanner()
    {
    }

    static UnderwayFort plan(long seed, DistrictPos pos)
    {
        var builder = new FortBuilder(seed, pos, UnderwayNetwork.isLineRow(seed, pos.z()), SALT, PROGRAM);
        boolean wing = builder.roll(PERCENT) < WING_PERCENT;
        boolean outer = !wing || builder.roll(PERCENT) < OUTER_PERCENT;
        boolean accessLow = UnderwayNetwork.galleryBetween(seed, pos, builder.frame.compass(0, -1));
        boolean accessHigh = UnderwayNetwork.galleryBetween(seed, pos, builder.frame.compass(0, 1));

        var core = builder.shaft(CORE_ALONG, CORE_ACROSS, PLATFORM_FLOOR, ENTRY_FLOOR, false, false, true, LIVING_FLOOR + 2);
        hall(builder);
        lowSide(builder, core);
        highSide(builder, wing);
        living(builder, core, outer, wing, accessLow, accessHigh);
        builder.entry(core, -1, 0);
        return builder.build(UnderwayNode.STATION);
    }

    /** The two platforms under one ceiling with the trunk, their columns, and steps down to the track bed at each end. */
    private static void hall(FortBuilder builder)
    {
        for (int platform : new int[] { LOW_PLATFORM, HIGH_PLATFORM })
            builder.room(UnderwayRoomKind.PLATFORM, HALL_START, platform, HALL_END, platform + PLATFORM_WIDTH - 1, PLATFORM_FLOOR, PLATFORM_HEIGHT);
        builder.swap(UnderwayMaterial.SHELL, UnderwayMaterial.AIR, HALL_START, TRUNK_TOP - 1, TrunkPainter.LOW, HALL_END, TRUNK_TOP, TrunkPainter.HIGH);
        for (int row : COLUMN_ROWS)
        {
            for (int along = COLUMN_FIRST; along < HALL_END; along += COLUMN_EVERY)
                builder.fill(UnderwayMaterial.COLUMN, along, PLATFORM_FLOOR + 1, row, along, TRUNK_TOP, row);
            builder.lamps(true, HALL_START, HALL_END, row + (row < TrunkPainter.CENTRE ? -1 : 1), TRUNK_TOP);
        }
        for (int edge : new int[] { TrunkPainter.LOW, TrunkPainter.HIGH })
        {
            builder.steps(HALL_START + 2, edge, -1);
            builder.steps(HALL_END - 2, edge, 1);
        }
    }

    /** Behind the living side's platform: the core's lobby, the magazine, and the depot with its pump room and stairs. */
    private static void lowSide(FortBuilder builder, UnderwayShaft core)
    {
        builder.room(UnderwayRoomKind.LOBBY, CORE_ALONG - 4, CORE_ACROSS, CORE_ALONG - 2, LOW_PLATFORM - 2, PLATFORM_FLOOR, RAIL_HEIGHT);
        builder.door(core, -1, 0, PLATFORM_FLOOR, false);
        builder.opening(CORE_ALONG - 4, LOW_PLATFORM - 1, CORE_ALONG - 2, LOW_PLATFORM - 1, PLATFORM_FLOOR, DOOR_HEIGHT);

        int firstRow = builder.roll(3);
        int legStart = 8 + MAGAZINE_ROW_PITCH * firstRow;
        for (int leg : MAGAZINE_LEGS)
        {
            builder.room(UnderwayRoomKind.MAGAZINE_GALLERY, leg, legStart, leg + LEG_WIDTH - 1, LOW_PLATFORM - 2, PLATFORM_FLOOR, RAIL_HEIGHT);
            builder.opening(leg, LOW_PLATFORM - 1, leg + LEG_WIDTH - 1, LOW_PLATFORM - 1, PLATFORM_FLOOR, DOOR_HEIGHT);
            builder.rail(false, false, legStart - 3, LOW_PLATFORM + PLATFORM_WIDTH - 1, leg + 1, PLATFORM_FLOOR);
        }
        builder.room(UnderwayRoomKind.MAGAZINE_GALLERY, MAGAZINE_LEGS[0], legStart - LEG_WIDTH, MAGAZINE_LEGS[1] + LEG_WIDTH - 1, legStart - 1, PLATFORM_FLOOR, RAIL_HEIGHT);
        builder.rail(false, true, MAGAZINE_LEGS[0] + 1, MAGAZINE_LEGS[1] + 1, legStart - 3, PLATFORM_FLOOR);
        for (int row = firstRow; row < MAGAZINE_ROWS; row++)
        {
            int across = 9 + MAGAZINE_ROW_PITCH * row;
            magazine(builder, MAGAZINE_LEGS[0] - 10, across, MAGAZINE_LEGS[0] - 1);
            magazine(builder, MAGAZINE_LEGS[0] + LEG_WIDTH + 1, across, MAGAZINE_LEGS[0] + LEG_WIDTH);
            magazine(builder, MAGAZINE_LEGS[1] - 10, across, MAGAZINE_LEGS[1] - 1);
        }

        builder.room(UnderwayRoomKind.DEPOT, 82, 36, HALL_END, LOW_PLATFORM - 2, PLATFORM_FLOOR, LIVING_HEIGHT + 1);
        builder.opening(88, LOW_PLATFORM - 1, 93, LOW_PLATFORM - 1, PLATFORM_FLOOR, RAIL_HEIGHT);
        builder.rail(false, false, 38, LOW_PLATFORM + PLATFORM_WIDTH - 1, 90, PLATFORM_FLOOR);
        builder.room(UnderwayRoomKind.PUMP_ROOM, 82, 24, 92, 34, PLATFORM_FLOOR, RAIL_HEIGHT);
        builder.opening(86, 35, 87, 35, PLATFORM_FLOOR, DOOR_HEIGHT);
        var stairs = builder.shaft(96, 24, PLATFORM_FLOOR, LIVING_FLOOR, false, true, false, 0);
        builder.opening(stairs.landingAlong(), 32, stairs.landingAlong() + 1, 35, PLATFORM_FLOOR, DOOR_HEIGHT);
        builder.opening(stairs.landingAlong(), 32, stairs.landingAlong() + 1, MAIN_LOW - 1, LIVING_FLOOR, DOOR_HEIGHT);
    }

    private static void magazine(FortBuilder builder, int along, int across, int wall)
    {
        builder.room(UnderwayRoomKind.MAGAZINE, along, across, along + 8, across + MAGAZINE_DEPTH - 1, PLATFORM_FLOOR, RAIL_HEIGHT);
        builder.opening(wall, across + 2, wall, across + 3, PLATFORM_FLOOR, DOOR_HEIGHT);
    }

    /** Behind the far platform: the signal cabin, the freight hall, and the wing's stairs when there is a wing. */
    private static void highSide(FortBuilder builder, boolean wing)
    {
        int wall = HIGH_PLATFORM + PLATFORM_WIDTH;
        builder.room(UnderwayRoomKind.SIGNAL_CABIN, 58, wall + 1, 70, wall + 7, PLATFORM_FLOOR, RAIL_HEIGHT);
        builder.opening(63, wall, 64, wall, PLATFORM_FLOOR, DOOR_HEIGHT);

        builder.room(UnderwayRoomKind.FREIGHT_HALL, 80, wall + 1, HALL_END, wall + 20, PLATFORM_FLOOR, LIVING_HEIGHT);
        builder.opening(90, wall, 93, wall, PLATFORM_FLOOR, RAIL_HEIGHT);
        builder.rail(false, false, HIGH_PLATFORM + 1, wall + 18, 91, PLATFORM_FLOOR);
        for (int along : new int[] { 86, 97 })
        {
            for (int across : new int[] { wall + 7, wall + 14 })
                builder.fill(UnderwayMaterial.COLUMN, along, PLATFORM_FLOOR + 1, across, along, PLATFORM_FLOOR + LIVING_HEIGHT, across);
        }

        if (!wing)
            return;
        var stairs = builder.shaft(32, WING_HIGH + 2, PLATFORM_FLOOR, LIVING_FLOOR, false, false, false, 0);
        builder.room(UnderwayRoomKind.LOBBY, 32, wall + 1, 39, WING_HIGH, PLATFORM_FLOOR, RAIL_HEIGHT);
        builder.door(stairs, 0, -1, PLATFORM_FLOOR, false);
        builder.opening(35, wall, 36, wall, PLATFORM_FLOOR, DOOR_HEIGHT);
        builder.door(stairs, 0, -1, LIVING_FLOOR, false);
    }

    private static void living(FortBuilder builder, UnderwayShaft core, boolean outer, boolean wing, boolean accessLow, boolean accessHigh)
    {
        gallery(builder, MAIN_LOW, MAIN_HIGH);
        builder.opening(core.landingAlong(), MAIN_HIGH + 1, core.landingAlong() + 1, MAIN_HIGH + 1, LIVING_FLOOR, DOOR_HEIGHT);
        int railAlong = builder.frame.flipAlong() ? GalleryPainter.HIGH - 1 : GalleryPainter.RAIL;

        if (accessLow)
        {
            builder.room(UnderwayRoomKind.ACCESS_GALLERY, GalleryPainter.LOW, 0, GalleryPainter.HIGH, MAIN_LOW - 1, LIVING_FLOOR, GalleryPainter.HEIGHT);
            builder.rail(false, false, 0, MAIN_LOW + 1, railAlong, LIVING_FLOOR);
        }
        else if (outer)
            builder.room(UnderwayRoomKind.RUNG, GalleryPainter.LOW, OUTER_HIGH + 1, GalleryPainter.HIGH, MAIN_LOW - 1, LIVING_FLOOR, GalleryPainter.HEIGHT);
        if (accessHigh)
        {
            builder.room(UnderwayRoomKind.ACCESS_GALLERY, GalleryPainter.LOW, MAIN_HIGH + 1, GalleryPainter.HIGH, DistrictPos.SIZE - 1, LIVING_FLOOR, GalleryPainter.HEIGHT);
            builder.rail(false, false, MAIN_HIGH - 1, DistrictPos.SIZE - 1, railAlong, LIVING_FLOOR);
        }
        else if (wing)
            builder.room(UnderwayRoomKind.CROSS_GALLERY, GalleryPainter.LOW, MAIN_HIGH + 1, GalleryPainter.HIGH, WING_LOW - 1, LIVING_FLOOR, GalleryPainter.HEIGHT);

        int[] lowMiddle = accessLow || outer ? MIDDLE : null;
        int[] highMiddle = accessHigh || wing ? MIDDLE : null;
        int[] depotStairs = { 95, 104 };
        builder.bays(false, BAYS_START, BAYS_END, MAIN_HIGH + 1, 1, wing ? reserved(CROSSINGS[0]) : null, highMiddle, new int[] { CORE_ALONG - 1, CORE_ALONG + StairShaft.SIZE },
                wing ? reserved(CROSSINGS[1]) : null);
        builder.bays(false, BAYS_START, BAYS_END, MAIN_LOW - 1, -1, outer ? reserved(RUNGS[0]) : null, lowMiddle, outer ? reserved(RUNGS[1]) : null, depotStairs);

        if (outer)
        {
            gallery(builder, OUTER_LOW, OUTER_HIGH);
            for (int[] rung : RUNGS)
                builder.room(UnderwayRoomKind.RUNG, rung[0], OUTER_HIGH + 1, rung[1], MAIN_LOW - 1, LIVING_FLOOR, GalleryPainter.HEIGHT);
            builder.bays(false, BAYS_START, BAYS_END, OUTER_LOW - 1, -1, accessLow ? MIDDLE : null);
        }
        if (wing)
        {
            gallery(builder, WING_LOW, WING_HIGH);
            for (int[] crossing : CROSSINGS)
                builder.room(UnderwayRoomKind.CROSS_GALLERY, crossing[0], MAIN_HIGH + 1, crossing[1], WING_LOW - 1, LIVING_FLOOR, GalleryPainter.HEIGHT);
            builder.bays(false, BAYS_START, BAYS_END, WING_HIGH + 1, 1, new int[] { 31, 40 }, accessHigh ? MIDDLE : null);
        }
    }

    private static void gallery(FortBuilder builder, int low, int high)
    {
        builder.room(UnderwayRoomKind.GALLERY, GALLERY_START, low, GALLERY_END, high, LIVING_FLOOR, LIVING_HEIGHT);
        builder.inAir(UnderwayMaterial.SHELL, GALLERY_START, LIVING_FLOOR + LIVING_HEIGHT, low, GALLERY_END, LIVING_FLOOR + LIVING_HEIGHT, low);
        builder.inAir(UnderwayMaterial.SHELL, GALLERY_START, LIVING_FLOOR + LIVING_HEIGHT, high, GALLERY_END, LIVING_FLOOR + LIVING_HEIGHT, high);
        builder.rail(false, true, GALLERY_START + 1, GALLERY_END - 1, (low + high) / 2, LIVING_FLOOR);
        builder.lamps(true, GALLERY_START, GALLERY_END, (low + high) / 2, LIVING_FLOOR + LIVING_HEIGHT);
    }

    private static int[] reserved(int[] passage)
    {
        return new int[] { passage[0] - 1, passage[1] + 1 };
    }
}
