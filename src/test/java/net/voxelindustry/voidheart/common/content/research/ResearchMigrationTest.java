package net.voxelindustry.voidheart.common.content.research;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.voxelindustry.voidheart.common.content.research.ResearchMigration.LEY_SENSE;
import static net.voxelindustry.voidheart.common.content.research.ResearchMigration.VOID_ENERGY;
import static org.assertj.core.api.Assertions.assertThat;

class ResearchMigrationTest
{
    private static final Map<Identifier, Research> TREE = new HashMap<>();

    @BeforeAll
    static void loadTree() throws IOException
    {
        try (var files = Files.list(Path.of("src/main/resources/data/voidheart/voidheart/research")))
        {
            for (var file : files.toList())
            {
                var json = JsonParser.parseString(Files.readString(file));
                TREE.put(id(file.getFileName().toString().replace(".json", "")), Research.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
            }
        }
    }

    private static Identifier id(String path)
    {
        return Identifier.fromNamespaceAndPath("voidheart", path);
    }

    private static PlayerKnowledge owning(String... research)
    {
        var knowledge = new PlayerKnowledge();
        for (var path : research)
            knowledge.grant(id(path));
        return knowledge;
    }

    @Test
    void owningTheArmillary_grantsVoidEnergy()
    {
        var knowledge = owning("altar_rite", "void_flesh", "shatter_forge", "armillary");

        assertThat(ResearchMigration.grantAddedParents(knowledge, TREE.keySet())).containsExactly(VOID_ENERGY);
        assertThat(knowledge.getCompleted()).contains(VOID_ENERGY, id("armillary")).doesNotContain(LEY_SENSE);
    }

    @Test
    void eachChild_grantsOnlyItsOwnParent()
    {
        assertThat(ResearchMigration.grantAddedParents(owning("tria_prima"), TREE.keySet())).containsExactly(VOID_ENERGY);
        assertThat(ResearchMigration.grantAddedParents(owning("portal_flexion"), TREE.keySet())).containsExactly(VOID_ENERGY);
        assertThat(ResearchMigration.grantAddedParents(owning("beholder"), TREE.keySet())).containsExactly(LEY_SENSE);
        assertThat(ResearchMigration.grantAddedParents(owning("armillary", "beholder"), TREE.keySet())).containsExactly(LEY_SENSE, VOID_ENERGY);
    }

    @Test
    void grantsOnce()
    {
        var knowledge = owning("armillary");
        ResearchMigration.grantAddedParents(knowledge, TREE.keySet());

        assertThat(ResearchMigration.grantAddedParents(knowledge, TREE.keySet())).isEmpty();
    }

    @Test
    void playersBeforeTheChildren_areLeftToResearchIt()
    {
        var knowledge = owning("altar_rite", "void_flesh", "shatter_forge", "far_sight");

        assertThat(ResearchMigration.grantAddedParents(knowledge, TREE.keySet())).isEmpty();
        assertThat(knowledge.getCompleted()).doesNotContain(VOID_ENERGY, LEY_SENSE);
    }

    @Test
    void grantingAParentBeingResearched_finishesIt()
    {
        var knowledge = owning("armillary");
        knowledge.getRevealed().add(VOID_ENERGY);
        knowledge.toggleActive(VOID_ENERGY);

        ResearchMigration.grantAddedParents(knowledge, TREE.keySet());

        assertThat(knowledge.getActive()).isEmpty();
        assertThat(knowledge.getProgress()).doesNotContainKey(VOID_ENERGY);
    }

    @Test
    void aParentTakenOutOfTheTree_isNotGranted()
    {
        var tree = new HashSet<>(TREE.keySet());
        tree.remove(VOID_ENERGY);

        assertThat(ResearchMigration.grantAddedParents(owning("armillary"), tree)).isEmpty();
    }

    /**
     * The migration only makes sense while the children really wait for their new parent, so the table has to follow the data.
     */
    @Test
    void shippedTree_childrenWaitForTheirAddedParent()
    {
        ResearchMigration.ADDED_PARENTS.forEach((parent, children) ->
        {
            assertThat(TREE).containsKey(parent);
            for (var child : children)
                assertThat(ancestorsOf(child)).as("what %s waits for", child).contains(parent);
        });
    }

    /**
     * A research shows once any of its requirements is done, so a second parent alone would not hold anything back. What has to come first must be in
     * every way to it.
     */
    @Test
    void shippedTree_energyComesBeforeWhatDrawsIt()
    {
        var beforeEnergy = Set.of(id("altar_rite"), id("void_flesh"), id("shatter_forge"), id("far_sight"));
        for (var drawsEnergy : List.of("armillary", "tria_prima", "portal_flexion", "beholder", "daedalus", "deep_siphon"))
            assertThat(TREE.get(id(drawsEnergy)).isDiscoverable(beforeEnergy)).as("%s before energy", drawsEnergy).isFalse();

        assertThat(TREE.get(id("armillary")).isDiscoverable(Set.of(VOID_ENERGY))).isTrue();
        assertThat(TREE.get(id("beholder")).isDiscoverable(Set.of(LEY_SENSE))).isTrue();
        assertThat(TREE.get(id("shoulder_eye")).isDiscoverable(Set.of(id("far_sight")))).as("the Shoulder Eye stays free of energy").isTrue();
    }

    @Test
    void shippedTree_conversionNeedsBothEnergies()
    {
        var conversion = TREE.get(id("ley_conversion"));

        assertThat(conversion.isDiscoverable(Set.of(VOID_ENERGY))).isFalse();
        assertThat(conversion.isDiscoverable(Set.of(LEY_SENSE))).isFalse();
        assertThat(conversion.isDiscoverable(Set.of(VOID_ENERGY, LEY_SENSE))).isTrue();
        assertThat(TREE.get(id("contravoid_reactor")).requires()).containsExactly(id("ley_conversion"));
        assertThat(TREE.get(id("void_fissure")).isDiscoverable(Set.of(VOID_ENERGY))).isTrue();
    }

    private static Set<Identifier> ancestorsOf(Identifier id)
    {
        var found = new HashSet<Identifier>();
        var open = new ArrayDeque<>(List.of(id));
        while (!open.isEmpty())
        {
            var research = TREE.get(open.pop());
            for (var before : research.requires())
                if (found.add(before))
                    open.add(before);
            for (var before : research.hiddenUntil())
                if (found.add(before))
                    open.add(before);
        }
        return found;
    }
}
