package net.voxelindustry.voidheart.common.content.armillary;

import org.junit.jupiter.api.Test;

import static net.voxelindustry.voidheart.common.content.armillary.ArmillaryChart.Phase.CONNECTED;
import static net.voxelindustry.voidheart.common.content.armillary.ArmillaryChart.Phase.REACHED;
import static net.voxelindustry.voidheart.common.content.armillary.ArmillaryChart.Phase.SCANNING;
import static net.voxelindustry.voidheart.common.content.armillary.ArmillaryChart.Phase.SEARCHING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ArmillaryDisplayTest
{
    @Test
    void staysHidden_untilThereIsSomethingToRead()
    {
        assertThat(ArmillaryDisplay.isShown(false, 3, SEARCHING)).isFalse();
        assertThat(ArmillaryDisplay.isShown(true, 0, SCANNING)).isFalse();
        assertThat(ArmillaryDisplay.isShown(true, 1, SCANNING)).isTrue();
        assertThat(ArmillaryDisplay.isShown(true, 0, CONNECTED)).isTrue();
    }

    @Test
    void searchFillsTheRing_reachedHoldsItFull_andAConnectionDrainsIt()
    {
        assertThat(ArmillaryDisplay.arcOf(SCANNING, 50, 100, 0.5F)).isZero();
        assertThat(ArmillaryDisplay.arcOf(SEARCHING, 25, 100, 0)).isEqualTo(0.25F);
        assertThat(ArmillaryDisplay.arcOf(REACHED, 0, 0, 0)).isEqualTo(1);
        assertThat(ArmillaryDisplay.arcOf(CONNECTED, 100, 100, 0.4F)).isEqualTo(0.4F);
    }

    @Test
    void arc_staysInRange_whateverTheServerSent()
    {
        assertThat(ArmillaryDisplay.arcOf(SEARCHING, 10, 0, 0)).isZero();
        assertThat(ArmillaryDisplay.arcOf(SEARCHING, 200, 100, 0)).isEqualTo(1);
        assertThat(ArmillaryDisplay.arcOf(CONNECTED, 0, 0, 3)).isEqualTo(1);
    }

    @Test
    void fadesAndEases_ratherThanJumping()
    {
        var display = new ArmillaryDisplay();
        display.tick(true, 1);
        assertThat(display.hud(1)).isBetween(0.01F, 0.5F);
        assertThat(display.arc(1)).isBetween(0.01F, 0.5F);
        assertThat(display.hud(0)).isZero();

        for (int tick = 0; tick < 200; tick++)
            display.tick(true, 1);
        assertThat(display.hud(1)).isEqualTo(1);
        assertThat(display.arc(1)).isCloseTo(1, within(0.001F));

        for (int tick = 0; tick < 200; tick++)
            display.tick(false, 0);
        assertThat(display.hud(1)).isZero();
        assertThat(display.arc(1)).isCloseTo(0, within(0.001F));
    }
}
