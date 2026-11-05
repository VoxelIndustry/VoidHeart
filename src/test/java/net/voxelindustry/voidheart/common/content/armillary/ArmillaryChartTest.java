package net.voxelindustry.voidheart.common.content.armillary;

import com.mojang.serialization.JavaOps;
import net.minecraft.util.RandomSource;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTheme;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArmillaryChartTest
{
    private static final ArmillaryChart.Rules RULES = new ArmillaryChart.Rules(10, 100, 3, 500, 40, 200);

    private final RandomSource random = RandomSource.create(42);

    private void tick(ArmillaryChart chart, int ticks)
    {
        for (int tick = 0; tick < ticks; tick++)
            chart.tick(RULES, random);
    }

    private VoidBubble found(ArmillaryChart chart)
    {
        var bubble = chart.find(RULES, random);
        assertThat(chart.bubbles()).contains(bubble);
        return bubble;
    }

    @Test
    void finds_firstBubbleEarly_thenOnePerScan()
    {
        var chart = new ArmillaryChart();
        tick(chart, 9);
        assertThat(chart.bubbles()).isEmpty();

        chart.tick(RULES, random);
        assertThat(chart.bubbles()).hasSize(1);

        tick(chart, 99);
        assertThat(chart.bubbles()).hasSize(1);
        chart.tick(RULES, random);
        assertThat(chart.bubbles()).hasSize(2);
    }

    @Test
    void findsOnlyDungeonsThatExist_includingEveryTheme()
    {
        var chart = new ArmillaryChart();
        var themes = new HashSet<DungeonTheme>();
        for (int bubble = 0; bubble < 500; bubble++)
        {
            var found = chart.find(RULES, random);
            assertThat(found.theme().comesIn(found.size())).as("%s", found).isTrue();
            themes.add(found.theme());
        }
        assertThat(themes).containsExactlyInAnyOrder(DungeonTheme.values());
    }

    @Test
    void stopsFinding_onceTheChartIsFull()
    {
        var chart = new ArmillaryChart();
        tick(chart, 400);
        assertThat(chart.bubbles()).hasSize(RULES.maxBubbles());
    }

    @Test
    void untargetedBubbles_fade()
    {
        var chart = new ArmillaryChart();
        found(chart);
        tick(chart, RULES.fadeTicks() + 1);

        assertThat(chart.bubbles().stream().filter(bubble -> bubble.foundAt() == 0)).isEmpty();
    }

    @Test
    void targetedBubble_neverFades_andIsReachedInItsSearchTime()
    {
        var chart = new ArmillaryChart();
        var bubble = found(chart);
        assertThat(chart.target(bubble.id(), RULES)).isTrue();
        assertThat(chart.getPhase()).isEqualTo(ArmillaryChart.Phase.SEARCHING);

        int duration = ArmillaryChart.searchDuration(bubble.difficulty(), RULES);
        for (int tick = 1; tick < duration; tick++)
            assertThat(chart.tick(RULES, random)).isNotEqualTo(ArmillaryChart.Event.REACHED);
        assertThat(chart.tick(RULES, random)).isEqualTo(ArmillaryChart.Event.REACHED);
        assertThat(chart.getPhase()).isEqualTo(ArmillaryChart.Phase.REACHED);

        tick(chart, RULES.fadeTicks() * 2);
        assertThat(chart.target()).isNotNull();
    }

    @Test
    void searchTime_growsWithDepth()
    {
        assertThat(ArmillaryChart.searchDuration(1, RULES)).isEqualTo(RULES.minSearchTicks());
        assertThat(ArmillaryChart.searchDuration(5, RULES)).isEqualTo(RULES.maxSearchTicks());
        assertThat(ArmillaryChart.searchDuration(3, RULES)).isBetween(RULES.minSearchTicks(), RULES.maxSearchTicks());
    }

    @Test
    void retargeting_discardsProgress_andLetsTheLastBubbleFade()
    {
        var chart = new ArmillaryChart();
        var first = found(chart);
        var second = found(chart);
        chart.target(first.id(), RULES);
        tick(chart, 5);
        assertThat(chart.getSearchTicks()).isEqualTo(5);

        assertThat(chart.target(second.id(), RULES)).isTrue();
        assertThat(chart.getSearchTicks()).isZero();
        assertThat(chart.getTargetId()).isEqualTo(second.id());
        assertThat(chart.bubble(first.id()).fadeAt()).isNotEqualTo(VoidBubble.NEVER);
    }

    @Test
    void cannotConnect_beforeReaching()
    {
        var chart = new ArmillaryChart();
        chart.target(found(chart).id(), RULES);

        assertThat(chart.connect()).isFalse();
        assertThat(chart.getPhase()).isEqualTo(ArmillaryChart.Phase.SEARCHING);
    }

    @Test
    void whileConnected_targetCannotChange_andClosingSpendsTheBubble()
    {
        var chart = new ArmillaryChart();
        var bubble = found(chart);
        var other = found(chart);
        chart.target(bubble.id(), RULES);
        chart.advanceSearch(1);
        assertThat(chart.connect()).isTrue();

        assertThat(chart.target(other.id(), RULES)).isFalse();
        assertThat(chart.abandon(RULES)).isFalse();
        tick(chart, RULES.fadeTicks() * 2);
        assertThat(chart.target()).isNotNull();

        chart.connectionClosed();
        assertThat(chart.getPhase()).isEqualTo(ArmillaryChart.Phase.SCANNING);
        assertThat(chart.bubble(bubble.id()).broken()).isTrue();
        assertThat(chart.target(bubble.id(), RULES)).isFalse();

        tick(chart, ArmillaryChart.RUIN_TICKS);
        assertThat(chart.bubble(bubble.id())).isNull();
    }

    @Test
    void brokenBubble_leavesRoomForANewOne()
    {
        var chart = new ArmillaryChart();
        tick(chart, 400);
        assertThat(chart.liveBubbles()).isEqualTo(RULES.maxBubbles());

        chart.target(chart.bubbles().getFirst().id(), RULES);
        chart.advanceSearch(1);
        chart.connect();
        chart.connectionClosed();
        assertThat(chart.liveBubbles()).isEqualTo(RULES.maxBubbles() - 1);

        tick(chart, RULES.scanTicks());
        assertThat(chart.liveBubbles()).isEqualTo(RULES.maxBubbles());
    }

    @Test
    void savedChart_comesBackTheSame()
    {
        var chart = new ArmillaryChart();
        var bubble = found(chart);
        chart.target(bubble.id(), RULES);
        tick(chart, 7);

        var encoded = ArmillaryChart.CODEC.encodeStart(JavaOps.INSTANCE, chart).getOrThrow();
        var decoded = ArmillaryChart.CODEC.parse(JavaOps.INSTANCE, encoded).getOrThrow();

        assertThat(decoded.bubbles()).isEqualTo(chart.bubbles());
        assertThat(decoded.getTargetId()).isEqualTo(bubble.id());
        assertThat(decoded.getPhase()).isEqualTo(ArmillaryChart.Phase.SEARCHING);
        assertThat(decoded.getSearchTicks()).isEqualTo(7);
        assertThat(decoded.age()).isEqualTo(chart.age());
    }

    @Test
    void savedChart_withATargetThatIsGone_fallsBackToScanning()
    {
        var chart = new ArmillaryChart();
        chart.target(found(chart).id(), RULES);

        var saved = new HashMap<Object, Object>((Map<?, ?>) ArmillaryChart.CODEC.encodeStart(JavaOps.INSTANCE, chart).getOrThrow());
        saved.put("target", 999);

        var decoded = ArmillaryChart.CODEC.parse(JavaOps.INSTANCE, (Object) saved).getOrThrow();
        assertThat(decoded.getPhase()).isEqualTo(ArmillaryChart.Phase.SCANNING);
        assertThat(decoded.target()).isNull();
    }

    @Test
    void bubblesLieInsideTheirDepthBand()
    {
        var chart = new ArmillaryChart();
        for (int index = 0; index < 500; index++)
        {
            var bubble = chart.find(RULES, random);
            float band = (bubble.difficulty() - 1) / 5F;
            assertThat(bubble.y()).isBetween(band, band + 0.2F);
            assertThat(bubble.x()).isBetween(0F, 1F);
            assertThat(bubble.openCost()).isEqualTo(bubble.difficulty());
        }
    }
}
