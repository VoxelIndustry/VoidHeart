package net.voxelindustry.voidheart.common.content.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Everything a player learned. Time is only applied when {@link #settle} is called, which lets knowledge keep flowing with the repository unloaded and its owner away.
 */
public class PlayerKnowledge
{
    private static final double FINISHED = 1E-6;

    public static final Codec<PlayerKnowledge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("knowledge", 0D).forGetter(PlayerKnowledge::getKnowledge),
            Codec.LONG.optionalFieldOf("last_settled", -1L).forGetter(data -> data.lastSettled),
            Codec.INT.optionalFieldOf("insights", 0).forGetter(PlayerKnowledge::getInsights),
            Codec.INT.optionalFieldOf("insights_bought", 0).forGetter(PlayerKnowledge::getInsightsBought),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("fed", Map.of()).forGetter(data -> data.fed),
            Identifier.CODEC.listOf().optionalFieldOf("revealed", List.of()).forGetter(data -> List.copyOf(data.revealed)),
            Identifier.CODEC.listOf().optionalFieldOf("completed", List.of()).forGetter(data -> List.copyOf(data.completed)),
            Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE).optionalFieldOf("progress", Map.of()).forGetter(data -> data.progress),
            Identifier.CODEC.optionalFieldOf("active").forGetter(PlayerKnowledge::getActive),
            GlobalPos.CODEC.optionalFieldOf("repository").forGetter(PlayerKnowledge::getRepository)
    ).apply(instance, PlayerKnowledge::new));

    public enum Feed
    {
        ACCEPTED,
        EXHAUSTED,
        FULL
    }

    @Getter
    private       double                   knowledge;
    private       long                     lastSettled;
    @Getter
    private       int                      insights;
    @Getter
    private       int                      insightsBought;
    @Getter
    private final Map<Identifier, Integer> fed;
    @Getter
    private final Set<Identifier>          revealed;
    @Getter
    private final Set<Identifier>          completed;
    @Getter
    private final Map<Identifier, Double>  progress;
    @Getter
    private       Optional<Identifier>     active;
    @Getter
    private       Optional<GlobalPos>      repository;

    public PlayerKnowledge()
    {
        this(0, -1, 0, 0, Map.of(), List.of(), List.of(), Map.of(), Optional.empty(), Optional.empty());
    }

    private PlayerKnowledge(double knowledge, long lastSettled, int insights, int insightsBought, Map<Identifier, Integer> fed, List<Identifier> revealed,
                            List<Identifier> completed, Map<Identifier, Double> progress, Optional<Identifier> active, Optional<GlobalPos> repository)
    {
        this.knowledge = knowledge;
        this.lastSettled = lastSettled;
        this.insights = insights;
        this.insightsBought = insightsBought;
        this.fed = new HashMap<>(fed);
        this.revealed = new HashSet<>(revealed);
        this.completed = new HashSet<>(completed);
        this.progress = new HashMap<>(progress);
        this.active = active;
        this.repository = repository;
    }

    /**
     * No repository bound means nothing flows in, though a running research still drains what is left in the pool.
     */
    public double rate(KnowledgeRules rules, Map<Identifier, Research> tree)
    {
        if (repository.isEmpty())
            return 0;
        return rules.baseRate() + completedOf(tree).stream().mapToDouble(Research::passiveBonus).sum();
    }

    public double cap(KnowledgeRules rules, Map<Identifier, Research> tree)
    {
        return rules.baseCap() + completedOf(tree).stream().mapToDouble(Research::capBonus).sum();
    }

    private List<Research> completedOf(Map<Identifier, Research> tree)
    {
        return completed.stream().map(tree::get).filter(Objects::nonNull).toList();
    }

    /**
     * Applies the time passed since the last call.
     *
     * @return the research finished along the way, if any
     */
    public Optional<Identifier> settle(long gameTime, KnowledgeRules rules, Map<Identifier, Research> tree)
    {
        double seconds = lastSettled < 0 ? 0 : (gameTime - lastSettled) / 20D;
        lastSettled = gameTime;
        if (seconds <= 0)
            return Optional.empty();

        var research = active.filter(id -> !completed.contains(id)).map(tree::get);
        if (research.isEmpty())
        {
            active = Optional.empty();
            knowledge = KnowledgeMath.accrue(knowledge, cap(rules, tree), rate(rules, tree), seconds);
            return Optional.empty();
        }

        var id = active.orElseThrow();
        double cost = research.get().knowledgeCost();
        var flow = KnowledgeMath.research(knowledge, cap(rules, tree), rate(rules, tree), cost - progress.getOrDefault(id, 0D), research.get().knowledgePerSecond(), seconds);

        knowledge = flow.knowledge();
        progress.put(id, cost - flow.remaining());
        if (flow.remaining() > FINISHED)
            return Optional.empty();

        grant(id);
        knowledge = KnowledgeMath.accrue(knowledge, cap(rules, tree), rate(rules, tree), seconds - flow.seconds());
        return Optional.of(id);
    }

    public boolean buyInsight(KnowledgeRules rules)
    {
        double cost = rules.insightCost(insightsBought);
        if (knowledge < cost)
            return false;

        knowledge -= cost;
        insights++;
        insightsBought++;
        return true;
    }

    /**
     * A research can only be revealed from the roots of the tree or next to what the player already has.
     */
    public boolean isDiscoverable(Research research)
    {
        return research.requires().isEmpty() || research.requires().stream().anyMatch(completed::contains);
    }

    public boolean reveal(Identifier id, Map<Identifier, Research> tree)
    {
        var research = tree.get(id);
        if (research == null || revealed.contains(id) || !isDiscoverable(research) || insights < research.insightCost())
            return false;

        insights -= research.insightCost();
        revealed.add(id);
        return true;
    }

    /**
     * Starts a revealed research, replacing the running one, or pauses it when it is the running one. Progress is kept either way.
     */
    public boolean toggleActive(Identifier id)
    {
        if (!revealed.contains(id) || completed.contains(id))
            return false;

        active = active.filter(id::equals).isPresent() ? Optional.empty() : Optional.of(id);
        return true;
    }

    public double yieldOf(Identifier item, double value)
    {
        return value * KnowledgeMath.feedMultiplier(fed.getOrDefault(item, 0));
    }

    public Feed feed(Identifier item, double value, double cap)
    {
        double gain = yieldOf(item, value);
        if (gain <= 0)
            return Feed.EXHAUSTED;
        if (knowledge >= cap)
            return Feed.FULL;

        knowledge = Math.min(cap, knowledge + gain);
        fed.merge(item, 1, Integer::sum);
        return Feed.ACCEPTED;
    }

    public void bind(Optional<GlobalPos> repository)
    {
        this.repository = repository;
    }

    public void addKnowledge(double amount)
    {
        knowledge = Math.max(0, knowledge + amount);
    }

    public void addInsights(int amount)
    {
        insights = Math.max(0, insights + amount);
    }

    public void grant(Identifier id)
    {
        revealed.add(id);
        completed.add(id);
        progress.remove(id);
        active = active.filter(running -> !running.equals(id));
    }
}
