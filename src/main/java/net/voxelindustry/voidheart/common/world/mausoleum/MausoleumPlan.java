package net.voxelindustry.voidheart.common.world.mausoleum;

import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusHash;
import net.voxelindustry.voidheart.common.world.daedalus.ShroudGate;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.common.world.mausoleum.MausoleumMaterial.*;

/**
 * A tomb of the old world cut in voidstone after the manner of the river kings: an antechamber where the shroud gate lets one in, a long
 * processional corridor lit by floating orbs of green fire, and a hypostyle hall of papyrus columns ending on a dais, where the sarcophagus lies
 * behind its rider. Sealed burial chambers line both sides of the hall behind false doors, walled up in plain stone.
 * <p>
 * Places are counted from the mausoleum's origin: {@code x} across, {@code z} from the gate to the sarcophagus, {@code y} up. Everything comes
 * from the seed alone.
 */
public final class MausoleumPlan
{
    public static final int WIDTH  = 49;
    public static final int LENGTH = 106;
    public static final int HEIGHT = 21;
    public static final int AXIS   = WIDTH / 2;

    /** The lowest layer of air, over the floor. */
    public static final int FLOOR_Y = 2;

    static final int ANTE_X0 = AXIS - 7, ANTE_X1 = AXIS + 7, ANTE_Z0 = 2, ANTE_Z1 = 15, ANTE_TOP = FLOOR_Y + 11;
    static final int ANTE_DOOR_HALF = 2, ANTE_DOOR_TOP = FLOOR_Y + 5, ANTE_DOOR_Z0 = 16, ANTE_DOOR_Z1 = 17;
    static final int CORRIDOR_X0 = AXIS - 3, CORRIDOR_X1 = AXIS + 3, CORRIDOR_Z0 = 18, CORRIDOR_Z1 = 53, CORRIDOR_TOP = FLOOR_Y + 6;
    static final int HALL_DOOR_HALF = 3, HALL_DOOR_TOP = CORRIDOR_TOP, HALL_DOOR_Z0 = 54, HALL_DOOR_Z1 = 55;
    public static final int HALL_X0 = 7, HALL_X1 = 41, HALL_Z0 = 56, HALL_Z1 = 101, HALL_TOP = FLOOR_Y + 15;
    /** Pilasters stand along the corridor wherever z falls on this beat. */
    static final int PILASTER_EVERY = 5, PILASTER_AT = 3;

    static final int[] COLUMN_ROWS = { AXIS - 8, AXIS + 8 };
    static final int[] COLUMN_ZS = { 61, 67, 73, 79, 85 };
    static final int ARCHITRAVE_Z0 = 58, ARCHITRAVE_Z1 = 88;

    /** The burial chambers on each side of the hall, by the z they start at, and how they are hollowed. */
    static final int[] CHAMBER_ZS = { 62, 72, 82, 92 };
    static final int CHAMBER_LENGTH = 4, CHAMBER_DEPTH = 4, CHAMBER_TOP = FLOOR_Y + 3;
    static final int WEST_CHAMBER_X0 = 1, EAST_CHAMBER_X0 = WIDTH - 1 - CHAMBER_DEPTH;

    /** The steps up to the dais and the dais itself, a block higher, running back to the hall's end wall. */
    static final int STEP_Z = 92, DAIS_X0 = AXIS - 10, DAIS_X1 = AXIS + 10;
    static final int[] OBELISK_XS = { AXIS - 7, AXIS + 7 };
    static final int OBELISK_Z = 99, OBELISK_TOP = FLOOR_Y + 10;
    static final int STELE_HALF = 3, STELE_TOP = FLOOR_Y + 10;

    /** The top of the dais, two steps up from the hall's floor. */
    public static final int DAIS_Y = FLOOR_Y + 2;
    /** The sarcophagus's middle block, on the dais; its head lies further in, toward the stele. */
    public static final int SARCOPHAGUS_X = AXIS, SARCOPHAGUS_Y = DAIS_Y, SARCOPHAGUS_Z = 97;
    public static final int SARCOPHAGUS_HEAD = Compass.SOUTH;

    /** Where the rider keeps its post before the steps, facing the way in. */
    public static final int RIDER_X = AXIS, RIDER_Y = FLOOR_Y, RIDER_Z = 88;
    public static final float RIDER_YAW = 180;

    /** The way back, a shroud gate in the antechamber's end wall facing in, as in the Daedalus. */
    public static final int GATE_Z = 1;
    public static final ShroudGate GATE = new ShroudGate(AXIS, FLOOR_Y, GATE_Z, Compass.SOUTH);
    /** Where one comes in, between the gate and its altar, facing in. */
    public static final int ARRIVAL_X = AXIS, ARRIVAL_Z = GATE_Z + 2;
    public static final float ARRIVAL_YAW = 0;

    private static final long CRACK_SALT = 0x435241434BL;
    private static final int  CRACK_PERCENT = 7;
    private static final int  PERCENT = 100;

    /** An orb of green fire floating free, at the block it lies in. */
    public record Orb(int x, int y, int z)
    {
    }

    /**
     * A burial chamber off the hall. {@code x, y, z} is the floor in its middle, and {@code side} the compass direction from the hall into it.
     */
    public record Chamber(int x, int y, int z, int side)
    {
    }

    private final long seed;
    private final byte[] cells = new byte[WIDTH * LENGTH * HEIGHT];
    private final List<Orb> orbs = new ArrayList<>();
    private final List<Chamber> chambers = new ArrayList<>();

    private MausoleumPlan(long seed)
    {
        this.seed = seed;
    }

    public static MausoleumPlan generate(long seed)
    {
        var plan = new MausoleumPlan(seed);
        plan.build();
        return plan;
    }

    public MausoleumMaterial material(int x, int y, int z)
    {
        if (!inside(x, y, z))
            return AIR;
        return MausoleumMaterial.of(cells[index(x, y, z)]);
    }

    public List<Orb> orbs()
    {
        return orbs;
    }

    public List<Chamber> chambers()
    {
        return chambers;
    }

    public static boolean inside(int x, int y, int z)
    {
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT && z >= 0 && z < LENGTH;
    }

    /** @return whether a place lies in the hall, where the rider wakes to whoever comes in */
    public static boolean inHall(double x, double z)
    {
        return x >= HALL_X0 && x < HALL_X1 + 1 && z >= HALL_Z0 && z < HALL_Z1 + 1;
    }

    /** @return the blocks of the sarcophagus from its foot to its head, as offsets along z from its middle */
    public static int[] sarcophagusParts()
    {
        return new int[] { -1, 0, 1 };
    }

    private static int index(int x, int y, int z)
    {
        return (y * LENGTH + z) * WIDTH + x;
    }

    private void set(int x, int y, int z, MausoleumMaterial material)
    {
        if (inside(x, y, z))
            cells[index(x, y, z)] = (byte) material.ordinal();
    }

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1, MausoleumMaterial material)
    {
        for (int y = y0; y <= y1; y++)
            for (int z = z0; z <= z1; z++)
                for (int x = x0; x <= x1; x++)
                    set(x, y, z, material);
    }

    private void build()
    {
        fill(0, 0, 0, WIDTH - 1, HEIGHT - 1, LENGTH - 1, WALL);
        fill(0, 0, 0, WIDTH - 1, 0, LENGTH - 1, FOUNDATION);

        carve();
        floors();
        dressWalls();
        doorFrames();
        falseDoors();
        for (int x : COLUMN_ROWS)
            for (int z : COLUMN_ZS)
                column(x, z);
        architraves();
        dais();
        ceilings();
        weather();
        placeOrbs();
        placeChambers();
    }

    private void carve()
    {
        fill(ANTE_X0, FLOOR_Y, ANTE_Z0, ANTE_X1, ANTE_TOP, ANTE_Z1, AIR);
        fill(AXIS - ANTE_DOOR_HALF, FLOOR_Y, ANTE_DOOR_Z0, AXIS + ANTE_DOOR_HALF, ANTE_DOOR_TOP, ANTE_DOOR_Z1, AIR);
        fill(CORRIDOR_X0, FLOOR_Y, CORRIDOR_Z0, CORRIDOR_X1, CORRIDOR_TOP, CORRIDOR_Z1, AIR);
        fill(AXIS - HALL_DOOR_HALF, FLOOR_Y, HALL_DOOR_Z0, AXIS + HALL_DOOR_HALF, HALL_DOOR_TOP, HALL_DOOR_Z1, AIR);
        fill(HALL_X0, FLOOR_Y, HALL_Z0, HALL_X1, HALL_TOP, HALL_Z1, AIR);
        for (int z : CHAMBER_ZS)
        {
            fill(WEST_CHAMBER_X0, FLOOR_Y, z, WEST_CHAMBER_X0 + CHAMBER_DEPTH - 1, CHAMBER_TOP, z + CHAMBER_LENGTH - 1, AIR);
            fill(EAST_CHAMBER_X0, FLOOR_Y, z, EAST_CHAMBER_X0 + CHAMBER_DEPTH - 1, CHAMBER_TOP, z + CHAMBER_LENGTH - 1, AIR);
        }
    }

    private void floors()
    {
        for (int z = 0; z < LENGTH; z++)
        {
            for (int x = 0; x < WIDTH; x++)
            {
                if (material(x, FLOOR_Y, z) != AIR)
                    continue;
                set(x, FLOOR_Y - 1, z, floorAt(x, z));
            }
        }
    }

    private static MausoleumMaterial floorAt(int x, int z)
    {
        int across = Math.abs(x - AXIS);
        if (z >= ANTE_Z0 && z <= ANTE_Z1)
        {
            if (across == 0)
                return GILT;
            return x == ANTE_X0 + 1 || x == ANTE_X1 - 1 || z == ANTE_Z1 - 1 ? INLAY : FLOOR;
        }
        if (z >= ANTE_DOOR_Z0 && z < HALL_Z0)
            return across == 0 ? GILT : across == 2 ? INLAY : FLOOR;
        if (z >= HALL_Z0 && z < STEP_Z && Math.abs(x - AXIS) <= 6)
            return across == 0 ? GILT : across == 6 ? INLAY : FLOOR;
        return FLOOR;
    }

    private enum Room
    {
        NONE,
        ANTECHAMBER,
        CORRIDOR,
        HALL,
        CHAMBER
    }

    private static Room roomOf(int x, int z)
    {
        if (x >= ANTE_X0 && x <= ANTE_X1 && z >= ANTE_Z0 && z <= ANTE_Z1)
            return Room.ANTECHAMBER;
        if (x >= CORRIDOR_X0 && x <= CORRIDOR_X1 && z >= ANTE_DOOR_Z0 && z < HALL_Z0)
            return Room.CORRIDOR;
        if (x >= HALL_X0 && x <= HALL_X1 && z >= HALL_Z0 && z <= HALL_Z1)
            return Room.HALL;
        if (x < HALL_X0 || x > HALL_X1)
            return Room.CHAMBER;
        return Room.NONE;
    }

    /**
     * Each face of wall is dressed by the room it looks into and its height in that room: a dado of inlay at the floor, registers of carved
     * glyphs between plain bands, and a cavetto cornice under the ceiling.
     */
    private void dressWalls()
    {
        for (int y = FLOOR_Y; y < HEIGHT; y++)
        {
            for (int z = 0; z < LENGTH; z++)
            {
                for (int x = 0; x < WIDTH; x++)
                {
                    if (material(x, y, z) != WALL)
                        continue;
                    var room = facedRoom(x, y, z);
                    if (room == Room.NONE || room == Room.CHAMBER)
                        continue;
                    set(x, y, z, dressing(room, x, y - FLOOR_Y, z));
                }
            }
        }
    }

    private Room facedRoom(int x, int y, int z)
    {
        for (int side = 0; side < Compass.COUNT; side++)
        {
            int nx = x + Compass.dx(side), nz = z + Compass.dz(side);
            if (material(nx, y, nz) == AIR)
                return roomOf(nx, nz);
        }
        return Room.NONE;
    }

    private static MausoleumMaterial dressing(Room room, int x, int up, int z)
    {
        return switch (room)
        {
            case ANTECHAMBER -> up == 0 ? INLAY
                                : up <= 6 ? ((x - ANTE_X0) % 4 == 0 && z > ANTE_Z0 || (z - ANTE_Z0) % 4 == 3 && (x < ANTE_X0 || x > ANTE_X1) ? PANEL : GLYPHS)
                                : up == 7 ? TRIM
                                : up == ANTE_TOP - FLOOR_Y - 1 ? CORNICE
                                : up == ANTE_TOP - FLOOR_Y ? TRIM
                                : WALL;
            case CORRIDOR -> Math.floorMod(z, PILASTER_EVERY) == PILASTER_AT ? PANEL
                             : up == 0 ? INLAY
                             : up <= 4 ? GLYPHS
                             : up == 5 ? TRIM
                             : CORNICE;
            case HALL -> up == 0 ? INLAY
                         : up <= 3 ? GLYPHS
                         : up == 4 ? TRIM
                         : up <= 7 ? GLYPHS
                         : up == 8 ? TRIM
                         : up == HALL_TOP - FLOOR_Y - 1 ? CORNICE
                         : up == HALL_TOP - FLOOR_Y ? TRIM
                         : WALL;
            default -> WALL;
        };
    }

    /** Doorways are framed in polished stone with a cornice over the lintel, on both of their faces. */
    private void doorFrames()
    {
        doorFrame(ANTE_DOOR_HALF, ANTE_DOOR_TOP, ANTE_DOOR_Z0, ANTE_Z1);
        doorFrame(ANTE_DOOR_HALF, ANTE_DOOR_TOP, ANTE_DOOR_Z1, CORRIDOR_Z0);
        doorFrame(HALL_DOOR_HALF, HALL_DOOR_TOP, HALL_DOOR_Z1, HALL_Z0);
    }

    /**
     * @param wallZ the layer of wall the doorway goes through
     * @param roomZ the layer of air the frame faces
     */
    private void doorFrame(int half, int top, int wallZ, int roomZ)
    {
        if (material(AXIS, FLOOR_Y, roomZ) != AIR)
            return;
        for (int y = FLOOR_Y; y <= top + 1; y++)
        {
            set(AXIS - half - 1, y, wallZ, PANEL);
            set(AXIS + half + 1, y, wallZ, PANEL);
        }
        for (int x = AXIS - half - 1; x <= AXIS + half + 1; x++)
        {
            set(x, top + 1, wallZ, PANEL);
            set(x, top + 2, wallZ, CORNICE);
        }
        set(AXIS, top + 1, wallZ, GILT);
    }

    /** Each burial chamber is shut behind a false door in the hall's side wall: a framed panel of glyphs under the eye. */
    private void falseDoors()
    {
        for (int z0 : CHAMBER_ZS)
        {
            for (int x : new int[] { HALL_X0 - 1, HALL_X1 + 1 })
            {
                for (int z = z0 - 1; z <= z0 + CHAMBER_LENGTH; z++)
                {
                    for (int y = FLOOR_Y; y <= FLOOR_Y + 5; y++)
                    {
                        boolean frame = z == z0 - 1 || z == z0 + CHAMBER_LENGTH || y == FLOOR_Y + 5;
                        set(x, y, z, frame ? PANEL : y == FLOOR_Y + 4 && (z == z0 + 1 || z == z0 + 2) ? FALSE_DOOR : GLYPHS);
                    }
                    set(x, FLOOR_Y + 6, z, CORNICE);
                }
            }
        }
    }

    /**
     * A papyrus column: a disc of a base, a round shaft bound with bronze, a capital swelling into a closed bud, and an abacus under the beam.
     */
    private void column(int cx, int cz)
    {
        int capital = HALL_TOP - 3;
        fill(cx - 1, FLOOR_Y, cz - 1, cx + 1, FLOOR_Y, cz + 1, PANEL);
        for (int y = FLOOR_Y + 1; y < capital; y++)
        {
            int beat = y - FLOOR_Y;
            var material = beat == 2 || beat == 7 || beat == 9 ? BAND : COLUMN;
            set(cx, y, cz, material);
            for (int side = 0; side < Compass.COUNT; side++)
                set(cx + Compass.dx(side), y, cz + Compass.dz(side), material);
        }
        for (int y = capital; y <= capital + 1; y++)
        {
            for (int dz = -2; dz <= 2; dz++)
            {
                for (int dx = -2; dx <= 2; dx++)
                {
                    if (Math.abs(dx) + Math.abs(dz) <= (y == capital ? 2 : 3))
                        set(cx + dx, y, cz + dz, CAPITAL);
                }
            }
        }
        fill(cx - 1, capital + 2, cz - 1, cx + 1, capital + 2, cz + 1, PANEL);
    }

    /** Beams run along each row of columns under the ceiling, out past the last column toward the dais. */
    private void architraves()
    {
        for (int x : COLUMN_ROWS)
            fill(x - 1, HALL_TOP, ARCHITRAVE_Z0, x + 1, HALL_TOP, ARCHITRAVE_Z1, TRIM);
    }

    /** Steps up to the dais, two obelisks on it, and the stele of a false door on the end wall behind the sarcophagus. */
    private void dais()
    {
        for (int x = DAIS_X0; x <= DAIS_X1; x++)
        {
            set(x, FLOOR_Y, STEP_Z, DAIS);
            for (int z = STEP_Z + 1; z <= HALL_Z1; z++)
            {
                set(x, FLOOR_Y, z, DAIS);
                set(x, DAIS_Y - 1, z, Math.abs(x - AXIS) == 2 && z >= SARCOPHAGUS_Z - 3 ? INLAY : DAIS);
            }
        }
        for (int x : OBELISK_XS)
        {
            set(x, DAIS_Y, OBELISK_Z, PANEL);
            for (int y = DAIS_Y + 1; y < OBELISK_TOP; y++)
                set(x, y, OBELISK_Z, GLYPHS);
            set(x, OBELISK_TOP, OBELISK_Z, GILT);
        }
        int wall = HALL_Z1 + 1;
        for (int x = AXIS - STELE_HALF; x <= AXIS + STELE_HALF; x++)
        {
            for (int y = DAIS_Y; y <= STELE_TOP; y++)
            {
                boolean frame = Math.abs(x - AXIS) == STELE_HALF || y == STELE_TOP;
                set(x, y, wall, frame ? PANEL : y == STELE_TOP - 1 && Math.abs(x - AXIS) <= 1 ? FALSE_DOOR : GLYPHS);
            }
        }
        set(AXIS, STELE_TOP, wall, GILT);
        for (int x = AXIS - STELE_HALF - 1; x <= AXIS + STELE_HALF + 1; x++)
            set(x, STELE_TOP + 1, wall, CORNICE);
    }

    /** Every room is roofed with a night sky: dark tiles starred in bronze. */
    private void ceilings()
    {
        for (int z = 0; z < LENGTH; z++)
        {
            for (int x = 0; x < WIDTH; x++)
            {
                var room = roomOf(x, z);
                int top = switch (room)
                {
                    case ANTECHAMBER -> ANTE_TOP;
                    case CORRIDOR -> z >= ANTE_DOOR_Z0 && z <= ANTE_DOOR_Z1 ? ANTE_DOOR_TOP : z >= HALL_DOOR_Z0 ? HALL_DOOR_TOP : CORRIDOR_TOP;
                    case HALL -> HALL_TOP;
                    default -> -1;
                };
                if (top > 0 && material(x, top + 1, z) == WALL)
                    set(x, top + 1, z, CEILING);
            }
        }
    }

    /** Plain stone left bare has cracked here and there. */
    private void weather()
    {
        for (int y = FLOOR_Y; y < HEIGHT; y++)
        {
            for (int z = 0; z < LENGTH; z++)
            {
                for (int x = 0; x < WIDTH; x++)
                {
                    if (material(x, y, z) == WALL && touchesAir(x, y, z)
                        && DaedalusHash.pick(DaedalusHash.mix(seed, x * 131 + y, z, CRACK_SALT), PERCENT) < CRACK_PERCENT)
                        set(x, y, z, WALL_CRACKED);
                }
            }
        }
    }

    private boolean touchesAir(int x, int y, int z)
    {
        for (int side = 0; side < Compass.COUNT; side++)
        {
            if (material(x + Compass.dx(side), y, z + Compass.dz(side)) == AIR)
                return true;
        }
        return material(x, y - 1, z) == AIR || material(x, y + 1, z) == AIR;
    }

    private void placeOrbs()
    {
        // Flanking the way on from the antechamber, and either side of the gate's altar
        for (int x : new int[] { AXIS - 4, AXIS + 4 })
            orbs.add(new Orb(x, FLOOR_Y + 4, ANTE_Z1 - 1));
        for (int x : new int[] { AXIS - 5, AXIS + 5 })
            orbs.add(new Orb(x, FLOOR_Y + 3, GATE_Z + ShroudGate.ALTAR_DEPTH + 1));
        // Down both sides of the corridor, between its pilasters
        for (int z = CORRIDOR_Z0 + PILASTER_AT; z < CORRIDOR_Z1; z += PILASTER_EVERY)
        {
            orbs.add(new Orb(CORRIDOR_X0 + 1, FLOOR_Y + 3, z));
            orbs.add(new Orb(CORRIDOR_X1 - 1, FLOOR_Y + 3, z));
        }
        // High over both edges of the nave, low in the aisles, and either side of the sarcophagus
        for (int z = COLUMN_ZS[0] + 3; z < STEP_Z; z += 6)
        {
            orbs.add(new Orb(COLUMN_ROWS[0] + 2, FLOOR_Y + 7, z));
            orbs.add(new Orb(COLUMN_ROWS[1] - 2, FLOOR_Y + 7, z));
        }
        for (int z = HALL_Z0 + 4; z < STEP_Z; z += 10)
        {
            orbs.add(new Orb(HALL_X0 + 3, FLOOR_Y + 3, z));
            orbs.add(new Orb(HALL_X1 - 3, FLOOR_Y + 3, z));
        }
        for (int x : new int[] { SARCOPHAGUS_X - 3, SARCOPHAGUS_X + 3 })
            orbs.add(new Orb(x, DAIS_Y + 3, SARCOPHAGUS_Z - 2));
    }

    private void placeChambers()
    {
        int middle = CHAMBER_LENGTH / 2;
        for (int z : CHAMBER_ZS)
        {
            chambers.add(new Chamber(WEST_CHAMBER_X0 + CHAMBER_DEPTH / 2, FLOOR_Y, z + middle, Compass.WEST));
            chambers.add(new Chamber(EAST_CHAMBER_X0 + CHAMBER_DEPTH / 2, FLOOR_Y, z + middle, Compass.EAST));
        }
    }
}
