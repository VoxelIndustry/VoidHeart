package net.voxelindustry.voidheart.common.content.tormenter;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The outline of a sated tormenter, shared by what draws it and what makes it solid. Lengths are in blocks, from the middle
 * of the floor of the tormenter's own block: a narrow tip there, a wide belly above it and a round dome on top.
 */
public final class TormenterDropletShape
{
    public static final float HEIGHT     = 4;
    public static final float MOST_WIDTH = 1.05F;

    /**
     * Share of the width a slice keeps along its sides: two crossed rectangles make it round the blocky way.
     */
    public static final float ROUNDING = 0.68F;

    // How low the belly hangs, and how plump the outline is around it
    private static final float BELLY_POWER = 1.25F;
    private static final float PLUMPNESS   = 0.7F;

    // Parts thinner than this would only be something to snag on
    private static final float THINNEST_PART = 0.05F;

    private static final int WIDTH_SAMPLES = 16;

    /**
     * Blocks around the tormenter's own that the droplet takes up, leaving the floor around its tip free.
     */
    public static final List<Cell> CELLS = cells();

    /**
     * A block's position relative to the tormenter's.
     */
    public record Cell(int x, int y, int z)
    {
    }

    /**
     * A box within a block, from 0 to 1.
     */
    public record Box(float minX, float minZ, float maxX, float maxZ)
    {
    }

    private TormenterDropletShape()
    {
    }

    /**
     * @param rise share of the height, 0 at the tip and 1 on top of the dome
     * @return half the width of the droplet at that height
     */
    public static float halfWidth(float rise)
    {
        var clamped = Mth.clamp(rise, 0, 1);
        return MOST_WIDTH * (float) Math.pow(Math.sin(Math.PI * Math.pow(clamped, BELLY_POWER)), PLUMPNESS);
    }

    /**
     * @return what the droplet fills of the block at these coordinates, nothing if it stays out of it
     */
    public static List<Box> boxes(int x, int y, int z)
    {
        var half = widestInLevel(y);
        var narrow = half * ROUNDING;
        var boxes = new ArrayList<Box>(2);
        addBox(boxes, x, z, half, narrow);
        addBox(boxes, x, z, narrow, half);
        return boxes;
    }

    private static void addBox(List<Box> boxes, int x, int z, float halfX, float halfZ)
    {
        var minX = Math.max(-halfX, x - 0.5F);
        var maxX = Math.min(halfX, x + 0.5F);
        var minZ = Math.max(-halfZ, z - 0.5F);
        var maxZ = Math.min(halfZ, z + 0.5F);
        if (maxX - minX >= THINNEST_PART && maxZ - minZ >= THINNEST_PART)
            boxes.add(new Box(minX - x + 0.5F, minZ - z + 0.5F, maxX - x + 0.5F, maxZ - z + 0.5F));
    }

    private static float widestInLevel(int y)
    {
        var widest = 0F;
        for (var sample = 0; sample <= WIDTH_SAMPLES; sample++)
            widest = Math.max(widest, halfWidth((y + (float) sample / WIDTH_SAMPLES) / HEIGHT));
        return widest;
    }

    private static List<Cell> cells()
    {
        var cells = new ArrayList<Cell>();
        for (var y = 1; y < HEIGHT; y++)
            for (var x = -1; x <= 1; x++)
                for (var z = -1; z <= 1; z++)
                    if (!boxes(x, y, z).isEmpty())
                        cells.add(new Cell(x, y, z));
        return List.copyOf(cells);
    }
}
