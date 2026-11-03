package net.voxelindustry.voidheart.common.content.research;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.voxelindustry.voidheart.common.recipe.AltarRecipe;
import net.voxelindustry.voidheart.common.recipe.ShatterForgeRecipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A recipe told plainly, for the repository's pages: recipes only exist on the server, so what a page shows of them is sent over when the screen opens.
 *
 * @param inputs     for the altar, what goes on the altar itself comes first
 * @param output     empty for a rite that makes no item
 * @param monoliths  monolith pairs a forge needs for it, 0 elsewhere
 * @param research   the research that locks it, if any
 * @param pattern      the cells of a shaped crafting recipe, row by row, empty for a recipe with no shape
 * @param patternWidth how many of those cells make a row, 0 for a recipe with no shape
 */
public record RecipeSheet(Kind kind, List<Part> inputs, Optional<Part> output, int experience, int monoliths, Optional<Identifier> research,
                          List<Optional<Identifier>> pattern, int patternWidth)
{
    private static final int GRID = 3;

    public RecipeSheet(Kind kind, List<Part> inputs, Optional<Part> output, int experience, int monoliths, Optional<Identifier> research)
    {
        this(kind, inputs, output, experience, monoliths, research, List.of(), 0);
    }

    public enum Kind
    {
        ALTAR,
        FORGE,
        CRAFTING
    }

    public record Part(Identifier item, int count)
    {
        static final StreamCodec<RegistryFriendlyByteBuf, Part> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Part::item,
                ByteBufCodecs.VAR_INT, Part::count,
                Part::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeSheet> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(index -> Kind.values()[index], Kind::ordinal), RecipeSheet::kind,
            Part.STREAM_CODEC.apply(ByteBufCodecs.list()), RecipeSheet::inputs,
            ByteBufCodecs.optional(Part.STREAM_CODEC), RecipeSheet::output,
            ByteBufCodecs.VAR_INT, RecipeSheet::experience,
            ByteBufCodecs.VAR_INT, RecipeSheet::monoliths,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), RecipeSheet::research,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC).apply(ByteBufCodecs.list()), RecipeSheet::pattern,
            ByteBufCodecs.VAR_INT, RecipeSheet::patternWidth,
            RecipeSheet::new);

    private static Part partOf(ItemStack stack)
    {
        return new Part(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount());
    }

    public static List<RecipeSheet> collect(MinecraftServer server)
    {
        var registries = server.registryAccess();
        var sheets = new ArrayList<RecipeSheet>();
        var withPages = Knowledge.tree(registries).values().stream().flatMap(node -> node.unlocks().stream()).collect(Collectors.toSet());

        // A category only lists the recipes added to it in code, those loaded from data are found in the game's recipes
        for (var holder : server.getRecipeManager().getRecipes())
        {
            var research = Knowledge.researchNaming(registries, holder.id().identifier());
            if (holder.value() instanceof AltarRecipe recipe)
                sheets.add(steamLayerSheet(Kind.ALTAR, recipe, recipe.getExperience(), 0, research));
            else if (holder.value() instanceof ShatterForgeRecipe recipe)
                sheets.add(steamLayerSheet(Kind.FORGE, recipe, recipe.getExperience(), recipe.getMonolithCount(), research));
            else if (holder.value() instanceof CraftingRecipe recipe)
                craftingSheet(server, recipe, research).filter(sheet -> withPages.contains(sheet.output().map(Part::item).orElse(null))).ifPresent(sheets::add);
        }
        return sheets;
    }

    private static RecipeSheet steamLayerSheet(Kind kind, net.voxelindustry.steamlayer.recipe.RecipeBase recipe, int experience, int monoliths, Optional<Identifier> research)
    {
        var inputs = recipe.getRecipeInputs(ItemStack.class).stream().map(ingredient -> partOf(ingredient.getRaw())).toList();
        var output = recipe.getRecipeOutputs(ItemStack.class).stream().map(ingredient -> partOf(ingredient.getRaw())).findFirst();
        return new RecipeSheet(kind, merged(inputs, kind == Kind.ALTAR ? 1 : 0), output, experience, monoliths, research);
    }

    private static Optional<RecipeSheet> craftingSheet(MinecraftServer server, CraftingRecipe recipe, Optional<Identifier> research)
    {
        var inputs = new ArrayList<Part>();
        for (var ingredient : recipe.placementInfo().ingredients())
            ingredient.items().findFirst().ifPresent(item -> inputs.add(new Part(BuiltInRegistries.ITEM.getKey(item.value()), 1)));

        var displays = recipe.display();
        if (displays.isEmpty())
            return Optional.empty();

        var result = displays.getFirst().result().resolveForFirstStack(SlotDisplayContext.fromLevel(server.overworld()));
        var pattern = new ArrayList<Optional<Identifier>>();
        int patternWidth = 0;
        if (recipe instanceof ShapedRecipe shaped)
        {
            patternWidth = shaped.getWidth();
            for (var cell : shaped.getIngredients())
                pattern.add(cell.flatMap(ingredient -> ingredient.items().findFirst()).map(item -> BuiltInRegistries.ITEM.getKey(item.value())));
        }
        return Optional.of(new RecipeSheet(Kind.CRAFTING, merged(inputs, 0), result.isEmpty() ? Optional.empty() : Optional.of(partOf(result)), 0, 0, research, pattern, patternWidth));
    }

    /**
     * The same item named several times becomes one count, past the first few inputs that are held apart.
     */
    private static List<Part> merged(List<Part> parts, int keptApart)
    {
        var merged = new ArrayList<>(parts.subList(0, Math.min(keptApart, parts.size())));
        var counts = new LinkedHashMap<Identifier, Integer>();
        for (var part : parts.subList(Math.min(keptApart, parts.size()), parts.size()))
            counts.merge(part.item(), part.count(), Integer::sum);
        counts.forEach((item, count) -> merged.add(new Part(item, count)));
        return merged;
    }

    /**
     * The nine cells of a crafting table with the pattern in their middle, empty for a recipe with no shape.
     */
    public List<Optional<Identifier>> gridCells()
    {
        if (patternWidth <= 0)
            return List.of();

        var cells = new ArrayList<Optional<Identifier>>(GRID * GRID);
        for (int index = 0; index < GRID * GRID; index++)
            cells.add(Optional.empty());

        int height = pattern.size() / patternWidth;
        int left = (GRID - patternWidth) / 2;
        int top = (GRID - height) / 2;
        for (int index = 0; index < pattern.size(); index++)
            cells.set((top + index / patternWidth) * GRID + left + index % patternWidth, pattern.get(index));
        return cells;
    }

    /**
     * The sheets a page about this item may show as how it is made: a recipe a research still locks stays hidden until that research is done.
     */
    public static List<RecipeSheet> making(List<RecipeSheet> sheets, Identifier item, List<Identifier> completed)
    {
        return sheets.stream()
                .filter(sheet -> sheet.output().filter(output -> output.item().equals(item)).isPresent())
                .filter(sheet -> sheet.research().isEmpty() || completed.contains(sheet.research().get()))
                .toList();
    }

    /**
     * Whether something about making this item is still hidden behind a research.
     */
    public static boolean hasLockedMaking(List<RecipeSheet> sheets, Identifier item, List<Identifier> completed)
    {
        return sheets.stream()
                .filter(sheet -> sheet.output().filter(output -> output.item().equals(item)).isPresent())
                .anyMatch(sheet -> sheet.research().isPresent() && !completed.contains(sheet.research().get()));
    }

    /**
     * What this item goes on to make, once each, among the recipes already open to the player.
     */
    public static List<Identifier> usedFor(List<RecipeSheet> sheets, Identifier item, List<Identifier> completed)
    {
        return sheets.stream()
                .filter(sheet -> sheet.research().isEmpty() || completed.contains(sheet.research().get()))
                .filter(sheet -> sheet.output().isPresent())
                .filter(sheet -> sheet.inputs().stream().anyMatch(input -> input.item().equals(item)))
                .map(sheet -> sheet.output().get().item())
                .filter(output -> !output.equals(item))
                .distinct()
                .sorted()
                .toList();
    }
}
