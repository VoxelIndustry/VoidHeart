package net.voxelindustry.voidheart.common.world.daedalus;

/** A box of lattice cells, bounds included. */
public record Room(int minI, int minK, int minJ, int maxI, int maxK, int maxJ, RoomShape shape, int variant)
{
    public int cellsWide()
    {
        return maxI - minI + 1;
    }

    public int cellsDeep()
    {
        return maxJ - minJ + 1;
    }
}
