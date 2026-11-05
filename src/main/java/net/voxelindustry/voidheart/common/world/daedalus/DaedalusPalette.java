package net.voxelindustry.voidheart.common.world.daedalus;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.voxelindustry.voidheart.common.content.daedalus.DaedalusPropBlock;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.EnumMap;
import java.util.Map;

import static net.voxelindustry.voidheart.common.setup.VoidHeartBlocks.*;
import static net.voxelindustry.voidheart.common.world.daedalus.DaedalusMaterial.*;

/**
 * Which blocks each theme is built from. An infinite supply of any of these can be mined, so sin metals and other crafted materials stay out, and lights are vanilla.
 */
public final class DaedalusPalette
{
    private static final Direction[] DIRECTIONS = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
    private static final Map<DaedalusTheme, DaedalusPalette> PALETTES = new EnumMap<>(DaedalusTheme.class);
    private static DaedalusPalette chasm;

    private final BlockState[][] options = new BlockState[DaedalusMaterial.values().length][];
    private final BlockState[] props = new BlockState[DaedalusProp.values().length * Compass.COUNT];

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
    public BlockState state(byte material, int x, int y, int z)
    {
        if (material >= DaedalusCanvas.PROP_BASE)
            return props[material - DaedalusCanvas.PROP_BASE];
        var variants = options[material];
        if (variants.length == 1)
            return variants[0];
        return variants[DaedalusHash.pick(DaedalusHash.mix(DaedalusHash.mix(x * 31L + y) + z), variants.length)];
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

    private void common()
    {
        set(AIR, Blocks.AIR);
        set(OPENING, Blocks.AIR);
        set(PROP, Blocks.AIR);
        set(FILL, VOIDSTONE);
        set(BOUND, POCKET_WALL);
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
        set(LAMP, Blocks.SEA_LANTERN);
        rails(VOIDSTONE_BRICKS_WALL);
        steps(VOIDSTONE_BRICKS_STAIRS, VOIDSTONE_BRICKS_SLAB);
    }

    private void rails(Block wall)
    {
        var post = wall.defaultBlockState();
        var bare = post.setValue(WallBlock.UP, false);
        setState(RAIL_POST, post);
        setState(RAIL_X, bare.setValue(WallBlock.EAST, WallSide.LOW).setValue(WallBlock.WEST, WallSide.LOW));
        setState(RAIL_Z, bare.setValue(WallBlock.NORTH, WallSide.LOW).setValue(WallBlock.SOUTH, WallSide.LOW));
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
        set(PANEL, VOIDSTONE_BRICKS_VERTICAL);
        set(TRIM, VOIDSTONE_CHISELED);
        set(FLOOR, VOIDSTONE_TILE_SMALL);
        set(FLOOR_TRIM, VOIDSTONE_TILE);
        set(CEILING, VOIDSTONE_POLISHED);
        set(SHELF, Blocks.BOOKSHELF);
        set(LAMP, Blocks.OCHRE_FROGLIGHT);
        set(FLOOR_INLAY, VOIDSTONE_POLISHED);
        set(CARPET, vanilla("red_carpet"));
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
        set(PANEL, VOIDSTONE_BRICKS);
        set(TRIM, Blocks.GILDED_BLACKSTONE);
        set(FLOOR, VOIDSTONE_TILE);
        set(FLOOR_TRIM, VOIDSTONE_POLISHED);
        set(FLOOR_INLAY, Blocks.GILDED_BLACKSTONE);
        set(CEILING, VOIDSTONE_TILE_SMALL);
        set(LAMP, Blocks.SHROOMLIGHT);
        set(CARPET, vanilla("purple_carpet"));
        steps(VOIDSTONE_POLISHED_STAIRS, VOIDSTONE_POLISHED_SLAB);
        rails(VOIDSTONE_POLISHED_WALL);
    }

    private void cistern()
    {
        set(WALL, ABYSSAL_ROCK, ABYSSAL_ROCK, VOIDSTONE_BRICKS);
        set(PANEL, VOIDSTONE_POLISHED);
        set(TRIM, VOIDSTONE_BRICKS_VERTICAL);
        set(FLOOR, VOIDSTONE_BRICKS, ABYSSAL_ROCK);
        set(FLOOR_TRIM, VOIDSTONE_POLISHED);
        set(CEILING, ABYSSAL_ROCK);
        set(COLUMN, VOIDSTONE_WEATHERED_PILLAR);
        set(LAMP, Blocks.VERDANT_FROGLIGHT);
        set(FLOOR_INLAY, VOIDSTONE_TILE);
        steps(ABYSSAL_ROCK_STAIRS, ABYSSAL_ROCK_SLAB);
        rails(ABYSSAL_ROCK_WALL);
    }

    private void stairwell()
    {
        set(WALL, VOIDSTONE_BRICKS_VERTICAL, VOIDSTONE_BRICKS_VERTICAL, VOIDSTONE_POLISHED);
        set(PANEL, VOIDSTONE_POLISHED);
        set(TRIM, Blocks.CHISELED_POLISHED_BLACKSTONE);
        set(FLOOR, VOIDSTONE_TILE);
        set(FLOOR_TRIM, VOIDSTONE_TILE_SMALL);
        set(FLOOR_INLAY, Blocks.CHISELED_POLISHED_BLACKSTONE);
        set(CARPET, vanilla("gray_carpet"));
        set(CEILING, VOIDSTONE_POLISHED);
        set(LAMP, Blocks.SEA_LANTERN);
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
        set(LAMP, Blocks.PEARLESCENT_FROGLIGHT);
    }
}
