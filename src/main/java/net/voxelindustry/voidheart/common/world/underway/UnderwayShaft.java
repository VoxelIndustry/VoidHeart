package net.voxelindustry.voidheart.common.world.underway;

/**
 * A stair shaft between levels, in its fort's frame.
 *
 * @param highAlong  whether the landings sit at the far end along the frame
 * @param highAcross whether the landings sit at the far end across the frame
 * @param lift       whether the stairs wind around a lift's cage rather than a concrete newel
 * @param carFloor   where the lift's car stopped for good
 */
public record UnderwayShaft(int a0, int c0, int bottomFloor, int topFloor, boolean highAlong, boolean highAcross, boolean lift, int carFloor)
{
    /** @return the first of the two blocks along the frame that a level's landing takes */
    public int landingAlong()
    {
        return highAlong ? a0 + StairShaft.SIZE - 2 : a0;
    }

    /** @return the first of the two blocks across the frame that a level's landing takes */
    public int landingAcross()
    {
        return highAcross ? c0 + StairShaft.SIZE - 2 : c0;
    }
}
