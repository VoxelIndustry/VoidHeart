package net.voxelindustry.voidheart.common.world.daedalus;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.voxelindustry.voidheart.common.block.StateProperties;
import net.voxelindustry.voidheart.common.block.VoidLampBlock;
import net.voxelindustry.voidheart.common.content.daedalus.DaedalusPropBlock;
import net.voxelindustry.voidheart.common.content.underway.UnderwayHatchBlock;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.EnumMap;
import java.util.Map;

import static net.voxelindustry.voidheart.common.setup.VoidHeartBlocks.*;
import static net.voxelindustry.voidheart.common.world.daedalus.DaedalusMaterial.*;

/**
 * Which blocks each theme is built from. An infinite supply of any of these can be mined, so sin metals stay out. The altars, pillars and lamps of the void's old rituals are cheap to craft anyway,
 * and the copper of the old machines is waxed so it never changes.
 */
public final class DaedalusPalette
{
    private static final Direction[] DIRECTIONS = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
    private static final Map<DaedalusTheme, DaedalusPalette> PALETTES = new EnumMap<>(DaedalusTheme.class);
    private static DaedalusPalette chasm;

    private final BlockState[][] options = new BlockState[DaedalusMaterial.values().length][];
    private final BlockState[] props = new BlockState[DaedalusProp.values().length * Compass.COUNT];
    private final BlockState[] railStates = new BlockState[1 << Compass.COUNT];

    private DaedalusPalette()
    {
        common();
        for (var prop : DaedalusProp.values())
        {
            var block = VoidHeartBlocks.daedalusProp(prop);
            for (int facing = 0; facing < Compass.COUNT; facing++)
                props[prop.ordinal() * Compass.COUNT + facing] = block.defaultBlockState().setValue(DaedalusPropBlock.FACING, DIRECTIONS[facing]);
        }
    }

    public static synchronized DaedalusPalette of(DaedalusTheme theme)
    {
        return PALETTES.computeIfAbsent(theme, DaedalusPalette::forTheme);
    }

    public static synchronized DaedalusPalette chasm()
    {
        if (chasm == null)
            chasm = new DaedalusPalette();
        return chasm;
    }

    private static DaedalusPalette forTheme(DaedalusTheme theme)
    {
        var palette = new DaedalusPalette();
        switch (theme)
        {
            case ARCHIVE -> palette.archive();
            case WARREN -> palette.warren();
            case GALLERY -> palette.gallery();
            case CISTERN -> palette.cistern();
            case STAIRWELL -> palette.stairwell();
            case WRITHING -> palette.writhing();
        }
        return palette;
    }

    /**
     * @param x y z world position, deciding which block a mixed material becomes
     */
    public BlockState state(int material, int x, int y, int z)
    {
        if (material >= DaedalusCanvas.PROP_BASE)
            return props[material - DaedalusCanvas.PROP_BASE];
        if (material == HATCH.ordinal())
            return hatch(x, y, z);
        var variants = options[material];
        if (variants.length == 1)
            return variants[0];
        return variants[DaedalusHash.pick(DaedalusHash.mix(DaedalusHash.mix(x * 31L + y) + z), variants.length)];
    }

    /**
     * Panels over open air are ceiling beams, which take the theme's plain panel so engravings only show on walls.
     *
     * @param below the material under this position, {@link DaedalusMaterial#AIR} at the bottom of the canvas
     */
    public BlockState state(int material, int below, int x, int y, int z)
    {
        if (material == DaedalusMaterial.PANEL.ordinal() && below == DaedalusMaterial.AIR.ordinal())
            return options[material][0];
        return state(material, x, y, z);
    }

    /**
     * The hatch fills a cell of the lattice, so its parts follow from the place alone: a striped coaming, its band going round two leaves that meet along the middle. Under the floor it is plain plate.
     */
    private static BlockState hatch(int x, int y, int z)
    {
        int u = Math.floorMod(x, DistrictPos.PITCH) % DistrictPos.CELL;
        int v = Math.floorMod(z, DistrictPos.PITCH) % DistrictPos.CELL;
        int last = DistrictPos.CELL - 1;
        var plate = UNDERWAY_HATCH.defaultBlockState();
        if (y < DistrictPos.floorY(0))
            return plate;
        boolean alongZ = u == 0 || u == last;
        boolean alongX = v == 0 || v == last;
        if (alongX || alongZ)
        {
            // The coaming's band follows each side, and its corners are plain caps
            var coaming = UNDERWAY_HAZARD_ARMOUR.defaultBlockState();
            return alongX && alongZ ? coaming : coaming.setValue(RotatedPillarBlock.AXIS, alongX ? Direction.Axis.X : Direction.Axis.Z);
        }
        if (u == last / 2)
            return plate.setValue(UnderwayHatchBlock.FACING, Direction.EAST);
        return u == last / 2 + 1 ? plate.setValue(UnderwayHatchBlock.FACING, Direction.WEST) : plate;
    }

    public boolean covers(DaedalusMaterial material)
    {
        return material == DaedalusMaterial.PROP ? java.util.Arrays.stream(props).allMatch(java.util.Objects::nonNull) : options[material.ordinal()] != null;
    }

    private void set(DaedalusMaterial material, Block... blocks)
    {
        var states = new BlockState[blocks.length];
        for (int index = 0; index < blocks.length; index++)
            states[index] = blocks[index].defaultBlockState();
        options[material.ordinal()] = states;
    }

    /**
     * Dyed blocks have no field of their own in this version.
     */
    private static Block vanilla(String name)
    {
        return BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(name)).orElseThrow(() -> new IllegalStateException("Unknown block " + name));
    }

    private void setState(DaedalusMaterial material, BlockState state)
    {
        options[material.ordinal()] = new BlockState[] { state };
    }

    private void setStates(DaedalusMaterial material, BlockState... states)
    {
        options[material.ordinal()] = states;
    }

    private void common()
    {
        set(AIR, Blocks.AIR);
        set(OPENING, Blocks.AIR);
        set(PROP, Blocks.AIR);
        set(FILL, VOIDSTONE);
        set(BOUND, POCKET_WALL);
        set(HATCH, UNDERWAY_HATCH);
        set(WATER, Blocks.WATER);
        set(CHAIN, Blocks.IRON_CHAIN);
        set(COLUMN, VOIDSTONE_PILLAR);
        set(SHELF, VOIDSTONE_POLISHED);
        set(FACADE, VOIDSTONE_BRICKS, VOIDSTONE_BRICKS, VOIDSTONE_BRICKS, VOIDSTONE_BRICKS_CRACKED);
        set(FACADE_TRIM, VOIDSTONE_POLISHED);
        set(WALL, VOIDSTONE_BRICKS);
        set(PANEL, VOIDSTONE_POLISHED);
        set(TRIM, VOIDSTONE_POLISHED);
        set(FLOOR, VOIDSTONE_TILE);
        set(FLOOR_TRIM, VOIDSTONE_TILE_SMALL);
        set(FLOOR_INLAY, VOIDSTONE_TILE_SMALL_CHISELED);
        set(CARPET, Blocks.AIR);
        set(CEILING, VOIDSTONE_BRICKS);
        set(LAMP, CAGED_LAMP_PALE);
        rails(VOIDSTONE_BRICKS_WALL);
        steps(VOIDSTONE_BRICKS_STAIRS, VOIDSTONE_BRICKS_SLAB);
        relics();
    }

    private void relics()
    {
        setState(ALTAR, VOID_ALTAR.defaultBlockState());
        setState(PILLAR, VOID_PILLAR.defaultBlockState());
        setStates(SHRINE_LAMP, voidLamp(2), voidLamp(3), voidLamp(4));
        var monolith = VOID_MONOLITH.defaultBlockState().setValue(StateProperties.BROKEN, true);
        setState(MONOLITH_ALONE, monolith);
        setState(MONOLITH_BASE, monolith.setValue(StateProperties.UP, true));
        setState(MONOLITH_SHAFT, monolith.setValue(StateProperties.UP, true).setValue(StateProperties.DOWN, true));
        setState(MONOLITH_TIP, monolith.setValue(StateProperties.DOWN, true));
        set(ELDRITCH, ELDRITCH_VOIDSTONE);
        set(OBSIDIAN, Blocks.OBSIDIAN, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN);
        set(COBWEB, Blocks.COBWEB);
        set(COPPER, vanilla("waxed_oxidized_copper"), vanilla("waxed_oxidized_copper"), vanilla("waxed_weathered_copper"), vanilla("waxed_oxidized_cut_copper"));
        setState(COPPER_SLAB, vanilla("waxed_oxidized_cut_copper_slab").defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        set(VENT, vanilla("waxed_oxidized_copper_grate"));
        var rod = vanilla("waxed_oxidized_lightning_rod").defaultBlockState();
        setState(PIPE_X, rod.setValue(BlockStateProperties.FACING, Direction.EAST));
        setState(PIPE_Y, rod.setValue(BlockStateProperties.FACING, Direction.UP));
        setState(PIPE_Z, rod.setValue(BlockStateProperties.FACING, Direction.SOUTH));
        setState(CACHE, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
    }

    private static BlockState voidLamp(int corruption)
    {
        return VOID_LAMP.defaultBlockState().setValue(VoidLampBlock.CORRUPTION, corruption);
    }

    private void rails(Block wall)
    {
        var post = wall.defaultBlockState();
        var bare = post.setValue(WallBlock.UP, false);
        setState(RAIL_POST, post);
        setState(RAIL_X, bare.setValue(WallBlock.EAST, WallSide.LOW).setValue(WallBlock.WEST, WallSide.LOW));
        setState(RAIL_Z, bare.setValue(WallBlock.NORTH, WallSide.LOW).setValue(WallBlock.SOUTH, WallSide.LOW));
        for (int sides = 0; sides < railStates.length; sides++)
        {
            boolean north = (sides & 1 << Compass.NORTH) != 0;
            boolean east = (sides & 1 << Compass.EAST) != 0;
            boolean south = (sides & 1 << Compass.SOUTH) != 0;
            boolean west = (sides & 1 << Compass.WEST) != 0;
            boolean straight = north == south && east == west && sides != 0;
            railStates[sides] = post.setValue(WallBlock.NORTH, north ? WallSide.LOW : WallSide.NONE)
                                    .setValue(WallBlock.EAST, east ? WallSide.LOW : WallSide.NONE)
                                    .setValue(WallBlock.SOUTH, south ? WallSide.LOW : WallSide.NONE)
                                    .setValue(WallBlock.WEST, west ? WallSide.LOW : WallSide.NONE)
                                    .setValue(WallBlock.UP, !straight);
        }
    }

    public static boolean isRail(int material)
    {
        return material == RAIL_X.ordinal() || material == RAIL_Z.ordinal() || material == RAIL_POST.ordinal();
    }

    /**
     * The rail joined to the sides in a {@link Compass} bit mask, shaped as a placed wall would be with open air above it: a post wherever it ends or turns.
     */
    public BlockState rail(int sides)
    {
        return railStates[sides];
    }

    private void steps(Block stairs, Block slab)
    {
        setState(SLAB, slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        setState(SLAB_TOP, slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        for (int direction = 0; direction < DIRECTIONS.length; direction++)
        {
            var facing = stairs.defaultBlockState().setValue(StairBlock.FACING, DIRECTIONS[direction]);
            setState(stairs(direction), facing.setValue(StairBlock.HALF, Half.BOTTOM));
            setState(arch(direction), facing.setValue(StairBlock.HALF, Half.TOP));
        }
    }

    private void archive()
    {
        set(WALL, VOIDSTONE_POLISHED, VOIDSTONE_POLISHED, VOIDSTONE_POLISHED, VOIDSTONE_BRICKS_VERTICAL);
        set(PANEL, VOIDSTONE_BRICKS_VERTICAL, VOIDSTONE_BRICKS_VERTICAL, VOIDSTONE_BRICKS_VERTICAL, VOIDSTONE_BRICKS_VERTICAL, VOIDSTONE_ENGRAVED_EYE);
        set(TRIM, VOIDSTONE_CHISELED);
        set(FLOOR, VOIDSTONE_TILE_SMALL);
        set(FLOOR_TRIM, VOIDSTONE_TILE);
        set(CEILING, VOIDSTONE_POLISHED);
        set(SHELF, VOIDSTONE_BOOKSHELF, VOIDSTONE_BOOKSHELF, VOIDSTONE_BOOKSHELF, RUINED_VOIDSTONE_BOOKSHELF);
        set(LAMP, CAGED_LAMP_AMBER);
        set(FLOOR_INLAY, VOIDSTONE_POLISHED);
        set(CARPET, UMBRAL_RUG);
    }

    private void warren()
    {
        set(WALL, VOIDSTONE, VOIDSTONE, VOIDSTONE, VOIDSTONE_BRICKS_CRACKED, VOIDSTONE_BRICKS_CRACKED);
        set(PANEL, VOIDSTONE_BRICKS_CRACKED);
        set(TRIM, VOIDSTONE_BRICKS_CRACKED);
        set(FLOOR, VOIDSTONE, VOIDSTONE_TILE_SMALL, VOIDSTONE_BRICKS_CRACKED);
        set(CEILING, VOIDSTONE);
        set(LAMP, Blocks.CRYING_OBSIDIAN);
        set(FLOOR_INLAY, VOIDSTONE_BRICKS_CRACKED);
        steps(VOIDSTONE_BRICKS_CRACKED_STAIRS, VOIDSTONE_BRICKS_CRACKED_SLAB);
    }

    private void gallery()
    {
        set(WALL, VOIDSTONE_POLISHED);
        set(PANEL, VOIDSTONE_BRICKS, VOIDSTONE_BRICKS, VOIDSTONE_BRICKS, VOIDSTONE_ENGRAVED_EYE);
        set(TRIM, BRONZE_INLAID_VOIDSTONE);
        set(FLOOR, VOIDSTONE_TILE);
        set(FLOOR_TRIM, VOIDSTONE_POLISHED);
        set(FLOOR_INLAY, BRONZE_INLAID_VOIDSTONE);
        set(CEILING, VOIDSTONE_TILE_SMALL);
        set(LAMP, CAGED_LAMP_AMBER);
        set(CARPET, VIOLET_RUG);
        steps(VOIDSTONE_POLISHED_STAIRS, VOIDSTONE_POLISHED_SLAB);
        rails(VOIDSTONE_POLISHED_WALL);
    }

    private void cistern()
    {
        set(WALL, ABYSSAL_ROCK, ABYSSAL_ROCK, VOIDSTONE_BRICKS);
        set(PANEL, VOIDSTONE_POLISHED, VOIDSTONE_POLISHED, VOIDSTONE_POLISHED, VOIDSTONE_ENGRAVED_GEAR);
        set(TRIM, VOIDSTONE_BRICKS_VERTICAL);
        set(FLOOR, VOIDSTONE_BRICKS, ABYSSAL_ROCK);
        set(FLOOR_TRIM, VOIDSTONE_POLISHED);
        set(CEILING, ABYSSAL_ROCK);
        set(COLUMN, VOIDSTONE_WEATHERED_PILLAR);
        set(LAMP, CAGED_LAMP_TEAL);
        set(FLOOR_INLAY, VOIDSTONE_TILE);
        steps(ABYSSAL_ROCK_STAIRS, ABYSSAL_ROCK_SLAB);
        rails(ABYSSAL_ROCK_WALL);
    }

    private void stairwell()
    {
        set(WALL, VOIDSTONE_BRICKS_VERTICAL, VOIDSTONE_BRICKS_VERTICAL, VOIDSTONE_POLISHED);
        set(PANEL, VOIDSTONE_POLISHED, VOIDSTONE_POLISHED, VOIDSTONE_POLISHED, VOIDSTONE_ENGRAVED_GEAR);
        set(TRIM, IRON_INLAID_VOIDSTONE);
        set(FLOOR, VOIDSTONE_TILE);
        set(FLOOR_TRIM, VOIDSTONE_TILE_SMALL);
        set(CARPET, TEAL_RUG);
        set(CEILING, VOIDSTONE_POLISHED);
        set(LAMP, CAGED_LAMP_PALE);
        steps(VOIDSTONE_POLISHED_STAIRS, VOIDSTONE_POLISHED_SLAB);
        rails(VOIDSTONE_POLISHED_WALL);
    }

    private void writhing()
    {
        set(WALL, VOIDSTONE_WRITHING, VOIDSTONE_WRITHING, VOIDSTONE_WRITHING, VOIDSTONE_WRITHING, ELDRITCH_VOIDSTONE, VOIDSTONE_WRITHING_BLOSSOM, VOIDSTONE_WRITHING_MAW);
        set(PANEL, ELDRITCH_VOIDSTONE);
        set(TRIM, VOIDSTONE_WRITHING_MAW);
        set(FLOOR, VOIDSTONE_WRITHING, VOIDSTONE_WRITHING, VOIDSTONE, ELDRITCH_VOIDSTONE);
        set(FLOOR_TRIM, ELDRITCH_VOIDSTONE);
        set(FLOOR_INLAY, VOIDSTONE_WRITHING_MAW);
        set(CEILING, VOIDSTONE_WRITHING, VOIDSTONE_WRITHING, VOIDSTONE_WRITHING, VOIDSTONE_WRITHING_MAW, VOIDSTONE_WRITHING_BLOSSOM);
        setStates(LAMP, voidLamp(5), voidLamp(5), voidLamp(6));
        setStates(SHRINE_LAMP, voidLamp(4), voidLamp(5), voidLamp(6));
    }
}
