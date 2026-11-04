package net.voxelindustry.voidheart.common.world.pocket;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import static net.voxelindustry.voidheart.common.world.pocket.PocketGrid.INNER_REACH;
import static net.voxelindustry.voidheart.common.world.pocket.PocketGrid.WALL_REACH;

/**
 * Shapes the rock inside the pocket cube into a natural cave. Pure and deterministic, so it can be tested without a world.
 */
public final class PocketTerrain
{
    private static final int COLUMNS = WALL_REACH * 2 + 1;
    private static final double SIDE_ROUNDING = 7.6;
    private static final double SIDE_POWER = 3.2;
    private static final int HEART_CLEARING = 2;
    private static final int HEART_HEADROOM = 5;
    private static final int STRATA_PERIOD = 7;
    private static final int CONE_MIN_HEART_DISTANCE = 3;
    private static final double GROUND_TOLERANCE = 1.5;

    private final PocketProfile profile;
    private final RandomSource random;
    private final PocketGrid grid;
    private final int innerBottom;
    private final int innerTop;

    private final float[] floor = new float[COLUMNS * COLUMNS];
    private final float[] ceil = new float[COLUMNS * COLUMNS];
    private final float[] stagmiteTop = new float[COLUMNS * COLUMNS];
    private final float[] stalactiteBottom = new float[COLUMNS * COLUMNS];

    private PocketTerrain(PocketBlueprint blueprint)
    {
        profile = blueprint.variant().profile();
        random = RandomSource.create(blueprint.seed());
        grid = new PocketGrid(profile.heartRise);
        innerBottom = grid.bottom() + 2;
        innerTop = grid.top() - 2;
    }

    public static PocketGrid generate(PocketBlueprint blueprint)
    {
        return new PocketTerrain(blueprint).run();
    }

    private PocketGrid run()
    {
        shapeColumns();
        raiseCones();
        carveCave();
        carveNiches();
        clearAroundHeart();
        flood();
        dressSurfaces();
        layPedestal();
        sealWithWall();
        return grid;
    }

    private void shapeColumns()
    {
        var floorA = new SimplexNoise(random);
        var floorB = new SimplexNoise(random);
        var ceilA = new SimplexNoise(random);
        var ceilB = new SimplexNoise(random);
        var shoreline = new SimplexNoise(random);

        double poolAngle = random.nextDouble() * Mth.TWO_PI;
        double poolX = Math.cos(poolAngle) * 4.2;
        double poolZ = Math.sin(poolAngle) * 4.2;

        for (int z = -WALL_REACH; z <= WALL_REACH; z++)
        {
            for (int x = -WALL_REACH; x <= WALL_REACH; x++)
            {
                double fromHeart = Math.hypot(x, z);
                double edge = Mth.clamp((Math.max(Math.abs(x), Math.abs(z)) - 3) / 4D, 0, 1);
                edge *= edge;

                double ground = innerBottom - 1 + profile.floorLift
                        + profile.floorAmp * (floorA.get(x / 8D, z / 8D) + 0.3 * floorB.get(x / 4D, z / 4D) + 0.5)
                        + profile.rimRise * edge;

                if (profile.poolRadius > 0)
                {
                    double pool = 1 - Math.hypot(x - poolX, z - poolZ) / (profile.poolRadius + 0.9 * shoreline.get(x / 3.5, z / 3.5));
                    if (pool > 0)
                        ground = Math.min(ground, innerBottom - 1);
                }

                double roof = innerTop + 1 - profile.ceilDrop * edge
                        - profile.ceilAmp * (ceilA.get(x / 7D, z / 7D) + 0.3 * ceilB.get(x / 3.5, z / 3.5) + 0.5);

                if (profile.islandRadius > 0)
                {
                    double reach = profile.islandRadius + 1.1 * shoreline.get(x / 4D, z / 4D);
                    double rise = smoothstep((1 - fromHeart / reach) * 2.2);
                    ground = Mth.lerp(rise, ground, -1);
                }
                else
                {
                    double flatten = smoothstep((fromHeart - 2) / 3.5);
                    ground = Mth.lerp(flatten, -1, ground);
                }

                int column = column(x, z);
                floor[column] = (float) ground;
                ceil[column] = (float) roof;
                stagmiteTop[column] = grid.bottom() - 1;
                stalactiteBottom[column] = grid.top() + 1;
            }
        }
    }

    private void raiseCones()
    {
        for (int i = 0; i < profile.stalactites; i++)
            raiseCone(false, true, 1.1 + random.nextDouble() * 1.4);
        for (int i = 0; i < profile.stagmites(); i++)
            raiseCone(true, false, 1.1 + random.nextDouble() * 1.4);
        for (int i = 0; i < profile.pillars; i++)
            raiseCone(true, true, 1.5 + random.nextDouble() * 0.8);
    }

    private void raiseCone(boolean rising, boolean hanging, double width)
    {
        for (int attempt = 0; attempt < 12; attempt++)
        {
            int x = random.nextInt(INNER_REACH * 2 + 1) - INNER_REACH;
            int z = random.nextInt(INNER_REACH * 2 + 1) - INNER_REACH;
            if (Math.hypot(x, z) < CONE_MIN_HEART_DISTANCE + width)
                continue;

            int center = column(x, z);
            double span = ceil[center] - floor[center];
            if (span < 4)
                continue;

            boolean pillar = rising && hanging;
            double rise = pillar ? span * 0.65 : rising ? 1 + random.nextDouble() * profile.coneReach * 0.7 : 0;
            double hang = pillar ? span * 0.65 : hanging ? 1.5 + random.nextDouble() * profile.coneReach : 0;
            placeCone(x, z, width, floor[center], ceil[center], rise, Math.min(hang, span - 2));
            return;
        }
    }

    private void placeCone(int centerX, int centerZ, double width, double base, double roof, double rise, double hang)
    {
        int reach = (int) Math.ceil(width);
        for (int z = Math.max(-INNER_REACH, centerZ - reach); z <= Math.min(INNER_REACH, centerZ + reach); z++)
        {
            for (int x = Math.max(-INNER_REACH, centerX - reach); x <= Math.min(INNER_REACH, centerX + reach); x++)
            {
                double fromAxis = Math.hypot(x - centerX, z - centerZ);
                if (fromAxis >= width)
                    continue;

                double taper = Math.pow(1 - fromAxis / width, 1.2);
                int column = column(x, z);
                if (rise > 0)
                    stagmiteTop[column] = (float) Math.max(stagmiteTop[column], base + rise * taper);
                if (hang > 0)
                    stalactiteBottom[column] = (float) Math.min(stalactiteBottom[column], roof - hang * taper);
            }
        }
    }

    private void carveCave()
    {
        var bulges = new SimplexNoise(random);
        var insets = new SimplexNoise(random);

        for (int y = grid.bottom(); y <= grid.top(); y++)
        {
            for (int z = -WALL_REACH; z <= WALL_REACH; z++)
            {
                for (int x = -WALL_REACH; x <= WALL_REACH; x++)
                    grid.set(x, y, z, isCave(x, y, z, bulges, insets) ? PocketSlot.AIR : solidAt(x, y, z));
            }
        }
    }

    private boolean isCave(int x, int y, int z, SimplexNoise bulges, SimplexNoise insets)
    {
        if (!grid.isInner(x, y, z))
            return false;

        int column = column(x, z);
        if (y <= stagmiteTop[column] || y >= stalactiteBottom[column])
            return false;

        double sides = Math.pow(Math.abs(x) / SIDE_ROUNDING, SIDE_POWER) + Math.pow(Math.abs(z) / SIDE_ROUNDING, SIDE_POWER);
        if (sides + profile.wallInset * (0.5 + 0.5 * insets.get(x / 7D, y / 6D, z / 7D)) > 1)
            return false;

        double fromHeart = Math.hypot(x, z);
        double bulge = 0.6 * bulges.get(x / 6D, y / 5D, z / 6D) * Mth.clamp((fromHeart - 3) / 3, 0, 1);
        return y > floor[column] + bulge && y < ceil[column] + bulge;
    }

    private PocketSlot solidAt(int x, int y, int z)
    {
        int column = column(x, z);
        boolean inCone = grid.isInner(x, y, z)
                && (y <= stagmiteTop[column] || y >= stalactiteBottom[column])
                && y > floor[column] && y < ceil[column];
        return inCone ? PocketSlot.CONE : PocketSlot.ROCK;
    }

    /**
     * Hollows set back into the walls, where crystals grow thickest. They stay inside the rock layer, so the pocket wall is never exposed.
     */
    private void carveNiches()
    {
        var shape = new SimplexNoise(random);
        for (int i = 0; i < profile.niches; i++)
        {
            for (int attempt = 0; attempt < 16; attempt++)
            {
                if (tryCarveNiche(shape, i))
                    break;
            }
        }
    }

    private boolean tryCarveNiche(SimplexNoise shape, int index)
    {
        double angle = random.nextDouble() * Mth.TWO_PI;
        double stepX = Math.cos(angle), stepZ = Math.sin(angle);
        int startY = nicheHeight(index);

        int hitX = 0, hitZ = 0;
        boolean found = false;
        for (double distance = 1; distance < INNER_REACH + 3; distance += 0.5)
        {
            hitX = (int) Math.round(stepX * distance);
            hitZ = (int) Math.round(stepZ * distance);
            if (!grid.slot(hitX, startY, hitZ).isOpen())
            {
                found = distance >= 4;
                break;
            }
        }
        if (!found)
            return false;

        for (var niche : grid.niches())
        {
            if (Math.abs(niche.x() - hitX) + Math.abs(niche.z() - hitZ) + Math.abs(niche.y() - startY) < 8)
                return false;
        }

        int radius = 2;
        for (int y = startY - radius; y <= startY + radius; y++)
        {
            for (int z = hitZ - radius; z <= hitZ + radius; z++)
            {
                for (int x = hitX - radius; x <= hitX + radius; x++)
                {
                    double limit = 1.7 + 0.5 * shape.get(x / 2.5, y / 2.5, z / 2.5);
                    if (grid.isInner(x, y, z) && Math.sqrt(Mth.square(x - hitX) + Mth.square(y - startY) + Mth.square(z - hitZ)) < limit)
                        grid.set(x, y, z, PocketSlot.AIR);
                }
            }
        }
        grid.niches().add(new PocketGrid.Niche(hitX, startY, hitZ, radius));
        return true;
    }

    /**
     * The first niche sits low, under the water when there is some, and the second high: Azurium gathers near water and Rubrum away from it.
     */
    private int nicheHeight(int index)
    {
        int low = profile.hasFluid() && profile.islandRadius > 0 ? profile.fluidLevel - 1 : 1;
        return switch (index)
        {
            case 0 -> Math.max(low, innerBottom + 1);
            case 1 -> Math.min(innerTop - 3, 5 + random.nextInt(3));
            default -> 1 + random.nextInt(Math.max(1, innerTop - 4));
        };
    }

    private void clearAroundHeart()
    {
        for (int z = -HEART_CLEARING; z <= HEART_CLEARING; z++)
        {
            for (int x = -HEART_CLEARING; x <= HEART_CLEARING; x++)
            {
                if (x * x + z * z > HEART_CLEARING * HEART_CLEARING + 1)
                    continue;
                for (int y = 0; y <= HEART_HEADROOM; y++)
                    grid.set(x, y, z, PocketSlot.AIR);
                for (int y = innerBottom; y < 0; y++)
                    grid.set(x, y, z, PocketSlot.ROCK);
            }
        }
    }

    private void flood()
    {
        if (!profile.hasFluid())
            return;

        for (int y = grid.bottom(); y <= profile.fluidLevel; y++)
        {
            for (int z = -WALL_REACH; z <= WALL_REACH; z++)
            {
                for (int x = -WALL_REACH; x <= WALL_REACH; x++)
                {
                    if (grid.slot(x, y, z).isOpen())
                        grid.set(x, y, z, profile.frozen && y == profile.fluidLevel ? PocketSlot.FROZEN_FLUID : PocketSlot.FLUID);
                }
            }
        }
    }

    private void dressSurfaces()
    {
        var soilPatches = new SimplexNoise(random);
        var bedPatches = new SimplexNoise(random);
        var strataWaves = new SimplexNoise(random);

        for (int z = -WALL_REACH; z <= WALL_REACH; z++)
        {
            for (int x = -WALL_REACH; x <= WALL_REACH; x++)
            {
                int strataShift = (int) Math.round(2 * strataWaves.get(x / 9D, z / 9D));
                dressColumn(x, z, soilPatches.get(x / 4D, z / 4D) > 0.3, bedPatches.get(x / 3.5, z / 3.5) > 0.2, strataShift);
            }
        }
        skinWalls();
    }

    private void dressColumn(int x, int z, boolean altSoil, boolean altBed, int strataShift)
    {
        int depth = Integer.MAX_VALUE;
        PocketSlot surface = PocketSlot.ROCK;

        for (int y = grid.top(); y >= grid.bottom(); y--)
        {
            var slot = grid.slot(x, y, z);
            if (slot.isOpen() || slot == PocketSlot.FROZEN_FLUID)
            {
                depth = 0;
                surface = surfaceUnder(slot, x, y, z, altSoil, altBed);
                continue;
            }
            if (depth != Integer.MAX_VALUE)
                depth++;

            boolean rock = slot == PocketSlot.ROCK;
            if (rock && depth == 1)
                grid.set(x, y, z, surface);
            else if (rock && depth == 2)
                grid.set(x, y, z, subsurfaceOf(surface));
            else if (rock && Math.floorMod(y + strataShift, STRATA_PERIOD) == 0)
                grid.set(x, y, z, PocketSlot.STRATA);

            if (rock && grid.slot(x, y - 1, z) == PocketSlot.AIR && y > grid.bottom())
                grid.set(x, y, z, PocketSlot.CEILING);
        }
    }

    /**
     * Soil only lies on the actual ground: a ledge on a wall is bare rock, or the walls read as scattered cubes of dirt.
     */
    private PocketSlot surfaceUnder(PocketSlot open, int x, int y, int z, boolean altSoil, boolean altBed)
    {
        if (open != PocketSlot.AIR)
            return altBed ? PocketSlot.BED_ALT : PocketSlot.BED;
        if (y - 1 > floor[column(x, z)] + GROUND_TOLERANCE)
            return PocketSlot.ROCK;
        if (profile.hasFluid() && y <= profile.fluidLevel + 1)
            return PocketSlot.SHORE;
        return altSoil ? PocketSlot.TOPSOIL_ALT : PocketSlot.TOPSOIL;
    }

    /**
     * A single material over every exposed wall, so that a cave reads as one surface rather than a checkerboard of what lies beneath.
     */
    private void skinWalls()
    {
        for (int y = grid.bottom(); y <= grid.top(); y++)
        {
            for (int z = -WALL_REACH; z <= WALL_REACH; z++)
            {
                for (int x = -WALL_REACH; x <= WALL_REACH; x++)
                {
                    var slot = grid.slot(x, y, z);
                    if ((slot == PocketSlot.ROCK || slot == PocketSlot.STRATA) && touchesOpenSide(x, y, z))
                        grid.set(x, y, z, PocketSlot.SKIN);
                }
            }
        }
    }

    private boolean touchesOpenSide(int x, int y, int z)
    {
        for (var side : Direction.Plane.HORIZONTAL)
        {
            if (grid.slot(x + side.getStepX(), y, z + side.getStepZ()).isOpen())
                return true;
        }
        return grid.slot(x, y + 1, z).isOpen();
    }

    private static PocketSlot subsurfaceOf(PocketSlot surface)
    {
        return switch (surface)
        {
            case TOPSOIL, TOPSOIL_ALT -> PocketSlot.SUBSOIL;
            case BED, BED_ALT, SHORE -> surface;
            default -> PocketSlot.ROCK;
        };
    }

    private void layPedestal()
    {
        for (int z = -1; z <= 1; z++)
        {
            for (int x = -1; x <= 1; x++)
                grid.set(x, -1, z, PocketSlot.PEDESTAL);
        }
    }

    private void sealWithWall()
    {
        for (int y = grid.bottom(); y <= grid.top(); y++)
        {
            for (int z = -WALL_REACH; z <= WALL_REACH; z++)
            {
                for (int x = -WALL_REACH; x <= WALL_REACH; x++)
                {
                    if (grid.isBoundary(x, y, z))
                        grid.set(x, y, z, PocketSlot.WALL);
                }
            }
        }
    }

    private static double smoothstep(double value)
    {
        double clamped = Mth.clamp(value, 0, 1);
        return clamped * clamped * (3 - 2 * clamped);
    }

    private static int column(int x, int z)
    {
        return (x + WALL_REACH) + (z + WALL_REACH) * COLUMNS;
    }
}
