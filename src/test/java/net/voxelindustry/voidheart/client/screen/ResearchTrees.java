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

    static TreeMap<Identifier, Research> grown()
    {
        var tree = shipped();
        tree.put(id("void_lens"), research(Constellation.ARTIFICES, 3, "far_sight"));
        return tree;
    }

    /** A few researches more in every constellation, as the tree will have after some updates. */
    static TreeMap<Identifier, Research> crowded()
    {
        var tree = grown();
        tree.put(id("ward_rite"), research(Constellation.RITES, 3, "void_flesh"));
        tree.put(id("echo_archive"), research(Constellation.ARCHIVE, 3, "vast_archive"));
        tree.put(id("void_compass"), research(Constellation.TRAVEL, 3, "portal_flexion"));
        tree.put(id("quiet_eye"), research(Constellation.ARTIFICES, 3, "shoulder_eye"));
        return tree;
    }

    /**
     * The tree as other mods might leave it: many more researches hung anywhere on it, some in a constellation other than the one they hang from, and a few
     * growing from the eye. Which goes where is fixed by the seed, so the same tree comes out every time.
     */
    static TreeMap<Identifier, Research> sprawling(int more, long seed)
    {
        var tree = shipped();
        var constellations = Constellation.values();
        long state = seed;
        for (int i = 0; i < more; i++)
        {
            state = state * 6364136223846793005L + 1442695040888963407L;
            var known = List.copyOf(tree.keySet());
            int pick = (int) Math.floorMod(state >>> 33, (long) known.size() + 1);
            state = state * 6364136223846793005L + 1442695040888963407L;
            boolean foreign = Math.floorMod(state >>> 33, 6L) == 0;
            int other = (int) Math.floorMod(state >>> 40, (long) constellations.length);
            var path = String.format("addon_%03d", i);
            if (pick == known.size())
                tree.put(Identifier.fromNamespaceAndPath("addon", path), research(constellations[other], 1));
            else
            {
                var above = known.get(pick);
                var constellation = foreign ? constellations[other] : tree.get(above).constellation();
                tree.put(Identifier.fromNamespaceAndPath("addon", path), new Research(3, constellation, Identifier.withDefaultNamespace("stone"), 1, 1, 1, List.of(above),
                        0, 0, 0, 0, List.of(), List.of()));
            }
        }
        return tree;
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
