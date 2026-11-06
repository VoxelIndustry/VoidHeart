package net.voxelindustry.voidheart.common.content.athanor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AthanorMathTest
{
    private static int[] match(List<String> ingredients, List<String> slots)
    {
        return AthanorMath.matchUnordered(ingredients, slots, slot -> !slot.isEmpty(), String::equals);
    }

    @Test
    void reagents_matchInAnyOrder()
    {
        assertThat(match(List.of("sulfur", "quicksilver"), List.of("quicksilver", "", "sulfur", ""))).containsExactly(2, 0);
    }

    @Test
    void reagents_needEveryIngredient()
    {
        assertThat(match(List.of("sulfur", "quicksilver"), List.of("sulfur", "", "", ""))).isNull();
    }

    @Test
    void reagents_refuseAnExtraFilledCrucible()
    {
        assertThat(match(List.of("sulfur"), List.of("sulfur", "salt"))).isNull();
    }

    @Test
    void reagents_pairDuplicatesWithTheirOwnCrucible()
    {
        assertThat(match(List.of("salt", "salt"), List.of("salt", "salt"))).containsExactly(0, 1);
        assertThat(match(List.of("salt", "salt"), List.of("salt", "sulfur"))).isNull();
    }

    @Test
    void reagents_backtrackWhenAnEarlyPairingBlocksALaterOne()
    {
        // The first ingredient accepts both, the second only the first slot
        var slots = List.of("ab", "a");
        var result = AthanorMath.matchUnordered(List.of("a", "b"), slots, slot -> !slot.isEmpty(), (ingredient, slot) -> slot.contains(ingredient));
        assertThat(result).containsExactly(1, 0);
    }

    @Test
    void noReagents_matchEmptyCrucibles()
    {
        assertThat(match(List.of(), List.of("", ""))).isEmpty();
        assertThat(match(List.of(), List.of())).isEmpty();
    }

    @Test
    void missing_onlyListsAspectsShortOfTheirRequirement()
    {
        var missing = AthanorMath.missing(Map.of("fiery", 500, "stillness", 200), Map.of("fiery", 120, "stillness", 300));
        assertThat(missing).containsExactlyEntriesOf(Map.of("fiery", 380));
    }

    @Test
    void consume_keepsWhatTheBrewDidNotUse()
    {
        var left = AthanorMath.consume(Map.of("fiery", 520, "stillness", 40), Map.of("fiery", 500));
        assertThat(left).containsExactlyInAnyOrderEntriesOf(Map.of("fiery", 20, "stillness", 40));
        assertThat(AthanorMath.consume(Map.of("fiery", 500), Map.of("fiery", 500))).isEmpty();
    }

    @Test
    void layout_findsPartsAroundTheCore()
    {
        var core = new BlockPos(10, 64, -3);
        var layout = AthanorLayout.scan(core, pos ->
        {
            if (pos.equals(core.above()))
                return AthanorLayout.Part.ALEMBIC;
            if (pos.equals(core.below()))
                return AthanorLayout.Part.INTAKE;
            if (pos.equals(core.east()) || pos.equals(core.west()))
                return AthanorLayout.Part.CRUCIBLE;
            // A crucible above or diagonal is not part of it
            return AthanorLayout.Part.NONE;
        });

        assertThat(layout.alembic()).isTrue();
        assertThat(layout.intake()).isTrue();
        assertThat(layout.crucibles()).containsExactlyInAnyOrder(Direction.EAST, Direction.WEST);
        assertThat(layout.canBrew()).isTrue();
    }

    @Test
    void layout_withoutAlembicCannotBrew()
    {
        var core = BlockPos.ZERO;
        var layout = AthanorLayout.scan(core, pos -> Arrays.asList(core.north(), core.south()).contains(pos) ? AthanorLayout.Part.CRUCIBLE : AthanorLayout.Part.NONE);

        assertThat(layout.crucibles()).hasSize(2);
        assertThat(layout.canBrew()).isFalse();
    }

    @Test
    void layout_ignoresAlembicAndIntakeOnTheWrongSide()
    {
        var core = BlockPos.ZERO;
        var layout = AthanorLayout.scan(core, pos ->
        {
            if (pos.equals(core.below()))
                return AthanorLayout.Part.ALEMBIC;
            if (pos.equals(core.above()))
                return AthanorLayout.Part.INTAKE;
            return AthanorLayout.Part.NONE;
        });

        assertThat(layout.alembic()).isFalse();
        assertThat(layout.intake()).isFalse();
    }
}
