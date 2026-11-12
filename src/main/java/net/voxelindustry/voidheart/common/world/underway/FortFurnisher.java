package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.ENTRY_FLOOR;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.LIVING_FLOOR;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayProp.*;

/**
 * Furnishes a fort's rooms by what they were for. Things stand against the walls and hang on them, tables and machines form islands down the middle of rooms wide enough to walk around them, and a little is left lying about. Doors, passages and rails stay clear, and time has stripped, opened or toppled a share of it, except in a sealed fort.
 */
final class FortFurnisher
{
    private static final int SIZE = DistrictPos.SIZE;
    private static final int LEVELS = 3;
    private static final int DOOR_CLEARANCE = 2;
    private static final int ISLAND_MIN_WIDTH = 7;
    private static final int ISLAND_EVERY = 4;
    private static final int ISLAND_MARGIN = 3;
    private static final int TALL_ROOM = 8;
    private static final int TABLETOP_PERCENT = 60;
    private static final int LOADED_WAGON_PERCENT = 40;
    private static final int WAGON_EVERY_RAILS = 30;
    private static final int MAX_WAGONS = 3;
    private static final int SCATTER_EVERY_CELLS = 48;
    private static final int RUBBLE_EVERY_CELLS = 30;
    private static final int PERCENT = 100;
    private static final byte RAIL_ALONG = 1;
    private static final byte RAIL_ACROSS = 2;
    private static final int[][] SIDES = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };

    private static final EnumSet<UnderwayRoomKind> PASSAGES = EnumSet.of(UnderwayRoomKind.ENTRY_GALLERY, UnderwayRoomKind.RUNG, UnderwayRoomKind.CROSS_GALLERY,
            UnderwayRoomKind.ACCESS_GALLERY, UnderwayRoomKind.GALLERY, UnderwayRoomKind.MAGAZINE_GALLERY, UnderwayRoomKind.LOBBY);
    private static final EnumSet<UnderwayRoomKind> NARROW_PASSAGES = EnumSet.of(UnderwayRoomKind.ENTRY_GALLERY, UnderwayRoomKind.RUNG, UnderwayRoomKind.CROSS_GALLERY, UnderwayRoomKind.ACCESS_GALLERY);
    private static final EnumSet<UnderwayRoomKind> WITH_WAGONS = EnumSet.of(UnderwayRoomKind.GALLERY, UnderwayRoomKind.ACCESS_GALLERY, UnderwayRoomKind.MAGAZINE_GALLERY,
            UnderwayRoomKind.DEPOT, UnderwayRoomKind.FREIGHT_HALL);
    private static final EnumSet<UnderwayProp> TABLES = EnumSet.of(TABLE, DESK);
    private static final EnumSet<UnderwayProp> HUNG_LOW = EnumSet.of(WASH_BASIN, SHOWER, WARD_PLATE, TRIPWIRE_EMITTER);
    private static final EnumSet<UnderwayProp> HUNG_HIGH = EnumSet.of(CONDUIT_RUN, FAN, KLAXON, PIPES, SENTINEL_EYE, EMERGENCY_WALL_LAMP);

    private final FortBuilder builder;
    private final List<UnderwayRoom> rooms;
    private final boolean[][] open = new boolean[LEVELS][SIZE * SIZE];
    private final boolean[][] kept = new boolean[LEVELS][SIZE * SIZE];
    private final byte[][] rails = new byte[LEVELS][SIZE * SIZE];
    private final Set<Long> taken = new HashSet<>();
    private final List<UnderwayPropSpot> spots = new ArrayList<>();
    private final int skipPercent;
    private final int wornPercent;

    FortFurnisher(FortBuilder builder, UnderwayNode node, List<UnderwayRoom> rooms, List<UnderwayOpening> openings, List<UnderwayShaft> shafts, List<UnderwayStroke> strokes)
    {
        this.builder = builder;
        this.rooms = rooms;
        skipPercent = switch (builder.ruin)
        {
            case SEALED -> 5;
            case DRY -> 25;
            case FLOODED -> 30;
            case COLLAPSED, BREACHED -> 40;
        };
        wornPercent = builder.ruin == UnderwayRuin.SEALED ? 0 : 30;

        for (var room : rooms)
        {
            if (room.kind() == UnderwayRoomKind.CAVED_IN)
                continue;
            int level = level(room.floorY());
            mark(open[level], room.a0(), room.c0(), room.a1(), room.c1());
            if (NARROW_PASSAGES.contains(room.kind()) || room.kind() == UnderwayRoomKind.PIT)
                mark(kept[level], room.a0(), room.c0(), room.a1(), room.c1());
        }
        for (var opening : openings)
        {
            int level = level(opening.floorY());
            mark(open[level], opening.a0(), opening.c0(), opening.a1(), opening.c1());
            mark(kept[level], opening.a0() - DOOR_CLEARANCE, opening.c0() - DOOR_CLEARANCE, opening.a1() + DOOR_CLEARANCE, opening.c1() + DOOR_CLEARANCE);
        }
        for (var shaft : shafts)
        {
            for (int level = level(shaft.topFloor()); level <= level(shaft.bottomFloor()); level++)
            {
                mark(open[level], shaft.a0(), shaft.c0(), shaft.a0() + StairShaft.SIZE - 1, shaft.c0() + StairShaft.SIZE - 1);
                mark(kept[level], shaft.a0(), shaft.c0(), shaft.a0() + StairShaft.SIZE - 1, shaft.c0() + StairShaft.SIZE - 1);
            }
        }
        if (node.isOnLine())
            mark(open[LEVELS - 1], 0, TrunkPainter.LOW, SIZE - 1, TrunkPainter.HIGH);
        if (node == UnderwayNode.HUB)
            mark(open[LEVELS - 1], TrunkPainter.LOW, 0, TrunkPainter.HIGH, SIZE - 1);
        if (node == UnderwayNode.POST)
            mark(open[1], 0, GalleryPainter.LOW, SIZE - 1, GalleryPainter.HIGH);

        for (var stroke : strokes)
        {
            if (stroke.material() == UnderwayMaterial.DOOR || stroke.material() == UnderwayMaterial.EMBRASURE)
                mark(kept[level(stroke.y0() - 1)], stroke.a0() - DOOR_CLEARANCE, stroke.c0() - DOOR_CLEARANCE, stroke.a1() + DOOR_CLEARANCE, stroke.c1() + DOOR_CLEARANCE);
            if (!stroke.material().isRail())
                continue;
            boolean alongX = stroke.material() == UnderwayMaterial.TRUNK_RAIL_X || stroke.material() == UnderwayMaterial.FEEDER_RAIL_X;
            byte direction = alongX == builder.frame.alongX() ? RAIL_ALONG : RAIL_ACROSS;
            for (int across = Math.max(0, stroke.c0()); across <= Math.min(SIZE - 1, stroke.c1()); across++)
            {
                for (int along = Math.max(0, stroke.a0()); along <= Math.min(SIZE - 1, stroke.a1()); along++)
                    rails[level(stroke.y0() - 1)][cell(along, across)] = direction;
            }
        }
    }

    List<UnderwayPropSpot> furnish()
    {
        for (var room : rooms)
        {
            var recipe = Recipe.of(room.kind());
            if (recipe == null)
                continue;
            if (room.kind() == UnderwayRoomKind.BRIG)
                bars(room);
            walls(room, recipe);
            islands(room, recipe);
            ceiling(room, recipe);
            wagons(room);
            scatter(room, recipe);
        }
        return spots;
    }

    private static int level(int floorY)
    {
        if (floorY >= ENTRY_FLOOR - 4)
            return 0;
        return floorY >= LIVING_FLOOR - 4 ? 1 : 2;
    }

    private static int cell(int along, int across)
    {
        return along * SIZE + across;
    }

    private static void mark(boolean[] cells, int a0, int c0, int a1, int c1)
    {
        for (int along = Math.max(0, a0); along <= Math.min(SIZE - 1, a1); along++)
        {
            for (int across = Math.max(0, c0); across <= Math.min(SIZE - 1, c1); across++)
                cells[cell(along, across)] = true;
        }
    }

    private boolean isOpen(int level, int along, int across)
    {
        return along >= 0 && along < SIZE && across >= 0 && across < SIZE && open[level][cell(along, across)];
    }

    /** @return whether something may stand on the floor there: off the rails and out of the way of doors and passages */
    private boolean isFree(int level, int along, int across)
    {
        return along >= 0 && along < SIZE && across >= 0 && across < SIZE && !kept[level][cell(along, across)] && rails[level][cell(along, across)] == 0;
    }

    private boolean place(UnderwayProp prop, int along, int y, int across, int faceAlong, int faceAcross)
    {
        if (!taken.add(((long) along << 40) | ((long) (y & 0xFFFF) << 20) | across))
            return false;
        spots.add(new UnderwayPropSpot(prop, along, y, across, faceAlong, faceAcross));
        return true;
    }

    /** @return the prop as time left it: stripped, opened, toppled or emptied, for some */
    private UnderwayProp worn(UnderwayProp prop)
    {
        var after = switch (prop)
        {
            case BUNK -> BUNK_BARE;
            case LOCKER -> LOCKER_OPEN;
            case CHAIR -> CHAIR_FALLEN;
            case CRATE -> builder.roll(2) == 0 ? BROKEN_CRATE : CRATE_OPEN;
            case SHELVING -> SHELVING_EMPTY;
            case LANCE_RACK -> LANCE_RACK_EMPTY;
            default -> prop;
        };
        if (after == prop)
            return prop;
        int percent = prop == LANCE_RACK ? wornPercent * 2 : wornPercent;
        return builder.roll(PERCENT) < percent ? after : prop;
    }

    /** Walks around the room's walls, standing things against them and hanging things on them. */
    private void walls(UnderwayRoom room, Recipe recipe)
    {
        int level = level(room.floorY());
        boolean passage = PASSAGES.contains(room.kind());
        int standing = builder.roll(Math.max(1, recipe.standEvery));
        int hanging = builder.roll(Math.max(1, recipe.hangEvery));
        int nextStanding = builder.roll(Math.max(1, recipe.stand.length));
        int nextHanging = builder.roll(Math.max(1, recipe.hang.length));
        for (int side = 0; side < SIDES.length; side++)
        {
            int outAlong = SIDES[side][0];
            int outAcross = SIDES[side][1];
            boolean runsAlong = outAlong == 0;
            int from = (runsAlong ? room.a0() : room.c0()) + 1;
            int to = (runsAlong ? room.a1() : room.c1()) - 1;
            for (int run = from; run <= to; run++)
            {
                int along = runsAlong ? run : (outAlong < 0 ? room.a0() : room.a1());
                int across = runsAlong ? (outAcross < 0 ? room.c0() : room.c1()) : run;
                if (isOpen(level, along + outAlong, across + outAcross))
                    continue;
                boolean free = isFree(level, along, across);

                if (free && recipe.stand.length > 0 && standing++ % recipe.standEvery == 0 && builder.roll(PERCENT) >= skipPercent)
                {
                    var prop = worn(recipe.stand[nextStanding++ % recipe.stand.length]);
                    if (place(prop, along, room.floorY() + 1, across, -outAlong, -outAcross) && TABLES.contains(prop) && recipe.tabletop.length > 0 && builder.roll(PERCENT) < TABLETOP_PERCENT)
                        place(recipe.tabletop[builder.roll(recipe.tabletop.length)], along, room.floorY() + 2, across, -outAlong, -outAcross);
                }
                if (recipe.hang.length > 0 && hanging++ % recipe.hangEvery == 0 && builder.roll(PERCENT) >= skipPercent)
                {
                    var prop = recipe.hang[nextHanging++ % recipe.hang.length];
                    int height = HUNG_LOW.contains(prop) ? 1 : (passage || HUNG_HIGH.contains(prop) ? 3 : 2);
                    if ((free || height == 3) && height <= room.height())
                        place(prop, along, room.floorY() + height, across, -outAlong, -outAcross);
                }
            }
        }
    }

    /** Sets islands down the middle of a room wide enough to walk around them: a table or a machine, with what goes beside it. */
    private void islands(UnderwayRoom room, Recipe recipe)
    {
        int width = room.a1() - room.a0() + 1;
        int depth = room.c1() - room.c0() + 1;
        if (recipe.middle.length == 0 || Math.min(width, depth) < ISLAND_MIN_WIDTH)
            return;
        int level = level(room.floorY());
        boolean longAlong = width >= depth;
        int middle = longAlong ? (room.c0() + room.c1()) / 2 : (room.a0() + room.a1()) / 2;
        int from = (longAlong ? room.a0() : room.c0()) + ISLAND_MARGIN;
        int to = (longAlong ? room.a1() : room.c1()) - ISLAND_MARGIN;
        int next = builder.roll(recipe.middle.length);
        int nextBeside = 0;
        for (int run = from; run <= to; run += ISLAND_EVERY)
        {
            int along = longAlong ? run : middle;
            int across = longAlong ? middle : run;
            if (!isFree(level, along, across) || builder.roll(PERCENT) < skipPercent)
                continue;
            var prop = worn(recipe.middle[next++ % recipe.middle.length]);
            int face = builder.roll(2) == 0 ? 1 : -1;
            if (!place(prop, along, room.floorY() + 1, across, longAlong ? face : 0, longAlong ? 0 : face))
                continue;
            if (TABLES.contains(prop) && recipe.tabletop.length > 0 && builder.roll(PERCENT) < TABLETOP_PERCENT)
                place(recipe.tabletop[builder.roll(recipe.tabletop.length)], along, room.floorY() + 2, across, longAlong ? face : 0, longAlong ? 0 : face);
            for (int flank = -1; flank <= 1 && recipe.beside.length > 0; flank += 2)
            {
                int besideAlong = longAlong ? along : along + flank;
                int besideAcross = longAlong ? across + flank : across;
                if (isFree(level, besideAlong, besideAcross) && builder.roll(PERCENT) >= skipPercent)
                    place(worn(recipe.beside[nextBeside++ % recipe.beside.length]), besideAlong, room.floorY() + 1, besideAcross, longAlong ? 0 : -flank, longAlong ? -flank : 0);
            }
        }
    }

    private void ceiling(UnderwayRoom room, Recipe recipe)
    {
        if (recipe.overhead.length == 0 || room.height() >= TALL_ROOM)
            return;
        boolean longAlong = room.a1() - room.a0() >= room.c1() - room.c0();
        int middle = longAlong ? (room.c0() + room.c1()) / 2 : (room.a0() + room.a1()) / 2;
        int next = 0;
        for (int run = (longAlong ? room.a0() : room.c0()) + 2 + builder.roll(recipe.overheadEvery); run <= (longAlong ? room.a1() : room.c1()) - 2; run += recipe.overheadEvery)
            place(recipe.overhead[next++ % recipe.overhead.length], longAlong ? run : middle, room.floorY() + room.height(), longAlong ? middle : run, longAlong ? 0 : 1, longAlong ? 1 : 0);
    }

    /** Leaves a few wagons standing on the room's tracks. */
    private void wagons(UnderwayRoom room)
    {
        if (!WITH_WAGONS.contains(room.kind()))
            return;
        int level = level(room.floorY());
        var tracks = new ArrayList<int[]>();
        for (int along = room.a0(); along <= room.a1(); along++)
        {
            for (int across = room.c0(); across <= room.c1(); across++)
            {
                if (along >= 0 && along < SIZE && across >= 0 && across < SIZE && rails[level][cell(along, across)] != 0 && !kept[level][cell(along, across)])
                    tracks.add(new int[] { along, across });
            }
        }
        for (int wagon = Math.min(MAX_WAGONS, tracks.size() / WAGON_EVERY_RAILS + (tracks.isEmpty() ? 0 : builder.roll(2))); wagon > 0; wagon--)
        {
            int[] at = tracks.get(builder.roll(tracks.size()));
            boolean runsAlong = rails[level][cell(at[0], at[1])] == RAIL_ALONG;
            int face = builder.roll(2) == 0 ? 1 : -1;
            place(builder.roll(PERCENT) < LOADED_WAGON_PERCENT ? WAGON_LOADED : WAGON, at[0], room.floorY() + 1, at[1], runsAlong ? face : 0, runsAlong ? 0 : face);
        }
    }

    private void scatter(UnderwayRoom room, Recipe recipe)
    {
        int level = level(room.floorY());
        int area = (room.a1() - room.a0() - 1) * (room.c1() - room.c0() - 1);
        if (area <= 0)
            return;
        int rubble = builder.ruin == UnderwayRuin.COLLAPSED || builder.ruin == UnderwayRuin.BREACHED ? area / RUBBLE_EVERY_CELLS : 0;
        int count = recipe.loose.length == 0 ? 0 : area / SCATTER_EVERY_CELLS + builder.roll(2);
        for (int piece = 0; piece < count + rubble; piece++)
        {
            int along = room.a0() + 1 + builder.roll(room.a1() - room.a0() - 1);
            int across = room.c0() + 1 + builder.roll(room.c1() - room.c0() - 1);
            if (!isFree(level, along, across))
                continue;
            var prop = piece < count ? recipe.loose[builder.roll(recipe.loose.length)] : RUBBLE_PILE;
            int turn = builder.roll(SIDES.length);
            place(prop, along, room.floorY() + 1, across, SIDES[turn][0], SIDES[turn][1]);
        }
    }

    /** Closes the far half of a brig behind a row of bars, with a gap for its door. */
    private void bars(UnderwayRoom room)
    {
        int level = level(room.floorY());
        boolean longAlong = room.a1() - room.a0() >= room.c1() - room.c0();
        int middle = longAlong ? (room.a0() + room.a1()) / 2 : (room.c0() + room.c1()) / 2;
        int from = longAlong ? room.c0() : room.a0();
        int to = longAlong ? room.c1() : room.a1();
        for (int run = from; run < to; run++)
        {
            int along = longAlong ? middle : run;
            int across = longAlong ? run : middle;
            if (isFree(level, along, across))
                place(CELL_BARS, along, room.floorY() + 1, across, longAlong ? 1 : 0, longAlong ? 0 : 1);
        }
        if (longAlong)
            mark(kept[level], middle - 1, to, middle + 1, to);
        else
            mark(kept[level], to, middle - 1, to, middle + 1);
    }

    /** What a kind of room holds. */
    private static final class Recipe
    {
        private static final UnderwayProp[] NONE = {};
        private static final Recipe[] BY_KIND = new Recipe[UnderwayRoomKind.values().length];

        private UnderwayProp[] stand = NONE;
        private int standEvery = 1;
        private UnderwayProp[] hang = NONE;
        private int hangEvery = 1;
        private UnderwayProp[] middle = NONE;
        private UnderwayProp[] beside = NONE;
        private UnderwayProp[] tabletop = NONE;
        private UnderwayProp[] overhead = NONE;
        private int overheadEvery = 6;
        private UnderwayProp[] loose = NONE;

        static Recipe of(UnderwayRoomKind kind)
        {
            return BY_KIND[kind.ordinal()];
        }

        private static Recipe room(UnderwayRoomKind kind)
        {
            return BY_KIND[kind.ordinal()] = new Recipe();
        }

        /** Things standing against the walls, taken in turn, one every {@code every} blocks of free wall. */
        private Recipe stand(int every, UnderwayProp... props)
        {
            stand = props;
            standEvery = every;
            return this;
        }

        private Recipe hang(int every, UnderwayProp... props)
        {
            hang = props;
            hangEvery = every;
            return this;
        }

        private Recipe middle(UnderwayProp... props)
        {
            middle = props;
            return this;
        }

        private Recipe beside(UnderwayProp... props)
        {
            beside = props;
            return this;
        }

        private Recipe tabletop(UnderwayProp... props)
        {
            tabletop = props;
            return this;
        }

        private Recipe overhead(int every, UnderwayProp... props)
        {
            overhead = props;
            overheadEvery = every;
            return this;
        }

        private Recipe loose(UnderwayProp... props)
        {
            loose = props;
            return this;
        }

        static
        {
            room(UnderwayRoomKind.HATCH_LOBBY).stand(3, PAVISE, WARD_STELE, CELL_CASES).hang(3, SECTOR_SIGN, KLAXON, ARROW_SIGN).overhead(4, TURRET_CUPOLA);
            room(UnderwayRoomKind.VESTIBULE).stand(3, PAVISE, WARD_STELE).hang(3, SENTINEL_EYE, SPEAKING_TUBE, SECTOR_SIGN).overhead(4, TURRET_CUPOLA);
            room(UnderwayRoomKind.ENTRY_GALLERY).hang(5, CONDUIT_RUN, EMERGENCY_WALL_LAMP, SENTINEL_EYE, CONDUIT_RUN, PIPES);
            room(UnderwayRoomKind.GUARD_ROOM).stand(2, LANCE_RACK, DESK, CHAIR, CELL_CASES, LOCKER, STOOL).hang(3, SPEAKING_TUBE, WARD_MASKS, NOTICE_BOARD).tabletop(VOICE_HORN, DESK_LAMP, PAPERS).loose(BOOTS, LITTER);
            room(UnderwayRoomKind.ARMOURY).stand(1, LANCE_RACK, LANCE_RACK, CELL_CASES, LANCE_RACK, CRATE, CANISTERS).hang(3, WARD_MASKS, HELMETS, TOOL_BOARD).loose(CELL_CASES, LITTER);
            room(UnderwayRoomKind.QUARTERS).stand(1, BUNK, LOCKER, DESK, FOOTLOCKER, BUNK, STOVE).hang(3, HOOKS, WALL_SHELF, POSTER).tabletop(DESK_LAMP, PAPERS, BOTTLES).loose(BOOTS, KIT_BAG);
            room(UnderwayRoomKind.DECONTAMINATION).stand(3, LOCKER, DRUM, BUCKET).hang(2, SHOWER, SHOWER, WARD_MASKS, WASH_BASIN, WARD_PLATE).overhead(4, DUCT_VENT);

            room(UnderwayRoomKind.GALLERY).stand(12, CRATE, PLATFORM_BENCH, TRASH_BIN, DRUM, TROLLEY, PAVISE).hang(6, CONDUIT_RUN, PIPES, ARROW_SIGN, CONDUIT_RUN, SECTOR_SIGN, RELAY_BOX, SENTINEL_EYE);
            room(UnderwayRoomKind.RUNG).hang(5, CONDUIT_RUN, ARROW_SIGN, PIPES);
            room(UnderwayRoomKind.CROSS_GALLERY).hang(6, CONDUIT_RUN, ARROW_SIGN, PIPES, EMERGENCY_WALL_LAMP);
            room(UnderwayRoomKind.ACCESS_GALLERY).hang(8, CONDUIT_RUN, EMERGENCY_WALL_LAMP, ARROW_SIGN, PIPES, SENTINEL_EYE);

            room(UnderwayRoomKind.BARRACKS).stand(1, BUNK, BUNK, LOCKER, BUNK, BUNK, FOOTLOCKER).hang(4, HELMETS, HOOKS, WALL_SHELF, POSTER).middle(TABLE, STOVE, TABLE).beside(STOOL, CHAIR)
                    .tabletop(CARD_GAME, MESS_KIT, BOTTLES).loose(BOOTS, KIT_BAG, LITTER);
            room(UnderwayRoomKind.SHELTER).stand(1, COT, COT, LUGGAGE, COT, KIT_BAG, COT, CRATE).hang(4, WALL_SHELF, HOOKS, POSTER, NOTICE_BOARD).middle(STOVE, TABLE, COT).beside(BENCH, STOOL)
                    .tabletop(MESS_KIT, BOTTLES).loose(LUGGAGE, LITTER, BOOTS, BUCKET);
            room(UnderwayRoomKind.MESS).stand(2, COUNTER, URN, TIN_RACK, BENCH, TRASH_BIN, BENCH).hang(4, NOTICE_BOARD, POSTER, WALL_SHELF).middle(TABLE).beside(BENCH)
                    .tabletop(MESS_KIT, BOTTLES, CARD_GAME, MESS_KIT).loose(LITTER, CHAIR_FALLEN);
            room(UnderwayRoomKind.KITCHEN).stand(1, RANGE, RANGE, COUNTER, COUNTER, TIN_RACK, SACKS, URN, BUCKET).hang(3, PIPES, WASH_BASIN, WALL_SHELF).middle(COUNTER).tabletop(MESS_KIT, BOTTLES)
                    .overhead(5, DUCT_VENT).loose(SACKS, LITTER);
            room(UnderwayRoomKind.INFIRMARY).stand(1, STRETCHER, MEDICAL_TROLLEY, STRETCHER, LOCKER, STRETCHER, FILING_CABINET).hang(3, MEDICINE_CABINET, WASH_BASIN, NOTICE_BOARD).middle(STRETCHER)
                    .beside(MEDICAL_TROLLEY, STOOL).loose(BUCKET, LITTER);
            room(UnderwayRoomKind.POWER_PLANT).stand(2, SWITCHGEAR, CELL_BANK, SWITCHGEAR, AIR_TANK, DRUM, CARBOYS).hang(3, RELAY_BOX, PIPES, PRESSURE_GAUGE, VALVE, CONDUIT_RUN, VOID_CONDUIT, WARD_PLATE)
                    .middle(BOILER, DYNAMO).overhead(6, HOIST, DUCT).loose(DRUMS, BUCKET, LITTER);
            room(UnderwayRoomKind.FILTERS).stand(2, FILTER_DRUM, FILTER_DRUM, TANK, SWITCHGEAR).hang(3, FAN, PIPES, VALVE, VOID_CONDUIT, WARD_PLATE).middle(FILTER_DRUM).overhead(4, DUCT, DUCT_VENT);
            room(UnderwayRoomKind.CISTERN).stand(1, TANK, TANK, PUMP, TANK).hang(3, PIPES, VALVE, PRESSURE_GAUGE).middle(TANK).overhead(6, DUCT).loose(BUCKET);
            room(UnderwayRoomKind.COMMAND_POST).stand(1, DESK, FILING_CABINET, CONSOLE, DESK, LOCKER, CHAIR).hang(3, WALL_MAP, SPEAKING_TUBE, NOTICE_BOARD, LINE_MAP).middle(MAP_TABLE).beside(CHAIR)
                    .tabletop(TYPEWRITER, VOICE_HORN, PAPERS, DESK_LAMP, RESONATOR).loose(LITTER, CHAIR_FALLEN);
            room(UnderwayRoomKind.EXCHANGE).stand(1, SWITCHBOARD, SWITCHBOARD, DESK, CELL_BANK, FILING_CABINET, STOOL).hang(3, SPEAKING_TUBE, RELAY_BOX, CONDUIT_RUN, NOTICE_BOARD)
                    .tabletop(RESONATOR, VOICE_HORN, PAPERS).loose(LITTER);
            room(UnderwayRoomKind.STORES).stand(1, SHELVING, SHELVING, CRATE_STACK, TIN_RACK, CRATE, SACKS, DRUM).hang(5, NOTICE_BOARD, WARD_PLATE).middle(CRATE_STACK, TARP_PILE, CRATE, CABLE_REEL)
                    .loose(BROKEN_CRATE, LITTER, CRATE_OPEN);
            room(UnderwayRoomKind.WORKSHOP).stand(1, WORKBENCH, LATHE, SHELVING, WORKBENCH, DRUM, CABLE_REEL, LOCKER).hang(3, TOOL_BOARD, RELAY_BOX, PIPES, POSTER).middle(WORKBENCH, LATHE).overhead(6, HOIST)
                    .loose(CARBOYS, BUCKET, LITTER);
            room(UnderwayRoomKind.WASHROOM).stand(2, LATRINE, LATRINE, BUCKET, LATRINE).hang(2, WASH_BASIN, WASH_BASIN, SHOWER, SHOWER, PIPES).loose(BUCKET);
            room(UnderwayRoomKind.LABORATORY).stand(1, COUNTER, COUNTER, FILING_CABINET, SHELVING, TANK, LOCKER).hang(3, MEDICINE_CABINET, VOID_CONDUIT, PIPES, WALL_SHELF, RELAY_BOX).middle(TABLE).beside(STOOL)
                    .tabletop(LAB_GLASS, VOID_JAR, LAB_GLASS, BOTTLES, PAPERS, DESK_LAMP).overhead(5, DUCT_VENT).loose(LITTER);
            room(UnderwayRoomKind.BRIG).stand(3, COT, BUCKET, COT, STOOL).hang(6, NOTICE_BOARD, HOOKS);
            room(UnderwayRoomKind.ARCHIVE).stand(1, ARCHIVE_SHELF, ARCHIVE_SHELF, ARCHIVE_SHELF, FILING_CABINET, ARCHIVE_SHELF, DESK).hang(6, NOTICE_BOARD, WALL_MAP).middle(ARCHIVE_SHELF)
                    .tabletop(PAPERS, DESK_LAMP).loose(LITTER, CRATE);
            room(UnderwayRoomKind.MORGUE).stand(1, MORGUE_DRAWERS, MORGUE_DRAWERS, MORGUE_DRAWERS, STRETCHER, FILING_CABINET).hang(4, WASH_BASIN, MEDICINE_CABINET).middle(STRETCHER).loose(BUCKET);

            room(UnderwayRoomKind.PLATFORM).stand(5, PLATFORM_BENCH, PLATFORM_BENCH, TRASH_BIN, LUGGAGE, PLATFORM_BENCH, TROLLEY, CRATE).hang(7, STATION_SIGN, LINE_MAP, ARROW_SIGN, NOTICE_BOARD, KLAXON)
                    .middle(TURRET_WELL, LUGGAGE, TROLLEY, CRATE_STACK).overhead(26, STATION_CLOCK).loose(LITTER, LUGGAGE);
            room(UnderwayRoomKind.LOBBY).hang(3, ARROW_SIGN, SECTOR_SIGN, CONDUIT_RUN);
            room(UnderwayRoomKind.MAGAZINE_GALLERY).hang(5, WARD_PLATE, CONDUIT_RUN, SECTOR_SIGN, WARD_PLATE);
            room(UnderwayRoomKind.MAGAZINE).stand(1, CELL_RACK, CELL_RACK, CELL_CASES, CANISTERS, CRATE_STACK, CRATE).hang(4, WARD_PLATE).loose(CELL_CASES, BROKEN_CRATE);
            room(UnderwayRoomKind.DEPOT).stand(2, WORKBENCH, DRUM, CARBOYS, CABLE_REEL, SHELVING, CRATE_STACK, LOCKER).hang(4, TOOL_BOARD, RELAY_BOX, PIPES).overhead(6, HOIST).loose(DRUMS, LITTER, BUCKET);
            room(UnderwayRoomKind.PUMP_ROOM).stand(2, PUMP, TANK, PUMP, SWITCHGEAR).hang(3, PIPES, VALVE, PRESSURE_GAUGE).loose(BUCKET);
            room(UnderwayRoomKind.FREIGHT_HALL).stand(2, CRATE_STACK, CRATE, DRUMS, TARP_PILE, SHELVING, SACKS).hang(6, WARD_PLATE, SECTOR_SIGN).middle(CRATE_STACK, TARP_PILE, CRATE).overhead(7, HOIST)
                    .loose(BROKEN_CRATE, LITTER);
            room(UnderwayRoomKind.SIGNAL_CABIN).stand(1, LEVER_FRAME, LEVER_FRAME, CONSOLE, DESK, STOOL).hang(3, LINE_MAP, SPEAKING_TUBE, NOTICE_BOARD).tabletop(VOICE_HORN, PAPERS).loose(LITTER);
            room(UnderwayRoomKind.CONTROL_ROOM).stand(1, CONSOLE, CONSOLE, SWITCHGEAR, LEVER_FRAME, DESK, FILING_CABINET).hang(3, LINE_MAP, WALL_MAP, SPEAKING_TUBE, KLAXON).middle(MAP_TABLE).beside(CHAIR)
                    .tabletop(VOICE_HORN, PAPERS, DESK_LAMP).overhead(6, PERISCOPE).loose(LITTER, CHAIR_FALLEN);
        }
    }
}
