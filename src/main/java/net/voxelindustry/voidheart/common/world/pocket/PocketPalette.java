package net.voxelindustry.voidheart.common.world.pocket;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.EnumMap;

/**
 * Which block fills each slot of a variant, and the biome that tints it. Biomes also bring each ambience its water color, sounds and music.
 */
public final class PocketPalette
{
    private final EnumMap<PocketSlot, BlockState> states = new EnumMap<>(PocketSlot.class);

    private PocketPalette()
    {
        set(PocketSlot.AIR, Blocks.AIR);
        set(PocketSlot.FLUID, Blocks.WATER);
        set(PocketSlot.FROZEN_FLUID, Blocks.ICE);
        set(PocketSlot.WALL, VoidHeartBlocks.POCKET_WALL);
        set(PocketSlot.ROCK, VoidHeartBlocks.VOIDSTONE);
        set(PocketSlot.STRATA, VoidHeartBlocks.VOIDSTONE);
        set(PocketSlot.CEILING, VoidHeartBlocks.VOIDSTONE);
        set(PocketSlot.SKIN, VoidHeartBlocks.VOIDSTONE);
        set(PocketSlot.CONE, VoidHeartBlocks.VOIDSTONE);
        set(PocketSlot.PEDESTAL, VoidHeartBlocks.VOIDSTONE_POLISHED);
    }

    public BlockState state(PocketSlot slot)
    {
        return states.get(slot);
    }

    private PocketPalette set(PocketSlot slot, Block block)
    {
        states.put(slot, block.defaultBlockState());
        return this;
    }

    private PocketPalette soil(Block top, Block alt, Block under)
    {
        return set(PocketSlot.TOPSOIL, top).set(PocketSlot.TOPSOIL_ALT, alt).set(PocketSlot.SUBSOIL, under);
    }

    private PocketPalette waterside(Block shore, Block bed, Block bedAlt)
    {
        return set(PocketSlot.SHORE, shore).set(PocketSlot.BED, bed).set(PocketSlot.BED_ALT, bedAlt);
    }

    public static PocketPalette of(PocketVariant variant)
    {
        var palette = new PocketPalette();
        return switch (variant)
        {
            case LAGOON -> palette.soil(Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.DIRT)
                    .waterside(Blocks.SAND, Blocks.SAND, Blocks.GRAVEL)
                    .set(PocketSlot.SKIN, Blocks.SMOOTH_SANDSTONE)
                    .set(PocketSlot.CEILING, Blocks.SANDSTONE)
                    .set(PocketSlot.CONE, Blocks.SANDSTONE);
            case DEEP_LAKE -> palette.soil(Blocks.MOSS_BLOCK, Blocks.GRAVEL, Blocks.DIRT)
                    .waterside(Blocks.GRAVEL, Blocks.GRAVEL, Blocks.CLAY)
                    .set(PocketSlot.SKIN, Blocks.DEEPSLATE)
                    .set(PocketSlot.CEILING, Blocks.DEEPSLATE)
                    .set(PocketSlot.CONE, Blocks.DEEPSLATE);
            case MANGROVE_MARSH -> palette.soil(Blocks.GRASS_BLOCK, Blocks.MUD, Blocks.DIRT)
                    .waterside(Blocks.MUD, Blocks.MUD, Blocks.CLAY)
                    .set(PocketSlot.SKIN, Blocks.TUFF)
                    .set(PocketSlot.CEILING, Blocks.TUFF)
                    .set(PocketSlot.CONE, Blocks.TUFF);
            case FROZEN_LAKE -> palette.soil(Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, Blocks.DIRT)
                    .waterside(Blocks.SNOW_BLOCK, Blocks.GRAVEL, Blocks.PACKED_ICE)
                    .set(PocketSlot.CEILING, Blocks.PACKED_ICE)
                    .set(PocketSlot.SKIN, Blocks.PACKED_ICE)
                    .set(PocketSlot.CONE, Blocks.BLUE_ICE);
            case AZALEA_GARDEN -> palette.soil(Blocks.MOSS_BLOCK, Blocks.ROOTED_DIRT, Blocks.DIRT)
                    .waterside(Blocks.CLAY, Blocks.CLAY, Blocks.GRAVEL)
                    .set(PocketSlot.CEILING, Blocks.MOSS_BLOCK)
                    .set(PocketSlot.SKIN, Blocks.MOSS_BLOCK)
                    .set(PocketSlot.STRATA, Blocks.TUFF);
            case BLOOM_FOREST -> palette.soil(Blocks.GRASS_BLOCK, Blocks.GRASS_BLOCK, Blocks.DIRT)
                    .waterside(Blocks.SAND, Blocks.CLAY, Blocks.GRAVEL)
                    .set(PocketSlot.SKIN, Blocks.STONE)
                    .set(PocketSlot.CEILING, Blocks.MOSS_BLOCK);
            case CHERRY_GROVE -> palette.soil(Blocks.GRASS_BLOCK, Blocks.GRASS_BLOCK, Blocks.DIRT)
                    .waterside(Blocks.SAND, Blocks.CLAY, Blocks.SAND)
                    .set(PocketSlot.SKIN, Blocks.CALCITE)
                    .set(PocketSlot.CEILING, Blocks.CALCITE);
            case JUNGLE -> palette.soil(Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.DIRT)
                    .waterside(Blocks.MUD, Blocks.MUD, Blocks.CLAY)
                    .set(PocketSlot.SKIN, Blocks.PACKED_MUD)
                    .set(PocketSlot.CEILING, Blocks.MOSS_BLOCK)
                    .set(PocketSlot.STRATA, Blocks.TUFF);
            case MUSHROOM_HOLLOW -> palette.soil(Blocks.MYCELIUM, Blocks.PODZOL, Blocks.DIRT)
                    .waterside(Blocks.MUD, Blocks.MUD, Blocks.CLAY)
                    .set(PocketSlot.STRATA, Blocks.DEEPSLATE);
            case OCHRE_CAVERN -> palette.soil(Blocks.DRIPSTONE_BLOCK, VoidHeartBlocks.VOIDSTONE, Blocks.DRIPSTONE_BLOCK)
                    .waterside(Blocks.CLAY, Blocks.CLAY, Blocks.GRAVEL)
                    .set(PocketSlot.CEILING, Blocks.DRIPSTONE_BLOCK)
                    .set(PocketSlot.SKIN, Blocks.DRIPSTONE_BLOCK)
                    .set(PocketSlot.CONE, Blocks.DRIPSTONE_BLOCK);
            case PALE_CATHEDRAL -> palette.soil(Blocks.CALCITE, Blocks.DIORITE, Blocks.CALCITE)
                    .waterside(Blocks.CALCITE, Blocks.CLAY, Blocks.CALCITE)
                    .set(PocketSlot.CEILING, Blocks.CALCITE)
                    .set(PocketSlot.SKIN, Blocks.CALCITE)
                    .set(PocketSlot.CONE, Blocks.CALCITE);
            case EMBER_DEEP -> palette.soil(Blocks.BLACKSTONE, Blocks.BASALT, Blocks.BLACKSTONE)
                    .waterside(Blocks.BASALT, Blocks.MAGMA_BLOCK, Blocks.BLACKSTONE)
                    .set(PocketSlot.FLUID, Blocks.LAVA)
                    .set(PocketSlot.CEILING, Blocks.BLACKSTONE)
                    .set(PocketSlot.SKIN, Blocks.BLACKSTONE)
                    .set(PocketSlot.CONE, Blocks.BASALT);
        };
    }

    public static ResourceKey<Biome> biome(PocketVariant variant)
    {
        return switch (variant)
        {
            case LAGOON -> Biomes.WARM_OCEAN;
            case DEEP_LAKE -> Biomes.DEEP_COLD_OCEAN;
            case MANGROVE_MARSH -> Biomes.MANGROVE_SWAMP;
            case FROZEN_LAKE -> Biomes.FROZEN_OCEAN;
            case AZALEA_GARDEN -> Biomes.LUSH_CAVES;
            case BLOOM_FOREST -> Biomes.FLOWER_FOREST;
            case CHERRY_GROVE -> Biomes.CHERRY_GROVE;
            case JUNGLE -> Biomes.JUNGLE;
            case MUSHROOM_HOLLOW -> Biomes.MUSHROOM_FIELDS;
            case OCHRE_CAVERN, PALE_CATHEDRAL -> Biomes.DRIPSTONE_CAVES;
            case EMBER_DEEP -> Biomes.BASALT_DELTAS;
        };
    }
}
