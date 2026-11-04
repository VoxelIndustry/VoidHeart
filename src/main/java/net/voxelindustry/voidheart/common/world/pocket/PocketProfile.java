package net.voxelindustry.voidheart.common.world.pocket;

/**
 * The numbers shaping a scene inside the fixed pocket cube. Heights are in blocks, relative to the heart unless noted.
 */
public final class PocketProfile
{
    public static final int NO_FLUID = Integer.MIN_VALUE;

    int heartRise;
    double floorLift;
    double floorAmp = 0.8;
    double rimRise = 2;
    double ceilAmp = 0.8;
    double ceilDrop = 2;
    double wallInset = 0.42;
    double islandRadius;
    double poolRadius;
    int fluidLevel = NO_FLUID;
    boolean frozen;
    int stalactites;
    int stalagmites;
    int pillars;
    int coneReach = 4;
    int niches = 2;
    int crystalClusters = 3;
    double rubrumShare = 0.5;
    int trees;
    int lights = 4;

    PocketProfile lights(int count)
    {
        lights = count;
        return this;
    }

    PocketProfile heartRise(int rise)
    {
        heartRise = rise;
        return this;
    }

    PocketProfile floor(double lift, double amp, double rim)
    {
        floorLift = lift;
        floorAmp = amp;
        rimRise = rim;
        return this;
    }

    PocketProfile ceiling(double amp, double drop)
    {
        ceilAmp = amp;
        ceilDrop = drop;
        return this;
    }

    PocketProfile island(double radius)
    {
        islandRadius = radius;
        return this;
    }

    PocketProfile pool(double radius)
    {
        poolRadius = radius;
        return this;
    }

    PocketProfile fluid(int level)
    {
        fluidLevel = level;
        return this;
    }

    PocketProfile frozen()
    {
        frozen = true;
        return this;
    }

    PocketProfile cones(int hanging, int rising, int columns, int reach)
    {
        stalactites = hanging;
        stalagmites = rising;
        pillars = columns;
        coneReach = reach;
        return this;
    }

    PocketProfile crystals(int nicheCount, int clusters, double rubrum)
    {
        niches = nicheCount;
        crystalClusters = clusters;
        rubrumShare = rubrum;
        return this;
    }

    PocketProfile trees(int count)
    {
        trees = count;
        return this;
    }

    public int heartRise()
    {
        return heartRise;
    }

    int stagmites()
    {
        return stalagmites;
    }

    boolean hasFluid()
    {
        return fluidLevel != NO_FLUID;
    }
}
