package net.voxelindustry.voidheart.common.content.repair;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MendingRepairTest
{
    @Test
    void materialRepair_restoresAQuarterOfTheTool()
    {
        assertThat(MendingRepair.materialRepair(1561, 1000)).isEqualTo(390);
    }

    @Test
    void materialRepair_neverRestoresMoreThanTheDamage()
    {
        assertThat(MendingRepair.materialRepair(1561, 12)).isEqualTo(12);
    }

    @Test
    void materialRepair_restoresAtLeastOnePointOnTinyTools()
    {
        assertThat(MendingRepair.materialRepair(3, 2)).isEqualTo(1);
    }

    @Test
    void experienceToDrain_takesNoMoreThanTheGulp()
    {
        assertThat(MendingRepair.experienceToDrain(1000, 500, 20)).isEqualTo(20);
    }

    @Test
    void experienceToDrain_takesNoMoreThanTheSkullHolds()
    {
        assertThat(MendingRepair.experienceToDrain(1000, 7, 20)).isEqualTo(7);
    }

    @Test
    void experienceToDrain_takesOnlyWhatTheToolStillNeeds()
    {
        // Five points of damage need three of experience, the last one half spent
        assertThat(MendingRepair.experienceToDrain(5, 500, 20)).isEqualTo(3);
        assertThat(MendingRepair.experienceToDrain(0, 500, 20)).isZero();
    }

    @Test
    void damageAfterExperience_mendsTwoPointsForEachOfExperience()
    {
        assertThat(MendingRepair.damageAfterExperience(100, 20)).isEqualTo(60);
        assertThat(MendingRepair.damageAfterExperience(5, 3)).isZero();
    }
}
