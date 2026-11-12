package net.voxelindustry.voidheart.common.content.underway;

import net.voxelindustry.voidheart.common.energy.siphon.SiphonWaves;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusMob;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusTheme;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class HatchWavesTest
{
    private static final int DISTRICTS = 12;

    @Test
    void wavesComeThroughTheWholeChargeAndGrow()
    {
        assertThat(HatchWaves.due(0)).isZero();
        assertThat(HatchWaves.due(HatchBalance.WAVE_FIRST_TICKS - 1)).isZero();
        assertThat(HatchWaves.due(HatchBalance.WAVE_FIRST_TICKS)).isEqualTo(1);
        assertThat(HatchWaves.due(HatchBalance.WAVE_FIRST_TICKS + HatchBalance.WAVE_INTERVAL_TICKS)).isEqualTo(2);

        int waves = HatchWaves.due(HatchBalance.CHARGE_TICKS);
        assertThat(waves).isBetween(4, 8);
        assertThat(HatchWaves.due(HatchBalance.CHARGE_TICKS * 2)).isEqualTo(waves);
        int last = HatchBalance.WAVE_FIRST_TICKS + (waves - 1) * HatchBalance.WAVE_INTERVAL_TICKS;
        assertThat(HatchBalance.CHARGE_TICKS - last).as("time left to fight the last wave").isGreaterThanOrEqualTo(100);

        for (int wave = 1; wave < waves; wave++)
            assertThat(HatchWaves.size(wave)).isGreaterThanOrEqualTo(HatchWaves.size(wave - 1));
        assertThat(HatchWaves.size(waves - 1)).isGreaterThan(HatchWaves.size(0));
    }

    @Test
    void everyWaveFitsItsSizeAndSendsSomethingAtTheKey()
    {
        var themes = EnumSet.noneOf(DaedalusTheme.class);
        var random = new SplittableRandom(7);
        for (int z = 0; z < DISTRICTS; z++)
        {
            for (int x = 0; x < DISTRICTS; x++)
            {
                var district = new DistrictPos(x, z);
                themes.add(DaedalusTheme.at(district));
                for (int wave = 0; wave < HatchWaves.due(HatchBalance.CHARGE_TICKS); wave++)
                {
                    var mobs = HatchWaves.compose(district, wave, random);
                    assertThat(mobs).isNotEmpty().allMatch(SiphonWaves::climbs).anyMatch(HatchWaves::goesForKey);
                    assertThat(mobs.size()).isLessThanOrEqualTo(HatchWaves.size(wave) + 1);
                    assertThat(mobs.stream().filter(mob -> mob == DaedalusMob.LEGIONARY).count() % 2).isZero();
                }
            }
        }
        assertThat(themes).hasSizeGreaterThan(3);
    }

    @Test
    void onlyMobsThatCanStrikeAThingGoForTheKey()
    {
        assertThat(HatchWaves.goesForKey(DaedalusMob.VOID_GHOUL)).isTrue();
        assertThat(HatchWaves.goesForKey(DaedalusMob.MALIGNANT_BOULDER)).isTrue();
        assertThat(HatchWaves.goesForKey(DaedalusMob.FALLEN_BISHOP)).isFalse();
        assertThat(HatchWaves.goesForKey(DaedalusMob.ARCANE_EYE)).isFalse();
        assertThat(HatchWaves.goesForKey(DaedalusMob.KERNACIAN_DRAGOON)).isFalse();
        assertThat(HatchWaves.goesForKey(DaedalusMob.BARNACLE_NEST)).isFalse();
    }
}
