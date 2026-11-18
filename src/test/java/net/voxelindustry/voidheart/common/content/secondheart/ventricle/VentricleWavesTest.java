package net.voxelindustry.voidheart.common.content.secondheart.ventricle;

import net.minecraft.util.RandomSource;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusMob;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTheme;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleBalance.*;
import static org.assertj.core.api.Assertions.assertThat;

class VentricleWavesTest
{
    @Test
    void due_risesToEveryWaveWithinTheBeating()
    {
        assertThat(VentricleWaves.due(0)).isZero();
        assertThat(VentricleWaves.due(FIRST_WAVE_BEAT)).isOne();
        assertThat(VentricleWaves.due(BEATING_TICKS / BEAT_TICKS - 1)).isEqualTo(WAVES);
        assertThat(VentricleWaves.due(10_000)).isEqualTo(WAVES);
    }

    @Test
    void waves_growAndEndInTheDaedalus()
    {
        assertThat(VentricleWaves.size(WAVES - 1)).isGreaterThan(VentricleWaves.size(0));
        assertThat(VentricleWaves.fromDaedalus(0)).isFalse();
        assertThat(VentricleWaves.fromDaedalus(DUNGEON_WAVES - 1)).isFalse();
        assertThat(VentricleWaves.fromDaedalus(WAVES - 1)).isTrue();
    }

    @Test
    void dungeonWave_neverBringsWhatClings()
    {
        var random = RandomSource.create(5);
        for (var theme : DungeonTheme.values())
            for (int wave = 0; wave < DUNGEON_WAVES; wave++)
            {
                var mobs = VentricleWaves.dungeonWave(wave, theme, random);
                assertThat(mobs).as("wave %s in %s", wave, theme).isNotEmpty();
                for (var mob : mobs)
                    assertThat(VentricleWaves.walksIn(mob)).as("%s in %s", mob, theme).isTrue();
            }
    }

    @Test
    void daedalusWave_neverBringsTheDragoonOrBarnacles()
    {
        var random = new SplittableRandom(3);
        for (int x = -20; x < 20; x++)
            for (var mob : VentricleWaves.daedalusWave(WAVES - 1, new DistrictPos(x, x * 7), random))
                assertThat(mob).isNotIn(DaedalusMob.KERNACIAN_DRAGOON, DaedalusMob.BARNACLE, DaedalusMob.BARNACLE_NEST);
    }
}
