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
        return new Research(1, Constellation.RITES, 0, 0, ITEM, insights, cost, 1, requires, passive, cap, List.of(), List.of());
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
        assertThat(progress).isCloseTo(2, within(1E-9));

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
}
