package net.voxelindustry.voidheart.common.content.works;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.recipe.AltarRecipe;
import net.voxelindustry.voidheart.common.recipe.ShatterForgeRecipe;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * What one owner's stations can make, as the planner sees it: the altar rites and crafting-table recipes the owner has researched, while an Officiant
 * serves that kind of station, and the forge recipes the builds their Officiants serve can run. A rite changing one item it is given is offered once
 * for each such item in the hoard.
 */
public final class WorksCatalogue
{
    private static final String VOID_HEART_RITE = "voidheart:altar/voidheart";

    private final Map<Item, List<WorksRecipe<ItemVariant>>> byOutput = new HashMap<>();
    private final Map<String, Object> sources = new HashMap<>();

    private WorksCatalogue()
    {
    }

    /**
     * @param stations     the kinds of station the owner's Officiants serve
     * @param forgeRecipes what the forges they serve can run
     * @param hoard        what the owner's hoard holds, for the rites that change an item given
     */
    public static WorksCatalogue of(MinecraftServer server, UUID owner, Set<StationKind> stations, Set<ShatterForgeRecipe> forgeRecipes,
                                    Map<ItemVariant, Long> hoard)
    {
        var catalogue = new WorksCatalogue();
        if (stations.contains(StationKind.ALTAR))
            catalogue.addAltar(server, owner, hoard);
        if (stations.contains(StationKind.FORGE))
            forgeRecipes.forEach(catalogue::addForge);
        if (stations.contains(StationKind.TABLE))
            catalogue.addTable(server, owner);
        return catalogue;
    }

    public List<WorksRecipe<ItemVariant>> recipesFor(ItemVariant item)
    {
        return byOutput.getOrDefault(item.getItem(), List.of()).stream().filter(recipe -> recipe.makes(item) > 0).toList();
    }

    /**
     * @return every kind some recipe here makes
     */
    public java.util.Set<ItemVariant> outputs()
    {
        var outputs = new java.util.LinkedHashSet<ItemVariant>();
        byOutput.values().forEach(recipes -> recipes.forEach(recipe -> outputs.add(recipe.outputs().getFirst().item())));
        return outputs;
    }

    public Function<ItemVariant, List<WorksRecipe<ItemVariant>>> lookup()
    {
        return this::recipesFor;
    }

    /**
     * @return every recipe with that id, several for a rite changing whichever item it is given
     */
    public List<WorksRecipe<ItemVariant>> byId(String id)
    {
        return byOutput.values().stream().flatMap(List::stream).filter(recipe -> recipe.id().equals(id)).toList();
    }

    /**
     * @return the station's own recipe behind an id: an {@link AltarRecipe}, a {@link ShatterForgeRecipe} or a crafting {@link RecipeHolder}
     */
    public @Nullable Object source(String id)
    {
        return sources.get(id);
    }

    private void add(WorksRecipe<ItemVariant> recipe, Object source)
    {
        byOutput.computeIfAbsent(recipe.outputs().getFirst().item().getItem(), item -> new ArrayList<>()).add(recipe);
        sources.put(recipe.id(), source);
    }

    private static WorksRecipe.Ingredient<ItemVariant> exactly(ItemStack stack)
    {
        return new WorksRecipe.Ingredient<>(List.of(ItemVariant.of(stack)), Math.max(1, stack.getCount()));
    }

    private void addAltar(MinecraftServer server, UUID owner, Map<ItemVariant, Long> hoard)
    {
        var category = VoidHeartRecipes.ALTAR_CATEGORY;
        var all = new ArrayList<AltarRecipe>(category.getRecipes());
        all.addAll(category.getVanillaRecipes());
        for (var recipe : all)
        {
            var id = recipe.getId().toString();
            if (id.equals(VOID_HEART_RITE) || !recipe.hasOutputType(ItemStack.class) || !Knowledge.canUse(server, owner, recipe.getId()))
                continue;

            var inputs = recipe.getRecipeInputs(ItemStack.class);
            var pillars = new ArrayList<WorksRecipe.Ingredient<ItemVariant>>();
            for (int index = 1; index < inputs.size(); index++)
                pillars.add(exactly(inputs.get(index).getRaw()));
            var essence = new LinkedHashMap<String, Integer>();
            recipe.getAspects().forEach((aspect, amount) -> essence.put(aspect.identifier().toString(), amount));

            if (!recipe.hasTransformingOutput())
            {
                var catalysts = recipe.catalystChoices().stream().map(ItemVariant::of).toList();
                var result = recipe.resultFor(recipe.catalystChoices().getFirst());
                var ingredients = new ArrayList<WorksRecipe.Ingredient<ItemVariant>>();
                ingredients.add(new WorksRecipe.Ingredient<>(catalysts, 1));
                ingredients.addAll(pillars);
                add(new WorksRecipe<>(id, StationKind.ALTAR, List.of(new WorksRecipe.Stack<>(ItemVariant.of(result), result.getCount())), ingredients, List.of(), essence),
                        recipe);
                continue;
            }

            // A rite changing the item it is given is offered for each such item held. Its source is known whatever is held, so a craft already
            // begun still finds it once its item has left the hoard
            sources.putIfAbsent(id, recipe);
            for (var held : hoard.keySet())
            {
                var stack = held.toStack();
                if (!inputs.getFirst().match(stack) || !recipe.changes(stack))
                    continue;
                var result = recipe.resultFor(stack);
                var ingredients = new ArrayList<WorksRecipe.Ingredient<ItemVariant>>();
                ingredients.add(new WorksRecipe.Ingredient<>(List.of(held), 1));
                ingredients.addAll(pillars);
                add(new WorksRecipe<>(id, StationKind.ALTAR, List.of(new WorksRecipe.Stack<>(ItemVariant.of(result), 1)), ingredients, List.of(), essence), recipe);
            }
        }
    }

    private void addForge(ShatterForgeRecipe recipe)
    {
        var input = recipe.getRecipeInputs(ItemStack.class).getFirst().getRaw();
        var output = recipe.getRecipeOutputs(ItemStack.class).getFirst().getRaw();
        add(new WorksRecipe<>(recipe.getId().toString(), StationKind.FORGE, List.of(new WorksRecipe.Stack<>(ItemVariant.of(output), output.getCount())),
                List.of(exactly(input.copyWithCount(1))), List.of(), Map.of()), recipe);
    }

    private void addTable(MinecraftServer server, UUID owner)
    {
        for (var holder : server.getRecipeManager().getRecipes())
        {
            if (!(holder.value() instanceof CraftingRecipe crafting) || !(crafting instanceof ShapedRecipe || crafting instanceof ShapelessRecipe))
                continue;
            var id = holder.id().identifier();
            if (!Knowledge.canUse(server, owner, id))
                continue;

            var slots = slotsOf(crafting);
            if (slots == null)
                continue;
            var input = inputOf(crafting, slots, ingredient -> ingredient.items().findFirst().map(item -> new ItemStack(item.value())).orElse(ItemStack.EMPTY));
            var result = crafting.assemble(input);
            if (result.isEmpty())
                continue;

            var counts = new LinkedHashMap<List<ItemVariant>, Long>();
            for (var slot : slots)
                slot.ifPresent(ingredient -> counts.merge(ingredient.items().map(item -> ItemVariant.of(item.value())).toList(), 1L, Long::sum));
            var ingredients = counts.entrySet().stream().map(entry -> new WorksRecipe.Ingredient<>(entry.getKey(), entry.getValue())).toList();
            var remainders = crafting.getRemainingItems(input).stream().filter(stack -> !stack.isEmpty())
                    .map(stack -> new WorksRecipe.Stack<>(ItemVariant.of(stack), (long) stack.getCount())).toList();

            add(new WorksRecipe<>(id.toString(), StationKind.TABLE, List.of(new WorksRecipe.Stack<>(ItemVariant.of(result), result.getCount())), ingredients,
                    remainders, Map.of()), holder);
        }
    }

    /**
     * @return a crafting recipe's grid, one optional ingredient per slot, or null for one it can't lay out
     */
    static @Nullable List<java.util.Optional<Ingredient>> slotsOf(CraftingRecipe recipe)
    {
        if (recipe instanceof ShapedRecipe shaped)
            return shaped.getIngredients();
        if (recipe instanceof ShapelessRecipe shapeless)
            return shapeless.placementInfo().ingredients().stream().map(java.util.Optional::of).toList();
        return null;
    }

    /**
     * Lays a crafting recipe's grid out with an item chosen for each ingredient.
     */
    static CraftingInput inputOf(CraftingRecipe recipe, List<java.util.Optional<Ingredient>> slots, Function<Ingredient, ItemStack> choose)
    {
        var stacks = slots.stream().map(slot -> slot.map(choose).orElse(ItemStack.EMPTY)).toList();
        if (recipe instanceof ShapedRecipe shaped)
            return CraftingInput.of(shaped.getWidth(), shaped.getHeight(), stacks);
        return CraftingInput.of(stacks.size(), 1, stacks);
    }
}
