package net.voxelindustry.voidheart.common.content.research;

import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.research.RecipeSheet.Kind;
import net.voxelindustry.voidheart.common.content.research.RecipeSheet.Part;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeSheetTest
{
    private static final Identifier RESEARCH = id("research");
    private static final Identifier AMALGAM  = id("amalgam");
    private static final Identifier PEARL    = id("pearl");
    private static final Identifier FLESH    = id("flesh");

    private static final RecipeSheet LOCKED = sheet(List.of(FLESH), AMALGAM, Optional.of(RESEARCH));
    private static final RecipeSheet FREE   = sheet(List.of(AMALGAM), PEARL, Optional.empty());

    private static Identifier id(String path)
    {
        return Identifier.fromNamespaceAndPath("voidheart", path);
    }

    private static RecipeSheet sheet(List<Identifier> inputs, Identifier output, Optional<Identifier> research)
    {
        return new RecipeSheet(Kind.ALTAR, inputs.stream().map(item -> new Part(item, 1)).toList(), Optional.of(new Part(output, 1)), 0, 0, research);
    }

    private static RecipeSheet shaped(int width, List<Optional<Identifier>> pattern)
    {
        return new RecipeSheet(Kind.CRAFTING, List.of(), Optional.empty(), 0, 0, Optional.empty(), pattern, width);
    }

    @Test
    void gridCells_keepsAFullPatternInPlace()
    {
        var pattern = List.of(Optional.of(FLESH), Optional.<Identifier>empty(), Optional.of(FLESH),
                Optional.of(PEARL), Optional.of(PEARL), Optional.of(PEARL),
                Optional.of(FLESH), Optional.<Identifier>empty(), Optional.of(FLESH));

        assertThat(shaped(3, pattern).gridCells()).isEqualTo(pattern);
    }

    @Test
    void gridCells_centersASmallerPattern()
    {
        var cells = shaped(1, List.of(Optional.of(FLESH), Optional.of(PEARL))).gridCells();

        assertThat(cells).hasSize(9);
        // One wide and two tall: the middle column, from the top
        assertThat(cells.get(1)).contains(FLESH);
        assertThat(cells.get(4)).contains(PEARL);
        assertThat(cells.stream().filter(Optional::isPresent)).hasSize(2);
    }

    @Test
    void gridCells_isEmptyWithoutAShape()
    {
        assertThat(LOCKED.gridCells()).isEmpty();
    }

    @Test
    void making_hidesWhatAResearchStillLocks()
    {
        assertThat(RecipeSheet.making(List.of(LOCKED), AMALGAM, List.of())).isEmpty();
        assertThat(RecipeSheet.hasLockedMaking(List.of(LOCKED), AMALGAM, List.of())).isTrue();
    }

    @Test
    void making_showsItOnceTheResearchIsDone()
    {
        assertThat(RecipeSheet.making(List.of(LOCKED), AMALGAM, List.of(RESEARCH))).containsExactly(LOCKED);
        assertThat(RecipeSheet.hasLockedMaking(List.of(LOCKED), AMALGAM, List.of(RESEARCH))).isFalse();
    }

    @Test
    void usedFor_namesEachOutputOnce()
    {
        assertThat(RecipeSheet.usedFor(List.of(FREE, FREE), AMALGAM, List.of())).containsExactly(PEARL);
    }

    @Test
    void usedFor_keepsLockedRecipesASecret()
    {
        assertThat(RecipeSheet.usedFor(List.of(LOCKED), FLESH, List.of())).isEmpty();
        assertThat(RecipeSheet.usedFor(List.of(LOCKED), FLESH, List.of(RESEARCH))).containsExactly(AMALGAM);
    }

    @Test
    void usedFor_skipsARecipeGivingBackItsOwnItem()
    {
        var recycling = sheet(List.of(AMALGAM), AMALGAM, Optional.empty());

        assertThat(RecipeSheet.usedFor(List.of(recycling), AMALGAM, List.of())).isEmpty();
    }
}
