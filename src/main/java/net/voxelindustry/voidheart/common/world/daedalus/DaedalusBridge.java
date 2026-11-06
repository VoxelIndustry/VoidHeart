package net.voxelindustry.voidheart.common.world.daedalus;

/** A crossing of the chasm east or south of {@code from}. Its walkable width is always odd, so it shares the centre of the corridors. */
public record DaedalusBridge(DistrictPos from, int direction, int cell, int fromTier, int toTier, int width, BridgeStyle style, int variant)
{
    private static final int BLOCKS_PER_RAMP_STEP = 4;

    public enum BridgeStyle
    {
        BEAM,
        PARAPET,
        ARCADE,
        TUNNEL,
        PIER,
        CHAINED,
        RAMP
    }

    public boolean isAlongX()
    {
        return direction == Compass.EAST;
    }

    public int spanStart()
    {
        return (isAlongX() ? from.originX() : from.originZ()) + DistrictPos.SIZE;
    }

    public int laneOrigin()
    {
        return (isAlongX() ? from.originZ() : from.originX()) + cell * DistrictPos.CELL;
    }

    public int worldX(int span, int lateral)
    {
        return isAlongX() ? spanStart() + span : laneOrigin() + lateral;
    }

    public int worldZ(int span, int lateral)
    {
        return isAlongX() ? laneOrigin() + lateral : spanStart() + span;
    }

    private int uphill(int span)
    {
        return toTier > fromTier ? span : DistrictPos.CHASM - 1 - span;
    }

    public int deckY(int span)
    {
        if (fromTier == toTier)
            return DistrictPos.floorY(fromTier);
        return DistrictPos.floorY(Math.min(fromTier, toTier)) + uphill(span) / BLOCKS_PER_RAMP_STEP;
    }

    public boolean isRampStep(int span)
    {
        return fromTier != toTier && uphill(span) % BLOCKS_PER_RAMP_STEP == BLOCKS_PER_RAMP_STEP - 1;
    }

    public int climbDirection()
    {
        return toTier > fromTier ? direction : Compass.opposite(direction);
    }

    public int walkY(int span)
    {
        return deckY(span) + (isRampStep(span) ? 2 : 1);
    }
}
