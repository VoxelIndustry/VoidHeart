package net.voxelindustry.voidheart.client.curse;

import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The blocks of meat and bone around the breach, in a still sky with +y straight up and the player at the origin. Built once from the seed, off the render thread.
 * The camera never leaves the origin, so what faces it and what is hidden is settled here once and for all.
 * <p>
 * The flesh is a heap of lumps. Each lump is a small rough voxel blob on a grid of its own, so that its cubes line up as blocks do while no line carries on from one lump into the next.
 * Bone grows out of the lumps as stacks of tapering cuboids.
 */
final class RaptureMassMesh
{
    /**
     * What each sprite index stands for, and the order {@link #write} wants their corners in. Each is {@code block/curse_rapture_<name>} in the block atlas.
     */
    static final String[] SPRITES = {"mass0", "mass1", "mass2", "mass3", "mass4", "mass5", "mass6", "mass7", "knot0", "knot1", "spur0", "spur1", "spur2", "spur3"};

    private static final int[][]   FACE_DIRS     = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final int[][]   FACE_TANGENTS = {{0, 1, 0}, {0, 1, 0}, {0, 0, 1}, {0, 0, 1}, {1, 0, 0}, {1, 0, 0}};
    private static final float[][] CORNERS       = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};

    private static final int[] WART   = {0, 1, 2};
    private static final int[] FIBER  = {3, 4};
    private static final int[] CELLS  = {5, 6};
    private static final int[] BRUISE = {7};
    private static final int[] KNOT   = {8, 9};
    private static final int[] SPUR   = {10, 11};
    private static final int[] ROOT   = {12, 13};

    static final int VERTEX_BYTES = 3 * Float.BYTES + 3 * Float.BYTES + 3 * Float.BYTES + 2 * Float.BYTES + 4 + 4;
    private static final int VERTEX_FLOATS = 11;

    // A block across, in units of a sky where the flesh hangs about 65 up: 3.5 to 4.6 degrees overhead
    private static final float CELL_LEAST = 3.9F;
    private static final float CELL_MOST  = 5.2F;

    private static final int   CREST_LUMPS    = 46;
    private static final float CREST_Y_LOW    = 58;
    private static final float CREST_Y_HIGH   = 72;
    private static final float CREST_R_LEAST  = 2F;
    private static final float CREST_R_MOST   = 4.8F;
    private static final float CREST_INTO_RIM = 0.85F;

    private static final int   WALL_LUMPS   = 52;
    private static final float WALL_Y_LOW   = 78;
    private static final float WALL_Y_HIGH  = 112;
    private static final float WALL_R_LEAST = 1.6F;
    private static final float WALL_R_MOST  = 3.2F;
    private static final float WALL_INTO_RIM = 0.8F;

    private static final int   BODY_LUMPS   = 70;
    private static final float BODY_Y       = 64;
    private static final float BODY_Y_RISE  = 20;
    private static final float BODY_Y_NOISE = 10;
    private static final float BODY_R_LEAST = 1.8F;
    private static final float BODY_R_MOST  = 4.2F;
    private static final float BODY_FLAT    = 0.62F;

    private static final int   SAGS        = 11;
    private static final float SAG_Y_LOW   = 50;
    private static final float SAG_Y_HIGH  = 60;
    private static final float SAG_R_LEAST = 1.1F;
    private static final float SAG_R_MOST  = 1.9F;
    private static final float SAG_LENGTH  = 3F;

    // No cube has its center closer than this to the zenith: what is down there must stay in sight, at the size it ends up at
    private static final float MIDDLE_CLEARS   = 0.235F;
    private static final int   INTRUDERS       = 7;
    private static final float INTRUDER_Y_LOW  = 64;
    private static final float INTRUDER_Y_HIGH = 92;
    private static final float INTRUDER_REACH  = 0.68F;
    private static final float INTRUDER_CLEARS = 0.27F;

    private static final int   DEEP_LUMPS   = 9;
    private static final float DEEP_Y_LOW   = 112;
    private static final float DEEP_Y_HIGH  = 150;
    private static final float DEEP_REACH   = 0.2F;

    private static final int   KNOTS        = 30;
    private static final float KNOT_CELL    = 2.4F;
    private static final float KNOT_TILT    = 1.1F;

    private static final int   STRAYS       = 22;

    // Radians. The whole grid of a lump leans by up to this much
    private static final float LUMP_TILT = 0.3F;
    private static final float LUMP_ROUGH = 0.8F;
    private static final float LUMP_ROUGH_AT = 0.7F;

    // As a share of the rim, the radius nothing stands inside of
    private static final float CLEAR_LEAST   = 0.82F;
    private static final float CLEAR_RAGS    = 0.2F;
    private static final float CLEAR_RAGS_AT = 6;

    // A share of a block: how far each cube stands out of or sinks into its lump, along whichever of the lump's axes faces the player most
    private static final float CUBE_STAGGER = 0.26F;
    private static final float CUBE_JITTER = 0.025F;
    private static final float CUBE_PAD    = 0.03F;
    private static final float CUBE_PROUD  = 0.1F;
    private static final float CUBE_PROUD_BY = 0.36F;

    private static final int   TOOTH_CLUSTERS      = 64;
    private static final int   TOOTH_CLUSTER_LEAST = 2;
    private static final int   TOOTH_CLUSTER_MOST  = 10;
    private static final float TOOTH_CLUSTER_SPAN  = 15;
    private static final float TOOTH_WIDTH_LEAST   = 1.5F;
    private static final float TOOTH_WIDTH_USUAL   = 3.6F;
    private static final float TOOTH_WIDTH_BIG     = 7F;
    private static final float TOOTH_BIG_SHARE     = 0.12F;
    private static final float TOOTH_HUGE_SHARE    = 0.1F;
    private static final float TOOTH_HUGE_WIDTH    = 7F;
    // Shares of the width by which a base goes under the flesh, along its aim for the thin ones and for the widest, and straight in from the surface for the widest
    private static final float TOOTH_SINK_THIN     = 0.35F;
    private static final float TOOTH_SINK_WIDE     = 0.8F;
    private static final float TOOTH_SINK_INWARD   = 0.5F;
    private static final float TOOTH_TAPER_LEAST   = 0.64F;
    private static final float TOOTH_TAPER_MOST    = 0.78F;
    private static final float TOOTH_LENGTH        = 1F;
    private static final float TOOTH_TIP_WIDTH     = 0.6F;
    private static final int   TOOTH_SEGMENTS_MOST = 9;
    private static final float TOOTH_BEND          = 0.09F;
    // How far a tooth's aim strays, and the share of them that point anywhere at all
    private static final float TOOTH_STRAY         = 0.5F;
    private static final float TOOTH_WRONG_SHARE   = 0.22F;
    // How close to the zenith the middle of a piece may come, and that much further for a corner of it
    private static final float TOOTH_CLEARS          = 0.21F;
    private static final float TOOTH_CLEARS_BY_WIDTH = 0.75F;
    private static final int   TOOTH_OUTER_CLUSTERS = 7;

    private static final int   STRANDS        = 6;
    private static final float STRAND_WIDTH_LEAST = 0.8F;
    private static final float STRAND_WIDTH_MOST  = 1.9F;
    private static final float STRAND_LINK    = 4.8F;
    private static final float STRAND_SAG     = 0.22F;
    private static final float STRAND_WANDER  = 2.6F;
    private static final float STRAND_CLEARS  = 0.25F;

    // Openings, from 0 to 1. Flesh comes out of the tear first, the bone last
    private static final float FLESH_FIRST  = 0.14F;
    private static final float FLESH_LAST   = 0.7F;
    private static final float FLESH_NOISE  = 0.09F;
    private static final float FLESH_SPAN   = 0.17F;
    private static final float BONE_FIRST   = 0.62F;
    private static final float BONE_LAST    = 0.87F;
    private static final float BONE_SEGMENT = 0.012F;
    private static final float BONE_SPAN    = 0.13F;
    private static final float STRAND_FIRST = 0.6F;
    private static final float STRAND_LAST  = 0.82F;
    // How far along the way to the crack a cube starts out, and how much deeper
    private static final float ORIGIN_TO_CRACK = 0.55F;
    private static final float ORIGIN_DEEPER   = 1.22F;

    private static final float WRITHE_FLESH  = 1;
    private static final float WRITHE_BONE   = 0.75F;
    private static final float WRITHE_STRAND = 1;

    // As vanilla shades a block's faces
    private static final float FACE_TOP    = 1;
    private static final float FACE_Z      = 0.8F;
    private static final float FACE_X      = 0.6F;
    private static final float FACE_BOTTOM = 0.5F;
    // From most hemmed in to free, as vanilla's smooth lighting steps
    private static final float[] CORNER = {0.34F, 0.56F, 0.78F, 1};
    private static final float DEEP_FROM = 80;
    private static final float DEEP_TO   = 152;
    private static final float DEEPEST   = 0.06F;
    private static final float FAR_OUT_FROM  = 1.2F;
    private static final float FAR_OUT_TO    = 2F;
    private static final float FAR_OUT_LIGHT = 0.5F;

    private static final float BULGE_FROM    = -0.05F;
    private static final float BULGE_TO      = 0.8F;
    private static final float BULGE_DARKEST = 0.42F;

    private static final float TINT_LUMP = 0.1F;
    private static final float TINT_CUBE = 0.08F;
    // Each piece of a stack darker where it comes out of the one below, which shows the stacking from afar
    private static final float BONE_BASE_SHADE = 0.58F;
    private static final float BONE_TINT = 0.52F;
    private static final float[] BRUISED = {0.86F, 0.8F, 1.02F};

    // A face turned away by less than this is kept, as the slow motion may bring it round
    private static final float FACING_SLACK = 0.03F;
    // Hash cell for finding the lumps around a point
    private static final float BUCKET = 18;

    private final RaptureMassShape shape;
    private final Random             random;

    private final List<Lump>             lumps   = new ArrayList<>();
    private final Map<Long, List<Lump>>  buckets = new HashMap<>();
    private final List<Box>              boxes   = new ArrayList<>();
    private final List<Anchor>           anchors = new ArrayList<>();
    private final float[]                nearest = new float[3];

    private float[] floats;
    private int[]   colors;
    private int[]   params;
    private byte[]  sprites;
    private int     quads;

    private RaptureMassMesh(long seed)
    {
        shape = new RaptureMassShape(seed);
        random = new Random(seed * 31 + 7);
    }

    static RaptureMassMesh build(long seed)
    {
        var mesh = new RaptureMassMesh(seed);
        mesh.placeLumps();
        mesh.placeCubes();
        mesh.growBone();
        mesh.stretchStrands();
        mesh.emit();
        return mesh;
    }

    RaptureMassShape shape()
    {
        return shape;
    }

    int quads()
    {
        return quads;
    }

    /**
     * Writes the vertices, with each sprite's corners in the atlas as {u0, v0, u1, v1}. Position, offset, origin and UV are floats, then two groups of four bytes.
     */
    void write(ByteBuffer out, float[][] atlas)
    {
        int vertices = quads * 4;
        for (int i = 0; i < vertices; i++)
        {
            int at = i * VERTEX_FLOATS;
            for (int f = 0; f < 9; f++)
                out.putFloat(floats[at + f]);
            var rect = atlas[sprites[i / 4]];
            out.putFloat(Mth.lerp(floats[at + 9], rect[0], rect[2]));
            out.putFloat(Mth.lerp(floats[at + 10], rect[1], rect[3]));
            out.putInt(colors[i]);
            out.putInt(params[i]);
        }
    }

    private void placeLumps()
    {
        for (int i = 0; i < CREST_LUMPS; i++)
        {
            float angle = (i + random.nextFloat() * 0.85F) / CREST_LUMPS * Mth.TWO_PI;
            float radius = Mth.lerp(square(random.nextFloat()), CREST_R_LEAST, CREST_R_MOST);
            float cell = cell();
            float y = Mth.lerp(random.nextFloat(), CREST_Y_LOW, CREST_Y_HIGH);
            float rho = shape.rim(angle) + radius * cell / y * CREST_INTO_RIM + random.nextFloat() * 0.05F;
            addLump(angle, rho, y, cell, radius * spread(0.85F, 1.25F), radius * spread(0.6F, 1), radius * spread(0.85F, 1.25F), wholeTilt(), flesh());
        }
        for (int i = 0; i < WALL_LUMPS; i++)
        {
            float angle = (i + random.nextFloat() * 0.85F) / WALL_LUMPS * Mth.TWO_PI;
            float radius = Mth.lerp(random.nextFloat(), WALL_R_LEAST, WALL_R_MOST);
            float cell = cell();
            float y = Mth.lerp(random.nextFloat(), WALL_Y_LOW, WALL_Y_HIGH);
            float rho = shape.rim(angle) + radius * cell / y * WALL_INTO_RIM;
            addLump(angle, rho, y, cell, radius, radius * spread(0.8F, 1.3F), radius, wholeTilt(), flesh());
        }
        for (int i = 0; i < BODY_LUMPS; i++)
        {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float rim = shape.rim(angle);
            float outer = shape.outer(angle);
            float at = (float) Math.pow(random.nextFloat(), 0.8F);
            float rho = Mth.lerp(at, rim * 1.22F, outer);
            float y = BODY_Y + BODY_Y_RISE * at + BODY_Y_NOISE * random.nextFloat();
            float radius = Mth.lerp(square(random.nextFloat()), BODY_R_LEAST, BODY_R_MOST) * (1 - at * 0.35F);
            addLump(angle, rho, y, cell(), radius * spread(0.9F, 1.35F), radius * BODY_FLAT, radius * spread(0.9F, 1.35F), wholeTilt(), flesh());
        }
        for (int i = 0; i < SAGS; i++)
        {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float radius = Mth.lerp(random.nextFloat(), SAG_R_LEAST, SAG_R_MOST);
            float y = Mth.lerp(random.nextFloat(), SAG_Y_LOW, SAG_Y_HIGH);
            float rho = shape.rim(angle) + 0.18F + random.nextFloat() * 0.3F;
            addLump(angle, rho, y + radius * SAG_LENGTH * 2, cell() * 0.92F, radius, radius * SAG_LENGTH, radius, new Matrix3f().rotateY(random.nextFloat() * Mth.TWO_PI).rotateZ(gaussian(0.16F)), flesh());
        }
        for (int i = 0; i < INTRUDERS; i++)
        {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float y = Mth.lerp(random.nextFloat(), INTRUDER_Y_LOW, INTRUDER_Y_HIGH);
            float rho = Math.max(INTRUDER_CLEARS, shape.rim(angle) * spread(INTRUDER_REACH, 0.95F));
            float radius = spread(1.4F, 2.7F);
            addLump(angle, rho, y, cell(), radius, radius * spread(0.9F, 1.6F), radius, wholeTilt(), flesh());
            lumps.getLast().intrudes = true;
        }
        for (int i = 0; i < DEEP_LUMPS; i++)
        {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float y = Mth.lerp(random.nextFloat(), DEEP_Y_LOW, DEEP_Y_HIGH);
            float rho = shape.rim(angle) * (1 - DEEP_REACH * random.nextFloat() * random.nextFloat());
            addLump(angle, rho, y, cell(), 2 + random.nextFloat() * 2, 2 + random.nextFloat() * 2, 2 + random.nextFloat() * 2, wholeTilt(), flesh());
        }
        for (int i = 0; i < KNOTS; i++)
        {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float rim = shape.rim(angle);
            float rho = Mth.lerp(random.nextFloat() * random.nextFloat(), rim * 1.02F, shape.outer(angle));
            float y = Mth.lerp(random.nextFloat(), CREST_Y_LOW - 6, CREST_Y_HIGH);
            float radius = 1.3F + random.nextFloat() * 1.5F;
            var lean = new Matrix3f().rotateXYZ(gaussian(KNOT_TILT), random.nextFloat() * Mth.TWO_PI, gaussian(KNOT_TILT));
            addLump(angle, rho, y, KNOT_CELL * spread(0.8F, 1.3F), radius, radius, radius, lean, KNOT);
        }
        for (int i = 0; i < STRAYS; i++)
        {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float rho = shape.outer(angle) * spread(0.96F, 1.2F);
            float y = BODY_Y + BODY_Y_RISE + BODY_Y_NOISE * random.nextFloat();
            float radius = 0.7F + random.nextFloat() * 0.9F;
            addLump(angle, rho, y, cell(), radius, radius, radius, wholeTilt(), flesh());
        }
    }

    private float cell()
    {
        return Mth.lerp(random.nextFloat(), CELL_LEAST, CELL_MOST);
    }

    private float spread(float low, float high)
    {
        return Mth.lerp(random.nextFloat(), low, high);
    }

    private float gaussian(float by)
    {
        return (float) (random.nextGaussian() * by * 0.5);
    }

    private static float square(float value)
    {
        return value * value;
    }

    private Matrix3f wholeTilt()
    {
        return new Matrix3f().rotateY(random.nextFloat() * Mth.TWO_PI).rotateX(gaussian(LUMP_TILT)).rotateZ(gaussian(LUMP_TILT));
    }

    private int[] flesh()
    {
        float kind = random.nextFloat();
        if (kind < 0.5F)
            return WART;
        if (kind < 0.72F)
            return FIBER;
        if (kind < 0.92F)
            return CELLS;
        return BRUISE;
    }

    private void addLump(float angle, float rho, float y, float cell, float rx, float ry, float rz, Matrix3f rotation, int[] sprites)
    {
        var lump = new Lump();
        lump.center = new Vector3f(Mth.cos(angle) * rho * y, y, Mth.sin(angle) * rho * y);
        lump.rotation = rotation;
        lump.inverse = new Matrix3f(rotation).transpose();
        lump.cell = cell;
        lump.size = Mth.ceil(Math.max(rx, Math.max(ry, rz)) * 1.35F) + 1;
        lump.sprites = sprites;
        lump.tint = spread(1 - TINT_LUMP, 1 + TINT_LUMP);
        lump.bruised = sprites == BRUISE || random.nextFloat() < 0.2F;

        int side = lump.size * 2 + 1;
        lump.solid = new boolean[side * side * side];
        lump.lift = new float[side * side * side];
        for (int i = 0; i < lump.lift.length; i++)
        {
            float lift = (random.nextFloat() * 2 - 1) * CUBE_STAGGER;
            lump.lift[i] = random.nextFloat() < CUBE_PROUD ? lift + CUBE_PROUD_BY : lift;
        }
        var toPlayer = new Vector3f(lump.center).negate().normalize().mul(lump.inverse);
        lump.liftAxis = Math.abs(toPlayer.x) > Math.abs(toPlayer.y)
                ? Math.abs(toPlayer.x) > Math.abs(toPlayer.z) ? 0 : 2
                : Math.abs(toPlayer.y) > Math.abs(toPlayer.z) ? 1 : 2;
        lump.liftSign = toPlayer.get(lump.liftAxis) > 0 ? 1 : -1;
        float offset = random.nextFloat() * 100;
        for (int z = -lump.size; z <= lump.size; z++)
        {
            for (int y2 = -lump.size; y2 <= lump.size; y2++)
            {
                for (int x = -lump.size; x <= lump.size; x++)
                {
                    float round = square(x / rx) + square(y2 / ry) + square(z / rz);
                    float rough = shape.noise(x * LUMP_ROUGH_AT + offset, y2 * LUMP_ROUGH_AT + offset * 0.37F, z * LUMP_ROUGH_AT - offset * 0.61F);
                    lump.solid[lump.index(x, y2, z)] = round + (rough - 0.5F) * 2 * LUMP_ROUGH < 1;
                }
            }
        }

        lumps.add(lump);
        float reach = (lump.size + 1) * cell * 1.8F;
        for (int bz = bucket(lump.center.z - reach); bz <= bucket(lump.center.z + reach); bz++)
        {
            for (int by = bucket(lump.center.y - reach); by <= bucket(lump.center.y + reach); by++)
            {
                for (int bx = bucket(lump.center.x - reach); bx <= bucket(lump.center.x + reach); bx++)
                    buckets.computeIfAbsent(key(bx, by, bz), unused -> new ArrayList<>()).add(lump);
            }
        }
    }

    private static int bucket(float value)
    {
        return Mth.floor(value / BUCKET);
    }

    private static long key(int x, int y, int z)
    {
        return (x & 0x1FFFFFL) | (y & 0x1FFFFFL) << 21 | (z & 0x1FFFFFL) << 42;
    }

    private boolean solidAt(float x, float y, float z)
    {
        var near = buckets.get(key(bucket(x), bucket(y), bucket(z)));
        if (near == null)
            return false;
        for (var lump : near)
        {
            if (lump.contains(x, y, z))
                return true;
        }
        return false;
    }

    private void placeCubes()
    {
        var normal = new Vector3f();
        var at = new Vector3f();
        for (var lump : lumps)
        {
            for (int z = -lump.size; z <= lump.size; z++)
            {
                for (int y = -lump.size; y <= lump.size; y++)
                {
                    for (int x = -lump.size; x <= lump.size; x++)
                    {
                        if (lump.solid[lump.index(x, y, z)])
                            placeCube(lump, x, y, z, normal, at);
                    }
                }
            }
        }
    }

    private void placeCube(Lump lump, int x, int y, int z, Vector3f normal, Vector3f at)
    {
        at.set(x * lump.cell, y * lump.cell, z * lump.cell).mul(lump.rotation).add(lump.center);
        var center = new Vector3f(at);
        if (lump.intrudes ? inMiddle(center) : inOpening(center))
            return;
        float half = lump.cell / 2;
        float lift = lump.lift[lump.index(x, y, z)];

        int faces = 0;
        for (int face = 0; face < 6; face++)
        {
            int nx = x + FACE_DIRS[face][0];
            int ny = y + FACE_DIRS[face][1];
            int nz = z + FACE_DIRS[face][2];
            if (lump.has(nx, ny, nz))
            {
                // Standing further out than the one next to it, it shows a side that would otherwise be hidden
                if (FACE_DIRS[face][lump.liftAxis] == 0 && lift > lump.lift[lump.index(nx, ny, nz)])
                    faces |= 1 << face;
                continue;
            }

            normal.set(FACE_DIRS[face][0], FACE_DIRS[face][1], FACE_DIRS[face][2]).mul(lump.rotation);
            at.set(center).add(normal.x * half, normal.y * half, normal.z * half);
            if (normal.dot(at) > FACING_SLACK * at.length())
                continue;
            // Buried in another lump: the middle and all four corners of the face are inside something
            if (faceBuried(lump, normal, at, half, face))
                continue;
            faces |= 1 << face;

            if (normal.y < 0.2F)
                anchors.add(new Anchor(new Vector3f(at), new Vector3f(normal), lump));
        }
        if (faces == 0)
            return;

        var box = new Box();
        box.center = center;
        box.rotation = lump.rotation;
        var along = new Vector3f(lump.liftAxis == 0 ? 1 : 0, lump.liftAxis == 1 ? 1 : 0, lump.liftAxis == 2 ? 1 : 0).mul(lump.rotation);
        box.center.add(jitter(lump.cell * CUBE_JITTER)).add(along.mul(lift * lump.liftSign * lump.cell));
        box.half = new Vector3f(half * (1 + CUBE_PAD));
        box.faces = faces;
        box.sprites = random.nextFloat() < 0.14F && lump.sprites != KNOT ? flesh() : lump.sprites;
        float tint = lump.tint * spread(1 - TINT_CUBE, 1 + TINT_CUBE) * bulge(lump, x, y, z);
        box.tint = lump.bruised
                ? new Vector3f(tint * BRUISED[0], tint * BRUISED[1], tint * BRUISED[2])
                : new Vector3f(tint);
        box.span = FLESH_SPAN;
        box.writhe = WRITHE_FLESH;
        box.corner = lump.cell;
        box.threshold = fleshTime(box.center);
        box.origin = originOf(box.center);
        boxes.add(box);
    }

    /**
     * The side of a lump turned to the player is the closest and the lightest, it gets darker around its edge and down into the creases between lumps.
     */
    private static float bulge(Lump lump, int x, int y, int z)
    {
        float length = Mth.sqrt(x * x + y * y + z * z);
        if (length == 0)
            return 1;
        var out = new Vector3f(x / length, y / length, z / length).mul(lump.rotation);
        float toPlayer = -out.dot(lump.center) / lump.center.length();
        return Mth.lerp(Mth.clamp((toPlayer - BULGE_FROM) / (BULGE_TO - BULGE_FROM), 0, 1), BULGE_DARKEST, 1);
    }

    /**
     * Nothing stands in the way of the dark except what is meant to, whatever the lumps did.
     */
    private boolean inOpening(Vector3f at)
    {
        float x = at.x / at.y;
        float z = at.z / at.y;
        float angle = (float) Mth.atan2(z, x);
        float clear = shape.rim(angle) * (CLEAR_LEAST + CLEAR_RAGS * shape.noise(Mth.cos(angle) * CLEAR_RAGS_AT + 5, Mth.sin(angle) * CLEAR_RAGS_AT + 9, at.y * 0.06F));
        return x * x + z * z < clear * clear;
    }

    private static boolean inMiddle(Vector3f at)
    {
        float x = at.x / at.y;
        float z = at.z / at.y;
        return x * x + z * z < MIDDLE_CLEARS * MIDDLE_CLEARS;
    }

    private boolean faceBuried(Lump lump, Vector3f normal, Vector3f at, float half, int face)
    {
        var u = new Vector3f(FACE_TANGENTS[face][0], FACE_TANGENTS[face][1], FACE_TANGENTS[face][2]).mul(lump.rotation).mul(half * 0.9F);
        var v = new Vector3f(normal).cross(u);
        float lift = half * 0.2F;
        float px = at.x + normal.x * lift;
        float py = at.y + normal.y * lift;
        float pz = at.z + normal.z * lift;
        return solidAt(px, py, pz)
                && solidAt(px + u.x + v.x, py + u.y + v.y, pz + u.z + v.z)
                && solidAt(px + u.x - v.x, py + u.y - v.y, pz + u.z - v.z)
                && solidAt(px - u.x + v.x, py - u.y + v.y, pz - u.z + v.z)
                && solidAt(px - u.x - v.x, py - u.y - v.y, pz - u.z - v.z);
    }

    private Vector3f jitter(float by)
    {
        return new Vector3f((random.nextFloat() * 2 - 1) * by, (random.nextFloat() * 2 - 1) * by, (random.nextFloat() * 2 - 1) * by);
    }

    /**
     * When this comes out of the tear: the nearer the crack, the sooner.
     */
    private float fleshTime(Vector3f at)
    {
        float x = at.x / at.y;
        float z = at.z / at.y;
        shape.nearestOnCrack(x, z, nearest);
        float far = nearest[2] / shape.outer((float) Mth.atan2(z, x));
        return Mth.clamp(FLESH_FIRST + (FLESH_LAST - FLESH_FIRST) * (float) Math.pow(Math.min(1, far), 0.8F) + (random.nextFloat() - 0.5F) * FLESH_NOISE, 0, FLESH_LAST);
    }

    /**
     * Where this starts from: behind the sky, part of the way toward the crack.
     */
    private Vector3f originOf(Vector3f at)
    {
        float x = at.x / at.y;
        float z = at.z / at.y;
        shape.nearestOnCrack(x, z, nearest);
        float y = at.y * ORIGIN_DEEPER;
        return new Vector3f(Mth.lerp(ORIGIN_TO_CRACK, x, nearest[0]) * y, y, Mth.lerp(ORIGIN_TO_CRACK, z, nearest[1]) * y);
    }

    private void growBone()
    {
        if (anchors.isEmpty())
            return;

        for (int i = 0; i < TOOTH_CLUSTERS; i++)
        {
            // Most toward the opening, where they can be seen against the dark, a few anywhere at all
            var seed = i < TOOTH_OUTER_CLUSTERS ? anchors.get(random.nextInt(anchors.size())) : rimAnchor();
            int count = TOOTH_CLUSTER_LEAST + random.nextInt(TOOTH_CLUSTER_MOST - TOOTH_CLUSTER_LEAST + 1);
            for (int tooth = 0; tooth < count; tooth++)
            {
                var anchor = clusterAnchor(seed);
                if (anchor != null)
                    growTooth(anchor, tooth == 0 && random.nextFloat() < TOOTH_HUGE_SHARE);
            }
        }
    }

    private Anchor rimAnchor()
    {
        Anchor best = anchors.get(random.nextInt(anchors.size()));
        float bestScore = Float.MAX_VALUE;
        for (int attempt = 0; attempt < 14; attempt++)
        {
            var candidate = anchors.get(random.nextInt(anchors.size()));
            float x = candidate.at.x / candidate.at.y;
            float z = candidate.at.z / candidate.at.y;
            float score = Mth.sqrt(x * x + z * z) / shape.rim((float) Mth.atan2(z, x));
            if (score < bestScore)
            {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private Anchor clusterAnchor(Anchor seed)
    {
        for (int attempt = 0; attempt < 24; attempt++)
        {
            var candidate = anchors.get(random.nextInt(anchors.size()));
            if (candidate.at.distanceSquared(seed.at) < TOOTH_CLUSTER_SPAN * TOOTH_CLUSTER_SPAN)
                return candidate;
        }
        return seed;
    }

    private void growTooth(Anchor anchor, boolean huge)
    {
        float size = random.nextFloat();
        float width = huge ? TOOTH_HUGE_WIDTH * spread(0.85F, 1)
                : size < TOOTH_BIG_SHARE ? Mth.lerp(random.nextFloat(), TOOTH_WIDTH_USUAL, TOOTH_WIDTH_BIG)
                : Mth.lerp(square(random.nextFloat()), TOOTH_WIDTH_LEAST, TOOTH_WIDTH_USUAL);

        // Toward the middle of the opening and down at the player, then off by however much
        var aim = new Vector3f(anchor.at).mul(-1, 0, -1).normalize().add(0, -0.7F, 0);
        aim.add(anchor.normal.x * 0.8F, anchor.normal.y * 0.8F, anchor.normal.z * 0.8F).normalize();
        if (random.nextFloat() < TOOTH_WRONG_SHARE)
            aim.set(anchor.normal).add(jitter(1.1F)).normalize();
        else
            aim.add(jitter(TOOTH_STRAY)).normalize();

        var bendAxis = jitter(1).normalize();
        float taper = spread(TOOTH_TAPER_LEAST, TOOTH_TAPER_MOST);
        float twist = random.nextFloat() * Mth.TWO_PI;
        float wide = Mth.clamp((width - TOOTH_WIDTH_USUAL) / (TOOTH_WIDTH_BIG - TOOTH_WIDTH_USUAL), 0, 1);
        float sink = width * Mth.lerp(wide, TOOTH_SINK_THIN, TOOTH_SINK_WIDE);
        float inward = width * wide * TOOTH_SINK_INWARD;
        var at = new Vector3f(anchor.at)
                .sub(aim.x * sink, aim.y * sink, aim.z * sink)
                .sub(anchor.normal.x * inward, anchor.normal.y * inward, anchor.normal.z * inward);
        var base = new Vector3f(at);
        float start = Mth.lerp(random.nextFloat(), BONE_FIRST, BONE_LAST - 0.08F);
        start = Math.max(start, Math.min(BONE_LAST - 0.08F, fleshTime(anchor.at) + FLESH_SPAN));

        float tint = spread(0.88F, 1.12F);
        for (int segment = 0; segment < TOOTH_SEGMENTS_MOST && width > TOOTH_TIP_WIDTH; segment++)
        {
            float length = width * TOOTH_LENGTH * spread(0.85F, 1.2F);
            float tipY = at.y + aim.y * length;
            float tipX = (at.x + aim.x * length) / tipY;
            float tipZ = (at.z + aim.z * length) / tipY;
            float clears = TOOTH_CLEARS + width * TOOTH_CLEARS_BY_WIDTH / tipY;
            if (tipX * tipX + tipZ * tipZ < clears * clears)
                break;
            var box = new Box();
            box.center = new Vector3f(at).add(aim.x * length / 2, aim.y * length / 2, aim.z * length / 2);
            box.rotation = aligned(aim, twist);
            box.half = new Vector3f(width / 2, length / 2, width / 2);
            box.faces = 0b111111;
            box.sprites = segment == 0 ? ROOT : SPUR;
            float shade = tint * BONE_TINT * spread(0.92F, 1.08F);
            box.tint = new Vector3f(shade);
            box.span = BONE_SPAN;
            box.writhe = WRITHE_BONE;
            box.corner = width;
            box.baseShade = BONE_BASE_SHADE;
            box.stainsAtBase = segment == 0;
            box.threshold = Math.min(BONE_LAST, start + segment * BONE_SEGMENT);
            box.origin = base;
            boxes.add(box);

            at.add(aim.x * length * 0.92F, aim.y * length * 0.92F, aim.z * length * 0.92F);
            width *= taper;
            aim.add(new Vector3f(bendAxis).mul(TOOTH_BEND)).normalize();
            bendAxis.add(jitter(0.12F)).normalize();
            twist += gaussian(0.3F);
        }
    }

    /**
     * A rotation taking +y onto the given direction, turned about it by twist.
     */
    private static Matrix3f aligned(Vector3f direction, float twist)
    {
        var up = new Vector3f(direction);
        var helper = Math.abs(up.y) > 0.9F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        var x = new Vector3f(helper).cross(up).normalize();
        var z = new Vector3f(x).cross(up).normalize();
        return new Matrix3f(x, up, z).rotateY(twist);
    }

    private void stretchStrands()
    {
        int made = 0;
        for (int attempt = 0; attempt < STRANDS * 40 && made < STRANDS; attempt++)
        {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float across = angle + Mth.PI * spread(0.55F, 1.1F);
            float ax = Mth.cos(angle) * shape.rim(angle);
            float az = Mth.sin(angle) * shape.rim(angle);
            float bx = Mth.cos(across) * shape.rim(across);
            float bz = Mth.sin(across) * shape.rim(across);

            // Clear of the very middle, so that what is down there stays in sight
            float dx = bx - ax;
            float dz = bz - az;
            float along = Mth.clamp(-(ax * dx + az * dz) / (dx * dx + dz * dz), 0, 1);
            float closestX = ax + dx * along;
            float closestZ = az + dz * along;
            if (closestX * closestX + closestZ * closestZ < STRAND_CLEARS * STRAND_CLEARS)
                continue;

            stretchStrand(ax, az, bx, bz);
            made++;
        }
    }

    private void stretchStrand(float ax, float az, float bx, float bz)
    {
        float heightA = spread(70, 96);
        float heightB = spread(70, 96);
        var from = new Vector3f(ax * 1.04F * heightA, heightA, az * 1.04F * heightA);
        var to = new Vector3f(bx * 1.04F * heightB, heightB, bz * 1.04F * heightB);
        float length = from.distance(to);
        int links = Mth.ceil(length / STRAND_LINK);
        var side = new Vector3f(to).sub(from).cross(0, 1, 0).normalize();
        float width = spread(STRAND_WIDTH_LEAST, STRAND_WIDTH_MOST);
        float phase = random.nextFloat() * 10;
        var previous = new Vector3f(from);
        for (int link = 1; link <= links; link++)
        {
            float t = link / (float) links;
            float bow = 4 * t * (1 - t);
            var point = new Vector3f(from).lerp(to, t);
            point.y -= bow * length * STRAND_SAG;
            float wander = (shape.noise(t * 7 + phase, phase) - 0.5F) * 2 * STRAND_WANDER * bow;
            point.add(side.x * wander, side.y * wander, side.z * wander);

            var direction = new Vector3f(point).sub(previous);
            float span = direction.length();
            direction.div(span);
            var box = new Box();
            box.center = new Vector3f(previous).add(point).mul(0.5F);
            box.rotation = aligned(direction, random.nextFloat() * Mth.TWO_PI);
            float thin = width * spread(0.75F, 1.2F) * (1.25F - bow * 0.55F);
            box.half = new Vector3f(thin / 2, span / 2 * 1.08F, thin / 2);
            box.faces = 0b111111;
            box.sprites = FIBER;
            box.tint = new Vector3f(spread(0.85F, 1.1F));
            box.span = FLESH_SPAN;
            box.writhe = WRITHE_STRAND;
            box.corner = thin;
            box.threshold = Mth.lerp(Math.abs(t - 0.5F) * 2, STRAND_LAST, STRAND_FIRST);
            box.origin = t < 0.5F ? from : to;
            boxes.add(box);
            previous = point;
        }
    }

    private void emit()
    {
        // Nearest first, so that the depth test spares most of what would otherwise be painted over
        boxes.sort(Comparator.comparingDouble((Box box) -> box.center.lengthSquared()));

        int faces = 0;
        for (var box : boxes)
            faces += Integer.bitCount(box.faces);
        floats = new float[faces * 4 * VERTEX_FLOATS];
        colors = new int[faces * 4];
        params = new int[faces * 4];
        sprites = new byte[faces];

        var normal = new Vector3f();
        var u = new Vector3f();
        var v = new Vector3f();
        var faceCenter = new Vector3f();
        for (var box : boxes)
        {
            float phase = random.nextFloat();
            for (int face = 0; face < 6; face++)
            {
                if ((box.faces & 1 << face) == 0)
                    continue;

                int[] dir = FACE_DIRS[face];
                int[] tan = FACE_TANGENTS[face];
                float normalHalf = dir[0] * box.half.x + dir[1] * box.half.y + dir[2] * box.half.z;
                float uHalf = tan[0] * box.half.x + tan[1] * box.half.y + tan[2] * box.half.z;
                float vHalf = Math.abs(dir[1] * tan[2] - dir[2] * tan[1]) * box.half.x
                        + Math.abs(dir[2] * tan[0] - dir[0] * tan[2]) * box.half.y
                        + Math.abs(dir[0] * tan[1] - dir[1] * tan[0]) * box.half.z;

                normal.set(dir[0], dir[1], dir[2]).mul(box.rotation);
                u.set(tan[0], tan[1], tan[2]).mul(box.rotation);
                v.set(normal).cross(u);
                faceCenter.set(box.center).add(normal.x * Math.abs(normalHalf), normal.y * Math.abs(normalHalf), normal.z * Math.abs(normalHalf));
                if (normal.dot(faceCenter) > FACING_SLACK * faceCenter.length())
                    continue;

                emitFace(box, normal, u, v, uHalf, vHalf, Math.abs(normalHalf), phase);
            }
        }

        // Bone and strands only learn here which of their faces turn away, so fewer may have come out than were allowed for
        floats = Arrays.copyOf(floats, quads * 4 * VERTEX_FLOATS);
        colors = Arrays.copyOf(colors, quads * 4);
        params = Arrays.copyOf(params, quads * 4);
        sprites = Arrays.copyOf(sprites, quads);
    }

    private void emitFace(Box box, Vector3f normal, Vector3f u, Vector3f v, float uHalf, float vHalf, float normalHalf, float phase)
    {
        int sprite = box.sprites[random.nextInt(box.sprites.length)];
        sprites[quads] = (byte) sprite;

        // Cropped rather than stretched on long faces, so that a texel stays square
        float longest = Math.max(uHalf, vHalf);
        float uSpan = uHalf / longest;
        float vSpan = vHalf / longest;
        float uFrom = random.nextFloat() * (1 - uSpan);
        float vFrom = random.nextFloat() * (1 - vSpan);
        int turn = uHalf == vHalf ? random.nextInt(4) : random.nextInt(2) * 2;
        boolean flip = random.nextBoolean();
        float uvFromAcross = random.nextFloat();

        float shade = normal.x * normal.x * FACE_X + normal.z * normal.z * FACE_Z + normal.y * normal.y * (normal.y > 0 ? FACE_TOP : FACE_BOTTOM);
        float light = shade * depthLight(box.center) * farLight(box.center);

        int packed = Math.round(Mth.clamp(box.threshold, 0, 1) * 255) | Math.round(box.span * 255) << 8 | Math.round(box.writhe * 255) << 16 | Math.round(phase * 255) << 24;

        for (int corner = 0; corner < 4; corner++)
        {
            float cu = CORNERS[corner][0];
            float cv = CORNERS[corner][1];
            float ox = normal.x * normalHalf + u.x * uHalf * cu + v.x * vHalf * cv;
            float oy = normal.y * normalHalf + u.y * uHalf * cu + v.y * vHalf * cv;
            float oz = normal.z * normalHalf + u.z * uHalf * cu + v.z * vHalf * cv;

            int index = quads * 4 + corner;
            int at = index * VERTEX_FLOATS;
            floats[at] = box.center.x;
            floats[at + 1] = box.center.y;
            floats[at + 2] = box.center.z;
            floats[at + 3] = ox;
            floats[at + 4] = oy;
            floats[at + 5] = oz;
            floats[at + 6] = box.origin.x;
            floats[at + 7] = box.origin.y;
            floats[at + 8] = box.origin.z;

            int uvCorner = flip ? (5 - corner + turn) % 4 : (corner + turn) % 4;
            floats[at + 9] = uFrom + (CORNERS[uvCorner][0] + 1) / 2 * uSpan;
            floats[at + 10] = vFrom + (CORNERS[uvCorner][1] + 1) / 2 * vSpan;

            float upward = box.rotation.m10 * ox + box.rotation.m11 * oy + box.rotation.m12 * oz;
            float uUp = Math.abs(box.rotation.m10 * u.x + box.rotation.m11 * u.y + box.rotation.m12 * u.z);
            float normalUp = Math.abs(box.rotation.m10 * normal.x + box.rotation.m11 * normal.y + box.rotation.m12 * normal.z);
            if (box.stainsAtBase && normalUp < 0.5F)
            {
                // The stain painted at the bottom of the texture goes where it comes out of the flesh, whichever way the texture was going to be turned
                boolean uAlong = uUp > 0.5F;
                float acrossSpan = uAlong ? vSpan : uSpan;
                float alongSpan = uAlong ? uSpan : vSpan;
                floats[at + 9] = uvFromAcross * (1 - acrossSpan) + ((uAlong ? cv : cu) + 1) / 2 * acrossSpan;
                floats[at + 10] = upward < 0 ? 1 : 1 - alongSpan;
            }

            float hemmed = cornerLight(box, normal, u, v, ox, oy, oz, cu, cv);
            if (box.baseShade != 1 && upward < 0)
                hemmed *= box.baseShade;
            float r = Mth.clamp(light * hemmed * box.tint.x, 0, 1);
            float g = Mth.clamp(light * hemmed * box.tint.y, 0, 1);
            float b = Mth.clamp(light * hemmed * box.tint.z, 0, 1);
            // Vertex formats read these little endian: red first
            colors[index] = Math.round(r * 255) | Math.round(g * 255) << 8 | Math.round(b * 255) << 16 | 0xFF000000;
            params[index] = packed;
        }
        quads++;
    }

    /**
     * As vanilla's smooth lighting darkens a corner: by what stands against it on the two sides and at the diagonal, except that what stands there can be any lump at all.
     */
    private float cornerLight(Box box, Vector3f normal, Vector3f u, Vector3f v, float ox, float oy, float oz, float cu, float cv)
    {
        float step = box.corner * 0.5F;
        float x = box.center.x + ox + normal.x * step;
        float y = box.center.y + oy + normal.y * step;
        float z = box.center.z + oz + normal.z * step;
        float ux = u.x * cu * step;
        float uy = u.y * cu * step;
        float uz = u.z * cu * step;
        float vx = v.x * cv * step;
        float vy = v.y * cv * step;
        float vz = v.z * cv * step;
        boolean side1 = solidAt(x + ux - vx, y + uy - vy, z + uz - vz);
        boolean side2 = solidAt(x - ux + vx, y - uy + vy, z - uz + vz);
        boolean diagonal = solidAt(x + ux + vx, y + uy + vy, z + uz + vz);
        int free = side1 && side2 ? 0 : 3 - (side1 ? 1 : 0) - (side2 ? 1 : 0) - (diagonal ? 1 : 0);
        return CORNER[free];
    }

    private static float depthLight(Vector3f at)
    {
        float deep = Mth.clamp((at.y - DEEP_FROM) / (DEEP_TO - DEEP_FROM), 0, 1);
        return Mth.lerp(deep * (2 - deep), 1, DEEPEST);
    }

    private float farLight(Vector3f at)
    {
        float x = at.x / at.y;
        float z = at.z / at.y;
        float rho = Mth.sqrt(x * x + z * z) / shape.rim((float) Mth.atan2(z, x));
        return Mth.lerp(Mth.clamp((rho - FAR_OUT_FROM) / (FAR_OUT_TO - FAR_OUT_FROM), 0, 1), 1, FAR_OUT_LIGHT);
    }

    private static final class Lump
    {
        private Vector3f center;
        private Matrix3f rotation;
        private Matrix3f inverse;
        private float    cell;
        private int      size;
        private int[]    sprites;
        private float    tint;
        private boolean  bruised;
        private boolean  intrudes;
        private boolean[] solid;
        private float[]   lift;
        private int       liftAxis;
        private int       liftSign;

        private int index(int x, int y, int z)
        {
            int side = size * 2 + 1;
            return (x + size) + (y + size) * side + (z + size) * side * side;
        }

        private boolean has(int x, int y, int z)
        {
            return Math.abs(x) <= size && Math.abs(y) <= size && Math.abs(z) <= size && solid[index(x, y, z)];
        }

        private boolean contains(float x, float y, float z)
        {
            float dx = x - center.x;
            float dy = y - center.y;
            float dz = z - center.z;
            float lx = inverse.m00 * dx + inverse.m10 * dy + inverse.m20 * dz;
            float ly = inverse.m01 * dx + inverse.m11 * dy + inverse.m21 * dz;
            float lz = inverse.m02 * dx + inverse.m12 * dy + inverse.m22 * dz;
            return has(Math.round(lx / cell), Math.round(ly / cell), Math.round(lz / cell));
        }
    }

    private record Anchor(Vector3f at, Vector3f normal, Lump lump)
    {
    }

    private static final class Box
    {
        private Vector3f center;
        private Vector3f origin;
        private Vector3f half;
        private Matrix3f rotation;
        private Vector3f tint;
        private int[]    sprites;
        private int      faces;
        private float    threshold;
        private float    span;
        private float    writhe;
        private float    corner;
        private float    baseShade = 1;
        private boolean  stainsAtBase;
    }
}
