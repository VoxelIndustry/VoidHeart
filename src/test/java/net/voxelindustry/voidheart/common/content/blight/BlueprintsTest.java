package net.voxelindustry.voidheart.common.content.blight;

import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.research.Constellation;
import net.voxelindustry.voidheart.common.content.research.Research;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BlueprintsTest
{
    private static final Identifier ICON = Identifier.withDefaultNamespace("stone");

    private static Identifier id(String path)
    {
        return Identifier.fromNamespaceAndPath("voidheart", path);
    }

    private static Research research(Optional<Identifier> blueprint)
    {
        return new Research(3, Constellation.ARTIFICES, ICON, 1, 1, 1, List.of(), 0, 0, 0, 0, List.of(), List.of(), List.of(), blueprint);
    }

    private static final Map<Identifier, Research> TREE = Map.of(
            id("plain"), research(Optional.empty()),
            id("a_research"), research(Optional.of(id("a"))),
            id("b_research"), research(Optional.of(id("b"))),
            id("c_research"), research(Optional.of(id("c"))),
            id("d_research"), research(Optional.of(id("d"))),
            id("e_research"), research(Optional.of(id("e"))));

    private static final List<Identifier> POOL = Blueprints.pool(TREE);

    @Test
    void pool_holdsEveryBlueprintTheTreeAsksForOnce()
    {
        assertThat(POOL).containsExactly(id("a"), id("b"), id("c"), id("d"), id("e"));
    }

    @Test
    void draw_givesThreeUnknownAndDistinctDesigns()
    {
        for (int seed = 0; seed < 50; seed++)
        {
            var drawn = Blueprints.draw(POOL, Set.of(id("a"), id("c")), new Random(seed));
            assertThat(drawn).hasSize(3).doesNotHaveDuplicates().doesNotContain(id("a"), id("c"));
        }
    }

    @Test
    void draw_givenFewerUnknownThanOffered_thenShowsThoseLeft()
    {
        assertThat(Blueprints.draw(POOL, Set.of(id("a"), id("b"), id("c"), id("d")), new Random(1))).containsExactly(id("e"));
        assertThat(Blueprints.draw(POOL, Set.copyOf(POOL), new Random(1))).isEmpty();
    }

    @Test
    void offer_keepsWhatWasDrawnSoOpeningAgainRerollsNothing()
    {
        var drawn = List.of(id("b"), id("d"), id("e"));
        assertThat(Blueprints.offer(drawn, POOL, Set.of(), new Random(7))).isEqualTo(drawn);
    }

    @Test
    void offer_dropsDesignsLearnedSince()
    {
        var drawn = List.of(id("b"), id("d"), id("e"));
        assertThat(Blueprints.offer(drawn, POOL, Set.of(id("d")), new Random(7))).containsExactly(id("b"), id("e"));
    }

    @Test
    void offer_givenEveryDrawnDesignKnown_thenDrawsAfresh()
    {
        var drawn = List.of(id("b"), id("d"));
        assertThat(Blueprints.offer(drawn, POOL, Set.of(id("b"), id("d")), new Random(7))).hasSize(3).doesNotContain(id("b"), id("d"));
    }

    @Test
    void offer_dropsDesignsNoLongerInTheTree()
    {
        assertThat(Blueprints.offer(List.of(id("gone")), POOL, Set.of(), new Random(3))).hasSize(3).doesNotContain(id("gone"));
    }

    @Test
    void researchOf_findsTheResearchABlueprintBringsIntoSight()
    {
        assertThat(Blueprints.researchOf(id("c"), TREE)).map(Map.Entry::getKey).contains(id("c_research"));
        assertThat(Blueprints.researchOf(id("gone"), TREE)).isEmpty();
    }
}
