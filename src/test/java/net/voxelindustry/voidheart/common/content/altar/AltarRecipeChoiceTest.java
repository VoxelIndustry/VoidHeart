package net.voxelindustry.voidheart.common.content.altar;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.voxelindustry.steamlayer.recipe.ingredient.ItemStackRecipeIngredient;
import net.voxelindustry.steamlayer.recipe.ingredient.ItemStackTransformIngredient;
import net.voxelindustry.steamlayer.recipe.ingredient.ItemTagRecipeIngredient;
import net.voxelindustry.voidheart.common.recipe.AltarRecipe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AltarRecipeChoiceTest
{
    private static final TagKey<Item> WEAPONS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("voidheart", "test_weapons"));

    @BeforeAll
    static void bootstrap()
    {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
                .forEach(DataComponentInitializers.PendingComponents::apply);
    }

    private static ItemStackRecipeIngredient[] items(Item... items)
    {
        var inputs = new ItemStackRecipeIngredient[items.length];
        for (int i = 0; i < items.length; i++)
            inputs[i] = new ItemStackRecipeIngredient(new ItemStack(items[i]), false);
        return inputs;
    }

    /**
     * A tag recipe that marks any weapon epic, standing for a sin infusion without needing the mod's components registered.
     */
    private static AltarRecipe epicInfusion(Item... ingredients)
    {
        var patch = DataComponentPatch.builder().set(DataComponents.RARITY, Rarity.EPIC).build();
        return new AltarRecipe(Identifier.fromNamespaceAndPath("voidheart", "epic"), ItemStackTransformIngredient.patching(patch), new ItemTagRecipeIngredient(WEAPONS), 20, items(ingredients));
    }

    private static AltarRecipe fixed(String name, Item catalyst, Item output, Item... ingredients)
    {
        return new AltarRecipe(Identifier.fromNamespaceAndPath("voidheart", name), items(output)[0], items(catalyst)[0], 20, items(ingredients));
    }

    @Test
    void choose_givenSharedCatalyst_thenRecipeWhoseIngredientsAreLaid()
    {
        var first = fixed("first", Items.IRON_SWORD, Items.DIAMOND, Items.BONE, Items.BONE);
        var second = fixed("second", Items.IRON_SWORD, Items.EMERALD, Items.ENDER_PEARL);

        var chosen = VoidAltarTile.chooseRecipe(List.of(first, second), new ItemStack(Items.IRON_SWORD), List.of(new ItemStack(Items.ENDER_PEARL), ItemStack.EMPTY));

        assertThat(chosen).isSameAs(second);
    }

    @Test
    void choose_givenACatalystWithComponents_thenItsExactRecipeComesFirst()
    {
        var anyBundle = fixed("any", Items.AMETHYST_CLUSTER, Items.DIAMOND, Items.BONE);
        var marked = new ItemStack(Items.AMETHYST_CLUSTER);
        marked.set(DataComponents.RARITY, Rarity.EPIC);
        var exact = new AltarRecipe(Identifier.fromNamespaceAndPath("voidheart", "exact"), ItemStackTransformIngredient.transmuting(Items.EMERALD),
                new ItemStackRecipeIngredient(marked, true), 20, items(Items.BONE));

        var chosen = VoidAltarTile.chooseRecipe(List.of(anyBundle, exact), marked, List.of(new ItemStack(Items.BONE)));

        assertThat(chosen).isSameAs(exact);
        assertThat(chosen.resultFor(marked).get(DataComponents.RARITY)).isEqualTo(Rarity.EPIC);
        assertThat(chosen.resultFor(marked).is(Items.EMERALD)).isTrue();
    }

    @Test
    void choose_givenNothingLaid_thenExactCatalystRecipeWaits()
    {
        var exact = fixed("exact", Items.IRON_SWORD, Items.DIAMOND, Items.BONE);
        var tag = epicInfusion(Items.GOLD_INGOT);

        var chosen = VoidAltarTile.chooseRecipe(List.of(tag, exact), new ItemStack(Items.IRON_SWORD), List.of());

        assertThat(chosen).isSameAs(exact);
    }

    @Test
    void choose_givenTagCatalystNotLaid_thenNothingStarts()
    {
        var tag = epicInfusion(Items.GOLD_INGOT, Items.GOLD_INGOT);

        var chosen = VoidAltarTile.chooseRecipe(List.of(tag), new ItemStack(Items.IRON_SWORD), List.of(new ItemStack(Items.GOLD_INGOT)));

        assertThat(chosen).isNull();
    }

    @Test
    void choose_givenTagCatalystLaid_thenStarts()
    {
        var tag = epicInfusion(Items.GOLD_INGOT, Items.GOLD_INGOT);

        var chosen = VoidAltarTile.chooseRecipe(List.of(tag), new ItemStack(Items.IRON_SWORD), List.of(new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.GOLD_INGOT)));

        assertThat(chosen).isSameAs(tag);
    }

    @Test
    void choose_givenCatalystAlreadyTransformed_thenNothingStarts()
    {
        var tag = epicInfusion(Items.GOLD_INGOT);
        var sword = new ItemStack(Items.IRON_SWORD);
        sword.set(DataComponents.RARITY, Rarity.EPIC);

        var chosen = VoidAltarTile.chooseRecipe(List.of(tag), sword, List.of(new ItemStack(Items.GOLD_INGOT)));

        assertThat(chosen).isNull();
    }

    @Test
    void result_givenWornCatalyst_thenKeepsItsComponents()
    {
        var tag = epicInfusion(Items.GOLD_INGOT);
        var sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(17);

        var result = tag.resultFor(sword);

        assertThat(result.is(Items.IRON_SWORD)).isTrue();
        assertThat(result.getDamageValue()).isEqualTo(17);
        assertThat(result.get(DataComponents.RARITY)).isEqualTo(Rarity.EPIC);
    }

    @Test
    void result_givenFixedOutput_thenThatOutput()
    {
        var recipe = fixed("fixed", Items.IRON_SWORD, Items.DIAMOND, Items.BONE);

        assertThat(recipe.resultFor(new ItemStack(Items.IRON_SWORD)).is(Items.DIAMOND)).isTrue();
        assertThat(recipe.changes(new ItemStack(Items.IRON_SWORD))).isTrue();
    }
}
