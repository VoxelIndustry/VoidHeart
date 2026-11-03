package net.voxelindustry.voidheart.common.content.research;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeRepositoryStatusTest
{
    private static final double CAP = 30;

    @Test
    void of_givenUnbound_thenDormantWhateverItHolds()
    {
        assertThat(KnowledgeRepositoryStatus.of(false, true, 10, CAP)).isEqualTo(KnowledgeRepositoryStatus.DORMANT);
        assertThat(KnowledgeRepositoryStatus.of(false, false, CAP, CAP)).isEqualTo(KnowledgeRepositoryStatus.DORMANT);
    }

    @Test
    void of_givenNoResearch_thenGatheringUntilFull()
    {
        assertThat(KnowledgeRepositoryStatus.of(true, false, 0, CAP)).isEqualTo(KnowledgeRepositoryStatus.GATHERING);
        assertThat(KnowledgeRepositoryStatus.of(true, false, CAP - 0.5, CAP)).isEqualTo(KnowledgeRepositoryStatus.GATHERING);
        assertThat(KnowledgeRepositoryStatus.of(true, false, CAP, CAP)).isEqualTo(KnowledgeRepositoryStatus.FULL);
    }

    @Test
    void of_givenColumnsGoneAndPoolAboveCap_thenFull()
    {
        assertThat(KnowledgeRepositoryStatus.of(true, false, CAP + 40, CAP)).isEqualTo(KnowledgeRepositoryStatus.FULL);
    }

    @Test
    void of_givenResearch_thenStarvedOnlyOncePoolIsDry()
    {
        assertThat(KnowledgeRepositoryStatus.of(true, true, CAP, CAP)).isEqualTo(KnowledgeRepositoryStatus.RESEARCHING);
        assertThat(KnowledgeRepositoryStatus.of(true, true, 1, CAP)).isEqualTo(KnowledgeRepositoryStatus.RESEARCHING);
        assertThat(KnowledgeRepositoryStatus.of(true, true, 0, CAP)).isEqualTo(KnowledgeRepositoryStatus.STARVED);
    }

    @Test
    void drawsFromColumns_thenOnlyWhileKnowledgeIsComingIn()
    {
        assertThat(KnowledgeRepositoryStatus.GATHERING.drawsFromColumns()).isTrue();
        assertThat(KnowledgeRepositoryStatus.STARVED.drawsFromColumns()).isTrue();
        assertThat(KnowledgeRepositoryStatus.FULL.drawsFromColumns()).isFalse();
        assertThat(KnowledgeRepositoryStatus.RESEARCHING.drawsFromColumns()).isFalse();
        assertThat(KnowledgeRepositoryStatus.DORMANT.drawsFromColumns()).isFalse();
    }
}
