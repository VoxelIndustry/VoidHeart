package net.voxelindustry.voidheart.client.screen;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.client.screen.ResearchLayout.Projection;
import net.voxelindustry.voidheart.common.content.research.Research;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shipped tree has to stay readable in the smallest map a screen can give it.
 */
class ResearchLayoutTest
{
    private static final Projection SMALLEST = new Projection(100, 120, 84, 102);
    private static final double     EYE_HALF_W = 28;
    private static final double     EYE_HALF_H = 18;

    private static final Map<Identifier, Research> TREE = new HashMap<>();

    @BeforeAll
    static void loadTree() throws IOException
    {
        try (var files = Files.list(Path.of("src/main/resources/data/voidheart/voidheart/research")))
        {
            for (var file : files.toList())
            {
                var name = file.getFileName().toString().replace(".json", "");
                var json = JsonParser.parseString(Files.readString(file));
                TREE.put(Identifier.fromNamespaceAndPath("voidheart", name), Research.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
            }
        }
    }

    @Test
    void compute_placesEveryResearchOnTheRingOfItsTier()
    {
        var polar = ResearchLayout.compute(TREE);

        assertThat(polar.keySet()).containsExactlyInAnyOrderElementsOf(TREE.keySet());
        TREE.forEach((id, research) -> assertThat(polar.get(id).fraction()).isEqualTo(ResearchLayout.fractionOf(research.tier(), 3)));
    }

    @Test
    void compute_keepsNodesApartAtTheSmallestSize()
    {
        var polar = ResearchLayout.compute(TREE);
        var ids = new ArrayList<>(polar.keySet());

        for (int a = 0; a < ids.size(); a++)
        {
            for (int b = a + 1; b < ids.size(); b++)
            {
                var first = polar.get(ids.get(a));
                var second = polar.get(ids.get(b));
                double distance = Math.hypot(SMALLEST.x(first.fraction(), first.angle()) - SMALLEST.x(second.fraction(), second.angle()),
                        SMALLEST.y(first.fraction(), first.angle()) - SMALLEST.y(second.fraction(), second.angle()));
                assertThat(distance).as("%s and %s", ids.get(a), ids.get(b)).isGreaterThanOrEqualTo(ResearchMapViewSizes.NODE);
            }
        }
    }

    @Test
    void compute_keepsNodesOffTheEye()
    {
        ResearchLayout.compute(TREE).forEach((id, at) ->
        {
            double dx = Math.abs(SMALLEST.x(at.fraction(), at.angle()) - SMALLEST.centerX()) - ResearchMapViewSizes.NODE / 2D;
            double dy = Math.abs(SMALLEST.y(at.fraction(), at.angle()) - SMALLEST.centerY()) - ResearchMapViewSizes.NODE / 2D;
            assertThat(dx > EYE_HALF_W || dy > EYE_HALF_H).as("%s clear of the eye", id).isTrue();
        });
    }

    @Test
    void compute_isStableBetweenCalls()
    {
        assertThat(ResearchLayout.compute(TREE)).isEqualTo(ResearchLayout.compute(TREE));
    }

    private static final class ResearchMapViewSizes
    {
        static final int NODE = 24;
    }
}
