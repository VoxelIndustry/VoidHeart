package net.voxelindustry.voidheart.client.render.oculus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hologram color of every block state: the average color of the texture seen from above, tinted like in the world.
 * Entries are filled on the main thread, where models and textures can be read, and read by the mesh builders.
 */
final class OculusPalette
{
    /**
     * @param color base color, zero when the block is not part of the hologram
     * @param tint  biome dependent tint multiplied with the base color, if any
     */
    record Entry(int color, @Nullable BlockTintSource tint)
    {
        static final Entry EMPTY = new Entry(0, null);

        boolean filled()
        {
            return color != 0;
        }

        int colorAt(BlockState state, BlockAndTintGetter level, BlockPos pos)
        {
            return tint == null ? color : 0xFF000000 | ARGB.multiply(color, tint.colorInWorld(state, level, pos));
        }
    }

    private static final int WATER_SHADE = 0xFFC8C8C8;
    private static final int LAVA_COLOR = 0xFFD4590F;
    private static final BlockTintSource WATER_TINT = new BlockTintSource()
    {
        @Override
        public int color(BlockState state)
        {
            return 0xFF3F76E4;
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos)
        {
            return BiomeColors.getAverageWaterColor(level, pos);
        }
    };

    private static final Map<BlockState, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final Map<Identifier, Integer> SPRITE_AVERAGES = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private static @Nullable Object modelSet;

    private OculusPalette()
    {
    }

    /**
     * Forgets every color after a resource reload, as models and textures may have changed.
     *
     * @return whether colors were forgotten, so meshes built from them are stale
     */
    static boolean refresh()
    {
        var current = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        if (current == modelSet)
            return false;

        boolean stale = modelSet != null;
        modelSet = current;
        ENTRIES.clear();
        SPRITE_AVERAGES.clear();
        return stale;
    }

    /**
     * Computes the colors of every state a copied section holds. Main thread only.
     */
    static void ensure(PalettedContainer<BlockState> states)
    {
        states.count((state, count) -> ENTRIES.computeIfAbsent(state, OculusPalette::compute));
    }

    static Entry get(BlockState state)
    {
        return ENTRIES.getOrDefault(state, Entry.EMPTY);
    }

    @SuppressWarnings("deprecation")
    private static Entry compute(BlockState state)
    {
        if (!state.isAir() && state.isSolid())
            return blockEntry(state);

        var fluid = state.getFluidState();
        if (fluid.is(FluidTags.WATER))
            return new Entry(WATER_SHADE, WATER_TINT);
        if (fluid.is(FluidTags.LAVA))
            return new Entry(LAVA_COLOR, null);
        return Entry.EMPTY;
    }

    private static Entry blockEntry(BlockState state)
    {
        var quad = topQuad(state);
        if (quad == null)
        {
            var mapColor = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            return mapColor == MapColor.NONE ? Entry.EMPTY : new Entry(0xFF000000 | mapColor.col, null);
        }

        var material = quad.materialInfo();
        var tint = material.tintIndex() < 0 ? null : Minecraft.getInstance().getBlockColors().getTintSource(state, material.tintIndex());
        return new Entry(spriteAverage(material.sprite().contents()), tint);
    }

    /**
     * The quad seen from above, or any quad the model has.
     */
    private static @Nullable BakedQuad topQuad(BlockState state)
    {
        var parts = new ArrayList<BlockStateModelPart>();
        RANDOM.setSeed(42);
        Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state).collectParts(RANDOM, parts);

        var up = firstQuad(parts, Direction.UP);
        if (up != null)
            return up;
        var unculled = firstQuad(parts, null);
        if (unculled != null)
            return unculled;
        for (var direction : Direction.values())
        {
            var quad = firstQuad(parts, direction);
            if (quad != null)
                return quad;
        }
        return null;
    }

    private static @Nullable BakedQuad firstQuad(List<BlockStateModelPart> parts, @Nullable Direction direction)
    {
        for (var part : parts)
        {
            var quads = part.getQuads(direction);
            if (!quads.isEmpty())
                return quads.getFirst();
        }
        return null;
    }

    /**
     * Average of the opaque pixels of the sprite's first frame.
     */
    private static int spriteAverage(SpriteContents contents)
    {
        return SPRITE_AVERAGES.computeIfAbsent(contents.name(), name ->
        {
            var image = contents.originalImage;
            long red = 0, green = 0, blue = 0, opaque = 0;
            for (int y = 0; y < contents.height(); y++)
            {
                for (int x = 0; x < contents.width(); x++)
                {
                    int pixel = image.getPixel(x, y);
                    if (ARGB.alpha(pixel) < 128)
                        continue;
                    red += ARGB.red(pixel);
                    green += ARGB.green(pixel);
                    blue += ARGB.blue(pixel);
                    opaque++;
                }
            }
            if (opaque == 0)
                return 0xFFFFFFFF;
            return ARGB.color(255, (int) (red / opaque), (int) (green / opaque), (int) (blue / opaque));
        });
    }
}
