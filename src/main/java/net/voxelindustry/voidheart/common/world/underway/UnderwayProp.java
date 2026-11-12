package net.voxelindustry.voidheart.common.world.underway;

import java.util.List;
import java.util.Locale;

import static net.voxelindustry.voidheart.common.world.underway.UnderwayProp.Mount.CEILING;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayProp.Mount.FLOOR;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayProp.Mount.WALL;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayProp.Sound.CLOTH;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayProp.Sound.GLASS;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayProp.Sound.METAL;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayProp.Sound.STONE;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayProp.Sound.WOOD;

/**
 * What the people of the Underway left behind: the copper, brass and lead of a steam age that drew on rubrum and held the void behind wards. Each prop is written once, as boxes in sixteenths of a block seen facing north, and both its model and its shape come from that: {@code x0 y0 z0 x1 y1 z1 texture}, boxes split by {@code |}. A box may end with faces that show another texture from a given corner, as {@code n=dials@1,1} for its north face.
 * <p>
 * Wall props hang on the wall to their south, ceiling props from the block above. Tabletop pieces are floor props meant for the block over a table.
 */
public enum UnderwayProp
{
    // Quarters
    BUNK(FLOOR, METAL, Parts.BUNK_FRAME + "|1 3 1 15 4 15 steel_dark|1 4 1 15 6 15 mattress|1 6 6 15 7 15 blanket|1 11 1 15 12 15 steel_dark|1 12 1 15 14 15 mattress|2 14 2 14 16 6 blanket"),
    BUNK_BARE(FLOOR, METAL, Parts.BUNK_FRAME + "|1 3 1 15 4 15 grille|1 11 1 15 12 15 grille"),
    COT(FLOOR, CLOTH, "1 4 1 15 5 15 canvas|0 4 0 1 5 16 wood|15 4 0 16 5 16 wood|1 0 2 2 4 3 steel|14 0 2 15 4 3 steel|1 0 13 2 4 14 steel|14 0 13 15 4 14 steel|3 5 2 13 7 5 blanket"),
    LOCKER(FLOOR, METAL, "1 0 5 15 16 16 lead|2 1 4 14 15 5 lead|4 11 3 12 14 4 grille|12 6 3 13 9 4 steel_dark"),
    LOCKER_OPEN(FLOOR, METAL, "1 0 15 15 16 16 lead|1 0 5 2 16 15 lead|14 0 5 15 16 15 lead|2 15 5 14 16 15 lead|2 0 5 14 1 15 lead|2 10 5 14 11 15 lead"
            + "|14 1 0 15 15 5 lead|3 2 9 7 10 13 cloth_dark|9 1 8 12 4 12 leather|4 11 7 9 14 12 brass"),
    FOOTLOCKER(FLOOR, METAL, "1 0 3 15 7 13 copper|1 7 3 15 9 13 copper|7 5 2 9 8 3 brass|0 4 6 1 5 10 leather|15 4 6 16 5 10 leather"),
    TABLE(FLOOR, WOOD, "0 14 1 16 16 15 wood|1 0 2 2 14 3 steel|14 0 2 15 14 3 steel|1 0 13 2 14 14 steel|14 0 13 15 14 14 steel|2 4 7 14 5 9 steel_dark"),
    BENCH(FLOOR, WOOD, "0 7 4 16 9 12 wood|1 0 5 2 7 11 steel|14 0 5 15 7 11 steel"),
    STOOL(FLOOR, WOOD, "4 8 4 12 10 12 wood|7 1 7 9 8 9 steel|5 0 5 11 1 11 steel_dark"),
    DESK(FLOOR, METAL, "0 15 2 16 16 14 lead|1 0 3 6 15 13 lead|3 10 2 4 11 3 steel_dark|3 5 2 4 6 3 steel_dark|14 0 3 15 15 4 steel|14 0 12 15 15 13 steel"),
    CHAIR(FLOOR, WOOD, "4 7 3 12 8 12 wood|4 0 3 5 7 4 steel|11 0 3 12 7 4 steel|4 0 12 5 16 13 steel|11 0 12 12 16 13 steel|5 11 12 11 15 13 wood"),
    CHAIR_FALLEN(FLOOR, WOOD, "6 0 3 7 8 11 wood|7 0 3 13 1 4 steel|7 7 3 13 8 4 steel|7 0 10 13 1 11 steel|7 7 10 13 8 11 steel|0 0 10 6 1 11 steel|0 7 10 6 8 11 steel|1 1 10 2 7 11 wood"),
    HOOKS(WALL, WOOD, "1 11 15 15 13 16 wood|3 10 14 4 11 15 steel_dark|8 10 14 9 11 15 steel_dark|12 10 14 13 11 15 steel_dark|2 2 14 6 10 15 cloth_dark|7 4 14 10 10 15 canvas|10 6 13 14 10 14 cloth_dark"),
    HELMETS(WALL, WOOD, "1 6 11 15 7 16 wood|3 3 15 4 6 16 steel_dark|12 3 15 13 6 16 steel_dark|2 7 12 6 9 15 copper|3 9 13 5 10 14 brass|9 7 12 13 9 15 copper|10 9 13 12 10 14 brass"),
    BOOTS(FLOOR, CLOTH, "3 0 4 6 2 10 leather|3 2 7 6 7 10 leather|9 0 5 12 2 11 leather|9 2 8 12 6 11 leather"),
    KIT_BAG(FLOOR, CLOTH, "2 0 5 14 5 11 canvas|3 5 6 13 6 10 canvas|1 1 6 2 4 10 leather"),
    WALL_SHELF(WALL, WOOD, "1 8 11 15 9 16 wood|2 9 12 4 14 15 leather|4 9 12 5 13 15 wood_dark|7 9 12 10 12 15 enamel|11 9 14 14 13 15 paper"),
    POSTER(WALL, CLOTH, "2 2 15 14 15 16 paper"),
    STOVE(FLOOR, METAL, "3 0 3 13 10 13 steel_dark|5 2 2 11 7 3 steel_plate|2 10 2 14 11 14 steel|6 11 9 10 16 13 steel|3 11 3 6 13 6 brass"),

    // Tabletop
    MESS_KIT(FLOOR, METAL, "2 0 3 8 1 9 enamel|10 0 4 13 3 7 enamel|9 0 10 13 5 12 copper|4 0 11 7 2 14 steel"),
    RESONATOR(FLOOR, METAL, "2 0 5 14 7 12 copper|3 1 4 13 6 5 brass n=dials@1,1|5 7 7 11 8 11 brass|7 8 8 9 13 10 ley"),
    VOICE_HORN(FLOOR, METAL, "4 0 6 12 4 12 wood_dark|7 4 8 9 8 10 brass|6 8 5 10 11 8 brass|5 7 3 11 12 5 brass|12 1 5 13 2 6 brass"),
    TYPEWRITER(FLOOR, METAL, "3 0 4 13 3 12 copper_dark|4 3 4 12 4 7 rubber|2 3 9 14 5 11 steel|5 5 10 11 9 11 paper"),
    PAPERS(FLOOR, CLOTH, "2 0 3 9 1 11 paper|9 0 6 15 1 13 paper|4 1 4 8 2 9 leather"),
    DESK_LAMP(FLOOR, METAL, "5 0 5 11 1 11 steel_dark|7 1 7 9 7 9 brass|6 7 6 10 8 10 lens_off|4 8 4 12 11 12 copper"),
    BOTTLES(FLOOR, GLASS, "2 0 3 5 5 6 glass_green|3 5 4 4 8 5 glass_green|8 0 9 14 3 12 glass_green|14 1 10 16 2 11 glass_green|9 0 3 12 3 6 enamel"),
    CARD_GAME(FLOOR, CLOTH, "3 0 4 6 1 8 paper|7 0 6 10 1 10 paper|11 0 3 14 3 6 enamel|4 0 10 8 1 14 brass"),
    LAB_GLASS(FLOOR, GLASS, "2 0 3 6 4 7 glass_green|3 4 4 5 7 6 glass_green|8 0 5 9 5 7 wood|14 0 5 15 5 7 wood|9 3 5 14 4 6 wood|10 0 6 11 6 7 void_glass|12 0 6 13 6 7 glass_dark|4 0 10 7 3 13 brass|10 0 10 14 1 14 enamel"),
    VOID_JAR(FLOOR, GLASS, 2, "5 0 5 11 1 11 brass|6 1 6 10 7 10 void_glass|5 7 5 11 8 11 brass|7 8 7 9 9 9 brass"),

    // Washing and cooking
    WASH_BASIN(WALL, STONE, "2 8 9 14 11 16 enamel|7 11 14 9 14 15 brass|7 13 12 9 14 14 brass|7 0 14 9 8 16 steel"),
    SHOWER(WALL, METAL, "7 0 15 9 16 16 steel|7 14 10 9 15 15 steel|7 13 10 9 14 12 steel|5 12 8 11 13 12 steel_dark|9 7 15 11 9 16 paint_red"),
    LATRINE(FLOOR, STONE, "4 0 5 12 6 13 enamel|3 6 4 13 7 13 wood_dark|3 10 13 13 16 16 enamel|7 7 14 9 10 15 steel"),
    BUCKET(FLOOR, METAL, "5 0 5 11 7 11 steel|10 7 9 11 16 10 wood"),
    RANGE(FLOOR, METAL, "0 0 2 16 12 16 steel_dark|0 12 1 16 13 16 steel_plate|1 3 1 7 9 2 steel_plate|9 3 1 15 9 2 steel_plate|0 10 0 16 11 1 steel|2 13 4 7 16 9 steel|9 13 5 14 14 10 steel_dark|14 13 7 16 14 8 steel_dark|11 13 12 15 16 16 steel"),
    COUNTER(FLOOR, METAL, "0 13 2 16 14 16 steel|0 0 3 16 13 16 lead|4 8 2 5 10 3 steel_dark|11 8 2 12 10 3 steel_dark|2 14 5 8 15 10 wood|10 14 6 13 16 9 steel"),
    TIN_RACK(FLOOR, METAL, Parts.RACK_POSTS + "|1 0 7 15 16 15 steel_dark n=tins@1,0"),
    SACKS(FLOOR, CLOTH, "1 0 3 9 5 11 canvas|9 0 5 15 4 13 canvas|3 5 5 11 9 12 canvas"),
    URN(FLOOR, METAL, "4 0 4 12 2 12 steel_dark|4 2 4 12 12 12 steel|5 12 5 11 13 11 steel|7 13 7 9 14 9 brass|7 3 2 9 5 4 brass"),

    // Stores
    CRATE(FLOOR, WOOD, "1 0 1 15 14 15 crate"),
    CRATE_STACK(FLOOR, WOOD, "0 0 0 10 9 10 crate|10 0 6 16 7 16 crate|1 9 1 9 15 9 crate"),
    CRATE_OPEN(FLOOR, WOOD, "1 0 1 15 1 15 wood|1 1 1 2 10 15 wood|14 1 1 15 10 15 wood|2 1 1 14 10 2 wood|2 1 14 14 10 15 wood|2 1 2 14 7 14 canvas|4 7 4 6 9 6 brass|9 7 8 11 9 10 brass"),
    BROKEN_CRATE(FLOOR, WOOD, "1 0 2 15 1 14 wood|1 1 2 2 9 14 wood|2 1 13 12 6 14 wood|5 1 4 14 2 7 wood_dark|3 1 8 10 3 12 canvas"),
    CELL_CASES(FLOOR, METAL, "1 0 2 8 5 7 lead|1 0 9 8 5 14 lead|9 0 4 16 5 9 lead|2 5 5 9 10 10 copper|4 2 1 5 3 2 brass|12 2 3 13 3 4 brass"),
    CELL_RACK(FLOOR, METAL, "0 0 3 1 14 13 steel_dark|15 0 3 16 14 13 steel_dark|1 2 4 11 5 7 rubrum|11 2 4 15 5 7 brass|1 2 9 11 5 12 rubrum|11 2 9 15 5 12 brass"
            + "|1 8 4 11 11 7 rubrum|11 8 4 15 11 7 brass|1 8 9 11 11 12 glass_dark|11 8 9 15 11 12 brass"),
    CANISTERS(FLOOR, METAL, "2 0 3 6 8 7 copper|2 8 3 6 10 7 brass|3 10 4 5 13 6 glass_dark|9 0 2 13 8 6 copper|9 8 2 13 10 6 brass|10 10 3 12 13 5 glass_dark"
            + "|6 0 9 10 8 13 copper_dark|6 8 9 10 10 13 brass|7 10 10 9 13 12 rubrum"),
    DRUM(FLOOR, METAL, "3 0 3 13 4 13 copper|2 4 2 14 5 14 brass|3 5 3 13 10 13 copper|2 10 2 14 11 14 brass|3 11 3 13 15 13 copper|5 15 5 7 16 7 brass"),
    DRUMS(FLOOR, METAL, "1 0 1 8 12 8 copper_dark|2 0 9 16 7 16 copper|9 0 2 15 9 8 lead"),
    CARBOYS(FLOOR, METAL, "1 0 5 5 9 12 copper|2 9 7 4 12 10 brass|6 0 5 10 9 12 glass_green|7 9 7 9 12 10 brass|11 0 4 15 9 11 copper_dark|12 9 6 14 12 9 brass"),
    SHELVING(FLOOR, METAL, Parts.RACK_POSTS + "|1 7 7 15 8 15 steel|1 15 7 15 16 15 steel|2 0 8 8 6 14 crate|9 0 8 14 5 14 copper|3 8 8 7 13 14 copper|8 8 9 14 14 14 canvas"),
    SHELVING_EMPTY(FLOOR, METAL, Parts.RACK_POSTS + "|1 7 7 15 8 15 steel|1 15 7 15 16 15 steel|2 0 8 7 3 13 wood"),
    CABLE_REEL(FLOOR, WOOD, "2 0 2 4 14 14 wood|12 0 2 14 14 14 wood|4 2 4 12 12 12 rubber|1 6 7 2 8 9 steel_dark|14 6 7 15 8 9 steel_dark"),
    TARP_PILE(FLOOR, CLOTH, "1 0 1 15 6 15 canvas|3 6 3 13 10 13 canvas|5 10 5 11 12 11 canvas"),
    PAVISE(FLOOR, METAL, "0 0 7 16 13 9 steel_plate|1 13 7 15 15 9 brass|6 5 6 10 9 7 brass|2 0 9 4 5 13 steel_dark|12 0 9 14 5 13 steel_dark"),

    // Armoury and workshop
    LANCE_RACK(FLOOR, METAL, Parts.LANCE_RACK + "|3 2 9 5 7 12 copper|3 7 11 4 14 12 steel|3 14 11 4 16 12 brass|6 2 9 8 7 12 copper|6 7 11 7 14 12 steel|6 14 11 7 16 12 brass|9 2 9 11 7 12 copper|9 7 11 10 14 12 steel|9 14 11 10 16 12 brass|12 2 9 14 7 12 copper|12 7 11 13 14 12 steel|12 14 11 13 16 12 brass"),
    LANCE_RACK_EMPTY(FLOOR, METAL, Parts.LANCE_RACK + "|9 2 9 11 7 12 copper|9 7 11 10 14 12 steel|9 14 11 10 16 12 brass"),
    WARD_MASKS(WALL, METAL, "1 12 15 15 13 16 steel_dark|2 6 13 6 11 15 brass|3 8 12 5 9 13 lens_off|3 4 13 5 6 15 copper|10 6 13 14 11 15 brass|11 8 12 13 9 13 lens_off|11 4 13 13 6 15 copper"),
    WORKBENCH(FLOOR, WOOD, "0 12 2 16 14 14 wood|1 0 3 2 12 4 steel|14 0 3 15 12 4 steel|1 0 12 2 12 13 steel|14 0 12 15 12 13 steel|2 3 4 14 4 12 wood_dark|11 14 4 15 16 7 steel_plate|3 14 6 7 15 7 steel_dark|2 14 9 7 16 12 paint_red"),
    TOOL_BOARD(WALL, WOOD, "0 2 15 16 15 16 wood|2 5 14 3 13 15 steel|6 4 14 7 12 15 wood_dark|5 12 14 8 14 15 steel_dark|10 6 14 14 9 15 steel|10 9 14 12 11 15 wood_dark"),
    LATHE(FLOOR, METAL, "0 6 5 16 8 11 steel_plate|1 0 5 4 6 11 steel_dark|12 0 5 15 6 11 steel_dark|0 8 4 5 14 12 lead|5 9 6 7 13 10 brass|12 8 6 15 12 10 lead|8 8 4 11 10 12 steel_dark"),
    HOIST(CEILING, METAL, "0 14 6 16 16 10 steel_plate|6 11 5 10 14 11 steel_dark|7 3 7 9 11 9 steel|6 0 7 10 3 9 rust"),

    // Command, records and care
    SWITCHBOARD(FLOOR, WOOD, "0 0 6 16 16 16 wood_dark|1 5 5 15 15 6 steel_dark n=switches@1,1|0 4 1 16 5 6 wood_dark|1 0 2 15 4 6 wood_dark|4 5 3 5 8 4 rubber|9 5 3 10 9 4 rubber"),
    SPEAKING_TUBE(WALL, METAL, "7 0 14 9 16 16 copper|6 8 12 10 11 14 brass|5 7 10 11 12 12 brass"),
    MAP_TABLE(FLOOR, WOOD, "0 12 0 16 13 16 wood|1 13 1 15 14 15 map|1 0 1 2 12 2 steel|14 0 1 15 12 2 steel|1 0 14 2 12 15 steel|14 0 14 15 12 15 steel|4 14 5 5 15 6 paint_red|10 14 9 11 15 10 lead|7 14 11 8 15 12 brass"),
    WALL_MAP(WALL, WOOD, "0 2 15 16 15 16 wood_dark|1 3 14 15 14 15 map"),
    LINE_MAP(WALL, METAL, "1 2 15 15 14 16 brass|2 3 14 14 13 15 map"),
    FILING_CABINET(FLOOR, METAL, "2 0 3 14 16 15 lead|3 1 2 13 5 3 lead|3 6 2 13 10 3 lead|3 11 2 13 15 3 lead|7 3 1 9 4 2 steel_dark|7 8 1 9 9 2 steel_dark|7 13 1 9 14 2 steel_dark"),
    ARCHIVE_SHELF(FLOOR, METAL, Parts.RACK_POSTS + "|1 0 7 15 16 15 steel_dark n=binders@1,0"),
    NOTICE_BOARD(WALL, WOOD, "1 3 15 15 14 16 wood|2 8 14 6 13 15 paper|7 5 14 11 11 15 paper|12 9 14 14 13 15 paper"),
    STRETCHER(FLOOR, CLOTH, "1 7 0 15 8 16 canvas|1 0 1 2 7 2 steel|14 0 1 15 7 2 steel|1 0 14 2 7 15 steel|14 0 14 15 7 15 steel|3 8 12 13 10 15 enamel|1 8 1 15 9 9 blanket"),
    MEDICINE_CABINET(WALL, METAL, "3 3 12 13 14 16 enamel|4 8 11 12 13 12 glass_dark|7 4 11 9 6 12 paint_red|11 6 11 12 7 12 brass"),
    MEDICAL_TROLLEY(FLOOR, METAL, "2 0 3 3 12 4 steel|13 0 3 14 12 4 steel|2 0 12 3 12 13 steel|13 0 12 14 12 13 steel|3 5 4 13 6 12 enamel|3 11 4 13 12 12 enamel|4 12 5 6 16 7 glass_green|8 12 6 12 13 10 enamel|5 6 5 10 9 10 enamel"),
    CELL_BARS(FLOOR, METAL, "0 15 7 16 16 9 steel_plate|0 0 7 16 1 9 steel_plate|1 1 7 3 15 9 steel_dark|5 1 7 7 15 9 steel_dark|9 1 7 11 15 9 steel_dark|13 1 7 15 15 9 steel_dark"),
    MORGUE_DRAWERS(FLOOR, METAL, "0 0 2 16 16 16 steel_plate|1 1 1 7 7 2 steel|9 1 1 15 7 2 steel|1 9 1 7 15 2 steel|9 9 1 15 15 2 steel|3 3 0 5 4 1 steel_dark|11 3 0 13 4 1 steel_dark|3 11 0 5 12 1 steel_dark|11 11 0 13 12 1 steel_dark"),

    // Machinery
    BOILER(FLOOR, METAL, "0 0 1 16 1 15 steel_plate|1 1 2 15 4 14 copper|0 4 1 16 5 15 brass|1 5 2 15 9 14 copper|0 9 1 16 10 15 brass|1 10 2 15 11 14 copper|6 5 1 10 8 2 glass_dark"
            + "|3 11 4 7 16 8 copper_dark|10 11 9 12 14 11 brass|9 14 8 13 15 12 brass"),
    DYNAMO(FLOOR, METAL, "1 0 2 15 2 14 steel_plate|2 2 2 14 12 14 copper|1 3 3 2 11 13 brass|14 3 3 15 11 13 brass|5 12 5 11 15 11 copper_dark|7 15 7 9 16 9 brass"),
    FILTER_DRUM(FLOOR, METAL, "2 0 2 14 3 14 lead|1 3 1 15 4 15 brass|2 4 2 14 9 14 lead|1 9 1 15 10 15 brass|2 10 2 14 12 14 lead|5 12 5 11 16 11 copper"),
    FAN(WALL, METAL, "1 1 12 15 15 16 steel_plate|3 3 11 13 13 12 grille|7 7 10 9 9 11 steel_dark"),
    DUCT(CEILING, METAL, "0 9 4 7 15 12 steel|9 9 4 16 15 12 steel|7 8 3 9 16 13 steel_dark"),
    DUCT_VENT(CEILING, METAL, "0 9 4 16 15 12 steel|4 7 5 12 9 11 grille|2 15 7 3 16 9 steel_dark|13 15 7 14 16 9 steel_dark"),
    PIPES(WALL, METAL, "0 10 13 16 12 15 copper|0 5 13 16 7 15 copper_dark|3 4 15 5 13 16 steel_dark|11 4 15 13 13 16 steel_dark|7 12 13 9 14 15 brass|5 14 12 11 15 16 paint_red"),
    VALVE(WALL, METAL, "6 0 12 10 16 16 steel|7 7 11 9 9 12 steel_dark|4 11 10 12 12 11 paint_red|4 4 10 12 5 11 paint_red|3 5 10 4 11 11 paint_red|12 5 10 13 11 11 paint_red|7 5 10 9 11 11 paint_red|4 7 10 7 9 11 paint_red|9 7 10 12 9 11 paint_red"),
    PRESSURE_GAUGE(WALL, METAL, "7 0 14 9 16 16 copper|4 5 12 12 13 14 brass|5 6 11 11 12 12 brass n=dials@1,1"),
    VOID_CONDUIT(WALL, GLASS, 2, "6 0 13 10 3 16 brass|7 3 13 9 7 15 void_glass|5 7 12 11 9 16 copper|7 9 13 9 13 15 void_glass|6 13 13 10 16 16 brass"),
    TANK(FLOOR, METAL, "1 0 1 15 16 15 copper|7 3 0 9 13 1 glass_dark|7 2 0 9 3 1 brass|7 13 0 9 14 1 brass"),
    PUMP(FLOOR, METAL, "0 0 2 16 2 14 steel_plate|1 2 4 8 10 12 copper|9 2 3 15 11 13 steel_dark|11 11 7 13 16 9 steel|8 5 7 9 8 9 steel|15 5 7 16 8 10 steel"),
    SWITCHGEAR(FLOOR, METAL, "0 0 4 16 16 16 lead|1 9 3 15 15 4 steel_dark n=dials@1,1|3 3 3 5 7 4 steel_dark|7 3 3 9 7 4 steel_dark|11 3 3 13 7 4 steel_dark|3 5 2 5 6 3 paint_red|7 3 2 9 4 3 paint_red|11 5 2 13 6 3 paint_red|0 0 3 16 1 4 hazard"),
    CONSOLE(FLOOR, METAL, "0 0 5 16 10 16 lead|0 10 2 16 11 16 steel_dark|0 11 12 16 16 16 lead|1 11 11 15 15 12 steel_dark n=dials@1,2|1 11 3 15 12 10 steel_dark u=switches@1,1"),
    LEVER_FRAME(FLOOR, METAL, "0 0 5 16 3 13 steel_plate|2 3 8 3 14 9 steel|5 3 8 6 11 9 steel|8 3 8 9 14 9 steel|11 3 8 12 11 9 steel|14 3 8 15 14 9 steel"
            + "|2 14 8 3 16 9 paint_red|5 11 8 6 13 9 enamel|8 14 8 9 16 9 paint_red|11 11 8 12 13 9 enamel|14 14 8 15 16 9 hazard"),
    RELAY_BOX(WALL, METAL, "4 4 13 12 14 16 copper|7 14 14 9 16 16 brass|7 0 14 9 4 16 brass|5 7 12 11 13 13 brass n=dials@1,1|5 5 12 7 6 13 paint_red"),
    CONDUIT_RUN(WALL, METAL, "0 10 13 3 13 16 copper|3 9 12 5 14 16 brass|5 10 13 11 13 16 copper|11 9 12 13 14 16 brass|13 10 13 16 13 16 copper|0 14 14 16 15 15 rubber"),
    CELL_BANK(FLOOR, METAL, "0 0 3 16 1 13 steel|0 8 3 16 9 13 steel|1 1 4 5 6 12 rubrum|6 1 4 10 6 12 glass_dark|11 1 4 15 6 12 rubrum|2 6 7 4 7 9 brass|7 6 7 9 7 9 brass|12 6 7 14 7 9 brass"
            + "|1 9 4 5 14 12 glass_dark|6 9 4 10 14 12 rubrum|11 9 4 15 14 12 glass_dark|2 14 7 4 15 9 brass|7 14 7 9 15 9 brass|12 14 7 14 15 9 brass"),
    AIR_TANK(FLOOR, METAL, "1 4 4 15 12 12 copper|0 5 5 1 11 11 brass|15 5 5 16 11 11 brass|3 0 4 5 4 12 steel_plate|11 0 4 13 4 12 steel_plate|7 12 7 9 14 9 brass|11 12 7 13 13 9 paint_red"),

    // Rails and platforms
    PLATFORM_BENCH(FLOOR, WOOD, "0 7 4 16 8 11 wood|0 10 11 16 14 12 wood|1 0 4 2 7 12 steel_dark|14 0 4 15 7 12 steel_dark|1 8 11 2 10 12 steel_dark|14 8 11 15 10 12 steel_dark"),
    STATION_CLOCK(CEILING, METAL, "7 12 7 9 16 9 steel|3 3 6 13 12 10 brass|4 4 5 12 11 6 enamel|4 4 10 12 11 11 enamel|8 8 4 9 10 5 steel_dark|8 7 4 11 8 5 steel_dark|7 8 11 8 10 12 steel_dark|5 7 11 8 8 12 steel_dark"),
    STATION_SIGN(WALL, METAL, "0 2 15 16 14 16 sign"),
    SECTOR_SIGN(WALL, METAL, "3 3 15 13 13 16 sign"),
    ARROW_SIGN(WALL, METAL, "0 4 15 16 12 16 sign_arrow"),
    WARD_PLATE(WALL, METAL, "0 0 15 16 4 16 hazard"),
    SIGNAL(FLOOR, METAL, 3, "5 0 5 11 1 11 steel_plate|7 1 7 9 10 9 steel|5 10 6 11 16 10 steel_dark|7 13 5 9 15 6 lens_red|7 11 5 9 12 6 lens_off"),
    BUFFER_STOP(FLOOR, WOOD, "0 6 6 16 10 10 wood_dark n=hazard@0,6|2 0 7 4 6 9 rust|12 0 7 14 6 9 rust|2 0 10 4 3 14 rust|12 0 10 14 3 14 rust|3 7 4 6 9 6 steel_plate|10 7 4 13 9 6 steel_plate"),
    POINTS_LEVER(FLOOR, METAL, "4 0 4 12 2 12 steel_plate|7 2 7 9 13 9 steel|6 10 5 10 13 7 paint_red|7 13 7 9 15 9 enamel"),
    WAGON(FLOOR, METAL, Parts.WAGON),
    WAGON_LOADED(FLOOR, METAL, Parts.WAGON + "|3 5 2 13 12 8 crate|3 5 8 13 10 14 canvas"),
    TROLLEY(FLOOR, WOOD, "2 3 1 14 4 13 wood|1 0 2 2 3 5 rubber|14 0 2 15 3 5 rubber|1 0 9 2 3 12 rubber|14 0 9 15 3 12 rubber|3 4 13 4 14 14 steel|12 4 13 13 14 14 steel|4 13 13 12 14 14 steel"),
    LUGGAGE(FLOOR, CLOTH, "1 0 3 11 7 9 leather|3 7 4 10 11 9 leather|12 0 6 16 6 13 canvas|5 11 6 8 12 7 steel_dark"),
    TRASH_BIN(FLOOR, METAL, "4 0 4 12 11 12 lead|3 11 3 13 12 13 steel_dark|6 12 6 9 13 9 paper"),

    // Defences: the housings of the fort's security, which stay behind as its wrecks
    TURRET_CUPOLA(CEILING, METAL, "1 13 1 15 16 15 copper|3 8 3 13 13 13 copper|5 5 5 11 8 11 copper_dark|7 6 1 9 8 5 brass|5 9 2 7 11 3 lens_off"),
    TURRET_WELL(FLOOR, METAL, "0 0 0 16 2 16 copper u=hazard@0,0|3 2 3 13 4 13 copper|6 4 6 10 5 10 brass"),
    EMBRASURE(WALL, METAL, "0 2 13 16 14 16 copper|1 9 12 15 11 13 brass|1 5 12 15 7 13 brass|2 7 12 14 9 13 copper_dark"),
    SENTINEL_EYE(WALL, METAL, "3 3 11 13 13 16 copper|4 4 10 12 12 11 brass|5 5 9 11 11 10 glass_dark"),
    TRIPWIRE_EMITTER(WALL, METAL, 2, "6 2 13 10 6 16 copper|7 3 12 9 5 13 lens_red|7 6 14 9 16 16 brass"),
    KLAXON(WALL, METAL, "6 6 14 10 10 16 copper_dark|6 6 9 10 10 14 brass|4 4 7 12 12 9 brass"),
    PERISCOPE(CEILING, METAL, "6 4 6 10 16 10 brass|5 1 5 11 4 11 copper|7 2 4 9 3 5 glass_dark|3 2 7 5 3 9 rubber|11 2 7 13 3 9 rubber"),
    BREAKER_CABINET(WALL, METAL, "2 1 12 14 15 16 copper|3 9 11 13 14 12 brass n=dials@1,1|5 3 11 11 8 12 copper_dark|12 2 11 13 4 12 lens_off"),
    BREAKER_CABINET_LIVE(WALL, METAL, 6, "2 1 12 14 15 16 copper|3 9 11 13 14 12 brass n=dials@1,1|5 3 11 11 8 12 copper_dark|6 4 10 10 7 11 lens_red|12 2 11 13 4 12 lens_warm"),
    SEARCHLIGHT(FLOOR, METAL, "4 0 4 12 2 12 copper|7 2 7 9 6 9 brass|4 6 3 12 14 12 copper|5 7 2 11 13 3 lens_off"),

    // Lights
    EMERGENCY_LAMP(CEILING, GLASS, 7, Parts.CAGED_LAMP + "|6 11 6 10 14 10 lens_red"),
    EMERGENCY_LAMP_ALARM(CEILING, GLASS, 7, Parts.CAGED_LAMP + "|6 11 6 10 14 10 lens_strobe"),
    EMERGENCY_LAMP_DARK(CEILING, GLASS, Parts.CAGED_LAMP + "|6 11 6 10 14 10 lens_off"),
    CEILING_LAMP(CEILING, GLASS, "7 12 7 9 16 9 brass|3 9 3 13 12 13 copper|6 7 6 10 9 10 lens_off"),
    CEILING_LAMP_LIT(CEILING, GLASS, 14, "7 12 7 9 16 9 brass|3 9 3 13 12 13 copper|6 7 6 10 9 10 lens_warm"),
    WALL_LAMP(WALL, GLASS, Parts.WALL_LAMP + "|6 7 12 10 11 14 lens_off"),
    WALL_LAMP_LIT(WALL, GLASS, 12, Parts.WALL_LAMP + "|6 7 12 10 11 14 lens_warm"),
    EMERGENCY_WALL_LAMP(WALL, GLASS, 7, Parts.WALL_LAMP + "|6 7 12 10 11 14 lens_red"),
    EMERGENCY_WALL_LAMP_ALARM(WALL, GLASS, 7, Parts.WALL_LAMP + "|6 7 12 10 11 14 lens_strobe"),

    // What time left
    LITTER(FLOOR, CLOTH, "2 0 3 7 1 9 paper|9 0 8 14 1 13 paper|10 0 2 13 2 5 steel"),
    RUBBLE_PILE(FLOOR, STONE, "1 0 2 9 4 9 concrete|8 0 9 15 3 15 concrete|4 4 4 8 6 8 concrete|2 0 10 7 2 15 concrete_stained|11 3 10 12 9 11 rust"),
    WARD_STELE(FLOOR, STONE, 3, "3 0 3 13 2 13 voidstone|4 2 4 12 6 12 voidstone|4 6 4 12 10 12 glyph|4 10 4 12 13 12 voidstone|3 13 3 13 15 13 brass|6 15 6 10 16 10 ley");

    public enum Mount
    {
        FLOOR,
        WALL,
        CEILING
    }

    public enum Sound
    {
        METAL,
        WOOD,
        CLOTH,
        STONE,
        GLASS
    }

    /** Boxes several props share. */
    private static final class Parts
    {
        static final String BUNK_FRAME = "0 0 0 1 16 1 steel|15 0 0 16 16 1 steel|0 0 15 1 16 16 steel|15 0 15 16 16 16 steel"
                + "|0 3 1 1 4 15 steel|15 3 1 16 4 15 steel|0 11 1 1 12 15 steel|15 11 1 16 12 15 steel|1 3 0 15 4 1 steel|1 3 15 15 4 16 steel|1 11 0 15 12 1 steel|1 11 15 15 12 16 steel";
        static final String RACK_POSTS = "0 0 6 1 16 7 steel|15 0 6 16 16 7 steel|0 0 15 1 16 16 steel|15 0 15 16 16 16 steel";
        static final String LANCE_RACK = "1 0 8 15 2 14 steel_dark|1 11 12 15 12 14 brass|1 2 13 2 11 14 steel_dark|14 2 13 15 11 14 steel_dark";
        static final String WAGON = "2 2 0 14 4 16 steel_dark|1 0 2 2 3 5 rust|14 0 2 15 3 5 rust|1 0 11 2 3 14 rust|14 0 11 15 3 14 rust"
                + "|3 4 1 13 5 15 lead|2 5 1 3 11 15 lead|13 5 1 14 11 15 lead|3 5 1 13 11 2 lead|3 5 14 13 11 15 lead";
        static final String CAGED_LAMP = "5 14 5 11 16 11 copper_dark|5 10 5 6 14 6 brass|10 10 5 11 14 6 brass|5 10 10 6 14 11 brass|10 10 10 11 14 11 brass";
        static final String WALL_LAMP = "5 6 14 11 12 16 copper|5 9 11 11 10 12 brass";
    }

    private static final UnderwayProp[] VALUES = values();

    private final Mount mount;
    private final Sound sound;
    private final int light;
    private final List<UnderwayPropBox> boxes;

    UnderwayProp(Mount mount, Sound sound, String boxes)
    {
        this(mount, sound, 0, boxes);
    }

    UnderwayProp(Mount mount, Sound sound, int light, String boxes)
    {
        this.mount = mount;
        this.sound = sound;
        this.light = light;
        this.boxes = UnderwayPropBox.parse(boxes);
    }

    public static UnderwayProp of(int ordinal)
    {
        return VALUES[ordinal];
    }

    public String id()
    {
        return "underway_" + name().toLowerCase(Locale.ROOT);
    }

    public Mount mount()
    {
        return mount;
    }

    public Sound sound()
    {
        return sound;
    }

    public int light()
    {
        return light;
    }

    public List<UnderwayPropBox> boxes()
    {
        return boxes;
    }

    /** @return the box around the whole prop, as its lowest then highest corner in sixteenths of a block */
    public int[] bounds()
    {
        int[] bounds = { 16, 16, 16, 0, 0, 0 };
        for (var box : boxes)
        {
            bounds[0] = Math.min(bounds[0], box.x0());
            bounds[1] = Math.min(bounds[1], box.y0());
            bounds[2] = Math.min(bounds[2], box.z0());
            bounds[3] = Math.max(bounds[3], box.x1());
            bounds[4] = Math.max(bounds[4], box.y1());
            bounds[5] = Math.max(bounds[5], box.z1());
        }
        return bounds;
    }
}
