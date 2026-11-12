package net.voxelindustry.voidheart.common.world.underway;

/** What lies under a district's hatch. */
public enum UnderwayNode
{
    /** Two trunk lines cross here. */
    HUB,
    /** One trunk line passes here. */
    STATION,
    /** No line: a guard post whose gallery leads to the nearest station. */
    POST;

    public boolean isOnLine()
    {
        return this != POST;
    }
}
