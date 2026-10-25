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
import net.voxelindustry.voidheart.common.recipe.ShatterForgeRecipe;

import java.util.List;
import java.util.Optional;

public class ShatterForgeRecipeDisplay extends BasicDisplay
{
    public static final DisplaySerializer<ShatterForgeRecipeDisplay> SERIALIZER = DisplaySerializer.of(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    EntryIngredient.codec().listOf().fieldOf("inputs").forGetter(ShatterForgeRecipeDisplay::getInputEntries),
                    EntryIngredient.codec().listOf().fieldOf("outputs").forGetter(ShatterForgeRecipeDisplay::getOutputEntries),
                    Identifier.CODEC.optionalFieldOf("location").forGetter(ShatterForgeRecipeDisplay::getDisplayLocation),
                    Codec.INT.fieldOf("time").forGetter(ShatterForgeRecipeDisplay::getTime),
                    Codec.INT.fieldOf("monolith_count").forGetter(ShatterForgeRecipeDisplay::getMonolithCount)
            ).apply(instance, ShatterForgeRecipeDisplay::new)),
            StreamCodec.composite(
                    EntryIngredient.streamCodec().apply(ByteBufCodecs.list()), ShatterForgeRecipeDisplay::getInputEntries,
                    EntryIngredient.streamCodec().apply(ByteBufCodecs.list()), ShatterForgeRecipeDisplay::getOutputEntries,
                    ByteBufCodecs.optional(Identifier.STREAM_CODEC), ShatterForgeRecipeDisplay::getDisplayLocation,
                    ByteBufCodecs.VAR_INT, ShatterForgeRecipeDisplay::getTime,
                    ByteBufCodecs.VAR_INT, ShatterForgeRecipeDisplay::getMonolithCount,
                    ShatterForgeRecipeDisplay::new));

    @Getter
    private final int time;
    @Getter
    private final int monolithCount;

    public ShatterForgeRecipeDisplay(List<EntryIngredient> inputs, List<EntryIngredient> outputs, Optional<Identifier> location, int time, int monolithCount)
    {
        super(inputs, outputs, location);

        this.time = time;
        this.monolithCount = monolithCount;
    }

    public ShatterForgeRecipeDisplay(RecipeHolder<ShatterForgeRecipe> holder)
    {
        this(CollectionUtils.map(holder.value().getRecipeInputs(ItemStack.class), ingredient -> EntryIngredients.of(ingredient.getRaw())),
                CollectionUtils.map(holder.value().getRecipeOutputs(ItemStack.class), ingredient -> EntryIngredients.of(ingredient.getRaw())),
                Optional.of(holder.id().identifier()),
                holder.value().getTime(),
                holder.value().getMonolithCount());
    }

    @Override
    public CategoryIdentifier<ShatterForgeRecipeDisplay> getCategoryIdentifier()
    {
        return ShatterForgeRecipeCategory.IDENTIFIER;
    }

    @Override
    public DisplaySerializer<ShatterForgeRecipeDisplay> getSerializer()
    {
        return SERIALIZER;
    }
}
