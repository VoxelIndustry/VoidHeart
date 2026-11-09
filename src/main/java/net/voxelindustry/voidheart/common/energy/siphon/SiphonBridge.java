package net.voxelindustry.voidheart.common.energy.siphon;

import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusBridge;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusBridges;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Which Daedalus bridge a block stands on, and where on it, from the seed alone like the bridges themselves. Pure, so it is unit tested.
 */
public final class SiphonBridge
{
    /** A lane's centre, the same local 3 as the corridors and {@code BridgePainter}. */
    public static final int LANE_CENTRE = 3;

    /** Waves climb onto the deck this far in from either end, so they stay on the bridge rather than step into a district that would fence them in. */
    static final int WAVE_FROM_END = 3;
    static final int WAVE_DEPTH    = 4;

    /**
     * @param span    how far along the crossing, 0 at the district it starts from
     * @param lateral across its lane, the lane's centre being {@link #LANE_CENTRE}
     */
    public record Footing(DaedalusBridge bridge, int span, int lateral)
    {
    }

    /**
     * A spot on the deck where a mob can climb out of the chasm, with the deck block's walkable height above it.
     */
    public record Spot(int x, int y, int z)
    {
    }

    private SiphonBridge()
    {
    }

    /**
     * @return the bridge under a block standing on its deck, its edges included, or null in a district, a corner of the chasm or the air beside a bridge
     */
    public static @Nullable Footing at(long seed, int x, int y, int z)
    {
        int alongX = Math.floorMod(x, DistrictPos.PITCH);
        int alongZ = Math.floorMod(z, DistrictPos.PITCH);
        int direction;
        int span;
        int across;
        if (alongX >= DistrictPos.SIZE && alongZ < DistrictPos.SIZE)
        {
            direction = Compass.EAST;
            span = alongX - DistrictPos.SIZE;
            across = z;
        }
        else if (alongZ >= DistrictPos.SIZE && alongX < DistrictPos.SIZE)
        {
            direction = Compass.SOUTH;
            span = alongZ - DistrictPos.SIZE;
            across = x;
        }
        else
            return null;

        for (var bridge : DaedalusBridges.between(seed, DistrictPos.at(x, z), direction))
        {
            int lateral = across - bridge.laneOrigin();
            if (lateral < low(bridge) - 1 || lateral > high(bridge) + 1)
                continue;
            int deck = bridge.deckY(span);
            if (y > deck && y <= bridge.walkY(span))
                return new Footing(bridge, span, lateral);
        }
        return null;
    }

    /**
     * The district on the far side of the crossing.
     */
    public static DistrictPos to(DaedalusBridge bridge)
    {
        var from = bridge.from();
        return bridge.isAlongX() ? new DistrictPos(from.x() + 1, from.z()) : new DistrictPos(from.x(), from.z() + 1);
    }

    /**
     * Where waves climb out onto the deck: near the ends, alternating between them so a wave splits between both districts' sides, and the middle of the
     * lane before its edges at each step in.
     */
    public static List<Spot> waveSpots(DaedalusBridge bridge)
    {
        var laterals = new ArrayList<Integer>();
        for (int lateral = low(bridge); lateral <= high(bridge); lateral++)
            laterals.add(lateral);
        laterals.sort((a, b) -> Integer.compare(Math.abs(a - LANE_CENTRE), Math.abs(b - LANE_CENTRE)));

        var spots = new ArrayList<Spot>();
        for (int depth = 0; depth < WAVE_DEPTH; depth++)
        {
            for (int lateral : laterals)
            {
                for (int span : new int[] { WAVE_FROM_END + depth, DistrictPos.CHASM - 1 - WAVE_FROM_END - depth })
                    spots.add(new Spot(bridge.worldX(span, lateral), bridge.walkY(span), bridge.worldZ(span, lateral)));
            }
        }
        return spots;
    }

    public static int low(DaedalusBridge bridge)
    {
        return LANE_CENTRE - (bridge.width() - 1) / 2;
    }

    public static int high(DaedalusBridge bridge)
    {
        return LANE_CENTRE + bridge.width() / 2;
    }
}
