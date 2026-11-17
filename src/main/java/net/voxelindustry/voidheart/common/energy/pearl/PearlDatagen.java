package net.voxelindustry.voidheart.common.energy.pearl;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.voxelindustry.voidheart.client.render.energy.pearl.PearlItemRender;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;
import net.voxelindustry.voidheart.data.energy.EnergyTags;

import java.util.Map;

import static net.minecraft.client.data.models.BlockModelGenerators.plainVariant;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Datagen hand-off for the Collapsing Rubrum Pearls, called through EnergyDatagen by each provider. Rerun runDatagen after changing it. The hanging
 * pearl's block model is hand-written, holding only the particles of its breaking, since its renderer draws all of it.
 */
public final class PearlDatagen
{
    private PearlDatagen()
    {
    }

    public static void blockModels(BlockModelGenerators generators)
    {
        generators.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(PearlContent.HANGING_RUBRUM_PEARL,
                plainVariant(Identifier.fromNamespaceAndPath(MODID, "block/hanging_rubrum_pearl"))));
    }

    public static void itemModels(ItemModelGenerators generators)
    {
        // Drawn like the pearl hung from a chain at their stage, the base models only give display transforms and particles
        pearlItem(generators, PearlContent.FRACTAL_RUBRUM_SEED, "fractal_rubrum_seed", PearlItemRender.Kind.FRACTAL_SEED);
        pearlItem(generators, PearlContent.KINDLED_FRACTAL_SEED, "kindled_fractal_seed", PearlItemRender.Kind.KINDLED_SEED);
        pearlItem(generators, PearlContent.COLLAPSING_RUBRUM_PEARL, "collapsing_rubrum_pearl", PearlItemRender.Kind.PEARL);
    }

    private static void pearlItem(ItemModelGenerators generators, Item item, String name, PearlItemRender.Kind kind)
    {
        generators.itemModelOutput.accept(item, ItemModelUtils.specialModel(Identifier.fromNamespaceAndPath(MODID, "item/" + name + "_display"),
                new PearlItemRender.Unbaked(kind)));
    }

    /**
     * The seed kindled in a large forge on Fiery alone. Placeholder numbers until picked from play, near the heavy core's.
     */
    public static void recipes(EnergyRecipes recipes)
    {
        recipes.shatterForge(PearlContent.FRACTAL_RUBRUM_SEED, PearlContent.KINDLED_FRACTAL_SEED, 1, 200, 7, 6000, Map.of(EssenceRegistry.FIERY, 8));
    }

    /**
     * The table stands for the block itself; the block gives its drops from its tile, the seed with its growth or the mature pearl.
     */
    public static void loot(BlockLootSubProvider loot)
    {
        loot.dropOther(PearlContent.HANGING_RUBRUM_PEARL, PearlContent.KINDLED_FRACTAL_SEED);
    }

    public static void tags(EnergyTags tags)
    {
    }
}
