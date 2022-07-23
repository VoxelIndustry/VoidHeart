package net.voxelindustry.voidheart.data.provider;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.advancement.criterion.InventoryChangedCriterion.Conditions;
import net.minecraft.data.DataCache;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.server.RecipesProvider;
import net.minecraft.data.server.recipe.RecipeJsonProvider;
import net.minecraft.data.server.recipe.ShapedRecipeJsonFactory;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.Items;
import net.minecraft.predicate.NumberRange.IntRange;
import net.minecraft.predicate.entity.EntityPredicate.Extended;
import net.minecraft.predicate.item.ItemPredicate;
import net.minecraft.predicate.item.ItemPredicate.Builder;
import net.minecraft.util.Identifier;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class VoidHeartRecipeProvider extends RecipesProvider
{
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson   GSON   = new GsonBuilder().setPrettyPrinting().create();

    private final DataGenerator root;

    public VoidHeartRecipeProvider(DataGenerator generator)
    {
        super(generator);

        root = generator;
    }

    @Override
    public String getName()
    {
        return VoidHeart.MODID + " recipes";
    }

    @Override
    public void run(DataCache cache)
    {
        Path path = root.getOutput();
        Set<Identifier> set = Sets.newHashSet();
        generate((recipeJsonProvider) ->
        {
            if (!set.add(recipeJsonProvider.getRecipeId()))
            {
                throw new IllegalStateException("Duplicate recipe " + recipeJsonProvider.getRecipeId());
            }
            else
            {
                saveRecipe(cache, recipeJsonProvider.toJson(), path.resolve("data/" + recipeJsonProvider.getRecipeId().getNamespace() + "/recipes/" + recipeJsonProvider.getRecipeId().getPath() + ".json"));
                JsonObject jsonObject = recipeJsonProvider.toAdvancementJson();
                if (jsonObject != null)
                {
                    saveRecipeAdvancement(cache, jsonObject, path.resolve("data/" + recipeJsonProvider.getRecipeId().getNamespace() + "/advancements/" + recipeJsonProvider.getAdvancementId().getPath() + ".json"));
                }

            }
        });
    }

    private void generate(Consumer<RecipeJsonProvider> exporter)
    {
        ShapedRecipeJsonFactory.create(VoidHeartBlocks.VOIDSTONE_CHISELED)
                .pattern("XX ")
                .input('X', Items.APPLE)
                .criterion("has_apple", conditionsFromItem(Items.APPLE)).offerTo(exporter);
    }

    private static void saveRecipe(DataCache dataCache, JsonObject jsonObject, Path path)
    {
        try
        {
            saveJSONData(dataCache, jsonObject, path);
        } catch (IOException var18)
        {
            LOGGER.error("Couldn't save recipe {}", path, var18);
        }
    }

    private static void saveRecipeAdvancement(DataCache dataCache, JsonObject jsonObject, Path path)
    {
        try
        {
            saveJSONData(dataCache, jsonObject, path);
        } catch (IOException var18)
        {
            LOGGER.error("Couldn't save recipe advancement {}", path, var18);
        }
    }

    private static void saveJSONData(DataCache dataCache, JsonObject jsonObject, Path path) throws IOException
    {
        String string = GSON.toJson(jsonObject);
        String string2 = SHA1.hashUnencodedChars(string).toString();
        if (!Objects.equals(dataCache.getOldSha1(path), string2) || !Files.exists(path))
        {
            Files.createDirectories(path.getParent());
            BufferedWriter bufferedWriter = Files.newBufferedWriter(path);
            Throwable var6 = null;

            try
            {
                bufferedWriter.write(string);
            } catch (Throwable var16)
            {
                var6 = var16;
                throw var16;
            } finally
            {
                if (var6 != null)
                {
                    try
                    {
                        bufferedWriter.close();
                    } catch (Throwable var15)
                    {
                        var6.addSuppressed(var15);
                    }
                }
                else
                {
                    bufferedWriter.close();
                }
            }
        }

        dataCache.updateSha1(path, string2);
    }

    private Conditions conditionsFromItem(ItemConvertible itemConvertible)
    {
        return conditionsFromItemPredicates(Builder.create().items(itemConvertible).build());
    }

    private Conditions conditionsFromItemPredicates(ItemPredicate... itemPredicates)
    {
        return new Conditions(Extended.EMPTY, IntRange.ANY, IntRange.ANY, IntRange.ANY, itemPredicates);
    }
}
