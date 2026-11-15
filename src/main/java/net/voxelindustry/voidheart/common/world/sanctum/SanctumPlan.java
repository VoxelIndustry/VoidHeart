package net.voxelindustry.voidheart.common.world.sanctum;

import net.minecraft.core.Direction;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusHash;
import net.voxelindustry.voidheart.common.world.underway.UnderwayProp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static net.voxelindustry.voidheart.common.world.sanctum.SanctumMaterial.*;

/**
 * A temple of the old world, columned and coffered in voidstone, that the flesh has taken from its far end. One comes in through a porch, then
 * a great doorway into a nave between two colonnades, up to a raised sanctuary where the flesh is thickest and the mass sits, before an apse.
 * <p>
 * Places are counted from the sanctum's origin: {@code x} across, {@code z} from the porch to the apse, {@code y} up. Everything comes from the
 * seed alone.
 */
public final class SanctumPlan
{
    public static final int WIDTH  = 41;
    public static final int LENGTH = 62;
    public static final int HEIGHT = 26;
    public static final int AXIS   = WIDTH / 2;

    /** The lowest layer of air, over the floor. */
    public static final int FLOOR_Y = 2;

    static final int PORCH_X0 = 8, PORCH_X1 = 32, PORCH_Z0 = 2, PORCH_Z1 = 10, PORCH_TOP = 14;
    static final int DOOR_X0  = 16, DOOR_X1 = 24, DOOR_TOP = 10, DOOR_Z0 = 11, DOOR_Z1 = 12;
    static final int NAOS_X0  = 3, NAOS_X1 = 37, NAOS_Z0 = 13, NAOS_Z1 = 57, AISLE_TOP = 13;
    static final int NAVE_X0  = 12, NAVE_X1 = 28, NAVE_TOP = 21;
    static final int[] COLONNADES = { 10, 30 };
    static final int[] COLUMN_ROWS = { 17, 23, 29, 35, 41 };
    static final int[] PORCH_COLUMNS = { 12, 28 };
    static final int PORCH_COLUMN_Z = 6;
    /** The step up to the sanctuary, then the sanctuary itself a step higher. */
    static final int STEP_Z = 45, SANCTUARY_Z = 46;
    static final int APSE_X0 = 15, APSE_X1 = 25, APSE_INNER_X0 = 17, APSE_INNER_X1 = 23, APSE_TOP = 16, APSE_INNER_TOP = 14;

    /** Where the mass sits, and the pillar rises once it is dead: on the sanctuary, in its middle. */
    public static final int MASS_X = AXIS, MASS_Y = FLOOR_Y + 2, MASS_Z = 51;

    /** The way back, a door of ink in the porch's south wall, three wide and four tall, its face looking into the sanctum. */
    public static final int GATE_X0 = AXIS - 1, GATE_COLUMNS = 3, GATE_ROWS = 4, GATE_Z = 1;
    public static final Direction GATE_FACING = Direction.SOUTH;
    /** Where one comes in, in front of the gate. */
    public static final int ARRIVAL_X = AXIS, ARRIVAL_Z = 3;

    private static final double FLESH_REACH   = 27;
    private static final double FLESH_NOISE   = 0.32;
    private static final double FLESH_FLOOR   = 0.22;
    private static final double FLESH_WALL    = 0.36;
    private static final double FLESH_CEILING = 0.46;
    private static final double FLESH_COLUMN  = 0.66;
    private static final int    PERCENT       = 100;

    public enum PostKind
    {
        MASS,
        SHOGGOTH
    }

    public record Prop(int x, int y, int z, UnderwayProp prop, Direction facing)
    {
    }

    public record Post(PostKind kind, int x, int y, int z, float yaw)
    {
    }

    /** Where the lesser shoggoths lurk, before a seeded nudge: down the aisles and the nave, none in the porch. */
    private static final int[][] SHOGGOTH_POSTS = {
            { 6, 20 }, { 34, 21 }, { 6, 32 }, { 34, 33 }, { 16, 26 }, { 25, 30 }, { 15, 38 }, { 26, 40 }
    };
    /** Braziers in the porch's corners, by every other column in the aisles, and at the sanctuary's corners. */
    private static final int[][] BRAZIERS = {
            { 9, FLOOR_Y, 3 }, { 31, FLOOR_Y, 3 }, { 9, FLOOR_Y, 9 }, { 31, FLOOR_Y, 9 },
            { 7, FLOOR_Y, 17 }, { 33, FLOOR_Y, 17 }, { 7, FLOOR_Y, 29 }, { 33, FLOOR_Y, 29 }, { 7, FLOOR_Y, 41 }, { 33, FLOOR_Y, 41 },
            { 13, FLOOR_Y + 2, 47 }, { 27, FLOOR_Y + 2, 47 }, { 13, FLOOR_Y + 2, 56 }, { 27, FLOOR_Y + 2, 56 }
    };

    private final long seed;
    private final byte[] cells = new byte[WIDTH * LENGTH * HEIGHT];
    private final List<Prop> props = new ArrayList<>();
    private final List<Post> posts = new ArrayList<>();
    private final Set<Long> taken = new HashSet<>();

    private SanctumPlan(long seed)
    {
        this.seed = seed;
    }

    public static SanctumPlan generate(long seed)
    {
        var plan = new SanctumPlan(seed);
        plan.build();
        return plan;
    }

    public SanctumMaterial material(int x, int y, int z)
    {
        if (!inside(x, y, z))
            return AIR;
        return SanctumMaterial.of(cells[index(x, y, z)]);
    }

    public List<Prop> props()
    {
        return props;
    }

    public List<Post> posts()
    {
        return posts;
    }

    public static boolean inside(int x, int y, int z)
    {
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT && z >= 0 && z < LENGTH;
    }

    /** @return whether a place of the gate, given across it from the west and down from its top */
    public static boolean inGate(int x, int y, int z)
    {
        return z == GATE_Z && x >= GATE_X0 && x < GATE_X0 + GATE_COLUMNS && y >= FLOOR_Y && y < FLOOR_Y + GATE_ROWS;
    }

    private static int index(int x, int y, int z)
    {
        return (y * LENGTH + z) * WIDTH + x;
    }

    private void set(int x, int y, int z, SanctumMaterial material)
    {
        if (inside(x, y, z))
            cells[index(x, y, z)] = (byte) material.ordinal();
    }

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1, SanctumMaterial material)
    {
        for (int y = y0; y <= y1; y++)
            for (int z = z0; z <= z1; z++)
                for (int x = x0; x <= x1; x++)
                    set(x, y, z, material);
    }

    private void build()
    {
        fill(0, 0, 0, WIDTH - 1, HEIGHT - 1, LENGTH - 1, WALL);
        fill(0, 0, 0, WIDTH - 1, FLOOR_Y - 2, LENGTH - 1, FOUNDATION);

        carve();
        floors();
        for (int x : COLONNADES)
            for (int z : COLUMN_ROWS)
                column(x, z, FLOOR_Y, AISLE_TOP - 1);
        for (int x : PORCH_COLUMNS)
            column(x, PORCH_COLUMN_Z, FLOOR_Y, PORCH_TOP);
        entablature();
        coffers(NAVE_X0, NAVE_X1, NAOS_Z0, NAOS_Z1, NAVE_TOP + 1);
        coffers(PORCH_X0, PORCH_X1, PORCH_Z0, PORCH_Z1, PORCH_TOP + 1);
        dress();
        gate();
        flesh();
        placePosts();
        coils();
        tendrils();
        floorProps();
    }

    private void carve()
    {
        fill(PORCH_X0, FLOOR_Y, PORCH_Z0, PORCH_X1, PORCH_TOP, PORCH_Z1, AIR);
        fill(DOOR_X0, FLOOR_Y, DOOR_Z0, DOOR_X1, DOOR_TOP, DOOR_Z1, AIR);
        fill(NAOS_X0, FLOOR_Y, NAOS_Z0, NAOS_X1, AISLE_TOP, NAOS_Z1, AIR);
        fill(NAVE_X0, FLOOR_Y, NAOS_Z0, NAVE_X1, NAVE_TOP, NAOS_Z1, AIR);
        fill(APSE_X0, FLOOR_Y + 2, NAOS_Z1 + 1, APSE_X1, APSE_TOP, NAOS_Z1 + 1, AIR);
        fill(APSE_INNER_X0, FLOOR_Y + 2, NAOS_Z1 + 2, APSE_INNER_X1, APSE_INNER_TOP, NAOS_Z1 + 2, AIR);
    }

    private void floors()
    {
        for (int z = 0; z < LENGTH; z++)
        {
            for (int x = 0; x < WIDTH; x++)
            {
                if (material(x, FLOOR_Y, z) != AIR)
                    continue;
                boolean axis = x == AXIS && z >= NAOS_Z0 && z < STEP_Z;
                boolean border = z >= NAOS_Z0 && z < STEP_Z && (x == NAVE_X0 + 1 || x == NAVE_X1 - 1);
                set(x, FLOOR_Y - 1, z, axis ? GILT : border ? INLAY : FLOOR);
            }
        }
        // A step up across the whole width, then the sanctuary a step higher, running on into the apse
        for (int x = NAOS_X0; x <= NAOS_X1; x++)
        {
            set(x, FLOOR_Y, STEP_Z, FLOOR);
            for (int z = SANCTUARY_Z; z <= NAOS_Z1 + 2; z++)
            {
                if (material(x, FLOOR_Y + 2, z) == AIR || z <= NAOS_Z1)
                {
                    set(x, FLOOR_Y, z, PANEL);
                    set(x, FLOOR_Y + 1, z, x == AXIS || Math.abs(x - AXIS) == 4 ? INLAY : FLOOR);
                }
            }
        }
    }

    /**
     * A column as the old world raised them: a square plinth, a shaft rounded off by its corners, and a capital as wide as the plinth.
     */
    private void column(int cx, int cz, int plinth, int capital)
    {
        fill(cx - 1, plinth, cz - 1, cx + 1, plinth, cz + 1, PLINTH);
        for (int y = plinth + 1; y < capital; y++)
        {
            set(cx, y, cz, COLUMN);
            for (var side : Direction.Plane.HORIZONTAL)
                set(cx + side.getStepX(), y, cz + side.getStepZ(), COLUMN);
        }
        fill(cx - 1, capital, cz - 1, cx + 1, capital, cz + 1, CAPITAL);
    }

    /** Beams over the colonnades carry the walls of the nave, and a cornice runs round under every ceiling. */
    private void entablature()
    {
        for (int x : COLONNADES)
            fill(x - 1, AISLE_TOP, NAOS_Z0, x + 1, AISLE_TOP, STEP_Z - 1, TRIM);
        for (int y : new int[] { AISLE_TOP, NAVE_TOP, PORCH_TOP })
        {
            for (int z = 0; z < LENGTH; z++)
            {
                for (int x = 0; x < WIDTH; x++)
                {
                    if (material(x, y, z) == WALL && touchesAir(x, y, z))
                        set(x, y, z, TRIM);
                }
            }
        }
    }

    private void coffers(int x0, int x1, int z0, int z1, int ceiling)
    {
        for (int z = z0; z <= z1; z++)
        {
            for (int x = x0; x <= x1; x++)
            {
                boolean sunk = Math.floorMod(x - x0, 4) != 0 && Math.floorMod(z - z0, 4) != 0;
                if (sunk && x > x0 && x < x1 && z > z0 && z < z1)
                {
                    set(x, ceiling, z, AIR);
                    set(x, ceiling + 1, z, COFFER);
                }
                else
                    set(x, ceiling, z, CEILING);
            }
        }
    }

    /** Pilasters on the side walls behind every column, panels round the doorway with a gilt lintel, and the odd cracked stone. */
    private void dress()
    {
        for (int z : COLUMN_ROWS)
        {
            for (int y = FLOOR_Y; y < AISLE_TOP; y++)
            {
                set(NAOS_X0 - 1, y, z, PANEL);
                set(NAOS_X1 + 1, y, z, PANEL);
            }
        }
        for (int z = DOOR_Z0; z <= DOOR_Z1; z++)
        {
            for (int y = FLOOR_Y; y <= DOOR_TOP; y++)
            {
                set(DOOR_X0 - 1, y, z, PANEL);
                set(DOOR_X1 + 1, y, z, PANEL);
            }
            fill(DOOR_X0 - 1, DOOR_TOP + 1, z, DOOR_X1 + 1, DOOR_TOP + 1, z, GILT);
        }
        for (int y = 0; y < HEIGHT; y++)
        {
            for (int z = 0; z < LENGTH; z++)
            {
                for (int x = 0; x < WIDTH; x++)
                {
                    if (material(x, y, z) == WALL && touchesAir(x, y, z) && DaedalusHash.pick(hash(x, y, z, 0x43524143L), PERCENT) < 9)
                        set(x, y, z, WALL_CRACKED);
                }
            }
        }
    }

    private void gate()
    {
        for (int y = FLOOR_Y; y <= FLOOR_Y + GATE_ROWS; y++)
        {
            set(GATE_X0 - 1, y, GATE_Z, GATE_FRAME);
            set(GATE_X0 + GATE_COLUMNS, y, GATE_Z, GATE_FRAME);
        }
        fill(GATE_X0, FLOOR_Y + GATE_ROWS, GATE_Z, GATE_X0 + GATE_COLUMNS - 1, FLOOR_Y + GATE_ROWS, GATE_Z, GATE_FRAME);
        fill(GATE_X0, FLOOR_Y, GATE_Z, GATE_X0 + GATE_COLUMNS - 1, FLOOR_Y + GATE_ROWS - 1, GATE_Z, WALL);
    }

    /**
     * How deep the flesh lies at a place, from nothing to 1 and more: thickest round the mass, thinning toward the porch in uneven tongues, and
     * a little less the higher up.
     */
    public double flesh(int x, int y, int z)
    {
        double dx = x - MASS_X, dz = z - MASS_Z;
        double reach = 1 - Math.sqrt(dx * dx + dz * dz) / FLESH_REACH;
        return reach + FLESH_NOISE * noise(x / 4.5, y / 6D, z / 4.5) - Math.max(0, y - FLOOR_Y) * 0.025;
    }

    private void flesh()
    {
        var next = cells.clone();
        for (int y = 0; y < HEIGHT; y++)
        {
            for (int z = 0; z < LENGTH; z++)
            {
                for (int x = 0; x < WIDTH; x++)
                {
                    var material = material(x, y, z);
                    if (material == AIR || material == FOUNDATION || inGate(x, y, z) || z <= GATE_Z + 1 || !touchesAir(x, y, z))
                        continue;
                    double depth = flesh(x, y, z);
                    boolean floor = material(x, y + 1, z) == AIR;
                    boolean ceiling = material(x, y - 1, z) == AIR;
                    SanctumMaterial to = null;
                    if (material.architectural())
                    {
                        if (depth > FLESH_COLUMN && material == COLUMN)
                            to = WRITHING;
                    }
                    else if (floor && depth > FLESH_FLOOR)
                        to = FLESH;
                    else if (ceiling && depth > FLESH_CEILING)
                        to = FLESH;
                    else if (!floor && !ceiling && depth > FLESH_WALL)
                    {
                        int roll = DaedalusHash.pick(hash(x, y, z, 0x5752495448L), PERCENT);
                        to = roll < 7 ? WRITHING_MAW : roll < 14 ? WRITHING_BLOSSOM : depth > FLESH_WALL + 0.25 ? FLESH : WRITHING;
                    }
                    // Bruised patches a few blocks across break up the flesh, most where it is deepest
                    if (to == FLESH && noise(x / 3.5 + 40, y / 3.5, z / 3.5) > 0.25 - depth * 0.15)
                        to = FLESH_BRUISED;
                    if (to != null)
                        next[index(x, y, z)] = (byte) to.ordinal();
                }
            }
        }
        System.arraycopy(next, 0, cells, 0, cells.length);
    }

    private void placePosts()
    {
        posts.add(new Post(PostKind.MASS, MASS_X, MASS_Y, MASS_Z, 180));
        for (int i = 0; i < SHOGGOTH_POSTS.length; i++)
        {
            long hash = hash(i, 0, 0, 0x53484F47L);
            int x = SHOGGOTH_POSTS[i][0] + DaedalusHash.pick(hash, 3) - 1;
            int z = SHOGGOTH_POSTS[i][1] + DaedalusHash.pick(DaedalusHash.mix(hash), 3) - 1;
            posts.add(new Post(PostKind.SHOGGOTH, x, standY(x, z), z, DaedalusHash.pick(DaedalusHash.mix(hash + 1), 360)));
            claim(x, standY(x, z), z);
        }
        // Nothing grows where the mass sits, nor where the pillar will rise
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                for (int dy = 0; dy < 4; dy++)
                    claim(MASS_X + dx, MASS_Y + dy, MASS_Z + dz);
        // Nor in the way in
        for (int z = 0; z <= ARRIVAL_Z + 2; z++)
            for (int x = GATE_X0 - 1; x <= GATE_X0 + GATE_COLUMNS; x++)
                for (int y = FLOOR_Y; y < FLOOR_Y + GATE_ROWS; y++)
                    claim(x, y, z);
        for (int[] brazier : BRAZIERS)
        {
            props.add(new Prop(brazier[0], brazier[1], brazier[2], UnderwayProp.BRAZIER_LIT, Direction.SOUTH));
            claim(brazier[0], brazier[1], brazier[2]);
        }
    }

    /** @return the lowest air over the floor at a place of the nave or the sanctuary */
    public int standY(int x, int z)
    {
        for (int y = FLOOR_Y; y < HEIGHT; y++)
        {
            if (material(x, y, z) == AIR)
                return y;
        }
        return FLOOR_Y;
    }

    /**
     * Tentacles climb the columns the flesh has reached, and some it has not yet: one round a shaft from a seeded side, rising a block for every
     * face it crosses, till the capital stops it.
     */
    private void coils()
    {
        var columns = new ArrayList<int[]>();
        for (int x : COLONNADES)
            for (int z : COLUMN_ROWS)
                columns.add(new int[] { x, z, AISLE_TOP - 1 });
        for (int x : PORCH_COLUMNS)
            columns.add(new int[] { x, PORCH_COLUMN_Z, PORCH_TOP });

        for (var column : columns)
        {
            int cx = column[0], cz = column[1], capital = column[2];
            long hash = hash(cx, 0, cz, 0x434F494CL);
            double depth = flesh(cx, FLOOR_Y, cz);
            if (depth < 0 && DaedalusHash.pick(hash, PERCENT) >= 35)
                continue;
            int start = DaedalusHash.pick(DaedalusHash.mix(hash), 4) * 2;
            int level = FLOOR_Y + 1 + DaedalusHash.pick(DaedalusHash.mix(hash + 1), 2);
            for (int step = 0; level < capital; step++)
            {
                int k = (start + step) % 8;
                boolean face = k % 2 == 0;
                if (!face)
                    level++;
                if (level >= capital)
                    break;
                // Faces then corners, round from the north the way the sun goes
                int[][] ring = { { 0, -2 }, { 1, -1 }, { 2, 0 }, { 1, 1 }, { 0, 2 }, { -1, 1 }, { -2, 0 }, { -1, -1 } };
                Direction facing = Direction.from2DDataValue((k / 2 + 2) % 4);
                int x = cx + ring[k][0], z = cz + ring[k][1];
                if (material(x, level, z) == AIR && !taken(x, level, z))
                {
                    props.add(new Prop(x, level, z, face ? UnderwayProp.TENDRIL_COIL : UnderwayProp.TENDRIL_COIL_CORNER, facing));
                    claim(x, level, z);
                }
            }
        }
    }

    /** Tentacles hang from the ceilings, thicker and longer toward the mass, never low enough to brush a head. */
    private void tendrils()
    {
        for (int z = NAOS_Z0; z <= NAOS_Z1; z++)
        {
            for (int x = NAOS_X0; x <= NAOS_X1; x++)
            {
                int top = -1;
                for (int y = HEIGHT - 2; y > FLOOR_Y; y--)
                {
                    if (material(x, y, z) == AIR && material(x, y + 1, z).solid())
                    {
                        top = y;
                        break;
                    }
                }
                if (top < 0)
                    continue;
                int floor = standY(x, z);
                int room = top - floor - 3;
                if (room < 1)
                    continue;
                double depth = Math.clamp(flesh(x, top, z) + 0.3, 0, 1);
                long hash = hash(x, top, z, 0x54454E44L);
                if (DaedalusHash.pick(hash, 1000) >= 30 + depth * 170 || near(x, top, z, 1))
                    continue;
                int length = 1 + DaedalusHash.pick(DaedalusHash.mix(hash), Math.min(room, 1 + (int) (depth * 6)));
                for (int i = 0; i < length; i++)
                {
                    var prop = i == length - 1 ? UnderwayProp.TENDRIL_TIP : i == 0 ? UnderwayProp.TENDRIL_ROOT : UnderwayProp.TENDRIL_HANG;
                    props.add(new Prop(x, top - i, z, prop, Direction.from2DDataValue(DaedalusHash.pick(DaedalusHash.mix(hash + i), 4))));
                    claim(x, top - i, z);
                }
            }
        }
    }

    /** Mounds where the flesh is deep, some with eyes, and bones left at the sanctuary's edge. */
    private void floorProps()
    {
        for (int z = PORCH_Z0; z <= NAOS_Z1 + 1; z++)
        {
            for (int x = NAOS_X0; x <= NAOS_X1; x++)
            {
                int y = standY(x, z);
                var under = material(x, y - 1, z);
                if (under != FLESH && under != FLESH_BRUISED || material(x, y, z) != AIR || material(x, y + 1, z) != AIR || taken(x, y, z) || near(x, y, z, 0))
                    continue;
                double depth = flesh(x, y, z);
                int roll = DaedalusHash.pick(hash(x, y, z, 0x4D4F554EL), PERCENT);
                UnderwayProp prop = null;
                if (depth > 0.75 && roll < 8)
                    prop = UnderwayProp.FLESH_MOUND_LARGE;
                else if (depth > 0.45 && roll < 14)
                    prop = roll < 3 ? UnderwayProp.FLESH_EYES : UnderwayProp.FLESH_MOUND;
                else if (depth > 0.3 && roll < 3)
                    prop = UnderwayProp.FLESH_MOUND;
                if (prop == null)
                    continue;
                props.add(new Prop(x, y, z, prop, Direction.from2DDataValue(DaedalusHash.pick(DaedalusHash.mix(roll + x * 31L + z), 4))));
                claim(x, y, z);
            }
        }
        for (int side = -1; side <= 1; side += 2)
        {
            for (int x : new int[] { AXIS + side * 6, AXIS + side * 9 })
            {
                int y = standY(x, SANCTUARY_Z);
                if (!taken(x, y, SANCTUARY_Z))
                {
                    props.add(new Prop(x, y, SANCTUARY_Z, UnderwayProp.BONE_OFFERING, Direction.NORTH));
                    claim(x, y, SANCTUARY_Z);
                }
            }
        }
    }

    private boolean touchesAir(int x, int y, int z)
    {
        for (var side : Direction.values())
        {
            int nx = x + side.getStepX(), ny = y + side.getStepY(), nz = z + side.getStepZ();
            if (inside(nx, ny, nz) && material(nx, ny, nz) == AIR)
                return true;
        }
        return false;
    }

    private void claim(int x, int y, int z)
    {
        taken.add(key(x, y, z));
    }

    private boolean taken(int x, int y, int z)
    {
        return taken.contains(key(x, y, z));
    }

    /** @return whether anything was put within a radius of a place, on its layer */
    private boolean near(int x, int y, int z, int radius)
    {
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++)
                if (taken(x + dx, y, z + dz))
                    return true;
        return false;
    }

    private static long key(int x, int y, int z)
    {
        return ((long) y * LENGTH + z) * WIDTH + x;
    }

    private long hash(int x, int y, int z, long salt)
    {
        return DaedalusHash.mix(seed, x * 73856093L ^ y * 19349663L, z, salt);
    }

    /** Smooth value noise from -1 to 1. */
    private double noise(double x, double y, double z)
    {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        double fx = smooth(x - x0), fy = smooth(y - y0), fz = smooth(z - z0);
        double total = 0;
        for (int corner = 0; corner < 8; corner++)
        {
            int cx = corner & 1, cy = corner >> 1 & 1, cz = corner >> 2 & 1;
            double weight = (cx == 1 ? fx : 1 - fx) * (cy == 1 ? fy : 1 - fy) * (cz == 1 ? fz : 1 - fz);
            total += weight * lattice(x0 + cx, y0 + cy, z0 + cz);
        }
        return total;
    }

    private double lattice(int x, int y, int z)
    {
        return DaedalusHash.pick(hash(x, y, z, 0x4E4F495345L), 2001) / 1000D - 1;
    }

    private static double smooth(double t)
    {
        return t * t * (3 - 2 * t);
    }
}
