package net.voxelindustry.voidheart.data;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.BlockFamilies;
import net.minecraft.data.BlockFamily;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.data.provider.AdvancementsProvider;
import net.voxelindustry.voidheart.data.provider.BlockLootsProvider;
import net.voxelindustry.voidheart.data.provider.BlockTagsProvider;
import net.voxelindustry.voidheart.data.provider.ModelsProvider;
import net.voxelindustry.voidheart.data.provider.RecipesProvider;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartDataGenerator implements DataGeneratorEntrypoint
{
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator)
    {
        BlockFamilies.familyBuilder(VoidHeartBlocks.RAVENOUS_GOLD_BLOCK)
                .cut(VoidHeartBlocks.CUT_RAVENOUS_GOLD)
                .getFamily();
        BlockFamilies.familyBuilder(VoidHeartBlocks.CUT_RAVENOUS_GOLD)
                .stairs(VoidHeartBlocks.CUT_RAVENOUS_GOLD_STAIRS)
                .slab(VoidHeartBlocks.CUT_RAVENOUS_GOLD_SLAB)
                .wall(VoidHeartBlocks.CUT_RAVENOUS_GOLD_WALL)
                .getFamily();

        BlockFamilies.familyBuilder(VoidHeartBlocks.ARROGANT_IRON_BLOCK)
                .cut(VoidHeartBlocks.CUT_ARROGANT_IRON)
                .getFamily();
        BlockFamilies.familyBuilder(VoidHeartBlocks.CUT_ARROGANT_IRON)
                .stairs(VoidHeartBlocks.CUT_ARROGANT_IRON_STAIRS)
                .slab(VoidHeartBlocks.CUT_ARROGANT_IRON_SLAB)
                .wall(VoidHeartBlocks.CUT_ARROGANT_IRON_WALL)
                .getFamily();

        BlockFamilies.familyBuilder(VoidHeartBlocks.VOIDSTONE)
                .polished(VoidHeartBlocks.VOIDSTONE_POLISHED)
                .stairs(VoidHeartBlocks.VOIDSTONE_STAIRS)
                .slab(VoidHeartBlocks.VOIDSTONE_SLAB)
                .wall(VoidHeartBlocks.VOIDSTONE_WALL)
                .getFamily();

        BlockFamilies.familyBuilder(VoidHeartBlocks.VOIDSTONE_POLISHED)
                .slab(VoidHeartBlocks.VOIDSTONE_POLISHED_SLAB)
                .stairs(VoidHeartBlocks.VOIDSTONE_POLISHED_STAIRS)
                .wall(VoidHeartBlocks.VOIDSTONE_POLISHED_WALL)
                .getFamily();

        BlockFamilies.familyBuilder(VoidHeartBlocks.VOIDSTONE_BRICKS)
                .stairs(VoidHeartBlocks.VOIDSTONE_BRICKS_STAIRS)
                .slab(VoidHeartBlocks.VOIDSTONE_BRICKS_SLAB)
                .wall(VoidHeartBlocks.VOIDSTONE_BRICKS_WALL)
                .getFamily();

        BlockFamilies.familyBuilder(VoidHeartBlocks.VOIDSTONE_BRICKS_CRACKED)
                .stairs(VoidHeartBlocks.VOIDSTONE_BRICKS_CRACKED_STAIRS)
                .slab(VoidHeartBlocks.VOIDSTONE_BRICKS_CRACKED_SLAB)
                .wall(VoidHeartBlocks.VOIDSTONE_BRICKS_CRACKED_WALL)
                .getFamily();

        BlockFamilies.familyBuilder(VoidHeartBlocks.VOIDSTONE_BRICKS_VERTICAL)
                .getFamily();

        BlockFamilies.familyBuilder(VoidHeartBlocks.VOIDSTONE_TILE)
                .getFamily();

        BlockFamilies.familyBuilder(VoidHeartBlocks.VOIDSTONE_TILE_SMALL_CHISELED)
                .getFamily();

        BlockFamilies.familyBuilder(VoidHeartBlocks.VOIDSTONE_TILE_SMALL_CARVED)
                .getFamily();

        BlockFamilies.familyBuilder(VoidHeartBlocks.VOIDSTONE_TILE_SMALL)
                .stairs(VoidHeartBlocks.VOIDSTONE_TILE_SMALL_STAIRS)
                .slab(VoidHeartBlocks.VOIDSTONE_TILE_SMALL_SLAB)
                .wall(VoidHeartBlocks.VOIDSTONE_TILE_SMALL_WALL)
                .getFamily();

        var pack = generator.createPack();

        pack.addProvider(AdvancementsProvider::new);
        pack.addProvider(ModelsProvider::new);
        pack.addProvider(BlockTagsProvider::new);
        pack.addProvider(BlockLootsProvider::new);
        pack.addProvider(RecipesProvider::new);
    }

    public static boolean isVoidHeartFamily(BlockFamily family)
    {
        return BuiltInRegistries.BLOCK.getKey(family.getBaseBlock()).getNamespace().equals(MODID);
    }
}
