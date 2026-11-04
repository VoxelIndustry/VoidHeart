package net.voxelindustry.voidheart.common.world.dungeon;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.voxelindustry.voidheart.common.block.StateProperties;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial.*;

/**
 * Which blocks make up each theme. A material with several blocks picks among them by position, so a wall mixes intact and cracked stone.
 */
public final class DungeonPalette
{
    private static final int METAL_REGION_BITS = 4;
    private static final int GROUND_REGION_BITS = 2;
    private static final int FILL_LIGHT_LEVEL = 12;
    private static final int DARK_STONE_FILL_LIGHT_LEVEL = 15;
    private static final int ORIENTATIONS = Direction.values().length * 2;
    private static final Map<DungeonTheme, DungeonPalette> PALETTES = new EnumMap<>(DungeonTheme.class);

    private static final Map<Block, DungeonMaterial> TEMPLATE_MATERIALS = new HashMap<>();

    private final BlockState[][][] options = new BlockState[DungeonMaterial.values().length][][];
    private final DungeonTheme theme;

    private DungeonPalette(DungeonTheme theme)
    {
        common();
        switch (theme)
        {
            case CATHEDRAL -> cathedral();
            case FOUNDRY -> foundry();
            case PALACE -> palace();
            case TEMPLE -> temple();
            case FOREST -> forest();
            case MYCOSIS -> mycosis();
        }
        if (options[PANEL.ordinal()] == null)
            options[PANEL.ordinal()] = options[WALL_TRIM.ordinal()];
        if (options[MONOLITH.ordinal()] == null)
            options[MONOLITH.ordinal()] = options[COLUMN.ordinal()];
        this.theme = theme;
    }

    public static synchronized DungeonPalette of(DungeonTheme theme)
    {
        return PALETTES.computeIfAbsent(theme, DungeonPalette::new);
    }

    /**
     * A block of one of the hand built pockets, in this theme's materials and keeping its shape. Void temples keep them as they were built.
     */
    public BlockState redraw(BlockState original, int x, int y, int z)
    {
        var material = theme == DungeonTheme.TEMPLE ? null : templateMaterial(original.getBlock());
        if (material == null)
            return original;

        var variants = options[material.ordinal()];
        return variants[pick(material, variants.length, x, y, z)][0].getBlock().withPropertiesOf(original);
    }

    private static synchronized DungeonMaterial templateMaterial(Block block)
    {
        if (TEMPLATE_MATERIALS.isEmpty())
        {
            TEMPLATE_MATERIALS.put(VoidHeartBlocks.VOIDSTONE_BRICKS, WALL);
            TEMPLATE_MATERIALS.put(VoidHeartBlocks.VOIDSTONE_POLISHED, PANEL);
            TEMPLATE_MATERIALS.put(VoidHeartBlocks.VOIDSTONE_TILE_SMALL, FLOOR);
            TEMPLATE_MATERIALS.put(VoidHeartBlocks.VOIDSTONE_TILE, FLOOR_TRIM);
            TEMPLATE_MATERIALS.put(VoidHeartBlocks.VOIDSTONE_TILE_SMALL_SLAB, SLAB);
            TEMPLATE_MATERIALS.put(VoidHeartBlocks.VOIDSTONE_BRICKS_SLAB, SLAB);
            TEMPLATE_MATERIALS.put(VoidHeartBlocks.VOIDSTONE_BRICKS_STAIRS, STAIRS);
            TEMPLATE_MATERIALS.put(VoidHeartBlocks.VOIDSTONE_BRICKS_WALL, FENCE);
            TEMPLATE_MATERIALS.put(VoidHeartBlocks.VOIDSTONE_PILLAR, COLUMN);
            TEMPLATE_MATERIALS.put(Blocks.POLISHED_BLACKSTONE, PANEL);
            TEMPLATE_MATERIALS.put(Blocks.POLISHED_BLACKSTONE_BRICKS, WALL);
            TEMPLATE_MATERIALS.put(Blocks.CHISELED_POLISHED_BLACKSTONE, WALL_TRIM);
            TEMPLATE_MATERIALS.put(Blocks.POLISHED_BLACKSTONE_STAIRS, STAIRS);
            TEMPLATE_MATERIALS.put(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, STAIRS);
            TEMPLATE_MATERIALS.put(Blocks.POLISHED_BLACKSTONE_SLAB, SLAB);
            TEMPLATE_MATERIALS.put(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, SLAB);
            TEMPLATE_MATERIALS.put(Blocks.NETHER_BRICKS, WALL);
            TEMPLATE_MATERIALS.put(Blocks.CRACKED_NETHER_BRICKS, WALL);
            TEMPLATE_MATERIALS.put(Blocks.NETHER_WART_BLOCK, WALL_TRIM);
            TEMPLATE_MATERIALS.put(Blocks.SOUL_LANTERN, LANTERN);
        }
        return TEMPLATE_MATERIALS.get(block);
    }

    public BlockState window(int x, int y, int z)
    {
        var variants = options[WINDOW.ordinal()];
        return variants[pick(WINDOW, variants.length, x, y, z)][0];
    }

    /**
     * @param x y z world position, deciding which block a mixed material becomes
     */
    public BlockState state(short cell, int x, int y, int z)
    {
        var material = DungeonCell.material(cell);
        var variants = options[material.ordinal()];
        int orientation = DungeonCell.facing(cell).ordinal() * 2 + (DungeonCell.flipped(cell) ? 1 : 0);
        return variants[pick(material, variants.length, x, y, z)][orientation];
    }

    private static int pick(DungeonMaterial material, int count, int x, int y, int z)
    {
        if (count == 1)
            return 0;

        int bits = regionBits(material);
        long hash = (x >> bits) * 73856093L ^ (y >> bits) * 19349663L ^ (z >> bits) * 83492791L;
        hash ^= hash >>> 13;
        return (int) Math.floorMod(hash * 0x5DEECE66DL >>> 16, count);
    }

    /**
     * How large the patches of one block are: a wall can be speckled, but ground and metal fittings read better in larger pieces.
     */
    private static int regionBits(DungeonMaterial material)
    {
        return switch (material)
        {
            case METAL_BLOCK, METAL_CUT, METAL_STAIRS, METAL_SLAB, METAL_WALL -> METAL_REGION_BITS;
            case ROCK_NATIVE, SOIL, SUBSOIL, LEAVES -> GROUND_REGION_BITS;
            case LOG -> METAL_REGION_BITS;
            default -> 0;
        };
    }

    /**
     * Each block counts once per repeat in the list, which is how a material favours one of its blocks.
     */
    private void set(DungeonMaterial material, Block... blocks)
    {
        var states = new ArrayList<BlockState>();
        for (var block : blocks)
            states.add(baseState(block));
        setStates(material, states);
    }

    private void setStates(DungeonMaterial material, List<BlockState> states)
    {
        var variants = new BlockState[states.size()][];
        for (int variant = 0; variant < variants.length; variant++)
        {
            variants[variant] = new BlockState[ORIENTATIONS];
            for (var facing : Direction.values())
            {
                variants[variant][facing.ordinal() * 2] = oriented(states.get(variant), facing, false);
                variants[variant][facing.ordinal() * 2 + 1] = oriented(states.get(variant), facing, true);
            }
        }
        options[material.ordinal()] = variants;
    }

    /**
     * Dyed and weathering blocks have no field of their own in this version.
     */
    private static Block vanilla(String name)
    {
        return BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(name))
                .orElseThrow(() -> new IllegalStateException("Unknown block " + name));
    }

    private static BlockState baseState(Block block)
    {
        var state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.LIT))
            state = state.setValue(BlockStateProperties.LIT, true);
        if (state.hasProperty(BlockStateProperties.PERSISTENT))
            state = state.setValue(BlockStateProperties.PERSISTENT, true);
        if (state.hasProperty(BlockStateProperties.BELL_ATTACHMENT))
            state = state.setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.CEILING);
        return state;
    }

    private static BlockState oriented(BlockState state, Direction facing, boolean flipped)
    {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && facing.getAxis().isHorizontal())
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        if (state.hasProperty(BlockStateProperties.FACING))
            state = state.setValue(BlockStateProperties.FACING, facing);
        if (state.hasProperty(BlockStateProperties.AXIS))
            state = state.setValue(BlockStateProperties.AXIS, facing.getAxis());
        if (state.hasProperty(BlockStateProperties.HALF))
            state = state.setValue(BlockStateProperties.HALF, flipped ? Half.TOP : Half.BOTTOM);
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE))
            state = state.setValue(BlockStateProperties.SLAB_TYPE, flipped ? SlabType.TOP : SlabType.BOTTOM);
        if (state.hasProperty(BlockStateProperties.HANGING))
            state = state.setValue(BlockStateProperties.HANGING, flipped);
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF))
            state = state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, flipped ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
        return state;
    }

    private void metals(Block[]... metals)
    {
        var blocks = new ArrayList<Block>();
        var cut = new ArrayList<Block>();
        var stairs = new ArrayList<Block>();
        var slabs = new ArrayList<Block>();
        var walls = new ArrayList<Block>();
        for (var metal : metals)
        {
            blocks.add(metal[0]);
            cut.add(metal[1]);
            stairs.add(metal[2]);
            slabs.add(metal[3]);
            walls.add(metal[4]);
        }
        set(METAL_BLOCK, blocks.toArray(Block[]::new));
        set(METAL_CUT, cut.toArray(Block[]::new));
        set(METAL_STAIRS, stairs.toArray(Block[]::new));
        set(METAL_SLAB, slabs.toArray(Block[]::new));
        set(METAL_WALL, walls.toArray(Block[]::new));
    }

    private static Block[] arrogantIron()
    {
        return new Block[] { VoidHeartBlocks.ARROGANT_IRON_BLOCK, VoidHeartBlocks.CUT_ARROGANT_IRON, VoidHeartBlocks.CUT_ARROGANT_IRON_STAIRS, VoidHeartBlocks.CUT_ARROGANT_IRON_SLAB, VoidHeartBlocks.CUT_ARROGANT_IRON_WALL };
    }

    private static Block[] ravenousGold()
    {
        return new Block[] { VoidHeartBlocks.RAVENOUS_GOLD_BLOCK, VoidHeartBlocks.CUT_RAVENOUS_GOLD, VoidHeartBlocks.CUT_RAVENOUS_GOLD_STAIRS, VoidHeartBlocks.CUT_RAVENOUS_GOLD_SLAB, VoidHeartBlocks.CUT_RAVENOUS_GOLD_WALL };
    }

    private static Block[] seethingSteel()
    {
        return new Block[] { VoidHeartBlocks.SEETHING_STEEL_BLOCK, VoidHeartBlocks.CUT_SEETHING_STEEL, VoidHeartBlocks.CUT_SEETHING_STEEL_STAIRS, VoidHeartBlocks.CUT_SEETHING_STEEL_SLAB, VoidHeartBlocks.CUT_SEETHING_STEEL_WALL };
    }

    private static Block[] covetousCopper()
    {
        return new Block[] { VoidHeartBlocks.COVETOUS_COPPER_BLOCK, VoidHeartBlocks.CUT_COVETOUS_COPPER, VoidHeartBlocks.CUT_COVETOUS_COPPER_STAIRS, VoidHeartBlocks.CUT_COVETOUS_COPPER_SLAB, VoidHeartBlocks.CUT_COVETOUS_COPPER_WALL };
    }

    private static Block[] listlessLead()
    {
        return new Block[] { VoidHeartBlocks.LISTLESS_LEAD_BLOCK, VoidHeartBlocks.CUT_LISTLESS_LEAD, VoidHeartBlocks.CUT_LISTLESS_LEAD_STAIRS, VoidHeartBlocks.CUT_LISTLESS_LEAD_SLAB, VoidHeartBlocks.CUT_LISTLESS_LEAD_WALL };
    }

    /**
     * What every theme shares, and what most never need to change.
     */
    private void common()
    {
        set(UNSET, Blocks.AIR);
        set(TEMPLATE, Blocks.AIR);
        set(AIR, Blocks.AIR);
        set(POCKET_WALL, VoidHeartBlocks.POCKET_WALL);
        set(ROCK, VoidHeartBlocks.VOIDSTONE);
        set(ROCK_NATIVE, VoidHeartBlocks.VOIDSTONE);
        set(VEIN, VoidHeartBlocks.ELDRITCH_VOIDSTONE);
        set(VEIN_WRITHING, VoidHeartBlocks.VOIDSTONE_WRITHING);
        set(VEIN_BLOSSOM, VoidHeartBlocks.VOIDSTONE_WRITHING_BLOSSOM);
        set(VEIN_MAW, VoidHeartBlocks.VOIDSTONE_WRITHING_MAW);
        set(POOL_BED, VoidHeartBlocks.VOIDSTONE_POLISHED);
        set(NEST, VoidHeartBlocks.BARNACLE_NEST);

        set(LANTERN, Blocks.LANTERN);
        set(CANDLE, Blocks.CANDLE);
        set(GLOW, Blocks.GLOWSTONE);
        set(CAMPFIRE, Blocks.CAMPFIRE);
        setStates(FILL_LIGHT, List.of(Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, FILL_LIGHT_LEVEL)));
        set(CHAIN, Blocks.IRON_CHAIN);
        set(FLUID, Blocks.WATER);

        set(SOIL, Blocks.GRASS_BLOCK);
        set(SUBSOIL, Blocks.DIRT);
        set(PLANKS, Blocks.OAK_PLANKS);
        set(FRAME, Blocks.OAK_LOG);
        set(ROOF_STAIRS, Blocks.SPRUCE_STAIRS);
        set(ROOF_SLAB, Blocks.SPRUCE_SLAB);
        set(WOOD_FENCE, Blocks.OAK_FENCE);
        set(DOOR, Blocks.OAK_DOOR);
        set(LOG, Blocks.OAK_LOG);
        set(LEAVES, Blocks.OAK_LEAVES);
        set(ROOTS, Blocks.MANGROVE_ROOTS);
        set(HANGING, Blocks.HANGING_ROOTS);
        set(PLANT, Blocks.SHORT_GRASS);
        set(TALL_PLANT, Blocks.TALL_GRASS);
        set(CAP, Blocks.RED_MUSHROOM_BLOCK);
        set(CAP_GLOW, Blocks.SHROOMLIGHT);
        set(CAP_GILL, Blocks.MAGMA_BLOCK);
        set(STEM, Blocks.MUSHROOM_STEM);

        set(BOOKSHELF, Blocks.BOOKSHELF);
        set(LECTERN, Blocks.LECTERN);
        set(FURNACE, Blocks.FURNACE);
        set(FORGE, Blocks.BLAST_FURNACE);
        set(ANVIL, Blocks.DAMAGED_ANVIL);
        set(CAULDRON, Blocks.CAULDRON);
        set(BARREL, Blocks.BARREL);
        set(HAY, Blocks.HAY_BLOCK);
        set(COBWEB, Blocks.COBWEB);
        set(BELL, Blocks.BELL);
        set(PIPE, Blocks.IRON_BLOCK);
        set(PIPE_THIN, Blocks.IRON_BARS);
        set(GRATE, Blocks.IRON_BARS);
        set(CHEST, Blocks.CHEST);

        set(AZURIUM_SMALL, VoidHeartBlocks.SMALL_AZURIUM_BUNDLE);
        set(AZURIUM_MEDIUM, VoidHeartBlocks.MEDIUM_AZURIUM_BUNDLE);
        set(AZURIUM_LARGE, VoidHeartBlocks.LARGE_AZURIUM_BUNDLE);
        set(RUBRUM_SMALL, VoidHeartBlocks.SMALL_RUBRUM_BUNDLE);
        set(RUBRUM_MEDIUM, VoidHeartBlocks.MEDIUM_RUBRUM_BUNDLE);
        set(RUBRUM_LARGE, VoidHeartBlocks.LARGE_RUBRUM_BUNDLE);
        set(MEMORIAM_SMALL, VoidHeartBlocks.SMALL_MEMORIAM_BUNDLE);
        set(MEMORIAM_MEDIUM, VoidHeartBlocks.MEDIUM_MEMORIAM_BUNDLE);
        set(MEMORIAM_LARGE, VoidHeartBlocks.LARGE_MEMORIAM_BUNDLE);
    }

    private void cathedral()
    {
        metals(arrogantIron());
        set(ROCK_NATIVE, Blocks.STONE, Blocks.STONE, Blocks.ANDESITE, Blocks.TUFF);
        set(WALL, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS);
        set(WALL_TRIM, Blocks.CHISELED_STONE_BRICKS, Blocks.POLISHED_ANDESITE);
        set(PANEL, Blocks.SMOOTH_STONE, Blocks.POLISHED_ANDESITE);
        set(FLOOR, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES);
        set(FLOOR_TRIM, Blocks.DEEPSLATE_TILES);
        set(CEILING, Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS);
        set(COLUMN, Blocks.POLISHED_ANDESITE);
        set(STAIRS, Blocks.STONE_BRICK_STAIRS);
        set(SLAB, Blocks.STONE_BRICK_SLAB);
        set(FENCE, Blocks.STONE_BRICK_WALL);
        set(WINDOW, vanilla("purple_stained_glass_pane"), vanilla("red_stained_glass_pane"), vanilla("blue_stained_glass_pane"), vanilla("yellow_stained_glass_pane"));
        set(RUNNER, vanilla("red_carpet"));
    }

    private void foundry()
    {
        metals(seethingSteel(), covetousCopper());
        set(ROCK_NATIVE, Blocks.TUFF, Blocks.TUFF, Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE);
        set(WALL, Blocks.TUFF_BRICKS, Blocks.TUFF_BRICKS, Blocks.POLISHED_TUFF, Blocks.BRICKS);
        set(WALL_TRIM, vanilla("waxed_exposed_cut_copper"), vanilla("waxed_cut_copper"));
        set(PANEL, Blocks.POLISHED_TUFF, Blocks.SMOOTH_BASALT);
        set(FLOOR, Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE, Blocks.POLISHED_TUFF, Blocks.IRON_BLOCK);
        set(FLOOR_TRIM, Blocks.CHISELED_TUFF, Blocks.SMOOTH_BASALT);
        set(CEILING, Blocks.SMOOTH_BASALT);
        set(COLUMN, vanilla("waxed_copper_block"), vanilla("waxed_exposed_copper"));
        set(STAIRS, Blocks.TUFF_BRICK_STAIRS);
        set(SLAB, Blocks.SMOOTH_STONE_SLAB);
        set(FENCE, Blocks.IRON_BARS);
        set(WINDOW, Blocks.IRON_BARS);
        set(RUNNER, vanilla("black_carpet"));
        set(GLOW, Blocks.OCHRE_FROGLIGHT);
        set(FLUID, Blocks.LAVA);
        set(POOL_BED, Blocks.SMOOTH_BASALT);
        set(PIPE, vanilla("waxed_copper_block"), vanilla("waxed_exposed_copper"), vanilla("waxed_weathered_copper"));
        set(PIPE_THIN, vanilla("waxed_copper_grate"));
    }

    private void palace()
    {
        metals(ravenousGold());
        set(ROCK_NATIVE, Blocks.SANDSTONE, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.TERRACOTTA);
        set(WALL, Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE);
        set(WALL_TRIM, Blocks.CHISELED_SANDSTONE, Blocks.CUT_RED_SANDSTONE);
        set(PANEL, Blocks.SMOOTH_RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE);
        set(FLOOR, Blocks.CUT_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE);
        set(FLOOR_TRIM, vanilla("cyan_terracotta"), vanilla("blue_terracotta"));
        set(CEILING, Blocks.SMOOTH_RED_SANDSTONE);
        set(COLUMN, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BLOCK);
        set(STAIRS, Blocks.SMOOTH_SANDSTONE_STAIRS);
        set(SLAB, Blocks.SMOOTH_SANDSTONE_SLAB);
        set(FENCE, Blocks.SANDSTONE_WALL);
        set(WINDOW, vanilla("orange_stained_glass_pane"), vanilla("yellow_stained_glass_pane"));
        set(RUNNER, vanilla("red_carpet"));
        set(GLOW, Blocks.SEA_LANTERN);
        set(POOL_BED, vanilla("cyan_terracotta"));
        set(PLANT, Blocks.POTTED_CACTUS);
    }

    private void temple()
    {
        metals(listlessLead());
        setStates(FILL_LIGHT, List.of(Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, DARK_STONE_FILL_LIGHT_LEVEL)));
        setStates(MONOLITH, List.of(VoidHeartBlocks.VOID_MONOLITH.defaultBlockState().setValue(StateProperties.BROKEN, true)));
        set(ROCK_NATIVE, Blocks.BLACKSTONE, Blocks.BLACKSTONE, Blocks.BASALT);
        set(WALL, VoidHeartBlocks.VOIDSTONE_BRICKS, VoidHeartBlocks.VOIDSTONE_BRICKS, VoidHeartBlocks.VOIDSTONE_BRICKS_CRACKED);
        set(WALL_TRIM, VoidHeartBlocks.VOIDSTONE_TILE_SMALL);
        set(PANEL, VoidHeartBlocks.VOIDSTONE_POLISHED);
        set(FLOOR, VoidHeartBlocks.VOIDSTONE_POLISHED);
        set(FLOOR_TRIM, VoidHeartBlocks.VOIDSTONE_BRICKS);
        set(CEILING, VoidHeartBlocks.VOIDSTONE_BRICKS, VoidHeartBlocks.VOIDSTONE_POLISHED);
        set(COLUMN, VoidHeartBlocks.VOIDSTONE_PILLAR, VoidHeartBlocks.VOIDSTONE_WEATHERED_PILLAR);
        set(STAIRS, VoidHeartBlocks.VOIDSTONE_BRICKS_STAIRS);
        set(SLAB, VoidHeartBlocks.VOIDSTONE_TILE_SMALL_SLAB);
        set(FENCE, VoidHeartBlocks.VOIDSTONE_BRICKS_WALL);
        set(WINDOW, Blocks.CRYING_OBSIDIAN, Blocks.TINTED_GLASS);
        set(RUNNER, vanilla("purple_carpet"));
        set(LANTERN, Blocks.SOUL_LANTERN);
        set(CANDLE, vanilla("purple_candle"));
        set(GLOW, Blocks.CRYING_OBSIDIAN);
        set(POOL_BED, VoidHeartBlocks.VOIDSTONE_BRICKS);
    }

    private void forest()
    {
        metals(covetousCopper());
        set(ROCK_NATIVE, Blocks.MUD);
        set(WALL, Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_COBBLESTONE);
        set(PANEL, Blocks.MOSSY_COBBLESTONE);
        set(WALL_TRIM, Blocks.STRIPPED_DARK_OAK_WOOD, Blocks.MOSSY_STONE_BRICKS);
        set(FLOOR, Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE);
        set(FLOOR_TRIM, Blocks.DARK_OAK_PLANKS, Blocks.PODZOL);
        set(CEILING, Blocks.PACKED_MUD, Blocks.MUDDY_MANGROVE_ROOTS);
        set(COLUMN, Blocks.DARK_OAK_LOG);
        set(STAIRS, Blocks.MOSSY_COBBLESTONE_STAIRS);
        set(SLAB, Blocks.MOSSY_COBBLESTONE_SLAB);
        set(FENCE, Blocks.DARK_OAK_FENCE);
        set(WINDOW, Blocks.DARK_OAK_FENCE);
        set(RUNNER, Blocks.MOSS_CARPET);
        set(SOIL, Blocks.MOSS_BLOCK);
        set(SUBSOIL, Blocks.MUD, Blocks.DIRT);
        set(PLANKS, Blocks.DARK_OAK_PLANKS);
        set(FRAME, Blocks.DARK_OAK_LOG);
        set(ROOF_STAIRS, Blocks.DARK_OAK_STAIRS);
        set(ROOF_SLAB, Blocks.DARK_OAK_SLAB);
        set(WOOD_FENCE, Blocks.DARK_OAK_FENCE);
        set(DOOR, Blocks.DARK_OAK_DOOR);
        set(LOG, Blocks.DARK_OAK_LOG);
        set(LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.PALE_OAK_LEAVES);
        set(HANGING, Blocks.HANGING_ROOTS);
        set(PLANT, Blocks.FERN, Blocks.SHORT_GRASS, Blocks.BROWN_MUSHROOM);
        set(TALL_PLANT, Blocks.LARGE_FERN, Blocks.TALL_GRASS);
        set(GLOW, Blocks.VERDANT_FROGLIGHT);
        set(POOL_BED, Blocks.MUD);
    }

    private void mycosis()
    {
        metals(seethingSteel());
        set(ROCK_NATIVE, Blocks.NETHERRACK, Blocks.NETHERRACK, Blocks.BLACKSTONE);
        set(WALL, Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.STONE_BRICKS);
        set(WALL_TRIM, Blocks.STRIPPED_DARK_OAK_WOOD, Blocks.STONE_BRICKS);
        set(PANEL, Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS);
        set(FLOOR, Blocks.PACKED_MUD, Blocks.COARSE_DIRT, Blocks.DIRT_PATH);
        set(FLOOR_TRIM, Blocks.COBBLESTONE, Blocks.GRAVEL, Blocks.COBBLESTONE);
        set(CEILING, Blocks.NETHERRACK);
        set(COLUMN, Blocks.OAK_LOG);
        set(STAIRS, Blocks.COBBLESTONE_STAIRS);
        set(SLAB, Blocks.COBBLESTONE_SLAB);
        set(FENCE, Blocks.OAK_FENCE);
        set(WINDOW, Blocks.GLASS_PANE);
        set(RUNNER, vanilla("orange_carpet"));
        set(SOIL, Blocks.CRIMSON_NYLIUM, Blocks.WARPED_NYLIUM, Blocks.MYCELIUM);
        set(SUBSOIL, Blocks.NETHERRACK);
        set(PLANKS, Blocks.OAK_PLANKS, Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS);
        set(PLANT, Blocks.CRIMSON_FUNGUS, Blocks.CRIMSON_ROOTS, Blocks.RED_MUSHROOM);
        set(CAP, Blocks.NETHER_WART_BLOCK, Blocks.RED_MUSHROOM_BLOCK, Blocks.NETHER_WART_BLOCK);
        set(STEM, Blocks.CRIMSON_STEM);
        set(GLOW, Blocks.SHROOMLIGHT);
        set(FLUID, Blocks.LAVA);
        set(POOL_BED, Blocks.BLACKSTONE);
    }
}
