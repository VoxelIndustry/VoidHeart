package net.voxelindustry.voidheart.common.content.armor;

import com.mojang.serialization.JavaOps;
import net.voxelindustry.voidheart.common.content.armor.ArmorGrids.Placement;
import net.voxelindustry.voidheart.common.content.armor.ArmorGrids.Refusal;
import org.junit.jupiter.api.Test;

import static net.voxelindustry.voidheart.common.content.armor.ArmorGridKind.CORE;
import static net.voxelindustry.voidheart.common.content.armor.ArmorGridKind.HELM;
import static net.voxelindustry.voidheart.common.content.armor.ArmorGridKind.LIMBS;
import static net.voxelindustry.voidheart.common.content.armor.ArmorModule.*;
import static org.assertj.core.api.Assertions.assertThat;

class ArmorGridsTest
{
    private static ArmorGrids set(ArmorGrids grids, ArmorGridKind kind, ArmorModule module, int x, int y, int rotation)
    {
        assertThat(grids.refusal(kind, module, x, y, rotation)).as("%s at %d,%d in %s", module, x, y, kind).isNull();
        return grids.with(kind, new Placement(module, x, y, rotation));
    }

    @Test
    void batteriesFitEveryGrid()
    {
        for (var kind : ArmorGridKind.values())
            assertThat(ArmorGrids.EMPTY.canPlace(kind, BATTERY_CELL, 0, 0, 0)).as(kind.name()).isTrue();
    }

    @Test
    void sensorsGoOnlyInTheHelmAndExoskeletonPowerOnlyInTheLimbs()
    {
        assertThat(ArmorGrids.EMPTY.refusal(CORE, NIGHT_VISION, 0, 0, 0)).isEqualTo(Refusal.WRONG_GRID);
        assertThat(ArmorGrids.EMPTY.refusal(LIMBS, NIGHT_VISION, 0, 0, 0)).isEqualTo(Refusal.WRONG_GRID);
        assertThat(ArmorGrids.EMPTY.refusal(HELM, EXOSKELETON_POWER, 0, 0, 0)).isEqualTo(Refusal.WRONG_GRID);
        assertThat(ArmorGrids.EMPTY.canPlace(LIMBS, EXOSKELETON_POWER, 0, 0, 0)).isTrue();
    }

    @Test
    void theCoresMiddleCellIsTheSocket()
    {
        assertThat(CORE.isSocket(2, 2)).isTrue();
        assertThat(ArmorGrids.EMPTY.refusal(CORE, BATTERY_CELL, 1, 1, 0)).isEqualTo(Refusal.SOCKET);
        assertThat(ArmorGrids.EMPTY.canPlace(CORE, BATTERY_CELL, 3, 3, 0)).isTrue();
        assertThat(HELM.isSocket(1, 1)).isFalse();
    }

    @Test
    void theCoreHoldsFourBatteriesAroundItsSocket()
    {
        var grids = ArmorGrids.EMPTY;
        grids = set(grids, CORE, BATTERY_CELL, 0, 0, 0);
        grids = set(grids, CORE, BATTERY_CELL, 3, 0, 0);
        grids = set(grids, CORE, BATTERY_CELL, 0, 3, 0);
        grids = set(grids, CORE, BATTERY_CELL, 3, 3, 0);
        assertThat(grids.stats().batteries()).isEqualTo(4);
        assertThat(grids.stats().capacity()).isEqualTo(ArmorBalance.FRAME_CAPACITY + 4 * ArmorBalance.BATTERY_CAPACITY);
    }

    @Test
    void piecesNeitherOverlapNorLeaveTheirGrid()
    {
        var grids = set(ArmorGrids.EMPTY, LIMBS, BATTERY_CELL, 0, 0, 0);
        assertThat(grids.refusal(LIMBS, BATTERY_CELL, 1, 1, 0)).isEqualTo(Refusal.OVERLAPS);
        assertThat(grids.refusal(LIMBS, BATTERY_CELL, 3, 3, 0)).isEqualTo(Refusal.OUTSIDE);
        assertThat(grids.refusal(LIMBS, EXOSKELETON_POWER, 2, 0, 1)).isEqualTo(Refusal.OUTSIDE);
    }

    @Test
    void aFrameTakesOneOfEachUniqueModule()
    {
        var grids = set(ArmorGrids.EMPTY, HELM, NIGHT_VISION, 0, 0, 0);
        assertThat(grids.refusal(HELM, NIGHT_VISION, 0, 2, 0)).isEqualTo(Refusal.SECOND);
        grids = set(grids, LIMBS, EXOSKELETON_POWER, 0, 0, 0);
        assertThat(grids.refusal(LIMBS, EXOSKELETON_POWER, 2, 0, 0)).isEqualTo(Refusal.SECOND);
        assertThat(grids.canPlace(LIMBS, BATTERY_CELL, 2, 0, 0)).isTrue();
    }

    @Test
    void theSpectralLensMustRestAgainstNightVision()
    {
        assertThat(ArmorGrids.EMPTY.refusal(HELM, SPECTRAL_LENS, 0, 0, 0)).isEqualTo(Refusal.NEEDS_SUPPORT);

        var grids = set(ArmorGrids.EMPTY, HELM, NIGHT_VISION, 0, 0, 0);
        // Night vision covers 0,0 and 1,0; a lens only touching it at a corner is refused
        assertThat(grids.refusal(HELM, SPECTRAL_LENS, 1, 1, 2)).isEqualTo(Refusal.NEEDS_SUPPORT);
        grids = set(grids, HELM, SPECTRAL_LENS, 0, 1, 0);
        assertThat(grids.stats().spectralLens()).isTrue();
    }

    @Test
    void aLensLeftWithoutNightVisionStopsWorkingUntilItIsBack()
    {
        var nightVision = new Placement(NIGHT_VISION, 0, 0, 0);
        var grids = ArmorGrids.EMPTY.with(HELM, nightVision);
        grids = set(grids, HELM, SPECTRAL_LENS, 0, 1, 0);

        var without = grids.without(HELM, nightVision);
        assertThat(without.stats().spectralLens()).isFalse();
        assertThat(without.helm()).hasSize(1);

        var back = set(without, HELM, NIGHT_VISION, 1, 2, 0);
        assertThat(back.stats().nightVision()).isTrue();
        assertThat(back.stats().spectralLens()).as("a lens set before its night vision still counts once it touches").isTrue();
    }

    @Test
    void editedDataBreakingTheRulesIsSkipped()
    {
        var grids = new ArmorGrids(
                java.util.List.of(new Placement(EXOSKELETON_POWER, 0, 0, 0)),
                java.util.List.of(new Placement(BATTERY_CELL, 1, 1, 0), new Placement(BATTERY_CELL, 0, 0, 0)),
                java.util.List.of(new Placement(BATTERY_CELL, 0, 0, 0), new Placement(BATTERY_CELL, 1, 0, 0)));
        var valid = grids.valid();
        assertThat(valid.get(HELM)).isEmpty();
        assertThat(valid.get(CORE)).containsExactly(new Placement(BATTERY_CELL, 0, 0, 0));
        assertThat(valid.get(LIMBS)).containsExactly(new Placement(BATTERY_CELL, 0, 0, 0));
        assertThat(grids.stats().batteries()).isEqualTo(2);
    }

    @Test
    void moreBatteriesMeanALongerSortieAndModulesAShorterOne()
    {
        var bare = ArmorGrids.EMPTY.stats();
        var battery = set(ArmorGrids.EMPTY, CORE, BATTERY_CELL, 0, 0, 0).stats();
        var exoskeleton = set(ArmorGrids.EMPTY, LIMBS, EXOSKELETON_POWER, 0, 0, 0).stats();
        assertThat(battery.sortieMinutes()).isGreaterThan(bare.sortieMinutes());
        assertThat(exoskeleton.sortieMinutes()).isLessThan(bare.sortieMinutes());
        assertThat(exoskeleton.speedBonus()).isGreaterThan(bare.speedBonus());
    }

    @Test
    void gridsSurviveTheirCodec()
    {
        var grids = set(ArmorGrids.EMPTY, HELM, NIGHT_VISION, 0, 0, 1);
        grids = set(grids, CORE, BATTERY_CELL, 3, 3, 0);
        var encoded = ArmorGrids.CODEC.encodeStart(JavaOps.INSTANCE, grids).getOrThrow();
        assertThat(ArmorGrids.CODEC.parse(JavaOps.INSTANCE, encoded).getOrThrow()).isEqualTo(grids);
    }
}
