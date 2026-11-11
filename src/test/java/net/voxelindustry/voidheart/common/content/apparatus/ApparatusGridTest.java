package net.voxelindustry.voidheart.common.content.apparatus;

import com.mojang.serialization.JavaOps;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusGrid.Placement;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusGrid.Refusal;
import net.voxelindustry.voidheart.common.content.apparatus.ComponentShape.Cell;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static net.voxelindustry.voidheart.common.content.apparatus.ApparatusComponent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ApparatusGridTest
{
    @Test
    void turningAShapeFourTimesComesBack()
    {
        for (var component : ApparatusComponent.values())
            assertThat(component.shape().rotated(4)).isEqualTo(component.shape());
    }

    @Test
    void turningAShapeKeepsItInTheTopLeftCorner()
    {
        var turned = BANK.shape().rotated(1);
        assertThat(turned.cells()).containsExactly(new Cell(0, 0), new Cell(0, 1), new Cell(0, 2));
        assertThat(WARD_SHAPE().rotated(3).cells()).allSatisfy(cell -> assertThat(cell.x()).isNotNegative());
    }

    @Test
    void turningNeverMirrorsAShape()
    {
        var turns = new HashSet<ComponentShape>();
        for (int quarter = 0; quarter < 4; quarter++)
            turns.add(HASTENER.shape().rotated(quarter));
        var mirrored = ComponentShape.of("##.", ".##");
        assertThat(turns).doesNotContain(mirrored);
    }

    private static ComponentShape WARD_SHAPE()
    {
        return EMBER_WARD.shape();
    }

    @Test
    void aPieceCanNotLeaveTheGrid()
    {
        assertThat(ApparatusGrid.EMPTY.refusal(3, BANK, 1, 0, 0)).isEqualTo(Refusal.OUTSIDE);
        assertThat(ApparatusGrid.EMPTY.refusal(3, BANK, 0, 0, 0)).isNull();
        assertThat(ApparatusGrid.EMPTY.refusal(3, BANK, 2, 0, 1)).isNull();
        assertThat(ApparatusGrid.EMPTY.refusal(3, CELL, -1, 0, 0)).isEqualTo(Refusal.OUTSIDE);
    }

    @Test
    void piecesCanNotOverlap()
    {
        var grid = ApparatusGrid.EMPTY.with(new Placement(RESERVOIR, 0, 0, 0));
        assertThat(grid.refusal(3, CELL, 1, 1, 0)).isEqualTo(Refusal.OVERLAPS);
        assertThat(grid.refusal(3, CELL, 2, 2, 0)).isNull();
        assertThat(grid.at(1, 1)).isEqualTo(grid.placements().getFirst());
        assertThat(grid.at(2, 2)).isNull();
    }

    @Test
    void anApparatusHoldsOneBreakCore()
    {
        var grid = ApparatusGrid.EMPTY.with(new Placement(PULSE_CORE, 0, 0, 0));
        assertThat(grid.refusal(5, MENDING_CORE, 0, 3, 0)).isEqualTo(Refusal.SECOND_CORE);
    }

    @Test
    void aWardKindIsSetOnce()
    {
        var grid = ApparatusGrid.EMPTY.with(new Placement(EMBER_WARD, 0, 0, 0));
        assertThat(grid.refusal(5, EMBER_WARD, 3, 0, 0)).isEqualTo(Refusal.SAME_WARD);
        assertThat(grid.refusal(5, ARROW_WARD, 3, 0, 0)).isNull();
    }

    @Test
    void anEmptyApparatusGivesItsTierStats()
    {
        for (var tier : ApparatusTier.values())
        {
            var stats = ApparatusGrid.EMPTY.stats(tier);
            assertThat(stats.capacity()).isEqualTo(tier.capacity());
            assertThat(stats.delayTicks()).isEqualTo(tier.delayTicks());
            assertThat(stats.regenPerTick() * 20).isCloseTo(tier.regenPerSecond(), within(0.0001F));
            assertThat(stats.wards()).isEmpty();
            assertThat(stats.breakEffect()).isEmpty();
        }
    }

    @Test
    void higherTiersAreStrongerOnEveryStat()
    {
        var tiers = ApparatusTier.values();
        for (int i = 1; i < tiers.length; i++)
        {
            assertThat(tiers[i].size()).isGreaterThan(tiers[i - 1].size());
            assertThat(tiers[i].capacity()).isGreaterThan(tiers[i - 1].capacity());
            assertThat(tiers[i].delayTicks()).isLessThan(tiers[i - 1].delayTicks());
            assertThat(tiers[i].regenPerSecond()).isGreaterThan(tiers[i - 1].regenPerSecond());
        }
    }

    @Test
    void componentsAddTheirStats()
    {
        var grid = new ApparatusGrid(List.of(
                new Placement(BANK, 0, 0, 0),
                new Placement(RESONATOR, 0, 1, 0),
                new Placement(QUICKENER, 1, 2, 0),
                new Placement(CELL, 2, 1, 0)));
        var stats = grid.stats(ApparatusTier.LESSER);

        assertThat(stats.capacity()).isEqualTo(4 + 3.5F + 1);
        assertThat(stats.regenPerTick() * 20).isCloseTo(1.3F, within(0.0001F));
        assertThat(stats.delayTicks()).isEqualTo(85);
    }

    @Test
    void quickeningMultipliesAndHasAFloor()
    {
        var placements = new java.util.ArrayList<Placement>();
        for (int y = 0; y < 5; y++)
            for (int x = 0; x + 1 < 5; x += 2)
                placements.add(new Placement(QUICKENER, x, y, 0));
        var stats = new ApparatusGrid(placements).stats(ApparatusTier.SUPREME);
        assertThat(stats.delayTicks()).isEqualTo(ShieldStats.MIN_DELAY_TICKS);
    }

    @Test
    void placementsThatNoLongerFitAreSkipped()
    {
        var grid = new ApparatusGrid(List.of(
                new Placement(RESERVOIR, 3, 3, 0),
                new Placement(CELL, 0, 0, 0),
                new Placement(CELL, 0, 0, 0),
                new Placement(PULSE_CORE, 0, 1, 0),
                new Placement(BLINK_CORE, 0, 3, 0)));
        assertThat(grid.valid(5)).extracting(Placement::component).containsExactly(RESERVOIR, CELL, PULSE_CORE);
        assertThat(grid.valid(3)).extracting(Placement::component).containsExactly(CELL, PULSE_CORE);
        assertThat(grid.stats(ApparatusTier.LESSER).breakEffect()).contains(BreakEffect.PULSE);
    }

    @Test
    void aGridSavesAndLoads()
    {
        var grid = new ApparatusGrid(List.of(new Placement(CHOIR, 1, 2, 3), new Placement(ARCANE_WARD, 0, 0, 1)));
        var saved = ApparatusGrid.CODEC.encodeStart(JavaOps.INSTANCE, grid).getOrThrow();
        assertThat(ApparatusGrid.CODEC.parse(JavaOps.INSTANCE, saved).getOrThrow()).isEqualTo(grid);
    }

    @Test
    void everyComponentFitsTheSmallestGrid()
    {
        for (var component : ApparatusComponent.values())
            assertThat(component.shape().width()).isLessThanOrEqualTo(ApparatusTier.LESSER.size());
    }
}
