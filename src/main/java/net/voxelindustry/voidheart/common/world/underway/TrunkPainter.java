package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;

import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.BALLAST;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.TRUNK_HEIGHT;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.TRUNK_TOP;

/** Draws a trunk line: a double-track vault through the middle of its districts and under the chasms between them, where its gates and cave-ins lie. */
final class TrunkPainter
{
    static final int LOW = 58;
    static final int HIGH = 69;
    static final int CENTRE = 63;
    static final int[] RAILS = { 59, 61, 66, 68 };

    private static final int RAIL_Y = BALLAST + 1;
    private static final int FLOOD_TOP = BALLAST + 2;
    private static final int LAMP_EVERY = 16;
    private static final int LAMP_AT = 8;
    private static final int CHASM_END = DistrictPos.PITCH - 1;
    static final int GATE_START = 143;
    static final int GATE_END = 144;
    private static final int RUBBLE_START = 138;
    private static final int RUBBLE_END = 150;
    private static final int RUBBLE_SLOPE = 2;
    private static final int BYPASS_START = 132;
    private static final int BYPASS_END = 155;
    private static final int BYPASS_LOW = HIGH + 2;
    private static final int BYPASS_HIGH = HIGH + 4;
    private static final int BYPASS_HEIGHT = 3;

    private TrunkPainter()
    {
    }

    /** Draws the trunk under a district, dry: a flooded district is drowned once its fort is drawn. */
    static void through(long seed, DistrictPos pos, boolean alongX, UnderwayCanvas canvas)
    {
        var brush = new UnderwayBrush(canvas, UnderwayFrame.ofLine(pos, alongX));
        var ruin = UnderwayNetwork.regionRuin(seed, pos);
        vault(brush, 0, DistrictPos.SIZE - 1);
        fit(brush, 0, DistrictPos.SIZE - 1, ruin);
    }

    /** Draws the trunk under the chasm east or south of a district, with what its link holds. */
    static void link(long seed, DistrictPos pos, boolean alongX, UnderwayCanvas canvas)
    {
        var brush = new UnderwayBrush(canvas, UnderwayFrame.ofLine(pos, alongX));
        int direction = alongX ? Compass.EAST : Compass.SOUTH;
        var link = UnderwayNetwork.linkFrom(seed, pos, direction);
        var here = UnderwayNetwork.regionRuin(seed, pos);
        var there = UnderwayNetwork.regionRuin(seed, new DistrictPos(pos.x() + Compass.dx(direction), pos.z() + Compass.dz(direction)));

        vault(brush, DistrictPos.SIZE, CHASM_END);
        if (link == UnderwayLink.GATED)
            gate(brush);
        else if (link == UnderwayLink.COLLAPSED)
            caveIn(brush);
        fit(brush, DistrictPos.SIZE, CHASM_END, here);

        if (link != UnderwayLink.GATED)
        {
            if (here == UnderwayRuin.FLOODED)
                flood(brush, DistrictPos.SIZE, CHASM_END);
            return;
        }
        if (here == UnderwayRuin.FLOODED)
            flood(brush, DistrictPos.SIZE, GATE_START - 1);
        if (there == UnderwayRuin.FLOODED)
            flood(brush, GATE_END + 1, CHASM_END);
    }

    private static void vault(UnderwayBrush brush, int from, int to)
    {
        brush.room(from, LOW, to, HIGH, BALLAST, TRUNK_HEIGHT, UnderwayMaterial.BALLAST);
        brush.fill(from, TRUNK_TOP, LOW, to, TRUNK_TOP, LOW + 1, UnderwayMaterial.SHELL);
        brush.fill(from, TRUNK_TOP, HIGH - 1, to, TRUNK_TOP, HIGH, UnderwayMaterial.SHELL);
        brush.fill(from, TRUNK_TOP - 1, LOW, to, TRUNK_TOP - 1, LOW, UnderwayMaterial.SHELL);
        brush.fill(from, TRUNK_TOP - 1, HIGH, to, TRUNK_TOP - 1, HIGH, UnderwayMaterial.SHELL);
    }

    private static void gate(UnderwayBrush brush)
    {
        brush.fill(GATE_START - 1, BALLAST, LOW - 1, GATE_END + 1, TRUNK_TOP + 1, HIGH + 1, UnderwayMaterial.HAZARD);
        brush.fill(GATE_START - 1, BALLAST + 1, LOW, GATE_END + 1, TRUNK_TOP, HIGH, UnderwayMaterial.AIR);
        brush.fill(GATE_START, BALLAST + 1, LOW, GATE_END, TRUNK_TOP, HIGH, UnderwayMaterial.GATE);
    }

    /** Fills the vault with rubble sloping down at both ends, and digs the service gallery that walks around it. */
    private static void caveIn(UnderwayBrush brush)
    {
        for (int along = RUBBLE_START; along <= RUBBLE_END; along++)
        {
            int fromEnd = Math.min(along - RUBBLE_START, RUBBLE_END - along);
            int top = Math.min(TRUNK_TOP, BALLAST + 3 + fromEnd * 3);
            brush.replace(along, BALLAST + 1, LOW, along, fromEnd < RUBBLE_SLOPE ? top : TRUNK_TOP, HIGH, UnderwayMaterial.AIR, UnderwayMaterial.RUBBLE);
        }
        brush.room(BYPASS_START, BYPASS_LOW, BYPASS_END, BYPASS_HIGH, BALLAST, BYPASS_HEIGHT, UnderwayMaterial.FLOOR);
        brush.opening(BYPASS_START, HIGH + 1, BYPASS_START + 1, HIGH + 1, BALLAST, BYPASS_HEIGHT);
        brush.opening(BYPASS_END - 1, HIGH + 1, BYPASS_END, HIGH + 1, BALLAST, BYPASS_HEIGHT);
    }

    private static void fit(UnderwayBrush brush, int from, int to, UnderwayRuin ruin)
    {
        var lamp = ruin == UnderwayRuin.SEALED ? UnderwayMaterial.LAMP : UnderwayMaterial.EMERGENCY_LAMP;
        int every = ruin == UnderwayRuin.SEALED ? LAMP_EVERY : LAMP_EVERY * 2;
        for (int along = from; along <= to; along++)
        {
            for (int rail : RAILS)
                brush.setInAir(along, RAIL_Y, rail, brush.trunkRail());
            if (along % every == LAMP_AT)
                brush.setInAir(along, TRUNK_TOP, CENTRE, lamp);
        }
    }

    /** Drowns every track bed under a district up to the height of its platforms. */
    static void flood(UnderwayBrush brush)
    {
        brush.replace(0, BALLAST + 1, 0, DistrictPos.SIZE - 1, FLOOD_TOP, DistrictPos.SIZE - 1, UnderwayMaterial.AIR, UnderwayMaterial.WATER);
    }

    private static void flood(UnderwayBrush brush, int from, int to)
    {
        brush.replace(from, BALLAST + 1, LOW, to, FLOOD_TOP, BYPASS_HIGH, UnderwayMaterial.AIR, UnderwayMaterial.WATER);
    }
}
