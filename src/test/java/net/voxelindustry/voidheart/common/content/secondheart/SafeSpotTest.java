package net.voxelindustry.voidheart.common.content.secondheart;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SafeSpotTest
{
    private static final Vec3 DEATH     = new Vec3(10.5, 64, -3.5);
    private static final Vec3 LAST_SAFE = new Vec3(8.5, 70, -1.5);
    private static final Vec3 FALLBACK  = new Vec3(10.5, 90, -3.5);

    @Test
    void choose_givenSafeDeathSpot_thenReformsThere()
    {
        assertThat(SafeSpot.choose(DEATH, SafeSpot.Hazards.NONE, LAST_SAFE, FALLBACK)).isEqualTo(DEATH);
    }

    @Test
    void choose_givenEachHazard_thenLastSafeSpot()
    {
        var hazards = new SafeSpot.Hazards[]{
                new SafeSpot.Hazards(true, false, false, false),
                new SafeSpot.Hazards(false, true, false, false),
                new SafeSpot.Hazards(false, false, true, false),
                new SafeSpot.Hazards(false, false, false, true)};
        for (var hazard : hazards)
            assertThat(SafeSpot.choose(DEATH, hazard, LAST_SAFE, FALLBACK)).as("%s", hazard).isEqualTo(LAST_SAFE);
    }

    @Test
    void choose_givenUnsafeAndNoLastSafe_thenFallback()
    {
        assertThat(SafeSpot.choose(DEATH, new SafeSpot.Hazards(false, true, false, false), null, FALLBACK)).isEqualTo(FALLBACK);
    }

    @Test
    void remember_onlyOnDryGroundWithoutHazards()
    {
        assertThat(SafeSpot.remember(true, false, SafeSpot.Hazards.NONE)).isTrue();
        assertThat(SafeSpot.remember(false, false, SafeSpot.Hazards.NONE)).isFalse();
        assertThat(SafeSpot.remember(true, true, SafeSpot.Hazards.NONE)).isFalse();
        assertThat(SafeSpot.remember(true, false, new SafeSpot.Hazards(false, true, false, false))).isFalse();
    }
}
