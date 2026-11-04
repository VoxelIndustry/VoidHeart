package net.voxelindustry.voidheart.common.world.pocket;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.Fluids;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.List;

/**
 * Turns the decor and crystals of a plan into blocks. Each placement checks the block can survive there, since trees and earlier decor reshape the ground.
 */
final class PocketDecorator
{
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private final ServerLevel level;
    private final BlockPos origin;
    private final PocketVariant variant;

    PocketDecorator(ServerLevel level, BlockPos origin, PocketVariant variant)
    {
        this.level = level;
        this.origin = origin;
        this.variant = variant;
    }

    void place(PocketCrystal crystal)
    {
        var pos = origin.offset(crystal.x(), crystal.y(), crystal.z());
        var current = level.getBlockState(pos);
        if (!current.isAir() && !current.is(Blocks.WATER))
            return;

        var state = bundle(crystal)
                .setValue(AmethystClusterBlock.FACING, crystal.facing())
                .setValue(BlockStateProperties.WATERLOGGED, current.is(Blocks.WATER));
        if (state.canSurvive(level, pos))
            level.setBlock(pos, state, FLAGS);
    }

    private static BlockState bundle(PocketCrystal crystal)
    {
        var block = switch (crystal.type())
        {
            case AZURIUM -> switch (crystal.size())
            {
                case SMALL -> VoidHeartBlocks.SMALL_AZURIUM_BUNDLE;
                case MEDIUM -> VoidHeartBlocks.MEDIUM_AZURIUM_BUNDLE;
                case LARGE -> VoidHeartBlocks.LARGE_AZURIUM_BUNDLE;
            };
            case RUBRUM -> switch (crystal.size())
            {
                case SMALL -> VoidHeartBlocks.SMALL_RUBRUM_BUNDLE;
                case MEDIUM -> VoidHeartBlocks.MEDIUM_RUBRUM_BUNDLE;
                case LARGE -> VoidHeartBlocks.LARGE_RUBRUM_BUNDLE;
            };
        };
        return block.defaultBlockState();
    }

    void place(PocketDecor decor, RandomSource random)
    {
        var pos = origin.offset(decor.x(), decor.y(), decor.z());
        switch (decor.kind())
        {
            case TREE -> plantTree(pos, random);
            case SMALL_PLANT -> placeIfAir(pos, smallPlant(random));
            case TALL_PLANT -> placeTall(pos, tallPlant(random));
            case FLOWER -> placeFlower(pos, random);
            case TALL_FLOWER -> placeTall(pos, tallFlower(random));
            case BUSH -> placeIfAir(pos, bush(random));
            case CARPET -> placeIfAir(pos, Blocks.MOSS_CARPET.defaultBlockState());
            case DRIPLEAF -> placeTall(pos, Blocks.SMALL_DRIPLEAF.defaultBlockState());
            case CAVE_VINE -> hangVine(pos, decor.amount(), random);
            case HANGING_ROOTS -> placeIfAir(pos, Blocks.HANGING_ROOTS.defaultBlockState());
            case SPORE_BLOSSOM -> placeIfAir(pos, Blocks.SPORE_BLOSSOM.defaultBlockState());
            case CEILING_LIGHT -> embedLight(pos);
            case FILL_LIGHT -> placeIfAir(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, decor.amount()));
            case GLOW_LICHEN -> placeLichen(pos, Direction.values()[decor.amount()]);
            case SEAGRASS -> placeInWater(pos, Blocks.SEAGRASS.defaultBlockState());
            case KELP -> growKelp(pos, decor.amount());
            case SEA_PICKLE -> placeInWater(pos, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, decor.amount()));
            case CORAL -> placeInWater(pos, coral(random));
            case LILY_PAD -> placeIfAir(pos, Blocks.LILY_PAD.defaultBlockState());
            case STALAGMITE -> growDripstone(pos, Direction.UP, decor.amount());
            case STALACTITE -> growDripstone(pos, Direction.DOWN, decor.amount());
            case EMBER_VENT -> ventEmber(pos);
            case SPRING -> level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private void placeIfAir(BlockPos pos, BlockState state)
    {
        if (level.getBlockState(pos).isAir() && state.canSurvive(level, pos))
            level.setBlock(pos, state, FLAGS);
    }

    private void placeTall(BlockPos pos, BlockState state)
    {
        if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir() && state.canSurvive(level, pos))
            DoublePlantBlock.placeAt(level, state, pos, FLAGS);
    }

    private void placeInWater(BlockPos pos, BlockState state)
    {
        if (level.getFluidState(pos).is(Fluids.WATER) && level.getBlockState(pos).is(Blocks.WATER) && state.canSurvive(level, pos))
            level.setBlock(pos, state, FLAGS);
    }

    private void plantTree(BlockPos pos, RandomSource random)
    {
        var features = level.registryAccess().lookupOrThrow(Registries.FEATURE);
        features.get(treeFor(random)).ifPresent(tree -> tree.value().place(level, level.getChunkSource().getGenerator(), random, pos));
    }

    private ResourceKey<Feature> treeFor(RandomSource random)
    {
        return switch (variant)
        {
            case AZALEA_GARDEN -> TreeFeatures.AZALEA_TREE;
            case CHERRY_GROVE -> TreeFeatures.CHERRY;
            case JUNGLE -> random.nextBoolean() ? TreeFeatures.JUNGLE_BUSH : TreeFeatures.JUNGLE_TREE;
            case MUSHROOM_HOLLOW -> random.nextBoolean() ? TreeFeatures.HUGE_RED_MUSHROOM : TreeFeatures.HUGE_BROWN_MUSHROOM;
            case MANGROVE_MARSH -> TreeFeatures.MANGROVE;
            case BLOOM_FOREST -> random.nextBoolean() ? TreeFeatures.BIRCH : TreeFeatures.OAK;
            default -> TreeFeatures.OAK;
        };
    }

    private BlockState smallPlant(RandomSource random)
    {
        return switch (variant)
        {
            case MUSHROOM_HOLLOW -> random.nextBoolean() ? Blocks.RED_MUSHROOM.defaultBlockState() : Blocks.BROWN_MUSHROOM.defaultBlockState();
            case JUNGLE -> random.nextInt(3) == 0 ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState();
            default -> random.nextInt(6) == 0 ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState();
        };
    }

    private BlockState tallPlant(RandomSource random)
    {
        return variant == PocketVariant.JUNGLE && random.nextBoolean() ? Blocks.LARGE_FERN.defaultBlockState() : Blocks.TALL_GRASS.defaultBlockState();
    }

    private void placeFlower(BlockPos pos, RandomSource random)
    {
        switch (variant)
        {
            case CHERRY_GROVE -> placeIfAir(pos, Blocks.PINK_PETALS.defaultBlockState()
                    .setValue(FlowerBedBlock.AMOUNT, 1 + random.nextInt(4))
                    .setValue(FlowerBedBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)));
            case BLOOM_FOREST -> placeIfAir(pos, bloom(random));
            case JUNGLE -> placeIfAir(pos, random.nextBoolean() ? Blocks.BLUE_ORCHID.defaultBlockState() : Blocks.DANDELION.defaultBlockState());
            case MUSHROOM_HOLLOW -> placeIfAir(pos, Blocks.BROWN_MUSHROOM.defaultBlockState());
            default -> placeIfAir(pos, Blocks.AZURE_BLUET.defaultBlockState());
        }
    }

    private static BlockState bloom(RandomSource random)
    {
        var blooms = List.of(Blocks.POPPY, Blocks.ALLIUM, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.WHITE_TULIP, Blocks.LILY_OF_THE_VALLEY);
        return blooms.get(random.nextInt(blooms.size())).defaultBlockState();
    }

    private BlockState tallFlower(RandomSource random)
    {
        var flowers = variant == PocketVariant.CHERRY_GROVE ? List.of(Blocks.PEONY, Blocks.PINK_TULIP) : List.of(Blocks.LILAC, Blocks.ROSE_BUSH, Blocks.PEONY);
        var flower = flowers.get(random.nextInt(flowers.size())).defaultBlockState();
        return flower.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) ? flower : Blocks.PEONY.defaultBlockState();
    }

    private BlockState bush(RandomSource random)
    {
        return switch (variant)
        {
            case AZALEA_GARDEN -> random.nextBoolean() ? Blocks.AZALEA.defaultBlockState() : Blocks.FLOWERING_AZALEA.defaultBlockState();
            case BLOOM_FOREST -> random.nextBoolean() ? Blocks.FIREFLY_BUSH.defaultBlockState() : Blocks.BUSH.defaultBlockState();
            default -> Blocks.BUSH.defaultBlockState();
        };
    }

    private void hangVine(BlockPos top, int length, RandomSource random)
    {
        var pos = top.mutable();
        for (int i = 0; i < length; i++)
        {
            if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.below()).isAir())
                return;

            boolean tip = i == length - 1 || !level.getBlockState(pos.below(2)).isAir();
            var vine = tip ? Blocks.CAVE_VINES.defaultBlockState() : Blocks.CAVE_VINES_PLANT.defaultBlockState();
            level.setBlock(pos, vine.setValue(CaveVines.BERRIES, random.nextInt(3) == 0), FLAGS);
            if (tip)
                return;
            pos.move(Direction.DOWN);
        }
    }

    private void embedLight(BlockPos pos)
    {
        if (!level.getBlockState(pos).isAir() && level.getBlockEntity(pos) == null)
            level.setBlock(pos, lightBlock().defaultBlockState(), FLAGS);
    }

    private Block lightBlock()
    {
        return switch (variant)
        {
            case LAGOON, DEEP_LAKE, FROZEN_LAKE -> Blocks.SEA_LANTERN;
            case MANGROVE_MARSH, JUNGLE -> Blocks.VERDANT_FROGLIGHT;
            case AZALEA_GARDEN, BLOOM_FOREST, MUSHROOM_HOLLOW -> Blocks.SHROOMLIGHT;
            case CHERRY_GROVE -> Blocks.PEARLESCENT_FROGLIGHT;
            case OCHRE_CAVERN, EMBER_DEEP -> Blocks.OCHRE_FROGLIGHT;
            case PALE_CATHEDRAL -> Blocks.PEARLESCENT_FROGLIGHT;
        };
    }

    private void placeLichen(BlockPos pos, Direction towardRock)
    {
        if (level.getBlockState(pos).isAir())
            placeIfAir(pos, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(towardRock), true));
    }

    private void growKelp(BlockPos bottom, int length)
    {
        var pos = bottom.mutable();
        for (int i = 0; i < length; i++)
        {
            if (!level.getBlockState(pos).is(Blocks.WATER))
                return;

            boolean top = i == length - 1 || !level.getBlockState(pos.above()).is(Blocks.WATER);
            level.setBlock(pos, top ? Blocks.KELP.defaultBlockState() : Blocks.KELP_PLANT.defaultBlockState(), FLAGS);
            if (top)
                return;
            pos.move(Direction.UP);
        }
    }

    private BlockState coral(RandomSource random)
    {
        var corals = List.of(Blocks.TUBE_CORAL, Blocks.BRAIN_CORAL, Blocks.BUBBLE_CORAL, Blocks.FIRE_CORAL, Blocks.HORN_CORAL,
                Blocks.TUBE_CORAL_FAN, Blocks.BRAIN_CORAL_FAN, Blocks.FIRE_CORAL_FAN);
        return corals.get(random.nextInt(corals.size())).defaultBlockState();
    }

    private void growDripstone(BlockPos start, Direction direction, int length)
    {
        var pos = start.mutable();
        int free = 0;
        while (free < length && level.getBlockState(pos).isAir())
        {
            free++;
            pos.move(direction);
        }

        pos.set(start);
        for (int i = 0; i < free; i++)
        {
            var thickness = i == free - 1 ? SpeleothemThickness.TIP
                    : i == free - 2 ? SpeleothemThickness.FRUSTUM
                    : i == 0 ? SpeleothemThickness.BASE
                    : SpeleothemThickness.MIDDLE;
            var state = Blocks.POINTED_DRIPSTONE.defaultBlockState()
                    .setValue(PointedDripstoneBlock.TIP_DIRECTION, direction)
                    .setValue(PointedDripstoneBlock.THICKNESS, thickness);
            if (!state.canSurvive(level, pos))
                return;
            level.setBlock(pos, state, FLAGS);
            pos.move(direction);
        }
    }

    private void ventEmber(BlockPos above)
    {
        var below = above.below();
        if (!level.getBlockState(below).isAir() && level.getBlockEntity(below) == null)
            level.setBlock(below, Blocks.MAGMA_BLOCK.defaultBlockState(), FLAGS);
    }
}
