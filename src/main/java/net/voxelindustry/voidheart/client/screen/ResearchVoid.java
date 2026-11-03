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

    private static final float STONE_LIGHT     = 0.42F;
    private static final int   LIP_COLOR       = 0xFF4A3860;
    private static final int   LIP_SHADE_COLOR = 0xFF08050C;
    private static final int   DEEP_COLOR      = 0xFF040208;
    private static final int   MID_COLOR       = 0xFF170E28;
    static final int           LIT_RGB         = 0x3A2858;
    private static final int   STAR_COLOR      = 0xFFB8A8D8;
    private static final float TEAR_DEPTH      = 11;
    private static final float INNER_SHADOW    = 14;
    private static final float STAR_CHANCE     = 0.0022F;
    private static final float SMOKE_SCALE     = 0.022F;
    private static final float LIGHT_EDGE      = 0.16F;
    private static final int   LIGHT_LEVELS    = 4;
    private static final int   LIP_BRIGHT      = 0xFF7A62A8;
    private static final float STONE_SHADOW    = 12;

    private static final float[][] BAYER = {
            { 0 / 16F, 8 / 16F, 2 / 16F, 10 / 16F },
            { 12 / 16F, 4 / 16F, 14 / 16F, 6 / 16F },
            { 3 / 16F, 11 / 16F, 1 / 16F, 9 / 16F },
            { 15 / 16F, 7 / 16F, 13 / 16F, 5 / 16F }
    };

    private ResearchVoid()
    {
    }

    static void bake(Minecraft minecraft, Identifier target, int width, int height, int left, int right, List<Light> lights, long seed)
    {
        var stone = readStone(minecraft);
        var image = new NativeImage(width, height, false);

        for (int y = 0; y < height; y++)
        {
            int edgeLeft = left + tear(y, seed);
            int edgeRight = right - tear(y, seed + 91);
            for (int x = 0; x < width; x++)
            {
                int color;
                if (x < edgeLeft || x >= edgeRight)
                    color = stoneAt(stone, x, y, Math.min(Math.abs(x - edgeLeft), Math.abs(x - edgeRight)), seed);
                else
                {
                    float wall = Math.min(x - edgeLeft, edgeRight - 1 - x);
                    color = voidAt(x, y, wall, lights, seed);
                }
                image.setPixel(x, y, color);
            }
        }

        if (stone != null)
            stone.close();

        var textures = minecraft.getTextureManager();
        textures.release(target);
        var texture = new DynamicTexture(() -> "Research void", image);
        texture.upload();
        textures.register(target, texture);
    }

    private static NativeImage readStone(Minecraft minecraft)
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

    private static int stoneAt(NativeImage stone, int x, int y, int fromEdge, long seed)
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

    private static int voidAt(int x, int y, float wall, List<Light> lights, long seed)
    {
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
        light = light < LIGHT_EDGE ? 0 : (float) Math.floor(Math.min(1, light) * LIGHT_LEVELS + BAYER[y & 3][x & 3]) / LIGHT_LEVELS;

        float warpX = smooth(x * SMOKE_SCALE, y * SMOKE_SCALE, seed) * 3;
        float smoke = smooth(x * SMOKE_SCALE * 1.7F + warpX, y * SMOKE_SCALE * 1.7F, seed + 17) * 0.65F
                + smooth(x * SMOKE_SCALE * 4.1F, y * SMOKE_SCALE * 4.1F + warpX, seed + 43) * 0.35F;

        int color = ARGB.srgbLerp(0.45F * smoke + 0.1F, DEEP_COLOR, MID_COLOR);
        color = ARGB.srgbLerp(light * (0.5F + 0.5F * smoke), color, litColor);

        if (hash(x, y, seed) < STAR_CHANCE)
            color = ARGB.srgbLerp((light > 0 ? 0.55F : 0.14F) * (0.4F + hash(y, x, seed) * 0.6F), color, STAR_COLOR);

        if (wall < INNER_SHADOW)
            color = ARGB.srgbLerp((1 - wall / INNER_SHADOW) * 0.85F, color, DEEP_COLOR);
        return color;
    }

    private static float hash(int x, int y, long seed)
    {
        long h = x * 73856093L ^ y * 19349663L ^ seed * 83492791L;
        h ^= h >>> 13;
        h *= 0x5BD1E995L;
        h ^= h >>> 15;
        return (h & 0xFFFFF) / (float) 0x100000;
    }

    private static float smooth(float x, float y, long seed)
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
