package net.voxelindustry.voidheart.common.content.research;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.voxelindustry.voidheart.common.content.research.PlayerKnowledge.Feed;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PlayerKnowledgeTest
{
    private static final KnowledgeRules RULES = new KnowledgeRules(0.1, 30, 10, 1.2);

    private static final Identifier ROOT  = Identifier.fromNamespaceAndPath("voidheart", "root");
    private static final Identifier CHILD = Identifier.fromNamespaceAndPath("voidheart", "child");
    private static final Identifier ITEM  = Identifier.fromNamespaceAndPath("minecraft", "ghast_tear");

    private static final Map<Identifier, Research> TREE = Map.of(
            ROOT, research(1, 10, List.of(), 0, 70),
            CHILD, research(2, 10, List.of(ROOT), 0.4, 0)
    );

    private static Research research(int insights, double cost, List<Identifier> requires, double passive, double cap)
    {
        return new Research(1, Constellation.RITES, ITEM, insights, cost, 1, requires, passive, cap, 0, 0, List.of(), List.of());
    }

    private PlayerKnowledge data;

    @BeforeAll
    static void bootstrap()
    {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void setUp()
    {
        data = new PlayerKnowledge();
        data.addInsights(-PlayerKnowledge.STARTING_INSIGHTS);
        data.bind(Optional.of(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld")), BlockPos.ZERO)));
        data.setColumns(1);
        data.settle(0, RULES, TREE);
    }

    private static long seconds(double seconds)
    {
        return (long) (seconds * 20);
    }

    @Test
    void new_thenHoldsOneInsightThatWasNotBought()
    {
        var fresh = new PlayerKnowledge();

        assertThat(fresh.getInsights()).isEqualTo(1);
        assertThat(fresh.getInsightsBought()).isZero();
    }

    @Test
    void codec_givenStartingInsightSpent_thenDoesNotComeBack()
    {
        var fresh = new PlayerKnowledge();
        fresh.reveal(ROOT, TREE);

        var saved = PlayerKnowledge.CODEC.encodeStart(NbtOps.INSTANCE, fresh).getOrThrow();

        assertThat(PlayerKnowledge.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow().getInsights()).isZero();
    }

    @Test
    void settle_givenTimePassing_thenGathersAtBaseRate()
    {
        data.settle(seconds(100), RULES, TREE);

        assertThat(data.getKnowledge()).isCloseTo(10, within(1E-9));
    }

    @Test
    void cap_countsColumnsAtTheBestResearchDone()
    {
        var tree = Map.of(ROOT, research(1, 10, List.of(), 0, 40), CHILD, research(2, 10, List.of(ROOT), 0, 200));
        data.setColumns(6);
        assertThat(data.cap(RULES, tree)).isEqualTo(RULES.baseCap());

        data.grant(ROOT);
        assertThat(data.cap(RULES, tree)).isEqualTo(RULES.baseCap() + 6 * 40);

        data.grant(CHILD);
        assertThat(data.cap(RULES, tree)).isEqualTo(RULES.baseCap() + 6 * 200);
    }

    @Test
    void bind_toAnotherRepository_thenForgetsItsColumns()
    {
        data.setColumns(6);
        data.bind(data.getRepository());
        assertThat(data.getColumns()).isEqualTo(6);

        data.bind(Optional.empty());
        assertThat(data.getColumns()).isZero();
    }

    @Test
    void settle_givenLongAbsence_thenStopsAtCap()
    {
        data.settle(seconds(100000), RULES, TREE);

        assertThat(data.getKnowledge()).isEqualTo(30);
    }

    @Test
    void settle_withoutRepository_thenGathersNothing()
    {
        data.bind(Optional.empty());

        data.settle(seconds(100), RULES, TREE);

        assertThat(data.getKnowledge()).isZero();
    }

    @Test
    void settle_cutIntoPieces_thenGathersTheSame()
    {
        for (int second = 1; second <= 100; second++)
            data.settle(seconds(second), RULES, TREE);

        assertThat(data.getKnowledge()).isCloseTo(10, within(1E-9));
    }

    @Test
    void buyInsight_eachOneIsTwentyPercentPricier()
    {
        data.addKnowledge(10 + 12 + 14.4);

        assertThat(data.buyInsight(RULES)).isTrue();
        assertThat(data.buyInsight(RULES)).isTrue();
        assertThat(data.buyInsight(RULES)).isTrue();

        assertThat(data.getInsights()).isEqualTo(3);
        assertThat(data.getKnowledge()).isCloseTo(0, within(1E-9));
    }

    @Test
    void buyInsight_givenTooLittleKnowledge_thenTakesNothing()
    {
        data.addKnowledge(9);

        assertThat(data.buyInsight(RULES)).isFalse();

        assertThat(data.getKnowledge()).isEqualTo(9);
        assertThat(data.getInsights()).isZero();
    }

    @Test
    void reveal_onlyWorksNextToWhatThePlayerHas()
    {
        data.addInsights(5);

        assertThat(data.reveal(CHILD, TREE)).isFalse();
        assertThat(data.getInsights()).isEqualTo(5);

        assertThat(data.reveal(ROOT, TREE)).isTrue();
        assertThat(data.getInsights()).isEqualTo(4);
        assertThat(data.reveal(ROOT, TREE)).isFalse();

        data.grant(ROOT);
        assertThat(data.reveal(CHILD, TREE)).isTrue();
        assertThat(data.getInsights()).isEqualTo(2);
    }

    @Test
    void reveal_givenTooFewInsights_thenRefuses()
    {
        assertThat(data.reveal(ROOT, TREE)).isFalse();
        assertThat(data.getRevealed()).isEmpty();
    }

    @Test
    void toggleActive_needsTheResearchRevealed()
    {
        assertThat(data.toggleActive(ROOT)).isFalse();
        assertThat(data.getActive()).isEmpty();
    }

    @Test
    void toggleActive_switchesAndPausesWhileKeepingProgress()
    {
        data.addInsights(1);
        data.reveal(ROOT, TREE);
        data.addKnowledge(4);

        data.toggleActive(ROOT);
        assertThat(data.getActive()).contains(ROOT);
        data.settle(seconds(2), RULES, TREE);
        double progress = data.getProgress().get(ROOT);
        assertThat(progress).isCloseTo(2.2, within(1E-9));

        data.toggleActive(ROOT);
        assertThat(data.getActive()).isEmpty();
        double held = data.getKnowledge();
        data.settle(seconds(4), RULES, TREE);

        assertThat(data.getProgress().get(ROOT)).isEqualTo(progress);
        assertThat(data.getKnowledge()).isCloseTo(held + 0.2, within(1E-9));
    }

    @Test
    void settle_givenResearchFinishing_thenRestOfTheTimeUsesItsBonuses()
    {
        data.addInsights(1);
        data.reveal(ROOT, TREE);
        data.addKnowledge(30);
        data.toggleActive(ROOT);

        // 10s to pour the 10 of the root, then 2000s of gathering under the cap of 30 + 70
        var finished = data.settle(seconds(2010), RULES, TREE);

        assertThat(finished).contains(ROOT);
        assertThat(data.getCompleted()).contains(ROOT);
        assertThat(data.getActive()).isEmpty();
        assertThat(data.getKnowledge()).isEqualTo(100);
    }

    @Test
    void settle_givenStoredKnowledge_thenAdvancesAtItsBurnPlusWhatIsGathered()
    {
        data.addInsights(1);
        data.reveal(ROOT, TREE);
        data.addKnowledge(30);
        data.toggleActive(ROOT);

        data.settle(seconds(5), RULES, TREE);

        assertThat(data.getProgress().get(ROOT)).isCloseTo(5.5, within(1E-9));
        assertThat(data.getKnowledge()).isCloseTo(25, within(1E-9));
    }

    @Test
    void settle_givenResearchWaitingOnKnowledge_thenDoesNotFinish()
    {
        data.addInsights(1);
        data.reveal(ROOT, TREE);
        data.toggleActive(ROOT);

        assertThat(data.settle(seconds(50), RULES, TREE)).isEmpty();

        assertThat(data.getProgress().get(ROOT)).isCloseTo(5, within(1E-9));
        assertThat(data.getKnowledge()).isZero();
    }

    @Test
    void settle_givenRemovedResearchRunning_thenDropsItAndGathers()
    {
        data.addInsights(1);
        data.reveal(ROOT, TREE);
        data.toggleActive(ROOT);

        data.settle(seconds(100), RULES, Map.of());

        assertThat(data.getActive()).isEmpty();
        assertThat(data.getKnowledge()).isCloseTo(10, within(1E-9));
    }

    @Test
    void feed_followsTheScheduleThenRefuses()
    {
        double total = 0;
        for (int fed = 0; fed < 8; fed++)
        {
            assertThat(data.feed(ITEM, 10, 1000)).isEqualTo(Feed.ACCEPTED);
            total = data.getKnowledge();
        }

        assertThat(total).isCloseTo(25, within(1E-9));
        assertThat(data.feed(ITEM, 10, 1000)).isEqualTo(Feed.EXHAUSTED);
        assertThat(data.getKnowledge()).isCloseTo(25, within(1E-9));
    }

    @Test
    void feed_givenFullPool_thenRefusesWithoutSpendingTheItem()
    {
        data.addKnowledge(30);

        assertThat(data.feed(ITEM, 10, 30)).isEqualTo(Feed.FULL);

        assertThat(data.yieldOf(ITEM, 10)).isEqualTo(10);
    }

    @Test
    void feed_nearTheCap_thenClampsToIt()
    {
        data.addKnowledge(25);

        assertThat(data.feed(ITEM, 10, 30)).isEqualTo(Feed.ACCEPTED);

        assertThat(data.getKnowledge()).isEqualTo(30);
    }

    @Test
    void rate_addsTheBonusesOfCompletedResearch()
    {
        assertThat(data.rate(RULES, TREE)).isEqualTo(0.1);

        data.grant(CHILD);

        assertThat(data.rate(RULES, TREE)).isCloseTo(0.5, within(1E-9));
    }

    private static final UUID PROXY = new UUID(0, 7);

    private static Map<Identifier, Research> strainedTree(int strain)
    {
        return Map.of(ROOT, new Research(1, Constellation.ARCHIVE, ITEM, 1, 10, 1, List.of(), 0, 70, 0.05, strain, List.of(), List.of()));
    }

    private void startStrained(Map<Identifier, Research> tree)
    {
        data.addInsights(1);
        data.reveal(ROOT, tree);
        data.addKnowledge(20);
        data.toggleActive(ROOT);
    }

    @Test
    void settle_givenStrainBeyondTolerance_thenResearchWaitsWhileKnowledgeGathers()
    {
        var tree = strainedTree(20);
        data.attune(1);
        startStrained(tree);

        assertThat(data.isStrained(RULES, tree)).isTrue();
        data.settle(seconds(10), RULES, tree);

        assertThat(data.getProgress().getOrDefault(ROOT, 0D)).isZero();
        assertThat(data.getKnowledge()).isCloseTo(21, within(1E-9));
    }

    @Test
    void settle_givenProxyBearingTheRest_thenResearchRunsAndWearsIt()
    {
        var tree = strainedTree(20);
        data.attune(1);
        data.reportProxies(Map.of(PROXY, 100D));
        startStrained(tree);

        assertThat(data.isStrained(RULES, tree)).isFalse();
        assertThat(data.settle(seconds(10), RULES, tree)).contains(ROOT);

        // Done once 10 is poured at its burn of 1 and the 0.1 gathered on top
        assertThat(data.getProxies().get(PROXY)).isCloseTo(100 - 10 / 1.1, within(1E-9));
    }

    @Test
    void settle_givenProxyGivingOut_thenResearchPausesWhereItWas()
    {
        var tree = strainedTree(20);
        data.attune(1);
        data.reportProxies(Map.of(PROXY, 4D));
        startStrained(tree);

        assertThat(data.settle(seconds(10), RULES, tree)).isEmpty();

        assertThat(data.getProgress().get(ROOT)).isCloseTo(4.4, within(1E-9));
        assertThat(data.getProxies().get(PROXY)).isZero();
        assertThat(data.isStrained(RULES, tree)).isTrue();
    }

    @Test
    void settle_givenStrainWithinTolerance_thenProxiesRest()
    {
        var tree = strainedTree(20);
        data.attune(2);
        data.reportProxies(Map.of(PROXY, 100D));
        startStrained(tree);

        data.settle(seconds(10), RULES, tree);

        assertThat(data.getProxies().get(PROXY)).isEqualTo(100);
    }

    @Test
    void reportProxies_keepsTheWearAppliedSinceTheyWereSeen()
    {
        data.reportProxies(Map.of(PROXY, 100D));
        var tree = strainedTree(20);
        data.attune(1);
        startStrained(tree);
        data.settle(seconds(5), RULES, tree);

        var left = data.reportProxies(Map.of(PROXY, 100D, new UUID(0, 8), 50D));

        assertThat(left.get(PROXY)).isCloseTo(95, within(1E-9));
        assertThat(left.get(new UUID(0, 8))).isEqualTo(50);
    }

    @Test
    void attune_onlyRises()
    {
        assertThat(data.attune(2)).isTrue();
        assertThat(data.attune(1)).isFalse();
        assertThat(data.attune(Strain.MAX_ATTUNEMENT + 1)).isFalse();
        assertThat(data.getAttunement()).isEqualTo(2);
    }

    @Test
    void rate_countsCogitantsUpToTheLimitOnceResearched()
    {
        var tree = strainedTree(0);
        data.setUpgrades(20, false);
        assertThat(data.rate(RULES, tree)).isEqualTo(0.1);

        data.grant(ROOT);

        assertThat(data.rate(RULES, tree)).isCloseTo(0.1 + 12 * 0.05, within(1E-9));
    }

    @Test
    void insightCost_givenLoom_thenGrowsAQuarterSlower()
    {
        data.addKnowledge(10 + 12);
        data.buyInsight(RULES);
        data.buyInsight(RULES);
        data.setUpgrades(0, true);

        assertThat(data.insightCost(RULES)).isCloseTo(10 * 1.15 * 1.15, within(1E-9));
    }

    @Test
    void bind_toAnotherRepository_thenForgetsWhatStoodAroundIt()
    {
        data.setUpgrades(3, true);
        data.reportProxies(Map.of(PROXY, 10D));

        data.bind(Optional.empty());

        assertThat(data.getCogitants()).isZero();
        assertThat(data.hasLoom()).isFalse();
        assertThat(data.getProxies()).isEmpty();
    }

    @Test
    void codec_keepsAttunementUpgradesAndProxies()
    {
        data.attune(2);
        data.setUpgrades(4, true);
        data.reportProxies(Map.of(PROXY, 12.5));
        data.settle(seconds(60), RULES, TREE);

        var saved = PlayerKnowledge.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        var loaded = PlayerKnowledge.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();

        assertThat(loaded.getAttunement()).isEqualTo(2);
        assertThat(loaded.getCogitants()).isEqualTo(4);
        assertThat(loaded.hasLoom()).isTrue();
        assertThat(loaded.getProxies()).containsEntry(PROXY, 12.5);
        assertThat(loaded.getInsightTrickle()).isCloseTo(data.getInsightTrickle(), within(1E-9)).isGreaterThan(0);
    }

    @Test
    void settle_givenThreeProxies_thenTheyMakeSixInsightsAnHour()
    {
        data.reportProxies(Map.of(new UUID(0, 1), 100D, new UUID(0, 2), 100D, new UUID(0, 3), 100D));
        int before = data.getInsights();

        data.settle(seconds(3600), RULES, TREE);

        assertThat(data.getInsights() - before).isEqualTo(6);
    }

    @Test
    void settle_givenMoreProxiesThanCount_thenOnlyThreeThink()
    {
        data.reportProxies(Map.of(new UUID(0, 1), 100D, new UUID(0, 2), 100D, new UUID(0, 3), 100D, new UUID(0, 4), 100D));
        int before = data.getInsights();

        data.settle(seconds(3600), RULES, TREE);

        assertThat(data.getInsights() - before).isEqualTo(6);
    }

    @Test
    void settle_cutIntoPieces_thenProxiesKeepTheirPartialInsight()
    {
        data.reportProxies(Map.of(PROXY, 100D));
        int before = data.getInsights();
        for (int minute = 1; minute <= 45; minute++)
            data.settle(seconds(minute * 60), RULES, TREE);

        assertThat(data.getInsights() - before).isEqualTo(1);
        assertThat(data.getInsightTrickle()).isCloseTo(0.5, within(1E-9));
    }

    @Test
    void settle_givenWornOutProxy_thenItThinksNoMore()
    {
        data.reportProxies(Map.of(PROXY, 0D));
        int before = data.getInsights();

        data.settle(seconds(3600), RULES, TREE);

        assertThat(data.getInsights()).isEqualTo(before);
    }
}
