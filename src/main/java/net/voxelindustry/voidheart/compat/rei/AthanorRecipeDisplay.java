package net.voxelindustry.voidheart.compat.rei;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
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
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.recipe.AthanorRecipe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The first input is the base set in the core, the others the reagents of the crucibles.
 */
public class AthanorRecipeDisplay extends BasicDisplay
{
    public static final DisplaySerializer<AthanorRecipeDisplay> SERIALIZER = DisplaySerializer.of(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    EntryIngredient.codec().listOf().fieldOf("inputs").forGetter(AthanorRecipeDisplay::getInputEntries),
                    EntryIngredient.codec().listOf().fieldOf("outputs").forGetter(AthanorRecipeDisplay::getOutputEntries),
                    Identifier.CODEC.optionalFieldOf("location").forGetter(AthanorRecipeDisplay::getDisplayLocation),
                    Codec.INT.fieldOf("time").forGetter(AthanorRecipeDisplay::getTime),
                    Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("aspects", Map.of()).forGetter(AthanorRecipeDisplay::getAspectIds)
            ).apply(instance, AthanorRecipeDisplay::new)),
            StreamCodec.composite(
                    EntryIngredient.streamCodec().apply(ByteBufCodecs.list()), AthanorRecipeDisplay::getInputEntries,
                    EntryIngredient.streamCodec().apply(ByteBufCodecs.list()), AthanorRecipeDisplay::getOutputEntries,
                    ByteBufCodecs.optional(Identifier.STREAM_CODEC), AthanorRecipeDisplay::getDisplayLocation,
                    ByteBufCodecs.VAR_INT, AthanorRecipeDisplay::getTime,
                    ByteBufCodecs.map(LinkedHashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_INT), display -> new LinkedHashMap<Identifier, Integer>(display.getAspectIds()),
                    AthanorRecipeDisplay::new));

    @Getter
    private final int time;
    @Getter
    private final Map<Identifier, Integer> aspectIds;

    public AthanorRecipeDisplay(List<EntryIngredient> inputs, List<EntryIngredient> outputs, Optional<Identifier> location, int time, Map<Identifier, Integer> aspectIds)
    {
        super(inputs, outputs, location);

        this.time = time;
        this.aspectIds = Map.copyOf(aspectIds);
    }

    public AthanorRecipeDisplay(RecipeHolder<AthanorRecipe> holder)
    {
        this(CollectionUtils.map(holder.value().getRecipeInputs(ItemStack.class), ingredient -> EntryIngredients.of(ingredient.getRaw())),
                CollectionUtils.map(holder.value().getRecipeOutputs(ItemStack.class), ingredient -> EntryIngredients.of(ingredient.getRaw())),
                Optional.of(holder.id().identifier()),
                holder.value().getTime(),
                aspectIds(holder.value().getAspects()));
    }

    private static Map<Identifier, Integer> aspectIds(Map<EssenceType, Integer> aspects)
    {
        var ids = new LinkedHashMap<Identifier, Integer>();
        aspects.forEach((type, amount) -> ids.put(type.identifier(), amount));
        return ids;
    }

    public Map<EssenceType, Integer> getAspects()
    {
        var aspects = new LinkedHashMap<EssenceType, Integer>();
        aspectIds.forEach((id, amount) ->
        {
            if (EssenceRegistry.ESSENCE_REGISTRY.containsKey(id))
                aspects.put(EssenceRegistry.ESSENCE_REGISTRY.getValue(id), amount);
        });
        return aspects;
    }

    @Override
    public CategoryIdentifier<AthanorRecipeDisplay> getCategoryIdentifier()
    {
        return AthanorRecipeCategory.IDENTIFIER;
    }

    @Override
    public DisplaySerializer<AthanorRecipeDisplay> getSerializer()
    {
        return SERIALIZER;
    }
}
