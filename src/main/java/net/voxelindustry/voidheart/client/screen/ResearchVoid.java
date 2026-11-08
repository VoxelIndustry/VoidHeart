package net.voxelindustry.voidheart.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import java.io.IOException;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Bakes what the screen stands on: a slab of voidstone with a torn hole in it, and through the hole a dark that is only lit around what the player has reached.
 */
final class ResearchVoid
{
    record Light(double x, double y, double radius, double strength, int rgb)
    {
    }

    private static final Identifier STONE = Identifier.fromNamespaceAndPath(MODID, "textures/block/voidstone.png");

    /** How much darker the unlit void is at a point, from 0 to 1. */
    interface Shade
    {
        float at(int x, int y);
    }

    private static final float STONE_LIGHT     = 0.42F;
    static final int           LIP_COLOR       = 0xFF4A3860;
    static final int           LIP_SHADE_COLOR = 0xFF08050C;
    static final int           DEEP_COLOR      = 0xFF040208;
    private static final int   MID_COLOR       = 0xFF170E28;
    static final int           LIT_RGB         = 0x3A2858;
    private static final int   STAR_COLOR      = 0xFFB8A8D8;
    private static final float TEAR_DEPTH      = 11;
    static final float         INNER_SHADOW    = 14;
    private static final float STAR_CHANCE     = 0.0022F;
    private static final float SMOKE_SCALE     = 0.022F;
    private static final float LIGHT_EDGE      = 0.16F;
    private static final int   LIGHT_LEVELS    = 4;
    static final int           LIP_BRIGHT      = 0xFF7A62A8;
    private static final float STONE_SHADOW    = 12;
    /** How dark the floor of the hole gets, and how far above the bottom it is darkest. */
    private static final float FLOOR_SHADOW    = 0.8F;
    private static final int   FLOOR_DARKEST   = 12;
    /** How far flesh can eat into the stone and spill into the hole once it crusts the edge. */
    private static final float CRUST_STONE     = 9;
    private static final float CRUST_HOLE      = 7;

    static final float[][] BAYER = {
            { 0 / 16F, 8 / 16F, 2 / 16F, 10 / 16F },
            { 12 / 16F, 4 / 16F, 14 / 16F, 6 / 16F },
            { 3 / 16F, 11 / 16F, 1 / 16F, 9 / 16F },
            { 15 / 16F, 7 / 16F, 13 / 16F, 5 / 16F }
    };

    private ResearchVoid()
    {
    }

    /**
     * The slab alone, with the hole left clear but for the shadow its torn edge casts, so that the map can be drawn under it and pan beneath its edge.
     * The floor of the hole darkens too, from floorTop down, so what is panned under the readouts sinks into the dark rather than fights them.
     */
    static void bakeSlab(Minecraft minecraft, Identifier target, int width, int height, int left, int right, int floorTop, boolean crusted, long seed)
    {
        var stone = readStone(minecraft);
        var image = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++)
        {
            int edgeLeft = edgeLeft(y, left, seed);
            int edgeRight = edgeRight(y, right, seed);
            for (int x = 0; x < width; x++)
            {
                int crust = crusted ? crustAt(x, y, x < edgeLeft || x >= edgeRight ? Math.min(Math.abs(x - edgeLeft), Math.abs(x - edgeRight)) : -Math.min(x - edgeLeft, edgeRight - 1 - x), seed) : 0;
                if (crust != 0)
                    image.setPixel(x, y, crust);
                else if (x < edgeLeft || x >= edgeRight)
                    image.setPixel(x, y, stoneAt(stone, x, y, Math.min(Math.abs(x - edgeLeft), Math.abs(x - edgeRight)), seed));
                else
                {
                    float wall = Math.min(x - edgeLeft, edgeRight - 1 - x);
                    float shadow = wall < INNER_SHADOW ? (1 - wall / INNER_SHADOW) * 0.85F : 0;
                    float floor = Math.clamp((y - floorTop) / (float) Math.max(1, height - FLOOR_DARKEST - floorTop), 0, 1) * FLOOR_SHADOW;
                    shadow = Math.max(shadow, floor);
                    image.setPixel(x, y, ARGB.color((int) (shadow * 255), DEEP_COLOR));
                }
            }
        }
        if (stone != null)
            stone.close();
        upload(minecraft, target, image);
    }

    /**
     * The dark seen through the hole, painted in the map's own units so that it pans with the map: its smoke and stars stay where they are among the
     * researches, and its light stays around them.
     *
     * @param minX where the painted dark starts, in the map's units
     */
    static void bakeDepths(Minecraft minecraft, Identifier target, int minX, int minY, int width, int height, List<Light> lights, long seed)
    {
        var image = new NativeImage(Math.max(1, width), Math.max(1, height), false);
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
                image.setPixel(x, y, voidAt(minX + x, minY + y, INNER_SHADOW, 0, lights, seed));
        }
        upload(minecraft, target, image);
    }

    static NativeImage paint(Minecraft minecraft, int width, int height, int left, int right, List<Light> lights, long seed, Shade shade)
    {
        var stone = readStone(minecraft);
        var image = new NativeImage(width, height, false);

        for (int y = 0; y < height; y++)
        {
            int edgeLeft = edgeLeft(y, left, seed);
            int edgeRight = edgeRight(y, right, seed);
            for (int x = 0; x < width; x++)
            {
                int color;
                if (x < edgeLeft || x >= edgeRight)
                    color = stoneAt(stone, x, y, Math.min(Math.abs(x - edgeLeft), Math.abs(x - edgeRight)), seed);
                else
                {
                    float wall = Math.min(x - edgeLeft, edgeRight - 1 - x);
                    color = voidAt(x, y, wall, shade.at(x, y), lights, seed);
                }
                image.setPixel(x, y, color);
            }
        }

        if (stone != null)
            stone.close();
        return image;
    }

    /**
     * Flesh grown over the torn edge once Maw has come all the way through: lumps that eat a little into the stone and spill a little into the hole, thicker
     * in some places than others. Returns 0 where there is none.
     *
     * @param fromEdge how far into the stone, or negative how far into the hole
     */
    static int crustAt(int x, int y, int fromEdge, long seed)
    {
        float into = CRUST_STONE * (0.35F + 0.65F * smooth(y * 0.06F, 4.2F, seed + 77));
        float out = CRUST_HOLE * smooth(y * 0.09F, 8.8F, seed + 79);
        if (fromEdge > into || -fromEdge > out)
            return 0;
        if (fromEdge > into - 1.5F || -fromEdge > out - 1.5F)
            return MawFlesh.FLESH_DARK;
        int lumpY = Math.floorDiv(y, 5);
        int lumpX = Math.floorDiv(fromEdge + 20, 4);
        float speck = hash(x, y, seed + 81);
        if (speck < 0.1F)
            return MawFlesh.FLESH_SPECK;
        if (speck > 0.93F)
            return MawFlesh.FLESH_SHINE;
        // A seam between two lumps now and then, so the crust reads as heaped rather than striped
        if (Math.floorMod(y, 5) == 0 && hash(lumpX, lumpY, seed + 85) < 0.55F)
            return MawFlesh.FLESH_DARK;
        return MawFlesh.flesh((int) (hash(lumpX, lumpY, seed + 83) * 6));
    }

    static int edgeLeft(int y, int left, long seed)
    {
        return left + tear(y, seed);
    }

    static int edgeRight(int y, int right, long seed)
    {
        return right - tear(y, seed + 91);
    }

    static void upload(Minecraft minecraft, Identifier target, NativeImage image)
    {
        var textures = minecraft.getTextureManager();
        textures.release(target);
        var texture = new DynamicTexture(() -> "Research void", image);
        texture.upload();
        textures.register(target, texture);
    }

    static NativeImage readStone(Minecraft minecraft)
    {
        try (var stream = minecraft.getResourceManager().getResourceOrThrow(STONE).open())
        {
            return NativeImage.read(stream);
        }
        catch (IOException exception)
        {
            return null;
        }
    }

    private static int tear(int y, long seed)
    {
        return Math.round((smooth(y * 0.07F, 3.1F, seed) * 0.7F + smooth(y * 0.31F, 7.7F, seed) * 0.3F) * TEAR_DEPTH);
    }

    static int stoneAt(NativeImage stone, int x, int y, int fromEdge, long seed)
    {
        if (fromEdge <= 1)
            return hash(x, y, seed) > 0.72F ? LIP_BRIGHT : LIP_COLOR;
        if (fromEdge == 2)
            return LIP_SHADE_COLOR;
        if (stone == null)
            return 0xFF120D18;

        float shade = STONE_LIGHT * Math.min(1, 0.45F + 0.55F * fromEdge / STONE_SHADOW);
        int texel = stone.getPixel(x % stone.getWidth(), y % stone.getWidth());
        return ARGB.color(255,
                (int) (ARGB.red(texel) * shade),
                (int) (ARGB.green(texel) * shade),
                (int) (ARGB.blue(texel) * shade));
    }

    static int voidAt(int x, int y, float wall, float dark, List<Light> lights, long seed)
    {
        // A dither cell keyed to the map rather than the texture, so the light's steps do not shimmer as the map pans
        int cellX = Math.floorMod(x, 4);
        int cellY = Math.floorMod(y, 4);
        float light = 0;
        float red = 0;
        float green = 0;
        float blue = 0;
        for (var source : lights)
        {
            double dx = x - source.x();
            double dy = y - source.y();
            float reached = (float) (source.strength() * Math.exp(-(dx * dx + dy * dy) / (source.radius() * source.radius())));
            light += reached;
            red += reached * ARGB.red(source.rgb());
            green += reached * ARGB.green(source.rgb());
            blue += reached * ARGB.blue(source.rgb());
        }
        // Each island lights the dark in its own hue, and they mix where they meet
        int litColor = light > 0 ? ARGB.color(255, (int) (red / light), (int) (green / light), (int) (blue / light)) : 0xFF000000 | LIT_RGB;
        // Stepped and dithered, so the light stops at an edge rather than fading into everything
        light = light < LIGHT_EDGE ? 0 : (float) Math.floor(Math.min(1, light) * LIGHT_LEVELS + BAYER[cellY][cellX]) / LIGHT_LEVELS;

        float warpX = smooth(x * SMOKE_SCALE, y * SMOKE_SCALE, seed) * 3;
        float smoke = smooth(x * SMOKE_SCALE * 1.7F + warpX, y * SMOKE_SCALE * 1.7F, seed + 17) * 0.65F
                + smooth(x * SMOKE_SCALE * 4.1F, y * SMOKE_SCALE * 4.1F + warpX, seed + 43) * 0.35F;

        int color = ARGB.srgbLerp(0.45F * smoke + 0.1F, DEEP_COLOR, MID_COLOR);
        if (dark > 0)
            color = ARGB.srgbLerp(dark, color, DEEP_COLOR);
        color = ARGB.srgbLerp(light * (0.5F + 0.5F * smoke), color, litColor);

        if (hash(x, y, seed) < STAR_CHANCE)
            color = ARGB.srgbLerp((light > 0 ? 0.55F : 0.14F) * (0.4F + hash(y, x, seed) * 0.6F), color, STAR_COLOR);

        if (wall < INNER_SHADOW)
            color = ARGB.srgbLerp((1 - wall / INNER_SHADOW) * 0.85F, color, DEEP_COLOR);
        return color;
    }

    static float hash(int x, int y, long seed)
    {
        long h = x * 73856093L ^ y * 19349663L ^ seed * 83492791L;
        h ^= h >>> 13;
        h *= 0x5BD1E995L;
        h ^= h >>> 15;
        return (h & 0xFFFFF) / (float) 0x100000;
    }

    static float smooth(float x, float y, long seed)
    {
        int cellX = (int) Math.floor(x);
        int cellY = (int) Math.floor(y);
        float tx = fade(x - cellX);
        float ty = fade(y - cellY);
        float top = hash(cellX, cellY, seed) * (1 - tx) + hash(cellX + 1, cellY, seed) * tx;
        float bottom = hash(cellX, cellY + 1, seed) * (1 - tx) + hash(cellX + 1, cellY + 1, seed) * tx;
        return top * (1 - ty) + bottom * ty;
    }

    private static float fade(float t)
    {
        return t * t * (3 - 2 * t);
    }
}
