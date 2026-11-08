package net.voxelindustry.voidheart.client.screen;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.research.Constellation;
import net.voxelindustry.voidheart.common.content.research.Research;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The shipped research tree, the sizes the map is laid out with, and what screens of the sizes it is played at show of it.
 */
final class ResearchTrees
{
    static final int[][] SIZES = { { 320, 240 }, { 427, 240 }, { 480, 270 }, { 640, 360 }, { 960, 540 }, { 1920, 1080 } };

    /** The widest a glyph of the game's font gets, so a name here is never narrower than in game. */
    private static final int GLYPH_WIDEST = 6;

    private static final Map<Constellation, String> NAMES = Map.of(Constellation.RITES, "The Rites", Constellation.ARTIFICES, "Artifices",
            Constellation.ARCHIVE, "The Archive", Constellation.TRAVEL, "Art of Travel", Constellation.DEITIES, "The Deities");

    private ResearchTrees()
    {
    }

    static TreeMap<Identifier, Research> shipped()
    {
        var tree = new TreeMap<Identifier, Research>();
        try (var files = Files.list(Path.of("src/main/resources/data/voidheart/voidheart/research")))
        {
            for (var file : files.sorted().toList())
            {
                var name = file.getFileName().toString().replace(".json", "");
                var json = JsonParser.parseString(Files.readString(file));
                tree.put(id(name), Research.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
            }
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
        return tree;
    }

    static Identifier id(String path)
    {
        return Identifier.fromNamespaceAndPath("voidheart", path);
    }

    static Research research(Constellation constellation, int tier, String... requires)
    {
        return new Research(tier, constellation, Identifier.withDefaultNamespace("stone"), 1, 1, 1, List.of(requires).stream().map(ResearchTrees::id).toList(),
                0, 0, 0, 0, List.of(), List.of());
    }

    static Research requiring(Research research, String... requires)
    {
        return new Research(research.tier(), research.constellation(), research.icon(), research.insightCost(), research.knowledgeCost(), research.knowledgePerSecond(),
                List.of(requires).stream().map(ResearchTrees::id).toList(), research.passiveBonus(), research.columnCapacity(), research.cogitantRate(), research.strain(),
                research.unlocks(), research.recipes());
    }

    static String nameOf(Constellation constellation)
    {
        return NAMES.get(constellation);
    }

    /** The sizes the map is laid out with, the same on every screen. */
    static ResearchMapPlan.Frame frame()
    {
        int scale = ResearchMapView.EYE_SCALE;
        var widths = new EnumMap<Constellation, Integer>(Constellation.class);
        NAMES.forEach((constellation, name) -> widths.put(constellation, ResearchMapView.SIGIL_SIZE + ResearchMapView.SIGIL_GAP + name.length() * GLYPH_WIDEST));
        return new ResearchMapPlan.Frame(ResearchMapView.eyeHalfWidth(scale), ResearchMapView.eyeHalfHeight(scale), ResearchMapView.NODE_SIZE, widths, ResearchMapView.NAME_HEIGHT);
    }

    /** What a screen of this size shows of the map: left, top, right and bottom. */
    static int[] view(int width, int height)
    {
        int rim = Math.clamp((int) (width * 0.045F), 20, 40);
        return new int[] { rim + ResearchMapView.VIEW_INSET, ResearchMapView.VIEW_INSET, width - rim - ResearchMapView.VIEW_INSET, height - ResearchMapView.INSIGHT_TOP };
    }
}
