package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusHash;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;

import java.util.List;

import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.BALLAST;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.TRUNK_TOP;

/**
 * Fits out the trunk tunnels outside the halls: a cable run along one wall, signs, lamps and telephones along the other, signals and points levers where a line comes up to a station or a hub, and buffer stops, signals and warnings before a blast gate. A few things were left on the walkway between the tracks, which always keeps a lane free.
 * <p>
 * Props only ever stand in air, so anything a train, a cave-in or water already takes is left out.
 */
final class TrunkFurnisher
{
    private static final int TRACK = BALLAST + 1;
    private static final int EYE = TRACK + 2;
    private static final int CABLES = TRUNK_TOP - 3;
    private static final int LAMPS = TRACK + 3;
    private static final int PERCENT = 100;
    private static final int CABLE_GAP_PERCENT = 8;
    private static final int FITTING_EVERY = 8;
    private static final int FITTING_AT = 4;
    private static final int FITTING_SKIP_PERCENT = 35;
    private static final int CLUTTER_EVERY = 16;
    private static final int CLUTTER_AT = 9;
    private static final int CLUTTER_PERCENT = 35;
    private static final int RUBBLE_PERCENT = 60;
    private static final int SIGNAL_BEFORE_HALL = 4;
    private static final int LEVER_BEFORE_HALL = 6;
    private static final int BUFFER_BEFORE_GATE = 3;
    private static final int WARNING_BEFORE_GATE = 2;
    private static final int SIGNAL_BEFORE_GATE = 7;
    private static final int WALKWAY_LOW = TrunkPainter.RAILS[1] + 1;
    private static final int WALKWAY_HIGH = TrunkPainter.RAILS[2] - 1;
    private static final int CHASM_END = DistrictPos.PITCH - 1;
    private static final UnderwayProp[] FITTINGS = { UnderwayProp.EMERGENCY_WALL_LAMP, UnderwayProp.ARROW_SIGN, UnderwayProp.PIPES, UnderwayProp.WALL_TELEPHONE,
            UnderwayProp.SECTOR_SIGN, UnderwayProp.FUSE_BOX, UnderwayProp.PIPES, UnderwayProp.EMERGENCY_WALL_LAMP };
    private static final UnderwayProp[] CLUTTER = { UnderwayProp.CABLE_REEL, UnderwayProp.DRUM, UnderwayProp.CRATE, UnderwayProp.JERRYCANS, UnderwayProp.TROLLEY, UnderwayProp.SANDBAGS,
            UnderwayProp.LITTER, UnderwayProp.BROKEN_CRATE };
    private static final long SALT = 0x554E4445525446L;

    private TrunkFurnisher()
    {
    }

    static void paint(long seed, DistrictPos pos, boolean alongX, UnderwayCanvas canvas)
    {
        var brush = new UnderwayBrush(canvas, UnderwayFrame.ofLine(pos, alongX));
        var node = UnderwayNetwork.nodeAt(seed, pos);
        var link = UnderwayNetwork.linkFrom(seed, pos, alongX ? Compass.EAST : Compass.SOUTH);
        boolean rubble = UnderwayNetwork.regionRuin(seed, pos) == UnderwayRuin.COLLAPSED;
        var trains = UnderwayTrain.at(seed, pos, alongX);
        long hash = DaedalusHash.mix(seed, pos.x(), pos.z(), alongX ? SALT : ~SALT);

        int hallStart = node == UnderwayNode.HUB ? HubPlanner.ATRIUM_START : StationPlanner.HALL_START;
        int hallEnd = node == UnderwayNode.HUB ? HubPlanner.ATRIUM_END : StationPlanner.HALL_END;
        tunnel(brush, 0, hallStart - 1, hash, rubble, true, trains);
        tunnel(brush, hallEnd + 1, DistrictPos.SIZE - 1, hash, rubble, true, trains);
        tunnel(brush, DistrictPos.SIZE, CHASM_END, hash, rubble, link == UnderwayLink.OPEN, trains);

        approach(brush, hallStart - SIGNAL_BEFORE_HALL, hallStart - LEVER_BEFORE_HALL, -1);
        approach(brush, hallEnd + SIGNAL_BEFORE_HALL, hallEnd + LEVER_BEFORE_HALL, 1);
        if (link == UnderwayLink.GATED)
        {
            gate(brush, TrunkPainter.GATE_START - 1, -1);
            gate(brush, TrunkPainter.GATE_END + 1, 1);
        }
    }

    /**
     * @param clutter whether things may lie on the walkway, which a gate or a cave-in ahead rules out
     */
    private static void tunnel(UnderwayBrush brush, int from, int to, long hash, boolean rubble, boolean clutter, List<UnderwayTrain> trains)
    {
        for (int along = from; along <= to; along++)
        {
            long roll = DaedalusHash.mix(hash + along);
            if (DaedalusHash.pick(roll, PERCENT) >= CABLE_GAP_PERCENT)
                prop(brush, UnderwayProp.CABLE_TRAY, along, CABLES, TrunkPainter.LOW, 0, 1);

            if (along % FITTING_EVERY == FITTING_AT && DaedalusHash.pick(DaedalusHash.mix(roll), PERCENT) >= FITTING_SKIP_PERCENT)
            {
                var fitting = FITTINGS[along / FITTING_EVERY % FITTINGS.length];
                int height = fitting == UnderwayProp.PIPES ? CABLES : (fitting == UnderwayProp.EMERGENCY_WALL_LAMP ? LAMPS : EYE);
                prop(brush, fitting, along, height, TrunkPainter.HIGH, 0, -1);
            }

            if (!clutter || along % CLUTTER_EVERY != CLUTTER_AT || isBesideTrain(trains, along))
                continue;
            long pick = DaedalusHash.mix(roll + 1);
            int across = DaedalusHash.pick(pick, 2) == 0 ? WALKWAY_LOW : WALKWAY_HIGH;
            int turn = DaedalusHash.pick(DaedalusHash.mix(pick), 2) == 0 ? 1 : -1;
            if (rubble && DaedalusHash.pick(DaedalusHash.mix(pick + 1), PERCENT) < RUBBLE_PERCENT)
                prop(brush, UnderwayProp.RUBBLE_PILE, along, TRACK, across, turn, 0);
            else if (DaedalusHash.pick(DaedalusHash.mix(pick + 2), PERCENT) < CLUTTER_PERCENT)
                prop(brush, CLUTTER[DaedalusHash.pick(DaedalusHash.mix(pick + 3), CLUTTER.length)], along, TRACK, across, turn, 0);
        }
    }

    private static boolean isBesideTrain(List<UnderwayTrain> trains, int along)
    {
        for (var train : trains)
        {
            if (along >= train.cars().getFirst().along() - 1 && along <= train.cars().getLast().along() + UnderwayTrain.CAR_LENGTH)
                return true;
        }
        return false;
    }

    /** A signal for each track, facing whatever comes up the line, and a points lever at the walkway's edge. */
    private static void approach(UnderwayBrush brush, int signal, int lever, int outward)
    {
        prop(brush, UnderwayProp.SIGNAL, signal, TRACK, TrunkPainter.LOW, outward, 0);
        prop(brush, UnderwayProp.SIGNAL, signal, TRACK, TrunkPainter.HIGH, outward, 0);
        prop(brush, UnderwayProp.POINTS_LEVER, lever, TRACK, outward < 0 ? WALKWAY_LOW : WALKWAY_HIGH, 0, outward < 0 ? 1 : -1);
    }

    /** Before a blast gate: buffer stops across both tracks, a klaxon and an eye over them, striped plates at the foot of the walls, and a signal further back. */
    private static void gate(UnderwayBrush brush, int frame, int outward)
    {
        int buffers = frame + outward * BUFFER_BEFORE_GATE;
        for (int across = TrunkPainter.RAILS[0]; across <= TrunkPainter.RAILS[1]; across++)
            prop(brush, UnderwayProp.BUFFER_STOP, buffers, TRACK, across, outward, 0);
        for (int across = TrunkPainter.RAILS[2]; across <= TrunkPainter.RAILS[3]; across++)
            prop(brush, UnderwayProp.BUFFER_STOP, buffers, TRACK, across, outward, 0);

        int warning = frame + outward * WARNING_BEFORE_GATE;
        prop(brush, UnderwayProp.HAZARD_PLATE, warning, TRACK, TrunkPainter.LOW, 0, 1);
        prop(brush, UnderwayProp.HAZARD_PLATE, warning, TRACK, TrunkPainter.HIGH, 0, -1);
        prop(brush, UnderwayProp.KLAXON, warning, LAMPS, TrunkPainter.HIGH, 0, -1);
        prop(brush, UnderwayProp.SENTINEL_EYE, warning, LAMPS, TrunkPainter.LOW, 0, 1);

        int signal = frame + outward * SIGNAL_BEFORE_GATE;
        prop(brush, UnderwayProp.SIGNAL, signal, TRACK, TrunkPainter.LOW, outward, 0);
        prop(brush, UnderwayProp.SIGNAL, signal, TRACK, TrunkPainter.HIGH, outward, 0);
    }

    private static void prop(UnderwayBrush brush, UnderwayProp prop, int along, int y, int across, int faceAlong, int faceAcross)
    {
        var frame = brush.frame();
        brush.canvas().setProp(frame.x(along, across), y, frame.z(along, across), prop, frame.compass(faceAlong, faceAcross));
    }
}
