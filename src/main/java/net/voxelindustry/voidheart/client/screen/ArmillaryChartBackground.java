package net.voxelindustry.voidheart.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonAddress;

import java.util.List;

/**
 * The same slab and torn hole as the knowledge repository, seen from the side: the world the player stands in closes the hole at the top, and the dark below it deepens band by band.
 */
final class ArmillaryChartBackground
{
    private static final int SKY_COLOR        = 0xFF0A0710;
    private static final int GRASS_TOP_COLOR  = 0xFF566B45;
    private static final int GRASS_COLOR      = 0xFF3C4D33;
    private static final int DIRT_COLOR       = 0xFF3B2E28;
    private static final int DIRT_SHADE_COLOR = 0xFF2B2120;
    private static final int STONE_COLOR      = 0xFF26212C;
    private static final int STONE_SHADE      = 0xFF1D1823;

    private static final int   GRASS_RISE    = 3;
    private static final int   GRASS_HEIGHT  = 5;
    private static final int   GRASS_STEP    = 8;
    private static final int   DIRT_HEIGHT   = 6;
    private static final int   STONE_HEIGHT  = 4;
    private static final int   SURFACE_ROWS  = GRASS_RISE + GRASS_HEIGHT + DIRT_HEIGHT + STONE_HEIGHT;
    private static final int   TORN_DEPTH    = 5;
    private static final float TORN_SCALE    = 0.09F;
    private static final float TOP_SHADOW    = 16;
    private static final float WALL_SHADOW   = 0.85F;

    private static final float DARKEST_BAND  = 0.82F;
    private static final float BAND_DITHER   = 0.14F;
    private static final int   TICK_LENGTH   = 5;

    private ArmillaryChartBackground()
    {
    }

    /**
     * How far down the screen the overworld can reach.
     */
    static int surfaceBottom(int scale)
    {
        return (SURFACE_ROWS + TORN_DEPTH) * scale;
    }

    static void bake(Minecraft minecraft, Identifier target, int width, int height, int left, int right, int chartTop, int chartBottom, int scale, List<ResearchVoid.Light> lights, long seed)
    {
        int chartHeight = chartBottom - chartTop;
        var image = ResearchVoid.paint(minecraft, width, height, left, right, lights, seed, (x, y) -> bandDarkness(x, y - chartTop, chartHeight));

        surface(image, width, height, left, right, scale, seed);
        depthTicks(image, left, right, chartTop, chartHeight, scale, seed);
        ResearchVoid.upload(minecraft, target, image);
    }

    /**
     * Steps rather than a gradient, so that depth reads as strata. The step between two bands breaks up in a dither, like the edges of the light.
     */
    private static float bandDarkness(int x, int y, int chartHeight)
    {
        if (y <= 0)
            return 0;

        float bands = (float) y / chartHeight * DungeonAddress.MAX_DIFFICULTY;
        int band = Math.min((int) bands, DungeonAddress.MAX_DIFFICULTY - 1);
        float within = bands - band;
        if (within > 1 - BAND_DITHER && ResearchVoid.BAYER[y & 3][x & 3] < (within - 1 + BAND_DITHER) / BAND_DITHER)
            band = Math.min(band + 1, DungeonAddress.MAX_DIFFICULTY - 1);
        return band * DARKEST_BAND / (DungeonAddress.MAX_DIFFICULTY - 1);
    }

    private static void surface(NativeImage image, int width, int height, int left, int right, int scale, long seed)
    {
        for (int x = 0; x < width; x++)
        {
            int block = x / scale;
            int bottom = (SURFACE_ROWS + Math.round(ResearchVoid.smooth(block * TORN_SCALE, 5.3F, seed) * TORN_DEPTH)) * scale;
            int rise = (int) (ResearchVoid.hash(block / GRASS_STEP, 7, seed) * (GRASS_RISE + 1));

            for (int y = 0; y < Math.min(height, bottom + (int) TOP_SHADOW); y++)
            {
                int edgeLeft = ResearchVoid.edgeLeft(y, left, seed);
                int edgeRight = ResearchVoid.edgeRight(y, right, seed);
                if (x < edgeLeft || x >= edgeRight)
                    continue;

                if (y >= bottom)
                {
                    float shadow = (1 - (y - bottom) / TOP_SHADOW) * WALL_SHADOW;
                    image.setPixel(x, y, ARGB.srgbLerp(shadow, image.getPixel(x, y), ResearchVoid.DEEP_COLOR));
                    continue;
                }

                int color = terrain(block, y / scale, (bottom - y - 1) / scale, rise, seed);
                float wall = Math.min(x - edgeLeft, edgeRight - 1 - x);
                if (wall < ResearchVoid.INNER_SHADOW)
                    color = ARGB.srgbLerp((1 - wall / ResearchVoid.INNER_SHADOW) * WALL_SHADOW, color, ResearchVoid.DEEP_COLOR);
                image.setPixel(x, y, color);
            }
        }
    }

    private static int terrain(int block, int row, int fromTear, int rise, long seed)
    {
        float grain = ResearchVoid.hash(block, row, seed);
        if (fromTear == 0)
            return grain > 0.72F ? ResearchVoid.LIP_BRIGHT : ResearchVoid.LIP_COLOR;
        if (fromTear == 1)
            return ResearchVoid.LIP_SHADE_COLOR;

        int turf = GRASS_RISE - rise;
        if (row < turf)
            return SKY_COLOR;
        if (row == turf)
            return GRASS_TOP_COLOR;
        if (row < GRASS_RISE + GRASS_HEIGHT - 2)
            return GRASS_COLOR;
        if (row < GRASS_RISE + GRASS_HEIGHT + DIRT_HEIGHT)
            return grain < 0.25F ? DIRT_SHADE_COLOR : DIRT_COLOR;
        return grain < 0.5F ? STONE_COLOR : STONE_SHADE;
    }

    /**
     * Notches in the lip of the hole where one depth meets the next.
     */
    private static void depthTicks(NativeImage image, int left, int right, int chartTop, int chartHeight, int scale, long seed)
    {
        for (int band = 1; band < DungeonAddress.MAX_DIFFICULTY; band++)
        {
            int y = chartTop + band * chartHeight / DungeonAddress.MAX_DIFFICULTY;
            for (int row = y; row < y + scale; row++)
            {
                int edgeLeft = ResearchVoid.edgeLeft(row, left, seed);
                int edgeRight = ResearchVoid.edgeRight(row, right, seed);
                for (int along = 0; along < TICK_LENGTH * scale; along++)
                {
                    int color = along < scale ? ResearchVoid.LIP_BRIGHT : ResearchVoid.LIP_COLOR;
                    image.setPixel(edgeLeft + along, row, color);
                    image.setPixel(edgeRight - 1 - along, row, color);
                }
            }
        }
    }
}
