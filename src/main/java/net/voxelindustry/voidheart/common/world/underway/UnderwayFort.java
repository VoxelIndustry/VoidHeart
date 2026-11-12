package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;

import java.util.List;

/**
 * Everything built under one district, laid out in its frame: a station, a hub or a post.
 *
 * @param hatchAlong  where the hatch's shaft starts along the frame, on the districts' lattice of cells
 * @param hatchAcross where the hatch's shaft starts across the frame
 * @param footAlong   a block at the foot of the hatch's stairs, on the entry level
 * @param footAcross  the same block, across the frame
 */
public record UnderwayFort(DistrictPos pos, UnderwayNode node, UnderwayFrame frame, UnderwayRuin ruin, int hatchAlong, int hatchAcross, int footAlong, int footAcross,
                           List<UnderwayRoom> rooms, List<UnderwayOpening> openings, List<UnderwayShaft> shafts, List<UnderwayStroke> strokes, List<UnderwayPropSpot> props)
{
    /** @return the lowest x of the 8 x 8 hatch in the floor of the district above */
    public int hatchX()
    {
        return Math.min(frame.x(hatchAlong, hatchAcross), frame.x(hatchAlong + StairShaft.SIZE - 1, hatchAcross + StairShaft.SIZE - 1));
    }

    /** @return the hatch's cell on the lattice of the district above, counted along x. A cell lies between it and the district's faces. */
    public int hatchI()
    {
        return (hatchX() - pos.originX()) / DistrictPos.CELL;
    }

    /** @return the hatch's cell on the lattice of the district above, counted along z */
    public int hatchJ()
    {
        return (hatchZ() - pos.originZ()) / DistrictPos.CELL;
    }

    /** @return the lowest z of the 8 x 8 hatch in the floor of the district above */
    public int hatchZ()
    {
        return Math.min(frame.z(hatchAlong, hatchAcross), frame.z(hatchAlong + StairShaft.SIZE - 1, hatchAcross + StairShaft.SIZE - 1));
    }
}
