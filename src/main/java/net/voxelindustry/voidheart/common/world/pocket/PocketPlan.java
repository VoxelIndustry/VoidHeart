package net.voxelindustry.voidheart.common.world.pocket;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.voxelindustry.voidheart.common.world.pocket.PocketCrystal.Size;
import net.voxelindustry.voidheart.common.world.pocket.PocketCrystal.Type;
import net.voxelindustry.voidheart.common.world.pocket.PocketDecor.Kind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static net.voxelindustry.voidheart.common.world.pocket.PocketGrid.INNER_REACH;

/**
 * Everything a pocket holds: its terrain, its crystals and what grows there. Pure and deterministic, like the terrain.
 */
public final class PocketPlan
{
    public static final int MINIMUM_CRYSTALS_PER_TYPE = 3;

    private static final double NICHE_DENSITY = 0.5;
    private static final double SCATTERED_DENSITY = 0.6;
    private static final int LIGHT_SPACING = 7;
    private static final int FILL_LIGHTS = 7;
    private static final int FILL_LIGHT_HEIGHTS = 6;
    private static final int FILL_LIGHT_LEVEL = 15;
    private static final int HEART_CLEAR_RADIUS = 1;
    private static final int HEART_CLEAR_HEIGHT = 3;
    private static final int TREE_MIN_HEART_DISTANCE = 4;
    private static final int TREE_HEADROOM = 5;
    private static final int TREE_SPACING = 5;
    private static final int RAY_LENGTH = 24;

    private final PocketVariant variant;
    private final PocketProfile profile;
    private final PocketGrid grid;
    private final RandomSource random;
    private final List<PocketCrystal> crystals = new ArrayList<>();
    private final List<PocketDecor> decor = new ArrayList<>();
    private final Set<Long> taken = new HashSet<>();

    private PocketPlan(PocketBlueprint blueprint)
    {
        variant = blueprint.variant();
        profile = variant.profile();
        grid = PocketTerrain.generate(blueprint);
        random = RandomSource.create(blueprint.seed() ^ 0x5DEECE66DL);

        growCrystals();
        plantTrees();
        placeLights();
        growDecor();
        placeSpring();
    }

    /**
     * The void has no daylight. Glowing rock on the ceiling gives the light a visible source, and invisible light blocks carry it down to the ground,
     * since a lamp on a smooth wall would read as something built rather than found.
     */
    private void placeLights()
    {
        var placed = new ArrayList<int[]>();
        for (int attempt = 0; attempt < 500 && placed.size() < profile.lights; attempt++)
        {
            int x = random.nextInt(INNER_REACH * 2 + 1) - INNER_REACH;
            int z = random.nextInt(INNER_REACH * 2 + 1) - INNER_REACH;
            int y = ceilingAbove(x, z);
            if (y != Integer.MIN_VALUE && isSpaced(placed, x, y, z))
            {
                placed.add(new int[] { x, y, z });
                decor.add(new PocketDecor(x, y, z, Kind.CEILING_LIGHT, 1));
            }
        }

        var fill = new ArrayList<int[]>();
        for (int attempt = 0; attempt < 500 && fill.size() < FILL_LIGHTS; attempt++)
        {
            int x = random.nextInt(INNER_REACH * 2 + 1) - INNER_REACH;
            int z = random.nextInt(INNER_REACH * 2 + 1) - INNER_REACH;
            int y = random.nextInt(FILL_LIGHT_HEIGHTS);
            if (isFree(x, y, z) && grid.slot(x, y, z) == PocketSlot.AIR && !isNearHeart(x, y, z) && isSpaced(fill, x, y, z))
            {
                fill.add(new int[] { x, y, z });
                add(x, y, z, Kind.FILL_LIGHT, FILL_LIGHT_LEVEL);
            }
        }
    }

    private static boolean isSpaced(List<int[]> placed, int x, int y, int z)
    {
        for (var other : placed)
        {
            if (Math.abs(other[0] - x) + Math.abs(other[1] - y) + Math.abs(other[2] - z) < LIGHT_SPACING)
                return false;
        }
        return true;
    }

    /**
     * The first rock cell above the open space of this column, or {@link Integer#MIN_VALUE} when the column has no open space.
     */
    private int ceilingAbove(int x, int z)
    {
        for (int y = grid.top() - 1; y > grid.bottom(); y--)
        {
            if (grid.slot(x, y, z).isAnchor() && grid.slot(x, y - 1, z) == PocketSlot.AIR)
                return y;
        }
        return Integer.MIN_VALUE;
    }

    public static PocketPlan generate(PocketBlueprint blueprint)
    {
        return new PocketPlan(blueprint);
    }

    public PocketGrid grid()
    {
        return grid;
    }

    public List<PocketCrystal> crystals()
    {
        return crystals;
    }

    public List<PocketDecor> decor()
    {
        return decor;
    }

    private void growCrystals()
    {
        var niches = grid.niches();
        for (int i = 0; i < niches.size(); i++)
        {
            var niche = niches.get(i);
            var type = switch (i)
            {
                case 0 -> Type.AZURIUM;
                case 1 -> Type.RUBRUM;
                default -> randomType();
            };
            growCluster(niche.x(), niche.y(), niche.z(), niche.radius() + 1, type, NICHE_DENSITY);
        }

        for (int i = 0; i < profile.crystalClusters; i++)
        {
            var hit = castToSurface();
            if (hit != null)
                growCluster(hit[0], hit[1], hit[2], 1 + random.nextInt(2), randomType(), SCATTERED_DENSITY);
        }

        ensureMinimum(Type.AZURIUM);
        ensureMinimum(Type.RUBRUM);
    }

    private Type randomType()
    {
        return random.nextDouble() < profile.rubrumShare ? Type.RUBRUM : Type.AZURIUM;
    }

    private void ensureMinimum(Type type)
    {
        for (int attempt = 0; attempt < 200 && count(type) < MINIMUM_CRYSTALS_PER_TYPE; attempt++)
        {
            var hit = castToSurface();
            if (hit != null)
                growCluster(hit[0], hit[1], hit[2], 2, type, 1);
        }
    }

    private long count(Type type)
    {
        return crystals.stream().filter(crystal -> crystal.type() == type).count();
    }

    private int[] castToSurface()
    {
        for (int attempt = 0; attempt < 30; attempt++)
        {
            int x = random.nextInt(INNER_REACH * 2 + 1) - INNER_REACH;
            int y = grid.bottom() + 2 + random.nextInt(INNER_REACH * 2 + 1);
            int z = random.nextInt(INNER_REACH * 2 + 1) - INNER_REACH;
            if (!isFree(x, y, z))
                continue;

            double yaw = random.nextDouble() * Math.PI * 2;
            double pitch = (random.nextDouble() - 0.5) * Math.PI;
            double stepX = Math.cos(yaw) * Math.cos(pitch), stepY = Math.sin(pitch), stepZ = Math.sin(yaw) * Math.cos(pitch);

            int lastX = x, lastY = y, lastZ = z;
            for (int step = 1; step < RAY_LENGTH; step++)
            {
                int cellX = (int) Math.round(x + stepX * step);
                int cellY = (int) Math.round(y + stepY * step);
                int cellZ = (int) Math.round(z + stepZ * step);
                var slot = grid.slot(cellX, cellY, cellZ);
                if (!slot.isOpen())
                {
                    if (slot.isAnchor() && !isOpenGround(lastX, lastY, lastZ))
                        return new int[] { lastX, lastY, lastZ };
                    break;
                }
                lastX = cellX;
                lastY = cellY;
                lastZ = cellZ;
            }
        }
        return null;
    }

    /**
     * Keeps meadows and shores clean, so that crystals feel found on rock rather than strewn over the ground.
     */
    private boolean isOpenGround(int x, int y, int z)
    {
        var under = grid.slot(x, y - 1, z);
        return under == PocketSlot.TOPSOIL || under == PocketSlot.TOPSOIL_ALT || under == PocketSlot.SHORE || under == PocketSlot.PEDESTAL;
    }

    private void growCluster(int centerX, int centerY, int centerZ, int radius, Type type, double density)
    {
        for (int y = centerY - radius; y <= centerY + radius; y++)
        {
            for (int z = centerZ - radius; z <= centerZ + radius; z++)
            {
                for (int x = centerX - radius; x <= centerX + radius; x++)
                {
                    double distance = Math.sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY) + (z - centerZ) * (z - centerZ));
                    if (distance > radius || !isFree(x, y, z) || isNearHeart(x, y, z))
                        continue;
                    if (variant == PocketVariant.EMBER_DEEP && grid.slot(x, y, z) == PocketSlot.FLUID)
                        continue;

                    double closeness = 1 - distance / (radius + 1);
                    if (random.nextDouble() > density * closeness)
                        continue;

                    var facing = faceFromRock(x, y, z);
                    if (facing == null)
                        continue;

                    double growth = random.nextDouble() + closeness * 0.25;
                    var size = growth > 0.98 ? Size.LARGE : growth > 0.62 ? Size.MEDIUM : Size.SMALL;
                    crystals.add(new PocketCrystal(x, y, z, type, size, facing));
                    take(x, y, z);
                }
            }
        }
    }

    private Direction faceFromRock(int x, int y, int z)
    {
        var directions = Direction.values();
        int start = random.nextInt(directions.length);
        for (int i = 0; i < directions.length; i++)
        {
            var towardRock = directions[(start + i) % directions.length];
            if (grid.slot(x + towardRock.getStepX(), y + towardRock.getStepY(), z + towardRock.getStepZ()).isAnchor())
                return towardRock.getOpposite();
        }
        return null;
    }

    private void plantTrees()
    {
        var sites = new ArrayList<int[]>();
        for (int attempt = 0; attempt < 400 && sites.size() < profile.trees; attempt++)
        {
            int x = random.nextInt(INNER_REACH * 2 + 1) - INNER_REACH;
            int z = random.nextInt(INNER_REACH * 2 + 1) - INNER_REACH;
            if (Math.hypot(x, z) < TREE_MIN_HEART_DISTANCE)
                continue;

            int y = groundAt(x, z);
            if (y == Integer.MIN_VALUE || !isFree(x, y, z) || !hasHeadroom(x, y, z))
                continue;

            boolean crowded = false;
            for (var site : sites)
                crowded |= Math.abs(site[0] - x) + Math.abs(site[1] - z) < TREE_SPACING;
            if (crowded)
                continue;

            sites.add(new int[] { x, z });
            add(x, y, z, Kind.TREE, 1);
        }
    }

    /**
     * The first air cell above soil in this column, or {@link Integer#MIN_VALUE} when there is none.
     */
    private int groundAt(int x, int z)
    {
        for (int y = grid.bottom() + 1; y < grid.top(); y++)
        {
            var under = grid.slot(x, y - 1, z);
            if (grid.slot(x, y, z) == PocketSlot.AIR && (under == PocketSlot.TOPSOIL || under == PocketSlot.TOPSOIL_ALT))
                return y;
        }
        return Integer.MIN_VALUE;
    }

    private boolean hasHeadroom(int x, int y, int z)
    {
        for (int up = 0; up < TREE_HEADROOM; up++)
        {
            if (grid.slot(x, y + up, z) != PocketSlot.AIR)
                return false;
        }
        return true;
    }

    private void growDecor()
    {
        var patches = new SimplexNoise(random);
        for (int y = grid.bottom(); y <= grid.top(); y++)
        {
            for (int z = -INNER_REACH; z <= INNER_REACH; z++)
            {
                for (int x = -INNER_REACH; x <= INNER_REACH; x++)
                {
                    var slot = grid.slot(x, y, z);
                    if (!slot.isOpen() || !isFree(x, y, z) || isNearHeart(x, y, z))
                        continue;

                    double patch = patches.get(x / 5D, z / 5D);
                    var under = grid.slot(x, y - 1, z);
                    var over = grid.slot(x, y + 1, z);

                    if (slot == PocketSlot.FLUID)
                    {
                        if (under.isAnchor())
                            growOnBed(x, y, z, patch);
                    }
                    else if (under.isAnchor())
                        growOnGround(x, y, z, under, patch);
                    else if (under == PocketSlot.FLUID)
                        growOnWater(x, y, z);
                    else if (over.isAnchor())
                        growFromCeiling(x, y, z, over);
                    else
                        growOnWall(x, y, z);
                }
            }
        }
    }

    private void growOnBed(int x, int y, int z, double patch)
    {
        if (variant == PocketVariant.FROZEN_LAKE || variant.ambience() == PocketAmbience.STALACTITES)
            return;

        double roll = random.nextDouble();
        double lush = Math.max(0, patch) * 0.2;
        if (variant == PocketVariant.LAGOON && roll < 0.25)
            add(x, y, z, Kind.CORAL, 1);
        else if (roll < 0.45 + lush)
            add(x, y, z, Kind.SEAGRASS, 1);
        else if (roll < 0.57 + lush)
            add(x, y, z, Kind.KELP, 1 + random.nextInt(Math.max(1, waterAbove(x, y, z))));
        else if (roll < 0.62 + lush)
            add(x, y, z, Kind.SEA_PICKLE, 1 + random.nextInt(4));
    }

    private int waterAbove(int x, int y, int z)
    {
        int depth = 0;
        while (grid.slot(x, y + depth + 1, z) == PocketSlot.FLUID)
            depth++;
        return depth;
    }

    private void growOnGround(int x, int y, int z, PocketSlot under, double patch)
    {
        double roll = random.nextDouble();
        switch (variant.ambience())
        {
            case LUSH ->
            {
                if (under == PocketSlot.TOPSOIL || under == PocketSlot.TOPSOIL_ALT)
                    growUnderstory(x, y, z, roll, patch);
            }
            case LAKE ->
            {
                if (variant != PocketVariant.FROZEN_LAKE && (under == PocketSlot.TOPSOIL || under == PocketSlot.TOPSOIL_ALT))
                {
                    if (roll < 0.4)
                        add(x, y, z, Kind.SMALL_PLANT, 1);
                    else if (roll < 0.5)
                        add(x, y, z, Kind.TALL_PLANT, 1);
                }
            }
            case STALACTITES ->
            {
                if (roll < 0.12)
                    add(x, y, z, Kind.STALAGMITE, 1 + random.nextInt(3));
                else if (variant == PocketVariant.EMBER_DEEP && roll < 0.2)
                    add(x, y, z, Kind.EMBER_VENT, 1);
            }
        }
    }

    private void growUnderstory(int x, int y, int z, double roll, double patch)
    {
        double lush = 0.8 + patch * 0.2;
        if (variant == PocketVariant.AZALEA_GARDEN)
        {
            if (roll < 0.06)
                add(x, y, z, Kind.BUSH, 1);
            else if (roll < 0.11)
                add(x, y, z, Kind.DRIPLEAF, 1);
            else if (roll < 0.11 + lush * 0.4)
                add(x, y, z, Kind.CARPET, 1);
            else if (roll < 0.11 + lush * 0.6)
                add(x, y, z, Kind.SMALL_PLANT, 1);
            return;
        }

        if (roll < lush * 0.4)
            add(x, y, z, Kind.SMALL_PLANT, 1);
        else if (roll < lush * 0.55)
            add(x, y, z, Kind.TALL_PLANT, 1);
        else if (roll < lush * 0.78)
            add(x, y, z, Kind.FLOWER, 1);
        else if (roll < lush * 0.84)
            add(x, y, z, Kind.TALL_FLOWER, 1);
        else if (roll < lush * 0.9)
            add(x, y, z, Kind.BUSH, 1);
    }

    private void growOnWater(int x, int y, int z)
    {
        if (random.nextDouble() < lilyChance())
            add(x, y, z, Kind.LILY_PAD, 1);
    }

    private double lilyChance()
    {
        return switch (variant)
        {
            case MANGROVE_MARSH -> 0.2;
            case AZALEA_GARDEN, BLOOM_FOREST, JUNGLE -> 0.25;
            case LAGOON -> 0.04;
            default -> 0;
        };
    }

    private void growFromCeiling(int x, int y, int z, PocketSlot over)
    {
        double roll = random.nextDouble();
        int clearance = airBelow(x, y, z);
        switch (variant.ambience())
        {
            case LUSH ->
            {
                if (roll < 0.14)
                    add(x, y, z, Kind.CAVE_VINE, 1 + random.nextInt(Math.max(1, Math.min(clearance - 2, 6))));
                else if (roll < 0.22)
                    add(x, y, z, Kind.HANGING_ROOTS, 1);
                else if (roll < 0.235)
                    add(x, y, z, Kind.SPORE_BLOSSOM, 1);
                else if (roll < 0.3 && !hasBrightRock())
                    add(x, y, z, Kind.GLOW_LICHEN, Direction.UP.ordinal());
            }
            case LAKE ->
            {
                if (roll < 0.22 && !hasBrightRock())
                    add(x, y, z, Kind.GLOW_LICHEN, Direction.UP.ordinal());
                else if (variant != PocketVariant.FROZEN_LAKE && roll < 0.29)
                    add(x, y, z, Kind.STALACTITE, 1 + random.nextInt(Math.max(1, Math.min(clearance - 1, 3))));
            }
            case STALACTITES ->
            {
                double spikes = over == PocketSlot.CONE ? 0.45 : 0.22;
                if (roll < spikes)
                    add(x, y, z, Kind.STALACTITE, 1 + random.nextInt(Math.max(1, Math.min(clearance - 1, 4))));
                else if (roll < spikes + 0.1 && !hasBrightRock())
                    add(x, y, z, Kind.GLOW_LICHEN, Direction.UP.ordinal());
            }
        }
    }

    private int airBelow(int x, int y, int z)
    {
        int depth = 0;
        while (depth < 15 && grid.slot(x, y - depth - 1, z) == PocketSlot.AIR)
            depth++;
        return depth;
    }

    /**
     * Glow lichen reads as stains of mould against pale rock.
     */
    private boolean hasBrightRock()
    {
        return variant == PocketVariant.LAGOON || variant == PocketVariant.CHERRY_GROVE || variant == PocketVariant.PALE_CATHEDRAL;
    }

    private void growOnWall(int x, int y, int z)
    {
        double chance = variant.ambience() == PocketAmbience.LUSH ? 0.015 : 0.025;
        if (hasBrightRock() || random.nextDouble() > chance)
            return;

        for (var side : Direction.Plane.HORIZONTAL)
        {
            if (grid.slot(x + side.getStepX(), y, z + side.getStepZ()).isAnchor())
            {
                add(x, y, z, Kind.GLOW_LICHEN, side.ordinal());
                return;
            }
        }
    }

    /**
     * A source set in the rock face above still water. It only falls into the water, so it never floods what grows around it.
     */
    private void placeSpring()
    {
        if (variant == PocketVariant.EMBER_DEEP || variant == PocketVariant.FROZEN_LAKE || !profile.hasFluid())
            return;

        for (int attempt = 0; attempt < 300; attempt++)
        {
            int x = random.nextInt(INNER_REACH * 2 + 1) - INNER_REACH;
            int y = profile.fluidLevel + 3 + random.nextInt(6);
            int z = random.nextInt(INNER_REACH * 2 + 1) - INNER_REACH;
            if (isSpringSite(x, y, z))
            {
                add(x, y, z, Kind.SPRING, 1);
                return;
            }
        }
    }

    private boolean isSpringSite(int x, int y, int z)
    {
        if (!grid.slot(x, y, z).isAnchor() || !grid.slot(x, y + 1, z).isAnchor() || !grid.slot(x, y - 1, z).isAnchor())
            return false;

        Direction open = null;
        for (var side : Direction.Plane.HORIZONTAL)
        {
            var neighbour = grid.slot(x + side.getStepX(), y, z + side.getStepZ());
            if (neighbour == PocketSlot.AIR)
            {
                if (open != null)
                    return false;
                open = side;
            }
            else if (!neighbour.isAnchor())
                return false;
        }
        return open != null && fallsIntoFluid(x + open.getStepX(), y, z + open.getStepZ());
    }

    private boolean fallsIntoFluid(int x, int y, int z)
    {
        int fall = y - 1;
        while (grid.slot(x, fall, z) == PocketSlot.AIR && !taken.contains(key(x, fall, z)))
            fall--;
        return grid.slot(x, fall, z) == PocketSlot.FLUID && !isNearHeart(x, y, z);
    }

    private boolean isFree(int x, int y, int z)
    {
        return grid.slot(x, y, z).isOpen() && !taken.contains(key(x, y, z));
    }

    private static boolean isNearHeart(int x, int y, int z)
    {
        return Math.abs(x) <= HEART_CLEAR_RADIUS && Math.abs(z) <= HEART_CLEAR_RADIUS && y >= 0 && y <= HEART_CLEAR_HEIGHT;
    }

    private void add(int x, int y, int z, Kind kind, int amount)
    {
        decor.add(new PocketDecor(x, y, z, kind, amount));
        take(x, y, z);
    }

    private void take(int x, int y, int z)
    {
        taken.add(key(x, y, z));
    }

    private static long key(int x, int y, int z)
    {
        return x + 100L + (z + 100L) * 1000L + (y + 100L) * 1000000L;
    }
}
