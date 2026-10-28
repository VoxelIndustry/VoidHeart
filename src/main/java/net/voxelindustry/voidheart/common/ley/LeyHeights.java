package net.voxelindustry.voidheart.common.ley;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntBinaryOperator;

/**
 * Line heights follow the generated terrain (noise only, ignoring trees, structures and builds) a few blocks above it.
 */
public class LeyHeights
{
    public static final int SAMPLE_SPACING = 8;
    public static final int HOVER = 3;

    private static final int SMOOTHING_RADIUS = 2;
    private static final int MIN_CLEARANCE = 1;
    private static final int CACHE_SIZE = 2048;

    private final IntBinaryOperator surface;
    private final IntBinaryOperator floor;

    private final Map<String, float[]> profiles = new LinkedHashMap<>(256, 0.75F, true)
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest)
        {
            return size() > CACHE_SIZE;
        }
    };

    public LeyHeights(IntBinaryOperator surface)
    {
        this(surface, surface);
    }

    /**
     * @param surface top of the terrain, fluids included
     * @param floor   top of the solid terrain, below any fluid
     */
    public LeyHeights(IntBinaryOperator surface, IntBinaryOperator floor)
    {
        this.surface = surface;
        this.floor = floor;
    }

    public static LeyHeights of(ServerLevel level)
    {
        var generator = level.getChunkSource().getGenerator();
        var randomState = level.getChunkSource().randomState();
        return new LeyHeights((x, z) -> generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState),
                (x, z) -> generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState));
    }

    public int nodeHeight(int x, int z)
    {
        return surface.applyAsInt(x, z) + HOVER;
    }

    public int groundHeight(int x, int z)
    {
        return surface.applyAsInt(x, z);
    }

    public boolean isSubmerged(int x, int z)
    {
        return floor.applyAsInt(x, z) < surface.applyAsInt(x, z);
    }

    public static int sampleCount(LeyEdge edge)
    {
        return Math.max(1, (int) Math.ceil(edge.length() / SAMPLE_SPACING)) + 1;
    }

    /**
     * Heights at evenly spaced points from the edge's {@code from} node to its {@code to} node.
     * Both ends are the node heights so every line meeting at a node joins at the same point.
     */
    public float[] profile(LeyEdge edge)
    {
        return profiles.computeIfAbsent(edge.from() + ":" + edge.to(), key ->
        {
            int count = sampleCount(edge);
            var ground = new int[count];
            for (int i = 0; i < count; i++)
            {
                float t = i / (float) (count - 1);
                ground[i] = surface.applyAsInt(Math.round(edge.fromX() + (edge.toX() - edge.fromX()) * t), Math.round(edge.fromZ() + (edge.toZ() - edge.fromZ()) * t));
            }

            var heights = new float[count];
            heights[0] = nodeHeight(edge.fromX(), edge.fromZ());
            heights[count - 1] = nodeHeight(edge.toX(), edge.toZ());
            for (int i = 1; i < count - 1; i++)
            {
                float sum = 0;
                int weights = 0;
                for (int j = Math.max(0, i - SMOOTHING_RADIUS); j <= Math.min(count - 1, i + SMOOTHING_RADIUS); j++)
                {
                    sum += ground[j];
                    weights++;
                }
                heights[i] = Math.max(sum / weights + HOVER, ground[i] + MIN_CLEARANCE);
            }
            return heights;
        });
    }
}
