package net.voxelindustry.voidheart.common.content.sin;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SinProficiencyTest
{
    private static float points(float devotion)
    {
        return devotion * SinBalance.DEVOTED_POINTS;
    }

    @Test
    void stage_givenNovice_thenFirstStage()
    {
        assertThat(SinProficiency.NONE.stage(Sin.PRIDE)).isEqualTo(1);
    }

    @Test
    void stage_givenEachStageStart_thenThatStage()
    {
        for (int stage = 1; stage <= SinBalance.STAGES; stage++)
        {
            var proficiency = SinProficiency.NONE.with(Sin.WRATH, points(SinBalance.STAGE_STARTS[stage - 1]));
            assertThat(proficiency.stage(Sin.WRATH)).as("stage %s", stage).isEqualTo(stage);
        }
    }

    @Test
    void stage_givenJustBelowAStage_thenStageBefore()
    {
        var proficiency = SinProficiency.NONE.with(Sin.WRATH, points(SinBalance.STAGE_STARTS[2]) - 1);
        assertThat(proficiency.stage(Sin.WRATH)).isEqualTo(2);
    }

    @Test
    void gain_thenOtherSinsFadeByHalfTheGain()
    {
        var proficiency = SinProficiency.of(Map.of(Sin.PRIDE, 1000F, Sin.SLOTH, 100F)).gain(Sin.WRATH, 400);

        assertThat(proficiency.points(Sin.WRATH)).isEqualTo(400);
        assertThat(proficiency.points(Sin.PRIDE)).isEqualTo(800);
        assertThat(proficiency.points(Sin.SLOTH)).as("never below nothing").isZero();
    }

    @Test
    void gain_givenFullDevotion_thenCappedButOthersStillFade()
    {
        var proficiency = SinProficiency.of(Map.of(Sin.GREED, SinBalance.DEVOTED_POINTS, Sin.PRIDE, 500F)).gain(Sin.GREED, 300);

        assertThat(proficiency.points(Sin.GREED)).isEqualTo(SinBalance.DEVOTED_POINTS);
        assertThat(proficiency.points(Sin.PRIDE)).isEqualTo(350);
    }

    @Test
    void gain_givenNothing_thenSame()
    {
        var proficiency = SinProficiency.of(Map.of(Sin.PRIDE, 10F));
        assertThat(proficiency.gain(Sin.WRATH, 0)).isSameAs(proficiency);
    }

    @Test
    void longSwitch_thenPlayerMovesOver()
    {
        var proficiency = SinProficiency.NONE.with(Sin.PRIDE, SinBalance.DEVOTED_POINTS);
        for (int hit = 0; hit < 2000; hit++)
            proficiency = proficiency.gain(Sin.WRATH, 100);

        assertThat(proficiency.points(Sin.PRIDE)).isZero();
        assertThat(proficiency.stage(Sin.WRATH)).isEqualTo(SinBalance.STAGES);
        assertThat(proficiency.strongest()).isEqualTo(Sin.WRATH);
    }

    @Test
    void codec_givenRoundTrip_thenEqual()
    {
        var proficiency = SinProficiency.of(Map.of(Sin.GLUTTONY, 1234.5F, Sin.SLOTH, 7F));

        var json = SinProficiency.CODEC.encodeStart(JsonOps.INSTANCE, proficiency).getOrThrow();
        var read = SinProficiency.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertThat(read).isEqualTo(proficiency);
        assertThat(read.devotion(Sin.GLUTTONY)).isCloseTo(1234.5F / SinBalance.DEVOTED_POINTS, within(1e-6F));
    }

    @Test
    void strongest_givenNone_thenNull()
    {
        assertThat(SinProficiency.NONE.strongest()).isNull();
    }
}
