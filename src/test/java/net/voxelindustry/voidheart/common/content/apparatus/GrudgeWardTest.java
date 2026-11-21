package net.voxelindustry.voidheart.common.content.apparatus;

import com.mojang.serialization.JsonOps;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusGrid.Placement;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusGrid.Refusal;
import net.voxelindustry.voidheart.common.content.mob.DragoonAdaptation.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static net.voxelindustry.voidheart.common.content.apparatus.ApparatusComponent.EMBER_WARD;
import static net.voxelindustry.voidheart.common.content.apparatus.ApparatusComponent.GRUDGE_WARD;
import static org.assertj.core.api.Assertions.assertThat;

class GrudgeWardTest
{
    @Test
    void onlyOneGrudgeFitsAnApparatus()
    {
        var grid = new ApparatusGrid(List.of(new Placement(GRUDGE_WARD, 0, 0, 0)));
        assertThat(grid.refusal(4, GRUDGE_WARD, 2, 0, 0)).isEqualTo(Refusal.SECOND_GRUDGE);
        assertThat(grid.refusal(4, EMBER_WARD, 2, 0, 0)).as("it sits beside the fixed wards").isNull();
    }

    @Test
    void aGrudgeWardSetMarksTheStats()
    {
        assertThat(ApparatusGrid.EMPTY.stats(ApparatusTier.GREATER).grudge()).isFalse();
        var stats = new ApparatusGrid(List.of(new Placement(GRUDGE_WARD, 0, 0, 0))).stats(ApparatusTier.GREATER);
        assertThat(stats.grudge()).isTrue();
        assertThat(stats.wards()).as("it holds no fixed kind").isEmpty();
    }

    @Test
    void itHoldsTheLastKindThatBrokeTheShield()
    {
        assertThat(GrudgeWard.afterBreak(null, Kind.FIRE)).isEqualTo(Kind.FIRE);
        assertThat(GrudgeWard.afterBreak(Kind.FIRE, Kind.BLADE)).isEqualTo(Kind.BLADE);
        assertThat(GrudgeWard.afterBreak(Kind.FIRE, null)).as("a hit no one dealt changes nothing").isEqualTo(Kind.FIRE);

        assertThat(GrudgeWard.wards(Kind.FIRE, Kind.FIRE)).isTrue();
        assertThat(GrudgeWard.wards(Kind.FIRE, Kind.BLADE)).isFalse();
        assertThat(GrudgeWard.wards(null, null)).isFalse();
    }

    @Test
    void theGrudgeIsSavedByName()
    {
        for (var kind : Kind.values())
        {
            var saved = GrudgeWard.KIND_CODEC.encodeStart(JsonOps.INSTANCE, kind).getOrThrow();
            assertThat(GrudgeWard.KIND_CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow()).isEqualTo(kind);
        }
        assertThat(GrudgeWard.KIND_CODEC.parse(JsonOps.INSTANCE, JsonOps.INSTANCE.createString("nothing")).isError()).isTrue();
    }
}
