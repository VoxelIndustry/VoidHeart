package net.voxelindustry.voidheart.common.content.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/** A node of the tree, from data/voidheart/voidheart/research. Its recipes are the crafting and altar recipes refused until it is done. */
public record Research(int tier, int row, Identifier icon, int insightCost, double knowledgeCost, double knowledgePerSecond,
                       List<Identifier> requires, double passiveBonus, double capBonus, List<Identifier> recipes)
{
    public static final Codec<Research> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(1, 16).fieldOf("tier").forGetter(Research::tier),
            Codec.intRange(0, 64).fieldOf("row").forGetter(Research::row),
            Identifier.CODEC.fieldOf("icon").forGetter(Research::icon),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("insight_cost").forGetter(Research::insightCost),
            Codec.doubleRange(0.001, Double.MAX_VALUE).fieldOf("knowledge_cost").forGetter(Research::knowledgeCost),
            Codec.doubleRange(0.001, Double.MAX_VALUE).fieldOf("knowledge_per_second").forGetter(Research::knowledgePerSecond),
            Identifier.CODEC.listOf().optionalFieldOf("requires", List.of()).forGetter(Research::requires),
            Codec.DOUBLE.optionalFieldOf("passive_bonus", 0D).forGetter(Research::passiveBonus),
            Codec.DOUBLE.optionalFieldOf("cap_bonus", 0D).forGetter(Research::capBonus),
            Identifier.CODEC.listOf().optionalFieldOf("recipes", List.of()).forGetter(Research::recipes)
    ).apply(instance, Research::new));

    public static String nameKey(Identifier id)
    {
        return "research." + id.getNamespace() + "." + id.getPath();
    }

    public static String descriptionKey(Identifier id)
    {
        return nameKey(id) + ".desc";
    }
}
