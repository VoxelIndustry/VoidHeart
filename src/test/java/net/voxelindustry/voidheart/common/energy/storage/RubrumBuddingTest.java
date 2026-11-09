package net.voxelindustry.voidheart.common.energy.storage;

import net.voxelindustry.voidheart.common.energy.storage.RubrumBudding.Stage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RubrumBuddingTest
{
    @Test
    void defaultsComeFromTheNumbers()
    {
        var budding = new RubrumBudding();
        assertThat(budding.duration()).isEqualTo(120 * 60 * 20L);
        assertThat(budding.cost()).isEqualTo(250_000);
    }

    @Test
    void growsInThreeStepsAboveTheThreshold()
    {
        var budding = new RubrumBudding(0.9F, 100, 1000);
        assertThat(budding.stage()).isEqualTo(Stage.NONE);

        budding.tick(0.95F, 1);
        assertThat(budding.stage()).isEqualTo(Stage.SPECK);
        budding.tick(0.95F, 49);
        assertThat(budding.stage()).isEqualTo(Stage.SHARD);
        budding.tick(0.95F, 49);
        assertThat(budding.stage()).isEqualTo(Stage.SHARD);
        budding.tick(0.95F, 1);
        assertThat(budding.stage()).isEqualTo(Stage.READY);

        budding.tick(1F, 500);
        assertThat(budding.progress()).isEqualTo(100);
    }

    @Test
    void belowTheThresholdPausesWithoutLosingProgress()
    {
        var budding = new RubrumBudding(0.9F, 100, 1000);
        budding.tick(0.92F, 30);
        budding.tick(0.89F, 1000);
        assertThat(budding.progress()).isEqualTo(30);
        budding.tick(0.9F, 10);
        assertThat(budding.progress()).isEqualTo(40);
    }

    @Test
    void readyBudWaitsForItsCostThenStartsOver()
    {
        var budding = new RubrumBudding(0.9F, 100, 1000);
        budding.tick(1F, 100);

        assertThat(budding.canGrow(999)).isFalse();
        assertThat(budding.canGrow(1000)).isTrue();

        budding.grown();
        assertThat(budding.stage()).isEqualTo(Stage.NONE);
        assertThat(budding.canGrow(1_000_000)).isFalse();
    }

    @Test
    void savedProgressIsClamped()
    {
        var budding = new RubrumBudding(0.9F, 100, 1000);
        budding.setProgress(5000);
        assertThat(budding.progress()).isEqualTo(100);
        budding.setProgress(-3);
        assertThat(budding.progress()).isZero();
    }
}
