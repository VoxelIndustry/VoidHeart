package net.voxelindustry.voidheart.common.content.crystal;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.renderer.item.properties.select.ComponentContents;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;

import java.util.ArrayList;

import static net.minecraft.client.data.models.BlockModelGenerators.plainVariant;

/**
 * Datagen for crystallography, called through EnergyDatagen and ModelsProvider. Rerun runDatagen after changing it.
 * <p>
 * A habit drawn here has its own texture for every crystal that takes it, named after the plain one with the habit as a suffix. A habit not drawn yet
 * looks like the plain crystal.
 */
public final class CrystalDatagen
{
    private CrystalDatagen()
    {
    }

    /**
     * Every habit a crystal takes has its own textures, drawn by .claude/tools/crystal_habits.lua from the plain ones.
     */
    private static boolean drawn(CrystalHabit habit, CrystalKind kind)
    {
        return habit.isHabit() && habit.takenBy(kind);
    }

    private static String suffix(CrystalHabit habit)
    {
        return "_" + habit.getSerializedName();
    }

    /**
     * Every bundle block and its item, each drawn habit with its own crossed texture, all of them turned by facing like an amethyst cluster.
     */
    public static void bundleModels(BlockModelGenerators generators)
    {
        for (var kind : CrystalKind.values())
        {
            for (var size : BundleSize.values())
                bundleModel(generators, kind, CrystalBundles.bundle(kind, size));
        }
    }

    private static void bundleModel(BlockModelGenerators generators, CrystalKind kind, Block bundle)
    {
        MultiVariant plain = plainVariant(ModelTemplates.CROSS.create(bundle, TextureMapping.cross(bundle), generators.modelOutput));
        var dispatch = PropertyDispatch.initial(CrystalBundleBlock.HABIT);
        for (var habit : CrystalHabit.values())
        {
            if (!drawn(habit, kind))
            {
                dispatch.select(habit, plain);
                continue;
            }
            var texture = TextureMapping.cross(TextureMapping.getBlockTexture(bundle, suffix(habit)));
            dispatch.select(habit, plainVariant(ModelTemplates.CROSS.createWithSuffix(bundle, suffix(habit), texture, generators.modelOutput)));
        }
        generators.blockStateOutput.accept(MultiVariantGenerator.dispatch(bundle).with(dispatch).with(BlockModelGenerators.ROTATIONS_COLUMN_WITH_FACING));

        var item = bundle.asItem();
        var plainItem = ItemModelUtils.plainModel(generators.createFlatItemModelWithBlockTexture(item, bundle));
        var cases = new ArrayList<net.minecraft.client.renderer.item.SelectItemModel.SwitchCase<CrystalHabit>>();
        for (var habit : CrystalHabit.values())
        {
            if (drawn(habit, kind))
                cases.add(ItemModelUtils.when(habit, ItemModelUtils.plainModel(ModelTemplates.FLAT_ITEM.create(ModelLocationUtils.getModelLocation(item, suffix(habit)),
                        TextureMapping.layer0(TextureMapping.getBlockTexture(bundle, suffix(habit))), generators.modelOutput))));
        }
        generators.itemModelOutput.accept(item, cases.isEmpty() ? plainItem
                : ItemModelUtils.select(new ComponentContents<>(VoidHeartDataComponents.CRYSTAL_HABIT), plainItem, cases));
    }

    /**
     * The shards, each drawn habit with its own sprite.
     */
    public static void shardModels(ItemModelGenerators generators)
    {
        for (var kind : CrystalKind.values())
        {
            var shard = CrystalBundles.shard(kind);
            var plain = ItemModelUtils.plainModel(generators.createFlatItemModel(shard, ModelTemplates.FLAT_ITEM));
            var cases = new ArrayList<net.minecraft.client.renderer.item.SelectItemModel.SwitchCase<CrystalHabit>>();
            for (var habit : CrystalHabit.values())
            {
                if (drawn(habit, kind))
                    cases.add(ItemModelUtils.when(habit, ItemModelUtils.plainModel(generators.createFlatItemModel(shard, suffix(habit), ModelTemplates.FLAT_ITEM))));
            }
            generators.itemModelOutput.accept(shard, cases.isEmpty() ? plain
                    : ItemModelUtils.select(new ComponentContents<>(VoidHeartDataComponents.CRYSTAL_HABIT), plain, cases));
        }
    }

    /**
     * The cradle's model is hand-written in assets/voidheart/models/block, since its basin is cut in.
     */
    public static void blockModels(BlockModelGenerators generators)
    {
        generators.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(CrystalContent.CRYSTAL_CRADLE,
                plainVariant(ModelLocationUtils.getModelLocation(CrystalContent.CRYSTAL_CRADLE))));
    }

    /**
     * A voidstone basin under the earth's own blue and an azurium shard. Research gates it through its id, which matches the block.
     */
    public static void recipes(EnergyRecipes recipes)
    {
        var provider = recipes.provider();
        provider.shaped(RecipeCategory.DECORATIONS, CrystalContent.CRYSTAL_CRADLE)
                .pattern("LAL").pattern("V V").pattern("VVV")
                .define('L', Items.LAPIS_LAZULI)
                .define('A', VoidHeartItems.AZURIUM_SHARD)
                .define('V', VoidHeartBlocks.VOIDSTONE_POLISHED)
                .unlockedBy(provider.getHasName(VoidHeartItems.AZURIUM_SHARD), provider.has(VoidHeartItems.AZURIUM_SHARD))
                .save(recipes.output());
    }

    public static void loot(BlockLootSubProvider loot)
    {
        loot.dropSelf(CrystalContent.CRYSTAL_CRADLE);
    }
}
