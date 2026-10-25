package net.voxelindustry.voidheart.compat.rei;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.CollectionUtils;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.voxelindustry.voidheart.common.recipe.AltarRecipe;

import java.util.List;
import java.util.Optional;

public class AltarRecipeDisplay extends BasicDisplay
{
    public static final DisplaySerializer<AltarRecipeDisplay> SERIALIZER = DisplaySerializer.of(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    EntryIngredient.codec().listOf().fieldOf("inputs").forGetter(AltarRecipeDisplay::getInputEntries),
                    EntryIngredient.codec().listOf().fieldOf("outputs").forGetter(AltarRecipeDisplay::getOutputEntries),
                    Identifier.CODEC.optionalFieldOf("location").forGetter(AltarRecipeDisplay::getDisplayLocation)
            ).apply(instance, AltarRecipeDisplay::new)),
            StreamCodec.composite(
                    EntryIngredient.streamCodec().apply(ByteBufCodecs.list()), AltarRecipeDisplay::getInputEntries,
                    EntryIngredient.streamCodec().apply(ByteBufCodecs.list()), AltarRecipeDisplay::getOutputEntries,
                    ByteBufCodecs.optional(Identifier.STREAM_CODEC), AltarRecipeDisplay::getDisplayLocation,
                    AltarRecipeDisplay::new));

    public AltarRecipeDisplay(List<EntryIngredient> inputs, List<EntryIngredient> outputs, Optional<Identifier> location)
    {
        super(inputs, outputs, location);
    }

    public AltarRecipeDisplay(RecipeHolder<AltarRecipe> holder)
    {
        this(CollectionUtils.map(holder.value().getRecipeInputs(ItemStack.class), ingredient -> EntryIngredients.of(ingredient.getRaw())),
                CollectionUtils.map(holder.value().getRecipeOutputs(ItemStack.class), ingredient -> EntryIngredients.of(ingredient.getRaw())),
                Optional.of(holder.id().identifier()));
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
