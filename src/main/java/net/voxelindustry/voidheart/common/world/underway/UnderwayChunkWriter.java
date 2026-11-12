package net.voxelindustry.voidheart.common.world.underway;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.voxelindustry.voidheart.common.content.underway.UnderwayEmbrasureBlock;
import net.voxelindustry.voidheart.common.content.underway.UnderwayPropBlock;
import net.voxelindustry.voidheart.common.content.underway.UnderwayTrackBlock;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusHash;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;

/**
 * Writes the Underway into the lowest sections of a Daedalus chunk at the terrain step, straight into the sections as the districts above are.
 * <p>
 * Walls and ceilings are mostly concrete showing the boards it was poured between, floors mostly smooth, with stains and cracks here and there and many more cracks where the region collapsed.
 */
public final class UnderwayChunkWriter
{
    private static final int SECTION = 16;
    /** How many of a chunk's sections, from its lowest, belong to the Underway. */
    public static final int SECTIONS = UnderwayLevels.HEIGHT / SECTION;

    private static final Direction[] FACING = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
    private static final int PERCENT = 100;
    private static final int RIB_MASK = 7;
    private static final int RIB_AT = 4;
    private static final int WALL_CRACKED = 5;
    private static final int WALL_CRACKED_COLLAPSED = 25;
    private static final int WALL_FORMED = 70;
    private static final int WALL_STAINED = 10;
    private static final int FLOOR_CRACKED = 3;
    private static final int FLOOR_CRACKED_COLLAPSED = 15;
    private static final int FLOOR_STAINED = 12;
    /** Stains spread over patches of this many blocks rather than falling block by block. */
    private static final int STAIN = 3;
    private static final long SALT = 0x554E44455243L;
    private static final ThreadLocal<UnderwayCanvas> CANVAS = ThreadLocal.withInitial(() -> new UnderwayCanvas(SECTION, SECTION));
    private static BlockState[] props;
    /** Written last and volatile, so whoever sees it also sees the props. */
    private static volatile BlockState[] materials;

    private UnderwayChunkWriter()
    {
    }

    public static void write(ChunkAccess chunk, long seed)
    {
        if (materials == null)
            prepare();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        var canvas = CANVAS.get();
        canvas.reset(minX, minZ);
        UnderwayPlans.paint(seed, canvas);
        boolean collapsed = UnderwayNetwork.regionRuin(seed, DistrictPos.at(minX, minZ)) == UnderwayRuin.COLLAPSED;

        var sections = chunk.getSections();
        for (int index = 0; index < SECTIONS; index++)
        {
            var section = sections[index];
            int baseY = UnderwayLevels.MIN_Y + index * SECTION;
            section.acquire();
            try
            {
                for (int y = 0; y < SECTION; y++)
                {
                    for (int z = 0; z < SECTION; z++)
                    {
                        for (int x = 0; x < SECTION; x++)
                        {
                            var state = state(canvas, minX + x, baseY + y, minZ + z, collapsed);
                            if (state != null)
                                section.setBlockState(x, y, z, state, false);
                        }
                    }
                }
            } finally
            {
                section.release();
            }
        }
    }

    /** @return the block at a place of the canvas, or null for air */
    private static BlockState state(UnderwayCanvas canvas, int x, int y, int z, boolean collapsed)
    {
        var material = canvas.get(x, y, z);
        return switch (material)
        {
            case AIR ->
            {
                var prop = canvas.propAt(x, y, z);
                if (prop != null)
                    yield props[prop.ordinal() * FACING.length + canvas.propFacing(x, y, z)];
                yield tie(canvas, x, y, z);
            }
            case SHELL -> rib(canvas, x, y, z) ? materials[UnderwayMaterial.STEEL.ordinal()] : concrete(x, y, z, collapsed ? WALL_CRACKED_COLLAPSED : WALL_CRACKED, WALL_FORMED, WALL_STAINED);
            case FLOOR -> concrete(x, y, z, collapsed ? FLOOR_CRACKED_COLLAPSED : FLOOR_CRACKED, 0, FLOOR_STAINED);
            case TRUNK_RAIL_X, TRUNK_RAIL_Z, FEEDER_RAIL_X, FEEDER_RAIL_Z -> materials[material.ordinal()].setValue(UnderwayTrackBlock.WATERLOGGED, canvas.get(x, y + 1, z) == UnderwayMaterial.WATER);
            case DOOR ->
            {
                boolean alongX = canvas.get(x - 1, y, z).isOpen() || canvas.get(x + 1, y, z).isOpen();
                boolean upper = canvas.get(x, y - 1, z) == UnderwayMaterial.DOOR;
                yield materials[material.ordinal()].setValue(DoorBlock.FACING, alongX ? Direction.EAST : Direction.SOUTH).setValue(DoorBlock.HALF, upper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
            }
            case EMBRASURE ->
            {
                boolean alongX = canvas.get(x - 1, y, z).isOpen() || canvas.get(x + 1, y, z).isOpen();
                yield materials[material.ordinal()].setValue(UnderwayEmbrasureBlock.AXIS, alongX ? Direction.Axis.X : Direction.Axis.Z);
            }
            default -> materials[material.ordinal()];
        };
    }

    /** @return ties in the gap between the two rails of a trunk track, or null */
    private static BlockState tie(UnderwayCanvas canvas, int x, int y, int z)
    {
        if (canvas.get(x, y, z - 1) == UnderwayMaterial.TRUNK_RAIL_X && canvas.get(x, y, z + 1) == UnderwayMaterial.TRUNK_RAIL_X)
            return VoidHeartBlocks.UNDERWAY_TRACK_TIE.defaultBlockState().setValue(UnderwayTrackBlock.AXIS, Direction.Axis.X);
        if (canvas.get(x - 1, y, z) == UnderwayMaterial.TRUNK_RAIL_Z && canvas.get(x + 1, y, z) == UnderwayMaterial.TRUNK_RAIL_Z)
            return VoidHeartBlocks.UNDERWAY_TRACK_TIE.defaultBlockState();
        return null;
    }

    /** @return whether an iron rib crosses the stone here: one stands in the middle of every lattice cell, up the walls and across the ceilings */
    private static boolean rib(UnderwayCanvas canvas, int x, int y, int z)
    {
        boolean acrossX = (x & RIB_MASK) == RIB_AT;
        boolean acrossZ = (z & RIB_MASK) == RIB_AT;
        if (!acrossX && !acrossZ)
            return false;
        if (canvas.get(x, y - 1, z).isOpen())
            return true;
        return acrossX && (canvas.get(x, y, z - 1).isOpen() || canvas.get(x, y, z + 1).isOpen()) || acrossZ && (canvas.get(x - 1, y, z).isOpen() || canvas.get(x + 1, y, z).isOpen());
    }

    private static BlockState concrete(int x, int y, int z, int cracked, int formed, int stained)
    {
        if (DaedalusHash.pick(DaedalusHash.mix(Math.floorDiv(y, STAIN), Math.floorDiv(x, STAIN), Math.floorDiv(z, STAIN), SALT), PERCENT) < stained)
            return VoidHeartBlocks.UNDERWAY_STAINED_CONCRETE.defaultBlockState();
        int roll = DaedalusHash.pick(DaedalusHash.mix(y, x, z, SALT), PERCENT);
        if (roll < cracked)
            return VoidHeartBlocks.UNDERWAY_CRACKED_CONCRETE.defaultBlockState();
        return (roll < cracked + formed ? VoidHeartBlocks.UNDERWAY_FORMED_CONCRETE : VoidHeartBlocks.UNDERWAY_CONCRETE).defaultBlockState();
    }

    private static synchronized void prepare()
    {
        var all = UnderwayProp.values();
        var propStates = new BlockState[all.length * FACING.length];
        for (var prop : all)
        {
            for (int facing = 0; facing < FACING.length; facing++)
                propStates[prop.ordinal() * FACING.length + facing] = VoidHeartBlocks.underwayProp(prop).defaultBlockState().setValue(UnderwayPropBlock.FACING, FACING[facing]);
        }
        props = propStates;

        var states = new BlockState[UnderwayMaterial.values().length];
        var armour = VoidHeartBlocks.UNDERWAY_ARMOUR.defaultBlockState();
        var stairs = VoidHeartBlocks.UNDERWAY_STEEL_STAIRS.defaultBlockState();
        var trunk = VoidHeartBlocks.UNDERWAY_TRUNK_RAIL.defaultBlockState();
        var feeder = VoidHeartBlocks.UNDERWAY_FEEDER_RAIL.defaultBlockState();
        states[UnderwayMaterial.ROCK.ordinal()] = VoidHeartBlocks.POCKET_WALL.defaultBlockState();
        states[UnderwayMaterial.PLATFORM.ordinal()] = VoidHeartBlocks.UNDERWAY_FORMED_CONCRETE.defaultBlockState();
        states[UnderwayMaterial.BALLAST.ordinal()] = VoidHeartBlocks.UNDERWAY_BALLAST.defaultBlockState();
        states[UnderwayMaterial.STEEL.ordinal()] = armour;
        states[UnderwayMaterial.COLUMN.ordinal()] = armour;
        var hazard = VoidHeartBlocks.UNDERWAY_HAZARD_ARMOUR.defaultBlockState();
        states[UnderwayMaterial.HAZARD.ordinal()] = hazard;
        states[UnderwayMaterial.HAZARD_X.ordinal()] = hazard.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        states[UnderwayMaterial.HAZARD_Z.ordinal()] = hazard.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        states[UnderwayMaterial.GATE.ordinal()] = VoidHeartBlocks.UNDERWAY_BLAST_GATE.defaultBlockState();
        states[UnderwayMaterial.GRATING.ordinal()] = VoidHeartBlocks.UNDERWAY_GRATING.defaultBlockState();
        for (int direction = 0; direction < FACING.length; direction++)
            states[UnderwayMaterial.stairs(direction).ordinal()] = stairs.setValue(StairBlock.FACING, FACING[direction]);
        states[UnderwayMaterial.TRUNK_RAIL_X.ordinal()] = trunk.setValue(UnderwayTrackBlock.AXIS, Direction.Axis.X);
        states[UnderwayMaterial.TRUNK_RAIL_Z.ordinal()] = trunk;
        states[UnderwayMaterial.FEEDER_RAIL_X.ordinal()] = feeder.setValue(UnderwayTrackBlock.AXIS, Direction.Axis.X);
        states[UnderwayMaterial.FEEDER_RAIL_Z.ordinal()] = feeder;
        states[UnderwayMaterial.DOOR.ordinal()] = VoidHeartBlocks.UNDERWAY_DOOR.defaultBlockState();
        states[UnderwayMaterial.EMBRASURE.ordinal()] = VoidHeartBlocks.UNDERWAY_EMBRASURE.defaultBlockState();
        states[UnderwayMaterial.LAMP.ordinal()] = propStates[UnderwayProp.CEILING_LAMP_LIT.ordinal() * FACING.length];
        states[UnderwayMaterial.EMERGENCY_LAMP.ordinal()] = propStates[UnderwayProp.EMERGENCY_LAMP.ordinal() * FACING.length];
        states[UnderwayMaterial.RUBBLE.ordinal()] = VoidHeartBlocks.UNDERWAY_RUBBLE.defaultBlockState();
        states[UnderwayMaterial.WATER.ordinal()] = Blocks.WATER.defaultBlockState();
        states[UnderwayMaterial.VOID.ordinal()] = VoidHeartBlocks.VOIDSTONE_WRITHING.defaultBlockState();
        materials = states;
    }
}
