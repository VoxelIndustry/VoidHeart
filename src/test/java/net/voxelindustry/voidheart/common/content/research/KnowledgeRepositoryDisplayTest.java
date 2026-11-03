package net.voxelindustry.voidheart.common.content.research;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static net.voxelindustry.voidheart.common.content.research.KnowledgeRepositoryStatus.DORMANT;
import static net.voxelindustry.voidheart.common.content.research.KnowledgeRepositoryStatus.GATHERING;
import static net.voxelindustry.voidheart.common.content.research.KnowledgeRepositoryStatus.RESEARCHING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class KnowledgeRepositoryDisplayTest
{
    private static final Optional<Identifier> ICON = Optional.of(Identifier.fromNamespaceAndPath("minecraft", "book"));

    private KnowledgeRepositoryDisplay display;

    @BeforeEach
    void setUp()
    {
        display = new KnowledgeRepositoryDisplay();
    }

    private void tick(int times, KnowledgeRepositoryStatus status, Optional<Identifier> running, float progress, float fill)
    {
        for (int i = 0; i < times; i++)
            display.tick(status, running, progress, fill);
    }

    @Test
    void tick_givenAwake_thenFadesInWithinTenTicks()
    {
        tick(10, GATHERING, Optional.empty(), 0, 0.5F);

        assertThat(display.getHud().at(1)).isEqualTo(1);
    }

    @Test
    void tick_givenDormant_thenFadesBackOut()
    {
        tick(10, GATHERING, Optional.empty(), 0, 0.5F);
        tick(10, DORMANT, Optional.empty(), 0, 0);

        assertThat(display.getHud().at(1)).isZero();
    }

    @Test
    void tick_givenNewResearch_thenRingStartsAtItsProgressRatherThanSweepingUp()
    {
        tick(1, RESEARCHING, ICON, 0.6F, 0.5F);

        assertThat(display.getProgress().at(1)).isEqualTo(0.6F);
        assertThat(display.getIcon()).isEqualTo(ICON);
    }

    @Test
    void tick_givenPausedResearch_thenIconLeavesOnceRingHasFaded()
    {
        tick(10, RESEARCHING, ICON, 0.6F, 0.5F);

        tick(5, GATHERING, Optional.empty(), 0, 0.5F);
        assertThat(display.getIcon()).isEqualTo(ICON);

        tick(5, GATHERING, Optional.empty(), 0, 0.5F);
        assertThat(display.getIcon()).isEmpty();
    }

    @Test
    void finish_givenServerAlreadyClearedTheResearch_thenRingFillsAndIconLingers()
    {
        tick(10, RESEARCHING, ICON, 0.9F, 0.5F);
        tick(1, GATHERING, Optional.empty(), 0, 0.5F);

        display.finish();
        tick(30, GATHERING, Optional.empty(), 0, 0.5F);

        assertThat(display.getIcon()).isEqualTo(ICON);
        assertThat(display.getRing().at(1)).isEqualTo(1);
        assertThat(display.getProgress().at(1)).isCloseTo(1, within(0.01F));

        tick(25, GATHERING, Optional.empty(), 0, 0.5F);
        assertThat(display.getIcon()).isEmpty();
    }

    @Test
    void finish_thenFlareDiesOut()
    {
        display.finish();
        assertThat(display.getFlare().at(1)).isEqualTo(1);

        tick(16, GATHERING, Optional.empty(), 0, 0.5F);
        assertThat(display.getFlare().at(1)).isZero();
    }
}
