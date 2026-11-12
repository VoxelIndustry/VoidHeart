package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusHash;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;

import java.util.ArrayList;
import java.util.List;

/**
 * An armoured train left dead on a trunk track, laid out along its line: a locomotive and the cars behind it, each a steel hull five wide that overhangs its track.
 *
 * @param across the lowest block across the line that the hull takes
 * @param inner  the step across the line from the hull towards the walkway between the tracks, where its boarding steps stand
 */
public record UnderwayTrain(int across, int inner, List<Car> cars)
{
    public static final int CAR_LENGTH = 11;
    public static final int WIDTH = 5;
    /** Cars stand a block apart, bridged by a strip of grating. */
    public static final int CAR_PITCH = CAR_LENGTH + 1;

    private static final int LOW_TRACK = TrunkPainter.LOW;
    private static final int HIGH_TRACK = TrunkPainter.HIGH - WIDTH + 1;
    private static final int PERCENT = 100;
    private static final int STATION_PERCENT = 35;
    private static final int TUNNEL_PERCENT = 20;
    private static final int TORN_PERCENT = 40;
    private static final int HATCH_PERCENT = 30;
    private static final int MIN_STATION_CARS = 3;
    private static final int TUNNEL_CARS = 2;
    private static final int TUNNEL_START = DistrictPos.SIZE + 4;
    private static final Kind[] CARGO = { Kind.TROOP, Kind.MUNITIONS, Kind.SUPPLY, Kind.GUN };
    private static final long SALT_STATION = 0x554E44455254L;
    private static final long SALT_TUNNEL = 0x554E4445525455L;

    public enum Kind
    {
        LOCOMOTIVE,
        TROOP,
        MUNITIONS,
        SUPPLY,
        GUN
    }

    /**
     * @param along    where the car starts along the line
     * @param frontLow for a locomotive, whether its cab is at its low end
     * @param torn     whether its inner side was ripped open
     * @param hatch    whether its roof hatch stands open
     */
    public record Car(Kind kind, int along, boolean frontLow, boolean torn, boolean hatch)
    {
    }

    /**
     * @return the trains standing under a district of a line and under the chasm beyond it: at most one at the platforms of a station, and one in an open tunnel
     */
    public static List<UnderwayTrain> at(long seed, DistrictPos pos, boolean alongX)
    {
        var trains = new ArrayList<UnderwayTrain>();
        if (UnderwayNetwork.nodeAt(seed, pos) == UnderwayNode.STATION)
        {
            long hash = DaedalusHash.mix(seed, pos.x(), pos.z(), SALT_STATION);
            if (DaedalusHash.pick(hash, PERCENT) < STATION_PERCENT)
            {
                int cars = MIN_STATION_CARS + DaedalusHash.pick(DaedalusHash.mix(hash + 1), 2);
                trains.add(train(hash, DistrictPos.SIZE / 2 - (cars * CAR_PITCH - 1) / 2, cars));
            }
        }
        long hash = DaedalusHash.mix(seed, pos.x(), pos.z(), alongX ? SALT_TUNNEL : ~SALT_TUNNEL);
        if (UnderwayNetwork.linkFrom(seed, pos, alongX ? Compass.EAST : Compass.SOUTH) == UnderwayLink.OPEN && DaedalusHash.pick(hash, PERCENT) < TUNNEL_PERCENT)
            trains.add(train(hash, TUNNEL_START, TUNNEL_CARS));
        return trains;
    }

    private static UnderwayTrain train(long hash, int along, int count)
    {
        boolean lowTrack = DaedalusHash.pick(DaedalusHash.mix(hash + 2), 2) == 0;
        boolean frontLow = DaedalusHash.pick(DaedalusHash.mix(hash + 3), 2) == 0;
        var cars = new ArrayList<Car>();
        for (int index = 0; index < count; index++)
        {
            long roll = DaedalusHash.mix(hash + 10 + index);
            boolean locomotive = index == (frontLow ? 0 : count - 1);
            var kind = locomotive ? Kind.LOCOMOTIVE : CARGO[DaedalusHash.pick(roll, CARGO.length)];
            boolean torn = DaedalusHash.pick(DaedalusHash.mix(roll), PERCENT) < TORN_PERCENT;
            boolean hatch = kind != Kind.GUN && kind != Kind.LOCOMOTIVE && DaedalusHash.pick(DaedalusHash.mix(roll + 1), PERCENT) < HATCH_PERCENT;
            cars.add(new Car(kind, along + index * CAR_PITCH, frontLow, torn, hatch));
        }
        return new UnderwayTrain(lowTrack ? LOW_TRACK : HIGH_TRACK, lowTrack ? 1 : -1, List.copyOf(cars));
    }

    /** @return the block across the line just inside the hull's inner wall, the middle of its three lanes */
    public int aisle()
    {
        return across + WIDTH / 2;
    }

    /** @return the hull's wall across the line on its inner side */
    public int innerWall()
    {
        return inner > 0 ? across + WIDTH - 1 : across;
    }
}
