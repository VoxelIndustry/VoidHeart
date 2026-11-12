package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;

import static net.voxelindustry.voidheart.common.world.underway.FortBuilder.DOOR_HEIGHT;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.BALLAST;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.ENTRY_FLOOR;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.LIVING_FLOOR;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.LIVING_HEIGHT;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.PLATFORM_FLOOR;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.RAIL_HEIGHT;

/**
 * Plans the interchange under a district where two trunk lines cross. The lines meet on the level in an atrium two levels tall, with a platform in each corner and the living level's balcony running around it. A wing leaves each corner on the rail level: the core, a magazine, a depot and the control room. Four living galleries lined with bays leave the balcony over the four tunnels.
 */
final class HubPlanner
{
    static final int ATRIUM_START = 40;
    static final int ATRIUM_END = 87;
    private static final int ATRIUM_TOP = LIVING_FLOOR + LIVING_HEIGHT;
    private static final int[] QUADRANTS = { ATRIUM_START, TrunkPainter.HIGH + 1 };
    private static final int QUADRANT_SIZE = TrunkPainter.LOW - ATRIUM_START;
    private static final int BALCONY = 4;
    private static final int[] COLUMNS = { 43, 57, 70, 84 };
    private static final int[] LAMPS = { 48, 63, 79 };
    private static final int ARM_START = 4;
    private static final int ARM_END = 123;
    private static final int CORE_ALONG = 24;
    private static final int CORE_ACROSS = 40;
    private static final int[] MAGAZINES = { 7, 15, 23, 31 };
    private static final UnderwayRoomKind[] PROGRAM = {
            UnderwayRoomKind.BARRACKS, UnderwayRoomKind.SHELTER, UnderwayRoomKind.MESS, UnderwayRoomKind.KITCHEN, UnderwayRoomKind.INFIRMARY, UnderwayRoomKind.COMMAND_POST,
            UnderwayRoomKind.EXCHANGE, UnderwayRoomKind.STORES, UnderwayRoomKind.WORKSHOP, UnderwayRoomKind.WASHROOM, UnderwayRoomKind.LABORATORY, UnderwayRoomKind.BRIG,
            UnderwayRoomKind.ARCHIVE, UnderwayRoomKind.FILTERS, UnderwayRoomKind.CISTERN, UnderwayRoomKind.SHELTER, UnderwayRoomKind.BARRACKS, UnderwayRoomKind.MORGUE
    };
    private static final long SALT = 0x554E44455248L;

    private HubPlanner()
    {
    }

    static UnderwayFort plan(long seed, DistrictPos pos)
    {
        var builder = new FortBuilder(seed, pos, true, SALT, PROGRAM);
        atrium(builder);
        var core = builder.shaft(CORE_ALONG, CORE_ACROSS, PLATFORM_FLOOR, ENTRY_FLOOR, true, false, true, LIVING_FLOOR + 2);
        wings(builder, core);
        arms(builder);
        builder.entry(core, 0, -1);
        return builder.build(UnderwayNode.HUB);
    }

    private static void atrium(FortBuilder builder)
    {
        int height = ATRIUM_TOP - PLATFORM_FLOOR;
        for (int along : QUADRANTS)
        {
            for (int across : QUADRANTS)
                builder.room(UnderwayRoomKind.PLATFORM, along, across, along + QUADRANT_SIZE - 1, across + QUADRANT_SIZE - 1, PLATFORM_FLOOR, height);
        }
        builder.room(UnderwayRoomKind.CROSSING, ATRIUM_START, TrunkPainter.LOW, ATRIUM_END, TrunkPainter.HIGH, BALLAST, ATRIUM_TOP - BALLAST);
        builder.room(UnderwayRoomKind.CROSSING, TrunkPainter.LOW, ATRIUM_START, TrunkPainter.HIGH, ATRIUM_END, BALLAST, ATRIUM_TOP - BALLAST);
        for (int rail : TrunkPainter.RAILS)
            builder.rail(true, true, ATRIUM_START, ATRIUM_END, rail, BALLAST);
        for (int rail : TrunkPainter.RAILS)
            builder.rail(true, false, ATRIUM_START, ATRIUM_END, rail, BALLAST);
        for (int edge : new int[] { TrunkPainter.LOW, TrunkPainter.HIGH })
        {
            builder.steps(ATRIUM_START + 2, edge, -1);
            builder.steps(ATRIUM_END - 2, edge, 1);
        }

        int inner = ATRIUM_START + BALCONY - 1;
        int outer = ATRIUM_END - BALCONY + 1;
        builder.inAir(UnderwayMaterial.FLOOR, ATRIUM_START, LIVING_FLOOR, ATRIUM_START, ATRIUM_END, LIVING_FLOOR, inner);
        builder.inAir(UnderwayMaterial.FLOOR, ATRIUM_START, LIVING_FLOOR, outer, ATRIUM_END, LIVING_FLOOR, ATRIUM_END);
        builder.inAir(UnderwayMaterial.FLOOR, ATRIUM_START, LIVING_FLOOR, ATRIUM_START, inner, LIVING_FLOOR, ATRIUM_END);
        builder.inAir(UnderwayMaterial.FLOOR, outer, LIVING_FLOOR, ATRIUM_START, ATRIUM_END, LIVING_FLOOR, ATRIUM_END);
        builder.inAir(UnderwayMaterial.GRATING, inner, LIVING_FLOOR + 1, inner, outer, LIVING_FLOOR + 1, inner);
        builder.inAir(UnderwayMaterial.GRATING, inner, LIVING_FLOOR + 1, outer, outer, LIVING_FLOOR + 1, outer);
        builder.inAir(UnderwayMaterial.GRATING, inner, LIVING_FLOOR + 1, inner, inner, LIVING_FLOOR + 1, outer);
        builder.inAir(UnderwayMaterial.GRATING, outer, LIVING_FLOOR + 1, inner, outer, LIVING_FLOOR + 1, outer);
        for (int along : COLUMNS)
        {
            for (int across : COLUMNS)
            {
                boolean onBalcony = along == inner || along == outer || across == inner || across == outer;
                if (onBalcony)
                    builder.inAir(UnderwayMaterial.COLUMN, along, PLATFORM_FLOOR + 1, across, along, LIVING_FLOOR - 1, across);
            }
        }
        for (int along : LAMPS)
        {
            for (int across : LAMPS)
                builder.lamps(true, along - 8, along, across, ATRIUM_TOP);
        }
    }

    /** One wing behind each corner platform, through the atrium's walls. */
    private static void wings(FortBuilder builder, UnderwayShaft core)
    {
        int wallLow = ATRIUM_START - 1;
        int wallHigh = ATRIUM_END + 1;

        builder.room(UnderwayRoomKind.LOBBY, CORE_ALONG + StairShaft.SIZE + 1, CORE_ACROSS, wallLow - 1, CORE_ACROSS + 3, PLATFORM_FLOOR, RAIL_HEIGHT);
        builder.door(core, 1, 0, PLATFORM_FLOOR, false);
        builder.opening(wallLow, CORE_ACROSS, wallLow, CORE_ACROSS + 3, PLATFORM_FLOOR, DOOR_HEIGHT);
        builder.opening(CORE_ALONG + StairShaft.SIZE, core.landingAcross(), wallLow, core.landingAcross() + 1, LIVING_FLOOR, DOOR_HEIGHT);

        builder.room(UnderwayRoomKind.MAGAZINE_GALLERY, 6, 82, wallLow - 1, 85, PLATFORM_FLOOR, RAIL_HEIGHT);
        builder.opening(wallLow, 82, wallLow, 85, PLATFORM_FLOOR, DOOR_HEIGHT);
        builder.rail(false, true, 7, ATRIUM_START + QUADRANT_SIZE - 2, 83, PLATFORM_FLOOR);
        for (int along : MAGAZINES)
        {
            builder.room(UnderwayRoomKind.MAGAZINE, along, 73, along + 6, 80, PLATFORM_FLOOR, RAIL_HEIGHT);
            builder.opening(along + 3, 81, along + 4, 81, PLATFORM_FLOOR, DOOR_HEIGHT);
            builder.room(UnderwayRoomKind.MAGAZINE, along, 87, along + 6, 94, PLATFORM_FLOOR, RAIL_HEIGHT);
            builder.opening(along + 3, 86, along + 4, 86, PLATFORM_FLOOR, DOOR_HEIGHT);
        }

        builder.room(UnderwayRoomKind.DEPOT, wallHigh + 2, 40, 111, 55, PLATFORM_FLOOR, LIVING_HEIGHT + 1);
        builder.opening(wallHigh, 44, wallHigh + 1, 51, PLATFORM_FLOOR, RAIL_HEIGHT);
        builder.rail(false, true, ATRIUM_END - QUADRANT_SIZE + 2, 109, 47, PLATFORM_FLOOR);
        builder.room(UnderwayRoomKind.PUMP_ROOM, wallHigh + 2, 26, 100, 38, PLATFORM_FLOOR, RAIL_HEIGHT);
        builder.opening(94, 39, 95, 39, PLATFORM_FLOOR, DOOR_HEIGHT);

        builder.room(UnderwayRoomKind.CONTROL_ROOM, wallHigh + 2, 74, 103, 87, PLATFORM_FLOOR, LIVING_HEIGHT);
        builder.opening(wallHigh, 80, wallHigh + 1, 81, PLATFORM_FLOOR, DOOR_HEIGHT);
        var stairs = builder.shaft(80, wallHigh + 2, PLATFORM_FLOOR, LIVING_FLOOR, true, false, false, 0);
        builder.opening(stairs.landingAlong(), wallHigh, stairs.landingAlong() + 1, wallHigh + 1, PLATFORM_FLOOR, DOOR_HEIGHT);
        builder.opening(stairs.landingAlong(), wallHigh, stairs.landingAlong() + 1, wallHigh + 1, LIVING_FLOOR, DOOR_HEIGHT);
    }

    /** The four living galleries over the tunnels, from the balcony out to the district's edges. */
    private static void arms(FortBuilder builder)
    {
        int wallLow = ATRIUM_START - 1;
        int wallHigh = ATRIUM_END + 1;
        int bayLow = GalleryPainter.LOW - 1;
        int bayHigh = GalleryPainter.HIGH + 1;
        for (int arm = 0; arm < 4; arm++)
        {
            boolean transposed = arm >= 2;
            boolean low = arm % 2 == 0;
            int from = low ? ARM_START : wallHigh;
            int to = low ? wallLow : ARM_END;
            if (transposed)
                builder.room(UnderwayRoomKind.GALLERY, GalleryPainter.LOW, from, GalleryPainter.HIGH, to, LIVING_FLOOR, GalleryPainter.HEIGHT);
            else
                builder.room(UnderwayRoomKind.GALLERY, from, GalleryPainter.LOW, to, GalleryPainter.HIGH, LIVING_FLOOR, GalleryPainter.HEIGHT);
            builder.rail(false, !transposed, from + 1, to - 1, GalleryPainter.RAIL, LIVING_FLOOR);
            builder.lamps(!transposed, from, to, GalleryPainter.HIGH - 1, LIVING_FLOOR + GalleryPainter.HEIGHT);
            int first = low ? ARM_START + 2 : wallHigh + 2;
            int last = low ? wallLow - 2 : ARM_END - 2;
            builder.bays(transposed, first, last, bayLow, -1);
            builder.bays(transposed, first, last, bayHigh, 1);
        }
    }
}
