package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.DaedalusHash;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;

import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.BALLAST;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.PLATFORM_FLOOR;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayTrain.CAR_LENGTH;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayTrain.WIDTH;

/**
 * Draws the dead trains over the trunk already drawn. A car's floor is level with the platforms, so it is boarded straight from one, or from the track bed by two steps on its inner side. Inside, a lane runs down the middle between what it carried, and doors at both ends lead on to the next car.
 */
final class TrainPainter
{
    private static final int FLOOR = PLATFORM_FLOOR;
    private static final int ROOF = FLOOR + 4;
    private static final int EYE = FLOOR + 2;
    private static final int LAST = CAR_LENGTH - 1;
    private static final int DOOR = CAR_LENGTH / 2;
    private static final int[] BOGIES = { 1, 7 };
    private static final int BOGIE_LENGTH = 3;
    private static final int[] SLITS = { 2, 8 };
    private static final int TEAR_START = 3;
    private static final int PERCENT = 100;
    private static final int LOOTED_PERCENT = 30;
    private static final UnderwayProp[] MUNITIONS = { UnderwayProp.SHELL_RACK, UnderwayProp.AMMO_BOXES, UnderwayProp.SHELLS, UnderwayProp.CRATE };
    private static final UnderwayProp[] SUPPLIES = { UnderwayProp.CRATE_STACK, UnderwayProp.SACKS, UnderwayProp.DRUM, UnderwayProp.CRATE, UnderwayProp.JERRYCANS };
    private static final UnderwayProp[] SHELLS = { UnderwayProp.SHELLS, UnderwayProp.AMMO_BOXES };
    private static final UnderwayProp[] BENCHES = { UnderwayProp.BENCH, UnderwayProp.BENCH, UnderwayProp.BENCH, UnderwayProp.KIT_BAG };

    private TrainPainter()
    {
    }

    static void paint(long seed, DistrictPos pos, boolean alongX, UnderwayCanvas canvas)
    {
        var brush = new UnderwayBrush(canvas, UnderwayFrame.ofLine(pos, alongX));
        for (var train : UnderwayTrain.at(seed, pos, alongX))
        {
            var cars = train.cars();
            for (int index = 0; index < cars.size(); index++)
            {
                car(brush, train, cars.get(index), DaedalusHash.mix(seed, pos.x() * 31L + index, pos.z(), cars.get(index).along()));
                if (index + 1 < cars.size())
                    brush.set(cars.get(index).along() + CAR_LENGTH, FLOOR, train.aisle(), UnderwayMaterial.GRATING);
            }
        }
    }

    private static void car(UnderwayBrush brush, UnderwayTrain train, UnderwayTrain.Car car, long hash)
    {
        int a0 = car.along();
        int c0 = train.across();
        int a1 = a0 + LAST;
        int c1 = c0 + WIDTH - 1;
        for (int bogie : BOGIES)
            brush.fill(a0 + bogie, BALLAST + 1, c0 + 1, a0 + bogie + BOGIE_LENGTH - 1, BALLAST + 1, c1 - 1, UnderwayMaterial.STEEL);
        brush.fill(a0, FLOOR, c0, a1, ROOF, c1, UnderwayMaterial.STEEL);
        brush.fill(a0 + 1, FLOOR + 1, c0 + 1, a1 - 1, ROOF - 1, c1 - 1, UnderwayMaterial.AIR);

        for (int slit : SLITS)
        {
            brush.set(a0 + slit, EYE, c0, UnderwayMaterial.EMBRASURE);
            brush.set(a0 + slit, EYE, c1, UnderwayMaterial.EMBRASURE);
        }
        brush.fill(a0 + DOOR, FLOOR + 1, c0, a0 + DOOR, FLOOR + 2, c1, UnderwayMaterial.AIR);
        boardingSteps(brush, train, a0 + DOOR);
        if (car.torn())
            brush.fill(a0 + TEAR_START, FLOOR + 1, train.innerWall(), a0 + TEAR_START + 1, FLOOR + 2, train.innerWall(), UnderwayMaterial.AIR);
        if (car.hatch())
            brush.set(a0 + DOOR, ROOF, train.aisle(), UnderwayMaterial.AIR);

        boolean locomotive = car.kind() == UnderwayTrain.Kind.LOCOMOTIVE;
        for (int end = 0; end < 2; end++)
        {
            int along = end == 0 ? a0 : a1;
            if (locomotive && car.frontLow() == (end == 0))
            {
                brush.fill(along, FLOOR + 1, c0, along, FLOOR + 1, c1, UnderwayMaterial.HAZARD);
                brush.fill(along, EYE, c0 + 1, along, EYE, c1 - 1, UnderwayMaterial.EMBRASURE);
            }
            else
                brush.fill(along, FLOOR + 1, train.aisle(), along, FLOOR + 2, train.aisle(), UnderwayMaterial.DOOR);
        }
        if (locomotive)
            cab(brush, train, car);
        else
            load(brush, train, car, hash);
    }

    /** Two steps up from the track bed to the car's door, standing in the walkway between the tracks. */
    private static void boardingSteps(UnderwayBrush brush, UnderwayTrain train, int along)
    {
        int near = train.innerWall() + train.inner();
        var stairs = brush.stairs(0, -train.inner());
        brush.set(along, BALLAST + 1, near, UnderwayMaterial.STEEL);
        brush.set(along, FLOOR, near, stairs);
        brush.set(along, BALLAST + 1, near + train.inner(), stairs);
    }

    private static void cab(UnderwayBrush brush, UnderwayTrain train, UnderwayTrain.Car car)
    {
        int back = car.frontLow() ? 1 : -1;
        int front = car.frontLow() ? car.along() : car.along() + LAST;
        int low = train.across() + 1;
        int high = train.across() + WIDTH - 2;
        prop(brush, UnderwayProp.CONSOLE, front + back, FLOOR + 1, train.aisle(), back, 0);
        prop(brush, UnderwayProp.LEVER_FRAME, front + back, FLOOR + 1, low, back, 0);
        prop(brush, UnderwayProp.STOOL, front + back * 2, FLOOR + 1, train.aisle(), back, 0);
        prop(brush, UnderwayProp.SWITCHGEAR, front + back * 3, FLOOR + 1, high, 0, -1);
        prop(brush, UnderwayProp.BATTERY_RACK, front + back * 7, FLOOR + 1, low, 0, 1);
        prop(brush, UnderwayProp.BATTERY_RACK, front + back * 8, FLOOR + 1, low, 0, 1);
        prop(brush, UnderwayProp.AIR_TANK, front + back * 8, FLOOR + 1, high, 0, -1);
        prop(brush, UnderwayProp.SEARCHLIGHT, front + back, ROOF + 1, train.aisle(), -back, 0);
    }

    /** Lines both walls with what the car carried, less what was taken, leaving the lane and the doors clear. */
    private static void load(UnderwayBrush brush, UnderwayTrain train, UnderwayTrain.Car car, long hash)
    {
        var props = switch (car.kind())
        {
            case TROOP -> BENCHES;
            case MUNITIONS -> MUNITIONS;
            case SUPPLY -> SUPPLIES;
            default -> SHELLS;
        };
        int low = train.across() + 1;
        int high = train.across() + WIDTH - 2;
        int next = 0;
        for (int offset = 1; offset < LAST; offset++)
        {
            if (offset == DOOR)
                continue;
            for (int side = 0; side < 2; side++)
            {
                hash = DaedalusHash.mix(hash);
                if (DaedalusHash.pick(hash, PERCENT) < (car.kind() == UnderwayTrain.Kind.GUN ? PERCENT - LOOTED_PERCENT : LOOTED_PERCENT))
                    continue;
                prop(brush, props[next++ % props.length], car.along() + offset, FLOOR + 1, side == 0 ? low : high, 0, side == 0 ? 1 : -1);
            }
        }
        if (car.kind() == UnderwayTrain.Kind.GUN)
        {
            prop(brush, UnderwayProp.PERISCOPE, car.along() + DOOR, ROOF - 1, train.aisle(), 1, 0);
            prop(brush, UnderwayProp.TURRET_WELL, car.along() + DOOR, ROOF + 1, train.aisle(), 1, 0);
        }
    }

    private static void prop(UnderwayBrush brush, UnderwayProp prop, int along, int y, int across, int faceAlong, int faceAcross)
    {
        var frame = brush.frame();
        brush.canvas().setProp(frame.x(along, across), y, frame.z(along, across), prop, frame.compass(faceAlong, faceAcross));
    }
}
