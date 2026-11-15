package net.voxelindustry.voidheart.compat.rei;

import com.mojang.serialization.Codec;
import lombok.Getter;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.voxelindustry.voidheart.common.recipe.AltarRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AltarRecipeDisplay extends BasicDisplay
{
    public static final DisplaySerializer<AltarRecipeDisplay> SERIALIZER = DisplaySerializer.of(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    EntryIngredient.codec().listOf().fieldOf("inputs").forGetter(AltarRecipeDisplay::getInputEntries),
                    EntryIngredient.codec().listOf().fieldOf("outputs").forGetter(AltarRecipeDisplay::getOutputEntries),
                    Identifier.CODEC.optionalFieldOf("location").forGetter(AltarRecipeDisplay::getDisplayLocation),
                    Codec.INT.optionalFieldOf("experience", 0).forGetter(AltarRecipeDisplay::getExperience)
            ).apply(instance, AltarRecipeDisplay::new)),
            StreamCodec.composite(
                    EntryIngredient.streamCodec().apply(ByteBufCodecs.list()), AltarRecipeDisplay::getInputEntries,
                    EntryIngredient.streamCodec().apply(ByteBufCodecs.list()), AltarRecipeDisplay::getOutputEntries,
                    ByteBufCodecs.optional(Identifier.STREAM_CODEC), AltarRecipeDisplay::getDisplayLocation,
                    ByteBufCodecs.VAR_INT, AltarRecipeDisplay::getExperience,
                    AltarRecipeDisplay::new));

    @Getter
    private final int experience;

    public AltarRecipeDisplay(List<EntryIngredient> inputs, List<EntryIngredient> outputs, Optional<Identifier> location, int experience)
    {
        super(inputs, outputs, location);

        this.experience = experience;
    }

    public AltarRecipeDisplay(RecipeHolder<AltarRecipe> holder)
    {
        this(inputsOf(holder.value()),
                outputsOf(holder.value()),
                Optional.of(holder.id().identifier()),
                holder.value().getExperience());
    }

    /**
     * The catalyst cycles through every item it takes, the pillar ingredients are fixed.
     */
    private static List<EntryIngredient> inputsOf(AltarRecipe recipe)
    {
        var inputs = new ArrayList<EntryIngredient>();
        inputs.add(EntryIngredients.ofItemStacks(recipe.catalystChoices()));
        var ingredients = recipe.getRecipeInputs(ItemStack.class);
        for (int index = 1; index < ingredients.size(); index++)
            inputs.add(EntryIngredients.of(ingredients.get(index).getRaw()));
        return inputs;
    }

    /**
     * A result made from the catalyst cycles alongside it.
     */
    private static List<EntryIngredient> outputsOf(AltarRecipe recipe)
    {
        var results = recipe.resultChoices();
        return results.isEmpty() ? List.of() : List.of(EntryIngredients.ofItemStacks(results));
    }

    @Override
    public CategoryIdentifier<AltarRecipeDisplay> getCategoryIdentifier()
    {
        return AltarRecipeCategory.IDENTIFIER;
    }

    @Override
    public DisplaySerializer<AltarRecipeDisplay> getSerializer()
    {
        return SERIALIZER;
    }
}
