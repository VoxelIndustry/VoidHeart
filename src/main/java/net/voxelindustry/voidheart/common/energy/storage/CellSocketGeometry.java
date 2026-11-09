package net.voxelindustry.voidheart.common.energy.storage;

/**
 * Where the cell stands in its socket, in blocks from the socket's floor, shared by the motes and the renderer.
 */
public final class CellSocketGeometry
{
    /** Top of the plinth, where the cell's lower cap rests. */
    public static final float CELL_BOTTOM = 8 / 16F;
    public static final float CELL_TOP    = 15 / 16F;

    private CellSocketGeometry()
    {
    }
}
