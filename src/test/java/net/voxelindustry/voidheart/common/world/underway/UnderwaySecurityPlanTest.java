package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class UnderwaySecurityPlanTest
{
    private static final long[] SEEDS = { 1L, 1234L };
    private static final int RANGE = 6;
    private static final int SEARCH = 80;

    @Test
    void sectionsFollowTheLevels()
    {
        assertThat(UnderwaySection.at(UnderwayLevels.HATCH_FLOOR)).isEqualTo(UnderwaySection.ENTRY);
        assertThat(UnderwaySection.at(UnderwayLevels.ENTRY_FLOOR - 2)).as("the bottom of a pit").isEqualTo(UnderwaySection.ENTRY);
        assertThat(UnderwaySection.at(UnderwayLevels.LIVING_FLOOR + 8)).as("the ceiling of the tallest bay").isEqualTo(UnderwaySection.LIVING);
        assertThat(UnderwaySection.at(UnderwayLevels.LIVING_FLOOR)).isEqualTo(UnderwaySection.LIVING);
        assertThat(UnderwaySection.at(UnderwayLevels.TRUNK_TOP + 1)).as("the trunk's vault").isEqualTo(UnderwaySection.RAIL);
        assertThat(UnderwaySection.at(UnderwayLevels.BALLAST)).isEqualTo(UnderwaySection.RAIL);
    }

    @Test
    void everyLevelWithRoomsHasACabinetOnAWall()
    {
        for (long seed : SEEDS)
        {
            for (int z = -RANGE; z <= RANGE; z++)
            {
                for (int x = -RANGE; x <= RANGE; x++)
                {
                    var pos = new DistrictPos(x, z);
                    var fort = UnderwayPlans.fort(seed, pos);
                    var canvas = paint(seed, pos);
                    var frame = fort.frame();
                    var withRooms = EnumSet.noneOf(UnderwaySection.class);
                    for (var room : fort.rooms())
                    {
                        if (!room.kind().isCutOff() && room.floorY() == UnderwaySection.at(room.floorY()).floorY())
                            withRooms.add(UnderwaySection.at(room.floorY()));
                    }
                    assertThat(withRooms).as("fort under %s, seed %d", pos, seed).contains(UnderwaySection.ENTRY);
                    for (var section : withRooms)
                    {
                        var cabinet = fort.cabinet(section);
                        assertThat(cabinet).as("cabinet of the %s level of the %s under %s, seed %d", section, fort.node(), pos, seed).isNotNull();
                        int cx = frame.x(cabinet.along(), cabinet.across());
                        int cz = frame.z(cabinet.along(), cabinet.across());
                        int behindX = frame.x(cabinet.along() - cabinet.faceAlong(), cabinet.across() - cabinet.faceAcross());
                        int behindZ = frame.z(cabinet.along() - cabinet.faceAlong(), cabinet.across() - cabinet.faceAcross());
                        assertThat(canvas.propAt(cx, cabinet.y(), cz)).isEqualTo(UnderwayProp.BREAKER_CABINET);
                        assertThat(cabinet.y()).isEqualTo(section.floorY() + 2);
                        assertThat(canvas.get(behindX, cabinet.y(), behindZ).isOpen()).as("wall behind the %s cabinet under %s", section, pos).isFalse();
                        assertThat(canvas.propAt(cx, cabinet.y() - 1, cz)).as("under the %s cabinet under %s", section, pos).isNull();
                    }
                }
            }
        }
    }

    @Test
    void unitsStandWhereTheyCan()
    {
        var kinds = EnumSet.noneOf(UnderwayUnitKind.class);
        for (long seed : SEEDS)
        {
            for (int z = -RANGE; z <= RANGE; z++)
            {
                for (int x = -RANGE; x <= RANGE; x++)
                {
                    var pos = new DistrictPos(x, z);
                    var fort = UnderwayPlans.fort(seed, pos);
                    var canvas = paint(seed, pos);
                    var frame = fort.frame();
                    var places = new HashSet<Long>();
                    for (var unit : fort.units())
                    {
                        kinds.add(unit.kind());
                        int ux = frame.x(unit.along(), unit.across());
                        int uz = frame.z(unit.along(), unit.across());
                        int aheadX = frame.x(unit.along() + unit.faceAlong(), unit.across() + unit.faceAcross());
                        int aheadZ = frame.z(unit.along() + unit.faceAlong(), unit.across() + unit.faceAcross());
                        var where = "%s at %d %d %d of the %s under %s, seed %d".formatted(unit.kind(), ux, unit.y(), uz, fort.node(), pos, seed);
                        assertThat(places.add(((long) unit.along() << 40) | ((long) (unit.y() & 0xFFFF) << 20) | unit.across())).as(where).isTrue();
                        if (unit.kind() != UnderwayUnitKind.RAM_CART)
                            assertThat(canvas.get(ux, unit.y(), uz)).as(where).isEqualTo(UnderwayMaterial.AIR);
                        switch (unit.kind())
                        {
                            case TRIPWIRE ->
                            {
                                int behindX = frame.x(unit.along() - unit.faceAlong(), unit.across() - unit.faceAcross());
                                int behindZ = frame.z(unit.along() - unit.faceAlong(), unit.across() - unit.faceAcross());
                                assertThat(canvas.propAt(ux, unit.y(), uz)).as(where).isEqualTo(UnderwayProp.TRIPWIRE_EMITTER);
                                assertThat(canvas.get(behindX, unit.y(), behindZ).isOpen()).as("wall behind the %s", where).isFalse();
                                assertThat(canvas.get(aheadX, unit.y(), aheadZ)).as("the beam's way from the %s", where).isEqualTo(UnderwayMaterial.AIR);
                                assertThat(unit.y()).as(where).isEqualTo(UnderwayLevels.ENTRY_FLOOR + 1);
                                assertThat(unit.emergency()).isTrue();
                            }
                            case TURRET_WELL -> assertThat(canvas.propAt(ux, unit.y(), uz)).as(where).isEqualTo(UnderwayProp.TURRET_WELL);
                            case SEARCHLIGHT ->
                            {
                                assertThat(canvas.propAt(ux, unit.y(), uz)).as(where).isEqualTo(UnderwayProp.SEARCHLIGHT);
                                assertThat(canvas.get(aheadX, unit.y(), aheadZ)).as("room before the %s", where).isEqualTo(UnderwayMaterial.AIR);
                                assertThat(unit.section()).as(where).isEqualTo(UnderwaySection.RAIL);
                            }
                            case SAPPER ->
                            {
                                assertThat(canvas.propAt(ux, unit.y() + 1, uz)).as("duct over the %s", where).isEqualTo(UnderwayProp.DUCT_VENT);
                                assertThat(canvas.propAt(ux, unit.y(), uz)).as(where).isNull();
                            }
                            case RAM_CART ->
                            {
                                var track = frame.alongX() == (unit.faceAlong() != 0) ? UnderwayMaterial.FEEDER_RAIL_X : UnderwayMaterial.FEEDER_RAIL_Z;
                                for (int step = -2; step <= 2; step++)
                                    assertThat(canvas.get(frame.x(unit.along() + unit.faceAlong() * step, unit.across() + unit.faceAcross() * step), unit.y(),
                                            frame.z(unit.along() + unit.faceAlong() * step, unit.across() + unit.faceAcross() * step))).as("track %d from the %s", step, where).isEqualTo(track);
                                assertThat(canvas.propAt(ux, unit.y(), uz)).as(where).isNull();
                            }
                            case MANTLET ->
                            {
                                assertThat(canvas.propAt(ux, unit.y(), uz)).as(where).isNull();
                                assertThat(canvas.propAt(ux, unit.y() + 1, uz)).as(where).isNull();
                                assertThat(canvas.get(ux, unit.y() + 1, uz)).as(where).isEqualTo(UnderwayMaterial.AIR);
                                assertThat(canvas.get(ux, unit.y() - 1, uz).isOpen()).as("floor under the %s", where).isFalse();
                            }
                            case FITTER ->
                            {
                                assertThat(canvas.propAt(ux, unit.y(), uz)).as(where).isNull();
                                assertThat(canvas.get(ux, unit.y() - 1, uz).isOpen()).as("floor under the %s", where).isFalse();
                                assertThat(unit.emergency()).isFalse();
                            }
                            case SENTINEL_EYE -> assertThat(canvas.propAt(ux, unit.y(), uz)).as(where).isEqualTo(UnderwayProp.SENTINEL_EYE);
                            case TURRET_CUPOLA ->
                            {
                                assertThat(canvas.propAt(ux, unit.y(), uz)).as(where).isEqualTo(UnderwayProp.TURRET_CUPOLA);
                                assertThat(canvas.get(ux, unit.y() - 1, uz)).as("room under the %s", where).isEqualTo(UnderwayMaterial.AIR);
                            }
                            case EMBRASURE_GUN ->
                            {
                                assertThat(canvas.get(aheadX, unit.y(), aheadZ)).as("slit before the %s", where).isEqualTo(UnderwayMaterial.EMBRASURE);
                                assertThat(canvas.propAt(ux, unit.y(), uz)).as(where).isNull();
                                assertThat(unit.emergency()).isTrue();
                            }
                            case AUTOMATON ->
                            {
                                assertThat(canvas.propAt(ux, unit.y(), uz)).as(where).isNull();
                                assertThat(canvas.propAt(ux, unit.y() + 1, uz)).as(where).isNull();
                                assertThat(canvas.get(ux, unit.y() + 1, uz)).as(where).isEqualTo(UnderwayMaterial.AIR);
                                assertThat(canvas.get(ux, unit.y() - 1, uz).isOpen()).as("floor under the %s", where).isFalse();
                            }
                        }
                    }
                    assertThat(fort.unitCount(UnderwaySection.ENTRY) + fort.unitCount(UnderwaySection.LIVING) + fort.unitCount(UnderwaySection.RAIL)).isEqualTo(fort.units().size());
                }
            }
        }
        assertThat(kinds).containsExactlyInAnyOrder(UnderwayUnitKind.values());
    }

    @Test
    void everyPitIsWatchedByATripwireOverItsGrating()
    {
        int pits = 0;
        for (int z = -RANGE; z <= RANGE; z++)
        {
            for (int x = -RANGE; x <= RANGE; x++)
            {
                var fort = UnderwayPlans.fort(SEEDS[0], new DistrictPos(x, z));
                if (fort.ruin() == UnderwayRuin.BREACHED)
                    continue;
                for (var room : fort.rooms())
                {
                    if (room.kind() != UnderwayRoomKind.PIT)
                        continue;
                    pits++;
                    assertThat(fort.units()).as("tripwire over the pit at %d %d of the fort under %d %d", room.a0(), room.c0(), x, z).anyMatch(unit -> unit.kind() == UnderwayUnitKind.TRIPWIRE
                            && unit.along() >= room.a0() && unit.along() <= room.a1() && unit.across() >= room.c0() && unit.across() <= room.c1());
                }
            }
        }
        assertThat(pits).isGreaterThan(RANGE);
    }

    @Test
    void theVoidAndTheWaterLeaveNothingWorking()
    {
        var breached = UnderwayPlans.fort(SEEDS[0], find(SEEDS[0], UnderwayRuin.BREACHED));
        assertThat(breached.units()).isEmpty();
        var flooded = UnderwayPlans.fort(SEEDS[0], find(SEEDS[0], UnderwayRuin.FLOODED));
        assertThat(flooded.unitCount(UnderwaySection.RAIL)).isZero();
        assertThat(flooded.units()).isNotEmpty();
    }

    @Test
    void lockedRoomsAreShutByAShutter()
    {
        int shutters = 0;
        for (int z = -RANGE; z <= RANGE; z++)
        {
            for (int x = -RANGE; x <= RANGE; x++)
            {
                var fort = UnderwayPlans.fort(SEEDS[0], new DistrictPos(x, z));
                for (var stroke : fort.strokes())
                {
                    if (stroke.material() != UnderwayMaterial.SHUTTER)
                        continue;
                    shutters++;
                    assertThat((stroke.a1() - stroke.a0() + 1) * (stroke.c1() - stroke.c0() + 1)).as("a shutter is one block thick and two wide").isEqualTo(2);
                    assertThat(stroke.y1() - stroke.y0() + 1).isEqualTo(FortBuilder.DOOR_HEIGHT);
                }
            }
        }
        assertThat(shutters).isGreaterThan(RANGE * RANGE);
    }

    private static DistrictPos find(long seed, UnderwayRuin ruin)
    {
        for (int z = 0; z < SEARCH; z++)
        {
            for (int x = 0; x < SEARCH; x++)
            {
                var pos = new DistrictPos(x, z);
                if (UnderwayNetwork.ruinAt(seed, pos) == ruin)
                    return pos;
            }
        }
        throw new IllegalStateException("No " + ruin + " fort within " + SEARCH + " districts");
    }

    private static UnderwayCanvas paint(long seed, DistrictPos pos)
    {
        var canvas = new UnderwayCanvas(DistrictPos.PITCH, DistrictPos.PITCH);
        canvas.reset(pos.originX(), pos.originZ());
        UnderwayPlans.paint(seed, canvas);
        return canvas;
    }
}
