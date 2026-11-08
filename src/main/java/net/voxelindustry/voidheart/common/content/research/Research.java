package net.voxelindustry.voidheart.common.content.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.List;

/** A node of the tree, from data/voidheart/voidheart/research, on the island of its constellation. Where it shows on the map is worked out from its requirements, see the client's ResearchMapPlan. Its recipes are the crafting and altar recipes refused until it is done, and unlocks is what they make, since a client is never told the recipes.
 * Strain is what running it weighs on the player, see {@link Strain}. Hidden until keeps it out of sight until all of those researches are done, without
 * hanging it from them on the map. */
public record Research(int tier, Constellation constellation, Identifier icon, int insightCost, double knowledgeCost, double knowledgePerSecond,
                       List<Identifier> requires, double passiveBonus, double columnCapacity, double cogitantRate, int strain, List<Identifier> unlocks,
                       List<Identifier> recipes, List<Identifier> hiddenUntil)
{
    public Research(int tier, Constellation constellation, Identifier icon, int insightCost, double knowledgeCost, double knowledgePerSecond,
                    List<Identifier> requires, double passiveBonus, double columnCapacity, double cogitantRate, int strain, List<Identifier> unlocks,
                    List<Identifier> recipes)
    {
        this(tier, constellation, icon, insightCost, knowledgeCost, knowledgePerSecond, requires, passiveBonus, columnCapacity, cogitantRate, strain, unlocks, recipes, List.of());
    }

    public static final Codec<Research> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(1, 16).fieldOf("tier").forGetter(Research::tier),
            Constellation.CODEC.fieldOf("constellation").forGetter(Research::constellation),
            Identifier.CODEC.fieldOf("icon").forGetter(Research::icon),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("insight_cost").forGetter(Research::insightCost),
            Codec.doubleRange(0.001, Double.MAX_VALUE).fieldOf("knowledge_cost").forGetter(Research::knowledgeCost),
            Codec.doubleRange(0.001, Double.MAX_VALUE).fieldOf("knowledge_per_second").forGetter(Research::knowledgePerSecond),
            Identifier.CODEC.listOf().optionalFieldOf("requires", List.of()).forGetter(Research::requires),
            Codec.DOUBLE.optionalFieldOf("passive_bonus", 0D).forGetter(Research::passiveBonus),
            Codec.DOUBLE.optionalFieldOf("column_capacity", 0D).forGetter(Research::columnCapacity),
            Codec.DOUBLE.optionalFieldOf("cogitant_rate", 0D).forGetter(Research::cogitantRate),
            Codec.intRange(0, 1000).optionalFieldOf("strain", 0).forGetter(Research::strain),
            Identifier.CODEC.listOf().optionalFieldOf("unlocks", List.of()).forGetter(Research::unlocks),
            Identifier.CODEC.listOf().optionalFieldOf("recipes", List.of()).forGetter(Research::recipes),
            Identifier.CODEC.listOf().optionalFieldOf("hidden_until", List.of()).forGetter(Research::hiddenUntil)
    ).apply(instance, Research::new));

    /** Whether it can be seen and revealed: from the roots of the tree or next to what is done, once whatever hides it is done too. */
    public boolean isDiscoverable(Collection<Identifier> completed)
    {
        return (requires.isEmpty() || requires.stream().anyMatch(completed::contains)) && completed.containsAll(hiddenUntil);
    }

    /** How fast it progresses while stored knowledge lasts: its own burn from the store, and everything gathered meanwhile on top. */
    public double speed(double gathering)
    {
        return knowledgePerSecond + gathering;
    }

    public static String nameKey(Identifier id)
    {
        return "research." + id.getNamespace() + "." + id.getPath();
    }

    public static String descriptionKey(Identifier id)
    {
        return nameKey(id) + ".desc";
    }
}
