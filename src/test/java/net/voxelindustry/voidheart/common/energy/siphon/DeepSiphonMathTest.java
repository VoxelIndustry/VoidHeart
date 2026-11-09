package net.voxelindustry.voidheart.common.energy.siphon;

import net.voxelindustry.voidheart.common.energy.machine.MachineEnergyBalance;
import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusBridges;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusMob;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusRoster;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusTheme;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.SplittableRandom;

import static net.voxelindustry.voidheart.common.energy.siphon.DeepSiphonBalance.*;
import static org.assertj.core.api.Assertions.assertThat;

class DeepSiphonMathTest
{
    private static final long SEED = 0x5EED_5170_7L;
    private static final long FULL_DRAW_MINUTE = OUTPUT * 20 * 60;

    // Output

    @Test
    void theSiphonPaysItsHoldAndLeavesAThirdForTheBase()
    {
        assertThat(OUTPUT).isEqualTo(96);
        assertThat(OUTPUT - MachineEnergyBalance.DAEDALUS_HOLD).isEqualTo(32);
    }

    // Notice

    @Test
    void oneMobIsNoticedPerFourMinutesAtFullDraw()
    {
        var notice = new SiphonNotice();
        notice.add(FULL_DRAW_MINUTE * 4 - 1);
        assertThat(notice.mobs()).isZero();
        assertThat(notice.towardNext()).isGreaterThan(0.99F);

        notice.add(1);
        assertThat(notice.mobs()).isEqualTo(1);
        notice.add(FULL_DRAW_MINUTE * 8);
        assertThat(notice.mobs()).isEqualTo(3);
    }

    @Test
    void drawingAtAThirdTakesThreeTimesAsLongToBeNoticed()
    {
        var notice = new SiphonNotice();
        for (int tick = 0; tick < 20 * 60 * 4; tick++)
            notice.add(OUTPUT / 3);
        assertThat(notice.mobs()).isZero();
        for (int tick = 0; tick < 20 * 60 * 8; tick++)
            notice.add(OUTPUT / 3);
        assertThat(notice.mobs()).isEqualTo(1);
    }

    @Test
    void noticeStopsAtTwelveMobsHoweverLongItDraws()
    {
        var notice = new SiphonNotice();
        notice.add(FULL_DRAW_MINUTE * 60 * 24);
        assertThat(notice.mobs()).isEqualTo(NOTICE_CAP).isEqualTo(12);
        assertThat(notice.towardNext()).isZero();
        notice.add(-FULL_DRAW_MINUTE);
        assertThat(notice.mobs()).isEqualTo(12);
    }

    @Test
    void nothingComesWhileNoPlayerIsNear()
    {
        var notice = new SiphonNotice();
        notice.setBanked(NOTICE_PER_MOB * 12);
        for (long now = 0; now < 20 * 60 * 10; now++)
            assertThat(notice.wave(now, false)).isZero();
        assertThat(notice.mobs()).isEqualTo(12);
    }

    @Test
    void aFullNoticeComesAsThreeWavesTwentySecondsApartOnceAPlayerArrives()
    {
        var notice = new SiphonNotice();
        notice.setBanked(NOTICE_PER_MOB * 12);
        long arrival = 5_000;

        var waves = new ArrayList<long[]>();
        for (long now = arrival; now < arrival + 20 * 60; now++)
        {
            int count = notice.wave(now, true);
            if (count > 0)
            {
                waves.add(new long[] { now, count });
                notice.spend(count);
            }
        }

        assertThat(waves).hasSize(3);
        assertThat(waves.get(0)).containsExactly(arrival, WAVE_SIZE);
        assertThat(waves.get(1)).containsExactly(arrival + WAVE_INTERVAL, WAVE_SIZE);
        assertThat(waves.get(2)).containsExactly(arrival + WAVE_INTERVAL * 2L, WAVE_SIZE);
        assertThat(WAVE_INTERVAL).isEqualTo(20 * 20);
        assertThat(notice.mobs()).isZero();
    }

    @Test
    void aWaveBringsOnlyWhatIsNoticedAndWhatDidNotComeStaysNoticed()
    {
        var notice = new SiphonNotice();
        notice.setBanked(NOTICE_PER_MOB * 3 + NOTICE_PER_MOB / 2);
        assertThat(notice.wave(0, true)).isEqualTo(3);
        // Only two found room on the deck
        notice.spend(2);
        assertThat(notice.mobs()).isEqualTo(1);
        assertThat(notice.towardNext()).isEqualTo(0.5F);
        assertThat(notice.wave(1, true)).as("the next wave waits its interval").isZero();
        assertThat(notice.wave(WAVE_INTERVAL, true)).isEqualTo(1);
    }

    @Test
    void aPlayerComingBackAfterTheIntervalMeetsTheNextWaveAtOnce()
    {
        var notice = new SiphonNotice();
        notice.setBanked(NOTICE_PER_MOB * 8);
        notice.spend(notice.wave(0, true));
        assertThat(notice.wave(100, false)).isZero();
        assertThat(notice.wave(WAVE_INTERVAL + 2_000, true)).isEqualTo(4);
    }

    // Waves

    @Test
    void wavesComeFromTheTwoDistrictsRostersAndNeverBringTheGuardOrWhatGrowsOnWalls()
    {
        var from = new DistrictPos(3, -2);
        var to = new DistrictPos(4, -2);
        var rosters = new DaedalusRoster[] { DaedalusRoster.of(DaedalusTheme.at(from)), DaedalusRoster.of(DaedalusTheme.at(to)) };
        var random = new SplittableRandom(7);
        for (int wave = 0; wave < 2_000; wave++)
        {
            int count = 1 + wave % WAVE_SIZE;
            var mobs = SiphonWaves.compose(from, to, count, random);
            assertThat(mobs).hasSizeLessThanOrEqualTo(count).isNotEmpty();
            for (var mob : mobs)
            {
                assertThat(mob).isNotIn(DaedalusMob.KERNACIAN_DRAGOON, DaedalusMob.BARNACLE, DaedalusMob.BARNACLE_NEST);
                assertThat(rosters[0].holds(mob) || rosters[1].holds(mob)).as(mob + " from either roster").isTrue();
            }
        }
    }

    @Test
    void legionariesAlwaysComeAsAPairAndCountAsTwo()
    {
        var random = new SplittableRandom(11);
        int pairs = 0;
        for (var district = 0; district < 40; district++)
        {
            var from = new DistrictPos(district, district * 3);
            for (int wave = 0; wave < 50; wave++)
            {
                var mobs = SiphonWaves.compose(from, new DistrictPos(district, district * 3 + 1), WAVE_SIZE, random);
                for (int i = 0; i < mobs.size(); i++)
                {
                    if (mobs.get(i) != DaedalusMob.LEGIONARY)
                        continue;
                    assertThat(i + 1).as("a Legionary's partner follows it").isLessThan(mobs.size());
                    assertThat(mobs.get(i + 1)).isEqualTo(DaedalusMob.LEGIONARY);
                    pairs++;
                    i++;
                }
            }
        }
        assertThat(pairs).as("pairs should come at all").isGreaterThan(10);
        // A single mob's wave never fits a pair
        assertThat(SiphonWaves.compose(new DistrictPos(0, 0), new DistrictPos(1, 0), 1, new SplittableRandom(3))).doesNotContain(DaedalusMob.LEGIONARY);
    }

    @Test
    void aboutAThirdOfEachWaveComesFromTheThemesOlderFamilies()
    {
        var random = new SplittableRandom(5);
        int family = 0;
        int total = 0;
        for (int district = 0; district < 60; district++)
        {
            var from = new DistrictPos(district * 7, -district);
            var to = new DistrictPos(district * 7 + 1, -district);
            var rosters = new DaedalusRoster[] { DaedalusRoster.of(DaedalusTheme.at(from)), DaedalusRoster.of(DaedalusTheme.at(to)) };
            for (int wave = 0; wave < 40; wave++)
            {
                for (var mob : SiphonWaves.compose(from, to, WAVE_SIZE, random))
                {
                    total++;
                    if (rosters[0].isFamily(mob) || rosters[1].isFamily(mob))
                        family++;
                }
            }
        }
        // Barnacles are a family the deck can't take, so the share sits a little under the districts' own
        assertThat(family / (double) total).isBetween(0.2, DaedalusRoster.FAMILY_SHARE + 0.05);
    }

    // Bridges

    @Test
    void aBlockOnTheDeckFindsItsBridgeAndOneAboveOrBesideItDoesNot()
    {
        var from = new DistrictPos(2, 5);
        for (int direction : new int[] { Compass.EAST, Compass.SOUTH })
        {
            for (var bridge : DaedalusBridges.between(SEED, from, direction))
            {
                for (int span : new int[] { 0, 9, DistrictPos.CHASM / 2, DistrictPos.CHASM - 1 })
                {
                    int x = bridge.worldX(span, SiphonBridge.LANE_CENTRE);
                    int z = bridge.worldZ(span, SiphonBridge.LANE_CENTRE);
                    var footing = SiphonBridge.at(SEED, x, bridge.walkY(span), z);
                    assertThat(footing).as(bridge + " at span " + span).isNotNull();
                    assertThat(footing.bridge()).isEqualTo(bridge);
                    assertThat(footing.span()).isEqualTo(span);
                    assertThat(footing.lateral()).isEqualTo(SiphonBridge.LANE_CENTRE);

                    assertThat(SiphonBridge.at(SEED, x, bridge.walkY(span) + 1, z)).as("in the air above the deck").isNull();
                    assertThat(SiphonBridge.at(SEED, x, bridge.deckY(span), z)).as("in the deck itself").isNull();
                }

                int edge = SiphonBridge.high(bridge) + 1;
                int span = DistrictPos.CHASM / 2;
                assertThat(SiphonBridge.at(SEED, bridge.worldX(span, edge), bridge.walkY(span), bridge.worldZ(span, edge))).as("on the rail's edge").isNotNull();
                int beside = edge + 1;
                assertThat(SiphonBridge.at(SEED, bridge.worldX(span, beside), bridge.walkY(span), bridge.worldZ(span, beside))).as("over the drop beside it").isNull();
            }
        }
    }

    @Test
    void noBridgeInsideADistrictOrWhereTwoChasmsCross()
    {
        var district = new DistrictPos(1, 1);
        assertThat(SiphonBridge.at(SEED, district.originX() + 40, 33, district.originZ() + 40)).isNull();
        int corner = DistrictPos.SIZE + DistrictPos.CHASM / 2;
        for (int y = 0; y < DistrictPos.HEIGHT; y++)
            assertThat(SiphonBridge.at(SEED, district.originX() + corner, y, district.originZ() + corner)).isNull();
    }

    @Test
    void wavesClimbOutNearBothEndsOfTheSameBridgeAndNeverAtTheVeryEdge()
    {
        var from = new DistrictPos(-3, 4);
        for (var bridge : DaedalusBridges.between(SEED, from, Compass.EAST))
        {
            var spots = SiphonBridge.waveSpots(bridge);
            assertThat(spots).isNotEmpty();
            boolean near = false;
            boolean far = false;
            for (var spot : spots)
            {
                var footing = SiphonBridge.at(SEED, spot.x(), spot.y(), spot.z());
                assertThat(footing).isNotNull();
                assertThat(footing.bridge()).isEqualTo(bridge);
                assertThat(Math.min(footing.span(), DistrictPos.CHASM - 1 - footing.span())).isGreaterThanOrEqualTo(SiphonBridge.WAVE_FROM_END);
                near |= footing.span() < DistrictPos.CHASM / 2;
                far |= footing.span() >= DistrictPos.CHASM / 2;
            }
            assertThat(near && far).as("both ends").isTrue();
            // The first two alternate ends, so even a wave of two splits between the districts
            assertThat(SiphonBridge.at(SEED, spots.get(0).x(), spots.get(0).y(), spots.get(0).z()).span())
                    .isLessThan(DistrictPos.CHASM / 2);
            assertThat(SiphonBridge.at(SEED, spots.get(1).x(), spots.get(1).y(), spots.get(1).z()).span())
                    .isGreaterThanOrEqualTo(DistrictPos.CHASM / 2);
            assertThat(SiphonBridge.to(bridge)).isEqualTo(new DistrictPos(from.x() + 1, from.z()));
        }
    }
}
