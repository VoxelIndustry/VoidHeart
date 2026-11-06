package net.voxelindustry.voidheart.common.content.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Everything a player learned. Time is only applied when {@link #settle} is called, which lets knowledge keep flowing with the repository unloaded and its owner away.
 */
public class PlayerKnowledge
{
    public static final int STARTING_INSIGHTS = 1;

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
            GlobalPos.CODEC.optionalFieldOf("repository").forGetter(PlayerKnowledge::getRepository),
            Codec.INT.optionalFieldOf("columns", 0).forGetter(PlayerKnowledge::getColumns),
            Codec.intRange(0, Strain.MAX_ATTUNEMENT).optionalFieldOf("attunement", 0).forGetter(PlayerKnowledge::getAttunement),
            Codec.INT.optionalFieldOf("cogitants", 0).forGetter(PlayerKnowledge::getCogitants),
            Codec.BOOL.optionalFieldOf("loom", false).forGetter(PlayerKnowledge::hasLoom),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.DOUBLE).optionalFieldOf("proxies", Map.of()).forGetter(PlayerKnowledge::getProxies),
            Codec.DOUBLE.optionalFieldOf("insight_trickle", 0D).forGetter(data -> data.insightTrickle)
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
    /** Knowledge Columns around the bound repository when it was last looked at, since it may be unloaded when knowledge flows. */
    @Getter
    private       int                      columns;
    /** Raised by the void heart, then by each graft eaten. */
    @Getter
    private       int                      attunement;
    /** Cogitants around the bound repository when it was last looked at, like the columns. */
    @Getter
    private       int                      cogitants;
    private       boolean                  loom;
    /** Seconds each Meat Proxy around the bound repository can still bear strain, worn here as research runs and told back to the blocks. */
    @Getter
    private       Map<UUID, Double>        proxies;
    /** The part of an insight the proxies have made so far. */
    private       double                   insightTrickle;

    public PlayerKnowledge()
    {
        this(0, -1, STARTING_INSIGHTS, 0, Map.of(), List.of(), List.of(), Map.of(), Optional.empty(), Optional.empty(), 0, 0, 0, false, Map.of(), 0);
    }

    private PlayerKnowledge(double knowledge, long lastSettled, int insights, int insightsBought, Map<Identifier, Integer> fed, List<Identifier> revealed,
                            List<Identifier> completed, Map<Identifier, Double> progress, Optional<Identifier> active, Optional<GlobalPos> repository, int columns,
                            int attunement, int cogitants, boolean loom, Map<UUID, Double> proxies, double insightTrickle)
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
        this.columns = columns;
        this.attunement = attunement;
        this.cogitants = cogitants;
        this.loom = loom;
        this.proxies = new HashMap<>(proxies);
        this.insightTrickle = insightTrickle;
    }

    /**
     * No repository bound means nothing flows in, though a running research still drains what is left in the pool.
     */
    public double rate(KnowledgeRules rules, Map<Identifier, Research> tree)
    {
        if (repository.isEmpty())
            return 0;
        return rules.baseRate() + completedOf(tree).stream().mapToDouble(Research::passiveBonus).sum() + countedCogitants(rules) * cogitantRate(tree);
    }

    public int countedCogitants(KnowledgeRules rules)
    {
        return Math.min(cogitants, rules.cogitants());
    }

    /** What each Cogitant adds, from the deepest cogitation researched. */
    public double cogitantRate(Map<Identifier, Research> tree)
    {
        return completedOf(tree).stream().mapToDouble(Research::cogitantRate).max().orElse(0);
    }

    public boolean hasLoom()
    {
        return loom;
    }

    public double insightCost(KnowledgeRules rules)
    {
        return rules.insightCost(insightsBought, loom);
    }

    public int tolerance()
    {
        return Strain.tolerance(attunement);
    }

    /** What the player bears now, with the proxies that still have something left. */
    public int bearableStrain(KnowledgeRules rules)
    {
        return tolerance() + Strain.alive(proxies) * rules.proxyStrain();
    }

    public boolean canBear(Research research, KnowledgeRules rules)
    {
        return research.strain() <= bearableStrain(rules);
    }

    /** Whether the running research is held back for want of someone to bear its strain. */
    public boolean isStrained(KnowledgeRules rules, Map<Identifier, Research> tree)
    {
        return active.filter(id -> !completed.contains(id)).map(tree::get).filter(research -> !canBear(research, rules)).isPresent();
    }

    public double cap(KnowledgeRules rules, Map<Identifier, Research> tree)
    {
        return rules.baseCap() + columns * columnCapacity(tree);
    }

    /** What each Knowledge Column holds, from the deepest archive researched. */
    public double columnCapacity(Map<Identifier, Research> tree)
    {
        return completedOf(tree).stream().mapToDouble(Research::columnCapacity).max().orElse(0);
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
        trickleInsights(rules, seconds);

        var research = active.filter(id -> !completed.contains(id)).map(tree::get);
        if (research.isEmpty())
        {
            active = Optional.empty();
            knowledge = KnowledgeMath.accrue(knowledge, cap(rules, tree), rate(rules, tree), seconds);
            return Optional.empty();
        }

        var id = active.orElseThrow();
        double cost = research.get().knowledgeCost();
        // Strain beyond what the player bears lets the research run only as long as proxies carry it, knowledge still gathers for the rest
        int needed = Strain.proxiesNeeded(research.get().strain(), tolerance(), rules.proxyStrain());
        double covered = Strain.cover(proxies, needed, seconds).seconds();
        var flow = KnowledgeMath.research(knowledge, cap(rules, tree), rate(rules, tree), cost - progress.getOrDefault(id, 0D), research.get().knowledgePerSecond(), covered);

        knowledge = flow.knowledge();
        progress.put(id, cost - flow.remaining());
        proxies = new HashMap<>(Strain.cover(proxies, needed, flow.seconds()).proxies());

        boolean finished = flow.remaining() <= FINISHED;
        if (finished)
            grant(id);
        knowledge = KnowledgeMath.accrue(knowledge, cap(rules, tree), rate(rules, tree), seconds - flow.seconds());
        return finished ? Optional.of(id) : Optional.empty();
    }

    /** Proxies think too, and their thoughts come out as insights. Counted from those still alive when settling, which is often enough. */
    private void trickleInsights(KnowledgeRules rules, double seconds)
    {
        int thinking = Math.min(Strain.alive(proxies), rules.proxies());
        if (thinking <= 0)
            return;

        insightTrickle += thinking * rules.proxyInsightsPerHour() * seconds / 3600;
        int whole = (int) insightTrickle;
        insights += whole;
        insightTrickle -= whole;
    }

    /** From 0 to 1, how far the proxies are into making the next insight. */
    public double getInsightTrickle()
    {
        return insightTrickle;
    }

    public boolean buyInsight(KnowledgeRules rules)
    {
        double cost = insightCost(rules);
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
        // What stands around another repository is not this, it reports its own shortly
        if (!this.repository.equals(repository))
        {
            columns = 0;
            cogitants = 0;
            loom = false;
            proxies = new HashMap<>();
        }
        this.repository = repository;
    }

    public void setColumns(int columns)
    {
        this.columns = Math.max(0, columns);
    }

    public void setUpgrades(int cogitants, boolean loom)
    {
        this.cogitants = Math.max(0, cogitants);
        this.loom = loom;
    }

    /**
     * Takes the proxies found around the repository, keeping the wear applied here since they were last seen.
     *
     * @return what each of them has left, to be told back to the blocks
     */
    public Map<UUID, Double> reportProxies(Map<UUID, Double> found)
    {
        var merged = new HashMap<UUID, Double>();
        found.forEach((id, left) -> merged.put(id, Math.max(0, Math.min(left, proxies.getOrDefault(id, left)))));
        proxies = merged;
        return Map.copyOf(merged);
    }

    /** @return whether that raised it */
    public boolean attune(int level)
    {
        if (level <= attunement || level > Strain.MAX_ATTUNEMENT)
            return false;
        attunement = level;
        return true;
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
