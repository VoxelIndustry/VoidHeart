package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.Compass;

import java.util.List;

/**
 * A bay that a cult of the void took for its own: pews down an aisle to a dais, and the mosaic of what it worshipped set into the back wall, facing the doorway.
 * <p>
 * Places in it are counted {@code w} along the wall the doorway is in, from the room's low end, and {@code d} from that wall in towards the back. The mosaic is in the back wall, one past the last {@code d}.
 *
 * @param inAlong  the step from the doorway in towards the mosaic, along the frame
 * @param inAcross the same, across the frame
 * @param wear     what time did to each tile of the mosaic, row by row from the top and by {@code w} within a row
 */
public record UnderwayTemple(UnderwayRoom room, int inAlong, int inAcross, List<Wear> wear)
{
    public static final int WIDTH = 17;
    public static final int DEPTH = 11;
    public static final int HEIGHT = 10;
    public static final int COLUMNS = 15;
    public static final int ROWS = 8;
    /** The {@code w} of the mosaic's first column, so it stands in the middle of the back wall. */
    public static final int FIRST_COLUMN = (WIDTH - COLUMNS) / 2;
    /** How far over the floor the mosaic's lowest row is: over the dais and what stands on it. */
    public static final int BOTTOM_ROW = 3;
    public static final int AISLE = WIDTH / 2;

    public enum Wear
    {
        WHOLE,
        /** Missing some tesserae. */
        CHIPPED,
        /** Down to the bare bed it was set in. */
        FALLEN
    }

    public int along(int w, int d)
    {
        if (inAlong == 0)
            return room.a0() + w;
        return inAlong > 0 ? room.a0() + d : room.a1() - d;
    }

    public int across(int w, int d)
    {
        if (inAlong != 0)
            return room.c0() + w;
        return inAcross > 0 ? room.c0() + d : room.c1() - d;
    }

    /** @return the wear of the tile at a place of the mosaic */
    public Wear wear(int w, int row)
    {
        return wear.get(row * COLUMNS + w - FIRST_COLUMN);
    }

    /** @return the y of a row of the mosaic, counted from the top */
    public int rowY(int row)
    {
        return room.floorY() + BOTTOM_ROW + ROWS - 1 - row;
    }

    /**
     * @return the column of the picture shown at a {@code w}, counted from the left as someone facing the mosaic sees it. Frames may mirror a fort, but never its picture.
     */
    public int imageColumn(UnderwayFrame frame, int w)
    {
        int in = frame.compass(inAlong, inAcross);
        int step = inAlong == 0 ? frame.compass(1, 0) : frame.compass(0, 1);
        // Facing (dx, dz), the right hand points to (-dz, dx)
        boolean rightward = Compass.dx(step) * -Compass.dz(in) + Compass.dz(step) * Compass.dx(in) > 0;
        int column = w - FIRST_COLUMN;
        return rightward ? column : COLUMNS - 1 - column;
    }
}
