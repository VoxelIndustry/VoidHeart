package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shape of a Great Ravenous Storage: a Ravenous Storage twice as wide, on a plinth across a 2x2 floor, a pillar rising
 * at each outer corner as it fills. Each of its eight blocks draws its own quarter, written once here for the block
 * models and the block shapes alike, as the north-west quarter turned into place.
 */
public final class GreatStorageLayout
{
    public static final int BLOCKS = 8;

    private static final double PILLAR_FROM  = 1;
    private static final double PILLAR_WIDTH = 5;
    private static final double CAP_OVERHANG = 0.5;
    private static final int    PLINTH_TOP   = 12;

    /**
     * Where a block stands in the cube, or loose while the cube is not whole. Each corner names its offset from the
     * cube's lowest north-west block and the quarter turns that bring the north-west quarter to it.
     */
    public enum Part implements StringRepresentable
    {
        LOOSE(0, 0, 0, 0),
        LOWER_NW(0, 0, 0, 0),
        LOWER_NE(1, 0, 0, 1),
        LOWER_SE(1, 0, 1, 2),
        LOWER_SW(0, 0, 1, 3),
        UPPER_NW(0, 1, 0, 0),
        UPPER_NE(1, 1, 0, 1),
        UPPER_SE(1, 1, 1, 2),
        UPPER_SW(0, 1, 1, 3);

        public static final List<Part> CORNERS = List.of(LOWER_NW, LOWER_NE, LOWER_SE, LOWER_SW, UPPER_NW, UPPER_NE, UPPER_SE, UPPER_SW);

        private final int dx;
        private final int dy;
        private final int dz;
        private final int quarterTurns;

        Part(int dx, int dy, int dz, int quarterTurns)
        {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.quarterTurns = quarterTurns;
        }

        public int dx()
        {
            return dx;
        }

        public int dy()
        {
            return dy;
        }

        public int dz()
        {
            return dz;
        }

        public int quarterTurns()
        {
            return quarterTurns;
        }

        public boolean isUpper()
        {
            return dy == 1;
        }

        /**
         * The block holding the cube for relays, its renderer and Jade.
         */
        public boolean isHolder()
        {
            return this == LOWER_NW;
        }

        @Override
        public String getSerializedName()
        {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A box in pixels of the block, with the faces it draws, by the first letter of each side.
     */
    public record Box(double x0, double y0, double z0, double x1, double y1, double z1, String texture, String faces)
    {
        /**
         * The box turned clockwise about the block's middle, seen from above, as a blockstate's y rotation turns it.
         */
        public Box turned(int quarterTurns)
        {
            var box = this;
            for (var turn = 0; turn < Math.floorMod(quarterTurns, 4); turn++)
                box = new Box(16 - box.z1, box.y0, box.x0, 16 - box.z0, box.y1, box.x1, box.texture, turnFaces(box.faces));
            return box;
        }

        private static String turnFaces(String faces)
        {
            var turned = new StringBuilder();
            for (var side : faces.toCharArray())
                turned.append(switch (side)
                {
                    case 'n' -> 'e';
                    case 'e' -> 's';
                    case 's' -> 'w';
                    case 'w' -> 'n';
                    default -> side;
                });
            return turned.toString();
        }
    }

    private GreatStorageLayout()
    {
    }

    /**
     * A loose block: the plinth's bands stacked into a whole block, waiting for its seven others.
     */
    public static List<Box> loose()
    {
        return List.of(
                new Box(0, 0, 0, 16, 3, 16, "frame", "nsewud"),
                new Box(1, 3, 1, 15, 13, 15, "body", "nsew"),
                new Box(0, 13, 0, 16, 16, 16, "trim", "nsewud"));
    }

    /**
     * The north-west quarter of a cube at this fill, lower or upper block. The plinth leaves out its faces against the
     * other quarters, and the pillar is cut where the two blocks meet.
     */
    public static List<Box> quarter(boolean upper, int fill)
    {
        var boxes = new ArrayList<Box>();
        if (!upper)
        {
            boxes.add(new Box(0, 0, 0, 16, 3, 16, "frame", "nwud"));
            boxes.add(new Box(1, 3, 1, 16, 10, 16, "body", "nw"));
            boxes.add(new Box(0, 10, 0, 16, PLINTH_TOP, 16, "trim", "nwud"));
        }

        var offset = upper ? 16 : 0;
        var top = StorageFill.pillarTop(fill);
        var pillarTo = PILLAR_FROM + PILLAR_WIDTH;
        cut(boxes, new Box(PILLAR_FROM, PLINTH_TOP, PILLAR_FROM, pillarTo, top - 1, pillarTo, "pillar", "nsew"), offset);
        cut(boxes, new Box(PILLAR_FROM - CAP_OVERHANG, top - 1, PILLAR_FROM - CAP_OVERHANG,
                pillarTo + CAP_OVERHANG, top, pillarTo + CAP_OVERHANG, "cap", "nsewud"), offset);
        return boxes;
    }

    /**
     * Adds the part of a box given from the bottom of the cube that falls in the block {@code offset} pixels up.
     */
    private static void cut(List<Box> boxes, Box box, int offset)
    {
        var from = Math.max(box.y0() - offset, 0);
        var to = Math.min(box.y1() - offset, 16);
        if (to > from)
            boxes.add(new Box(box.x0(), from, box.z0(), box.x1(), to, box.z1(), box.texture(), box.faces()));
    }

    /**
     * The boxes a block draws, turned to its corner.
     */
    public static List<Box> boxes(Part part, int fill)
    {
        if (part == Part.LOOSE)
            return loose();
        return quarter(part.isUpper(), fill).stream().map(box -> box.turned(part.quarterTurns())).toList();
    }
}
