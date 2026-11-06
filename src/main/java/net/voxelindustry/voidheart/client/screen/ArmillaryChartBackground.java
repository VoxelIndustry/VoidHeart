package net.voxelindustry.voidheart.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.voxelindustry.voidheart.common.content.armillary.VoidBubble;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonAddress;

import java.util.List;

/**
 * The same slab and torn hole as the knowledge repository, seen from the side: the world the player stands in closes the hole at the top, the dark below it deepens band by band,
 * and the strata of the Daedalus closes it at the bottom.
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

    // The strata: the slab itself closing the hole at the bottom, with the Daedalus cut into it. Sizes in units of the chart's pixel scale
    private static final int   LIP_TEAR       = 6;
    private static final float LIP_SCALE      = 0.11F;
    private static final float LIP_FINE_SCALE = 0.37F;
    private static final int   PITCH_UNITS    = 30;
    private static final float FISSURE_HALF   = 3.2F;
    private static final float FISSURE_NARROW = 0.75F;
    private static final float FISSURE_WANDER = 1.8F;
    private static final int   SOLID_UNITS    = 4;
    private static final int   TIER_UNITS     = 7;
    private static final int   HALL_TOP       = 3;
    private static final int   CELL_UNITS     = 5;
    private static final float HALL_CHANCE    = 0.28F;
    private static final float BRIDGE_CHANCE  = 0.35F;
    private static final int   EDGE_SEARCH    = 3;
    private static final float HOLLOW_DARK    = 0.9F;
    private static final float DEEP_FADE      = 0.7F;
    private static final float SHADOW_ROCK    = 0.1F;
    private static final float SHADOW_LIP     = 0.55F;
    private static final int   SHADOW_REACH   = 10;
    private static final float SHADOW_STRENGTH = 0.7F;

    /**
     * How the strata shows: a shapeless shadow to whoever has not studied it, and to whoever has, the Daedalus cut into it. Only light ever colours it.
     *
     * @param top where its lip runs, on screen, before it is torn
     */
    record Strata(boolean known, int top)
    {
    }

    private ArmillaryChartBackground()
    {
    }

    /**
     * Where the strata's torn lip is at a column of the screen, the same in every bake for one seed.
     */
    static int lipY(int x, int left, int top, int scale, long seed)
    {
        int unitX = Math.floorDiv(x - left, scale);
        return top + lipUnits(unitX, seed) * scale;
    }

    private static int lipUnits(int unitX, long seed)
    {
        float tear = ResearchVoid.smooth(unitX * LIP_SCALE, 13.7F, seed) * 0.7F + ResearchVoid.smooth(unitX * LIP_FINE_SCALE, 29.3F, seed) * 0.3F;
        return Math.round(tear * LIP_TEAR);
    }

    /**
     * How far down the screen the overworld can reach.
     */
    static int surfaceBottom(int scale)
    {
        return (SURFACE_ROWS + TORN_DEPTH) * scale;
    }

    static void bake(Minecraft minecraft, Identifier target, int width, int height, int left, int right, int chartTop, int chartBottom, int scale, List<ResearchVoid.Light> lights, long seed,
                     Strata strata)
    {
        int chartHeight = chartBottom - chartTop;
        var image = ResearchVoid.paint(minecraft, width, height, left, right, lights, seed, (x, y) -> bandDarkness(x, y - chartTop, chartHeight));

        surface(image, width, height, left, right, scale, seed);
        depthTicks(image, left, right, chartTop, chartHeight, scale, seed);
        var stone = ResearchVoid.readStone(minecraft);
        strata(image, stone, width, height, left, right, scale, seed, strata, lights);
        if (stone != null)
            stone.close();
        strataTick(image, left, right, strata.top(), scale, seed);
        ResearchVoid.upload(minecraft, target, image);
    }

    private enum Cut
    {
        ROCK,
        HOLLOW,
        BRIDGE
    }

    /**
     * The same voidstone as the slab's walls, with the same lip where it meets the dark. Studied, it opens on the Daedalus: chasms as fissures narrowing downward with bridges
     * hanging across, and corridors hollowed out tier under tier, where any light that reaches them shows. Steady from one bake to the next, so only the light ever changes.
     */
    private static void strata(NativeImage image, NativeImage stone, int width, int height, int left, int right, int scale, long seed, Strata strata, List<ResearchVoid.Light> lights)
    {
        int firstUnit = Math.floorDiv(-left, scale);
        int columns = Math.floorDiv(width - left, scale) - firstUnit + 1;
        int rows = Math.max(1, (height - strata.top()) / scale + 1);

        var cuts = new Cut[columns][rows];
        var lips = new int[columns];
        for (int column = 0; column < columns; column++)
        {
            int unitX = firstUnit + column;
            lips[column] = lipUnits(unitX, seed);
            for (int row = 0; row < rows; row++)
                cuts[column][row] = row < lips[column] ? null : strata.known() ? cutAt(unitX, row - lips[column], rows, seed) : Cut.ROCK;
        }

        for (int x = 0; x < width; x++)
        {
            int column = Math.floorDiv(x - left, scale) - firstUnit;
            int lipY = strata.top() + lips[column] * scale;
            for (int y = Math.max(0, lipY - SHADOW_REACH * scale); y < height; y++)
            {
                int edgeLeft = ResearchVoid.edgeLeft(y, left, seed);
                int edgeRight = ResearchVoid.edgeRight(y, right, seed);
                if (x < edgeLeft || x >= edgeRight)
                    continue;

                if (y < lipY)
                {
                    // A dark rising a little above it, as there is under the overworld
                    float shade = (1 - (lipY - y) / (float) (SHADOW_REACH * scale)) * SHADOW_STRENGTH;
                    if (ResearchVoid.BAYER[y & 3][x & 3] < shade)
                        image.setPixel(x, y, ARGB.srgbLerp(shade, image.getPixel(x, y), ResearchVoid.DEEP_COLOR));
                    continue;
                }

                int row = Math.min(rows - 1, (y - strata.top()) / scale);
                float wall = Math.min(x - edgeLeft, edgeRight - 1 - x);
                float deep = Math.clamp((y - strata.top()) / (float) Math.max(1, height - strata.top()) * DEEP_FADE, 0, 1);
                int color = switch (cuts[column][row])
                {
                    case HOLLOW -> ResearchVoid.voidAt(x, y, wall, HOLLOW_DARK, lights, seed);
                    case BRIDGE -> ResearchVoid.LIP_COLOR;
                    case null, default -> rockAt(stone, cuts, column, row, x, y, (y - strata.top()) % scale == 0, scale, strata.known(), seed);
                };
                if (cuts[column][row] != Cut.HOLLOW)
                {
                    color = ARGB.srgbLerp(deep, color, ResearchVoid.DEEP_COLOR);
                    if (wall < ResearchVoid.INNER_SHADOW)
                        color = ARGB.srgbLerp((1 - wall / ResearchVoid.INNER_SHADOW) * WALL_SHADOW, color, ResearchVoid.DEEP_COLOR);
                }
                image.setPixel(x, y, color);
            }
        }
    }

    /**
     * Rock lit from above where the dark opens over it, shaded where it opens beside or under it, and in the shadow nothing but a darker dark.
     */
    private static int rockAt(NativeImage stone, Cut[][] cuts, int column, int row, int x, int y, boolean firstRow, int scale, boolean known, long seed)
    {
        if (!known)
        {
            // Only its torn edge catches any light, dimly
            if (isOpen(cuts, column, row - 1))
                return firstRow ? ARGB.srgbLerp(SHADOW_LIP, ResearchVoid.DEEP_COLOR, ResearchVoid.LIP_COLOR) : ResearchVoid.LIP_SHADE_COLOR;
            int rock = ResearchVoid.stoneAt(stone, x, y, (int) ResearchVoid.INNER_SHADOW, seed);
            return ARGB.srgbLerp(SHADOW_ROCK, ResearchVoid.DEEP_COLOR, rock);
        }

        if (isOpen(cuts, column, row - 1))
            return ResearchVoid.stoneAt(stone, x, y, firstRow ? 1 : 2, seed);

        int distance = EDGE_SEARCH + 1;
        for (int dy = -EDGE_SEARCH; dy <= EDGE_SEARCH; dy++)
        {
            for (int dx = -EDGE_SEARCH; dx <= EDGE_SEARCH; dx++)
            {
                if (isOpen(cuts, column + dx, row + dy))
                    distance = Math.min(distance, Math.max(Math.abs(dx), Math.abs(dy)));
            }
        }
        return ResearchVoid.stoneAt(stone, x, y, distance == 1 ? 2 : distance * scale + 1, seed);
    }

    private static boolean isOpen(Cut[][] cuts, int column, int row)
    {
        if (column < 0 || column >= cuts.length || row < 0)
            return row < 0;
        if (row >= cuts[column].length)
            return false;
        return cuts[column][row] == null || cuts[column][row] == Cut.HOLLOW;
    }

    /**
     * What one unit of the strata is, counted from its own lip.
     */
    private static Cut cutAt(int unitX, int depth, int rows, long seed)
    {
        int district = Math.floorDiv(unitX, PITCH_UNITS);
        float sink = depth / (float) rows;
        float centre = district * PITCH_UNITS + (ResearchVoid.smooth(depth * 0.13F, district * 3.1F, seed + 7) - 0.5F) * 2 * FISSURE_WANDER;
        float half = FISSURE_HALF * (1 - sink * FISSURE_NARROW) + (ResearchVoid.hash(depth, district, seed + 3) - 0.5F) * 0.8F;
        float across = unitX - centre;
        if (Math.abs(across) < half)
        {
            int tier = depth / TIER_UNITS;
            boolean bridged = depth > SOLID_UNITS && depth % TIER_UNITS == HALL_TOP + 2 && ResearchVoid.hash(district, tier, seed + 23) < BRIDGE_CHANCE;
            return bridged ? Cut.BRIDGE : Cut.HOLLOW;
        }

        if (depth < SOLID_UNITS)
            return Cut.ROCK;

        int tier = (depth - SOLID_UNITS) / TIER_UNITS;
        int inTier = (depth - SOLID_UNITS) % TIER_UNITS;
        if (inTier < HALL_TOP)
            return Cut.ROCK;

        int within = Math.floorMod(unitX, PITCH_UNITS);
        int cell = within / CELL_UNITS;
        if (within % CELL_UNITS == 0)
            return ResearchVoid.hash(district * 41 + cell, tier, seed + 11) < 0.5F ? Cut.ROCK : Cut.HOLLOW;
        return ResearchVoid.hash(district * 17 + cell, tier, seed + 5) < HALL_CHANCE ? Cut.HOLLOW : Cut.ROCK;
    }

    /**
     * A long notch in the lip of the hole where the depths end and the strata begins, longer than those between the depths.
     */
    private static void strataTick(NativeImage image, int left, int right, int top, int scale, long seed)
    {
        for (int row = top; row < top + scale; row++)
        {
            int edgeLeft = ResearchVoid.edgeLeft(row, left, seed);
            int edgeRight = ResearchVoid.edgeRight(row, right, seed);
            for (int along = 0; along < TICK_LENGTH * 2 * scale; along++)
            {
                int color = along < scale ? ResearchVoid.LIP_BRIGHT : ResearchVoid.LIP_COLOR;
                image.setPixel(edgeLeft + along, row, color);
                image.setPixel(edgeRight - 1 - along, row, color);
            }
        }
    }

    /**
     * Steps rather than a gradient, so that depth reads as strata. The step between two bands breaks up in a dither, like the edges of the light.
     */
    private static float bandDarkness(int x, int y, int chartHeight)
    {
        if (y <= 0)
            return 0;

        float bands = (float) y / (chartHeight * VoidBubble.DEPTHS_SHARE) * DungeonAddress.MAX_DIFFICULTY;
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
            int y = chartTop + Math.round(VoidBubble.bandY(band) * chartHeight);
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
