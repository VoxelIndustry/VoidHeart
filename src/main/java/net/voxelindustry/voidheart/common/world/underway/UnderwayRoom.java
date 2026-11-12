package net.voxelindustry.voidheart.common.world.underway;

/**
 * A room's inside, along and across its station's frame.
 *
 * @param floorY the block walked on
 * @param height the blocks of air over the floor
 */
public record UnderwayRoom(UnderwayRoomKind kind, int a0, int c0, int a1, int c1, int floorY, int height)
{
    public int centreAlong()
    {
        return (a0 + a1) / 2;
    }

    public int centreAcross()
    {
        return (c0 + c1) / 2;
    }
}
