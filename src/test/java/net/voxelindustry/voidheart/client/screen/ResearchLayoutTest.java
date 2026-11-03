package net.voxelindustry.voidheart.client.screen;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.client.screen.ResearchLayout.Projection;
import net.voxelindustry.voidheart.common.content.research.Research;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shipped islands have to stay readable at every size a screen can give them.
 */
class ResearchLayoutTest
{
    private static final int NODE         = ResearchMapView.NODE_SIZE;
    private static final int EYE_HALF_W   = 28;
    private static final int EYE_HALF_H   = 18;
    private static final int LARGE_EYE    = 350;
    private static final int BUTTON_TOP   = 45;

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

    private static Projection at(int width, int height)
    {
        int rim = Math.clamp((int) (width * 0.045F), 20, 40);
        return ResearchLayout.projection(rim, width - rim, height);
    }

    @ParameterizedTest
    @CsvSource({ "320,240", "427,240", "480,270", "640,360", "960,540", "1920,1080" })
    void projection_keepsNodesApart(int width, int height)
    {
        var projection = at(width, height);
        var ids = new ArrayList<>(TREE.keySet());

        for (int a = 0; a < ids.size(); a++)
        {
            for (int b = a + 1; b < ids.size(); b++)
            {
                var first = TREE.get(ids.get(a));
                var second = TREE.get(ids.get(b));
                double distance = Math.hypot(projection.x(first.x()) - projection.x(second.x()), projection.y(first.y()) - projection.y(second.y()));
                assertThat(distance).as("%s and %s", ids.get(a), ids.get(b)).isGreaterThanOrEqualTo(NODE);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({ "320,240", "427,240", "480,270", "640,360", "960,540", "1920,1080" })
    void projection_keepsNodesOffTheEye(int width, int height)
    {
        var projection = at(width, height);
        int scale = height >= LARGE_EYE ? 2 : 1;

        TREE.forEach((id, research) ->
        {
            double dx = Math.abs(projection.x(research.x()) - projection.centerX()) - NODE / 2D;
            double dy = Math.abs(projection.y(research.y()) - projection.centerY()) - NODE / 2D;
            assertThat(dx > EYE_HALF_W * scale || dy > EYE_HALF_H * scale).as("%s clear of the eye", id).isTrue();
        });
    }

    @ParameterizedTest
    @CsvSource({ "320,240", "427,240", "480,270", "640,360", "960,540", "1920,1080" })
    void projection_keepsNodesInsideTheHoleAndAboveTheButton(int width, int height)
    {
        var projection = at(width, height);
        int rim = Math.clamp((int) (width * 0.045F), 20, 40);

        TREE.forEach((id, research) ->
        {
            assertThat(projection.x(research.x())).as("%s across", id).isBetween(rim + NODE / 2D, width - rim - NODE / 2D);
            assertThat(projection.y(research.y())).as("%s down", id).isBetween(NODE / 2D, height - BUTTON_TOP - NODE / 2D);
        });
    }
}
