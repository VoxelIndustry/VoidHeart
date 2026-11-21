package net.voxelindustry.voidheart.common.content.crystal;

import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceKey;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;

import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.stream.Stream;

/**
 * A shaped recipe whose result takes the habit of the crystal crafted into it, for the habits that act on that result. A Searing bundle makes a Searing
 * cell. Crystal still holding taint it drew in is refused, as cradles and sockets refuse it.
 */
public class HabitShapedRecipe extends ShapedRecipe
{
    /** The habits a result made this way carries: those that change a Rubrum Cell. */
    public static final Set<CrystalHabit> CARRIED = Set.of(CrystalHabit.SEARING, CrystalHabit.STILL, CrystalHabit.WRUNG);

    public static final MapCodec<HabitShapedRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Recipe.CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.commonInfo),
            CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(recipe -> recipe.bookInfo),
            ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(recipe -> recipe.result)
    ).apply(instance, HabitShapedRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, HabitShapedRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, recipe -> recipe.commonInfo,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC, recipe -> recipe.bookInfo,
            ShapedRecipePattern.STREAM_CODEC, recipe -> recipe.pattern,
            ItemStackTemplate.STREAM_CODEC, recipe -> recipe.result,
            HabitShapedRecipe::new);

    public static final RecipeSerializer<HabitShapedRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final ShapedRecipePattern pattern;
    private final ItemStackTemplate   result;

    public HabitShapedRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ShapedRecipePattern pattern, ItemStackTemplate result)
    {
        super(commonInfo, bookInfo, pattern, result);
        this.pattern = pattern;
        this.result = result;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public RecipeSerializer<ShapedRecipe> getSerializer()
    {
        return (RecipeSerializer) SERIALIZER;
    }

    @Override
    public boolean matches(CraftingInput input, Level level)
    {
        for (int slot = 0; slot < input.size(); slot++)
        {
            if (CrystalBundles.soakOf(input.getItem(slot)) > 0)
                return false;
        }
        return super.matches(input, level);
    }

    @Override
    public ItemStack assemble(CraftingInput input)
    {
        var stack = super.assemble(input);
        var habit = habitIn(input);
        if (CARRIED.contains(habit))
            stack.set(VoidHeartDataComponents.CRYSTAL_HABIT, habit);
        return stack;
    }

    /**
     * For datagen: an output that writes the shaped recipes saved to it as habit recipes. Both share one shape on disk, so each goes through its codec.
     */
    public static RecipeOutput carryingHabit(RecipeOutput output)
    {
        return new RecipeOutput()
        {
            @Override
            public void accept(ResourceKey<Recipe<?>> id, Recipe<?> recipe, @Nullable AdvancementHolder advancement)
            {
                if (!(recipe instanceof ShapedRecipe shaped))
                    throw new IllegalArgumentException("Only shaped recipes carry a habit: " + id);
                var json = ShapedRecipe.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, shaped).getOrThrow();
                output.accept(id, MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow(), advancement);
            }

            @Override
            public Advancement.Builder advancement()
            {
                return output.advancement();
            }

            @Override
            public <S> HolderGetter<S> lookup(ResourceKey<? extends Registry<? extends S>> key)
            {
                return output.lookup(key);
            }

            @Override
            @SuppressWarnings("deprecation")
            public <S> Stream<Holder.Reference<S>> listContextElements(ResourceKey<? extends Registry<? extends S>> key)
            {
                return output.listContextElements(key);
            }
        };
    }

    private static CrystalHabit habitIn(CraftingInput input)
    {
        for (int slot = 0; slot < input.size(); slot++)
        {
            var habit = CrystalBundles.habitOf(input.getItem(slot));
            if (habit.isHabit())
                return habit;
        }
        return CrystalHabit.NONE;
    }
}
