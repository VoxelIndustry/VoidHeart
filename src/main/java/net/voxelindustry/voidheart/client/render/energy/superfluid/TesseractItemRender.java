package net.voxelindustry.voidheart.client.render.energy.superfluid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.common.energy.superfluid.SuperfluidBalance;
import net.voxelindustry.voidheart.common.energy.superfluid.TesseractItem;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The Tesseract as an item, built like a block model on the sixteenth grid: an outer cube of voidstone edges with seething-steel corners, a small
 * steel cube nested in its middle, and each inner corner stepped out to its outer corner by two steel pixels. Every piece is an axis-aligned
 * cuboid and no two overlap, so faces never fight. The superfluid it holds glows inside the inner cube as two nested plasma cubes, larger the
 * fuller it is; an empty one is the bare frame.
 * <p>
 * The frame is {@link RenderTypes#entityCutout}, lit by the world, mapped a texture pixel to a model pixel from {@code frame.png}: voidstone in its
 * top half, seething steel in its bottom half. The plasma is {@link VoidHeartRenderTypes#radiant}.
 * <p>
 * Per draw: 56 cuboids (336 quads), two plasma cubes (12).
 */
public class TesseractItemRender implements SpecialModelRenderer<Float>
{
    private static final Identifier FRAME_TEXTURE  = Identifier.fromNamespaceAndPath(MODID, "textures/entity/tesseract/frame.png");
    private static final Identifier PLASMA_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/tesseract/plasma.png");

    private static final RenderType FRAME_TYPE  = RenderTypes.entityCutout(FRAME_TEXTURE);
    private static final RenderType PLASMA_TYPE = VoidHeartRenderTypes.radiant(PLASMA_TEXTURE);

    // Shape, in pixels
    /** The outer cube spans these, its bars this thick. */
    private static final int OUTER_LOW  = 1;
    private static final int OUTER_HIGH = 15;
    private static final int OUTER_BAR  = 2;
    /** The inner cube spans these, its bars one pixel thick, leaving it hollow from 6 to 10. */
    private static final int INNER_LOW  = 5;
    private static final int INNER_HIGH = 11;
    private static final int INNER_BAR  = 1;
    /** The plasma's half size in pixels with a thread of superfluid and brimming, and how far its halo stands out; it stays clear of the bars. */
    private static final float PLASMA_LEAST = 0.6F;
    private static final float PLASMA_MOST  = 1.5F;
    private static final float HALO_OUT     = 0.4F;

    /** Texture bands: voidstone above, seething steel below. */
    private static final int STONE = 0;
    private static final int STEEL = 8;

    // Color and light
    private static final int FRAME_COLOR  = 0xFFFFFFFF;
    private static final int PLASMA_COLOR = 0x9EECFF;
    private static final int PLASMA_ALPHA = 230;
    private static final int HALO_COLOR   = 0x3D7ED9;
    private static final int HALO_ALPHA   = 120;

    private static final SuperfluidQuads FRAME = frame();

    @Override
    public @Nullable Float extractArgument(ItemStack stack)
    {
        return TesseractItem.held(stack) / (float) SuperfluidBalance.TESSERACT_CAPACITY;
    }

    @Override
    public void submit(@Nullable Float fill, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords, boolean hasFoil,
                       int outlineColor)
    {
        collector.submitCustomGeometry(poseStack, FRAME_TYPE, (pose, buffer) -> FRAME.emit(pose, buffer, lightCoords));

        var share = fill == null ? 0 : Mth.clamp(fill, 0, 1);
        if (share <= 0)
            return;
        var half = Mth.lerp((float) Math.cbrt(share), PLASMA_LEAST, PLASMA_MOST);
        var plasma = new SuperfluidQuads();
        plasmaCube(plasma, half, ARGB.color(PLASMA_ALPHA, PLASMA_COLOR));
        plasmaCube(plasma, half + HALO_OUT, ARGB.color(HALO_ALPHA, HALO_COLOR));
        collector.submitCustomGeometry(poseStack, PLASMA_TYPE, (pose, buffer) -> plasma.emit(pose, buffer, LightCoordsUtil.FULL_BRIGHT));
    }

    private static void plasmaCube(SuperfluidQuads quads, float half, int color)
    {
        var h = half / 16;
        quads.box(new Vector3f(0.5F, 0.5F, 0.5F), new Vector3f(1, 0, 0), h, new Vector3f(0, 1, 0), h, new Vector3f(0, 0, 1), h, color);
    }

    /**
     * The frame never changes, so it is built once.
     */
    private static SuperfluidQuads frame()
    {
        var quads = new SuperfluidQuads();
        cube(quads, OUTER_LOW, OUTER_HIGH, OUTER_BAR, STONE);
        cube(quads, INNER_LOW, INNER_HIGH, INNER_BAR, STEEL);

        // Struts: one pixel stepping diagonally from each outer corner to the inner corner facing it
        var outerIn = OUTER_LOW + OUTER_BAR;
        for (var corner = 0; corner < 8; corner++)
        {
            for (var step = 0; step < INNER_LOW - outerIn; step++)
            {
                var min = new int[3];
                for (var axis = 0; axis < 3; axis++)
                    min[axis] = (corner >> axis & 1) == 0 ? outerIn + step : OUTER_HIGH - OUTER_BAR - 1 - step;
                cuboid(quads, min[0], min[1], min[2], min[0] + 1, min[1] + 1, min[2] + 1, STEEL);
            }
        }
        return quads;
    }

    /**
     * A cube's twelve edges as bars between its eight corners, the corners in seething steel and the bars in the given band.
     */
    private static void cube(SuperfluidQuads quads, int low, int high, int bar, int barBand)
    {
        int[] starts = {low, high - bar};
        for (var corner = 0; corner < 8; corner++)
        {
            var x = starts[corner & 1];
            var y = starts[corner >> 1 & 1];
            var z = starts[corner >> 2 & 1];
            cuboid(quads, x, y, z, x + bar, y + bar, z + bar, STEEL);
        }
        for (var axis = 0; axis < 3; axis++)
        {
            for (var side = 0; side < 4; side++)
            {
                var min = new int[3];
                var max = new int[3];
                min[axis] = low + bar;
                max[axis] = high - bar;
                var a = (axis + 1) % 3;
                var b = (axis + 2) % 3;
                min[a] = starts[side & 1];
                min[b] = starts[side >> 1];
                max[a] = min[a] + bar;
                max[b] = min[b] + bar;
                cuboid(quads, min[0], min[1], min[2], max[0], max[1], max[2], barBand);
            }
        }
    }

    /**
     * An axis-aligned box in pixels, each face showing the texture a pixel to a pixel within its band, wound counter-clockwise from outside.
     */
    private static void cuboid(SuperfluidQuads quads, int x0, int y0, int z0, int x1, int y1, int z1, int band)
    {
        var a = new Vector3f(x0 / 16F, y0 / 16F, z0 / 16F);
        var b = new Vector3f(x1 / 16F, y1 / 16F, z1 / 16F);
        // Each face: its four corners from bottom left, counter-clockwise, its normal, and where it starts in the texture
        face(quads, v(b.x, a.y, b.z), v(b.x, a.y, a.z), v(b.x, b.y, a.z), v(b.x, b.y, b.z), v(1, 0, 0), z1 - z0, y1 - y0, 16 - z1, y0, band);
        face(quads, v(a.x, a.y, a.z), v(a.x, a.y, b.z), v(a.x, b.y, b.z), v(a.x, b.y, a.z), v(-1, 0, 0), z1 - z0, y1 - y0, z0, y0, band);
        face(quads, v(a.x, a.y, b.z), v(b.x, a.y, b.z), v(b.x, b.y, b.z), v(a.x, b.y, b.z), v(0, 0, 1), x1 - x0, y1 - y0, x0, y0, band);
        face(quads, v(b.x, a.y, a.z), v(a.x, a.y, a.z), v(a.x, b.y, a.z), v(b.x, b.y, a.z), v(0, 0, -1), x1 - x0, y1 - y0, 16 - x1, y0, band);
        face(quads, v(a.x, b.y, b.z), v(b.x, b.y, b.z), v(b.x, b.y, a.z), v(a.x, b.y, a.z), v(0, 1, 0), x1 - x0, z1 - z0, x0, z0, band);
        face(quads, v(a.x, a.y, a.z), v(b.x, a.y, a.z), v(b.x, a.y, b.z), v(a.x, a.y, b.z), v(0, -1, 0), x1 - x0, z1 - z0, x0, z0, band);
    }

    private static void face(SuperfluidQuads quads, Vector3f bottomLeft, Vector3f bottomRight, Vector3f topRight, Vector3f topLeft, Vector3f normal,
                             int width, int height, int u, int v, int band)
    {
        // Keep the face inside the texture and its band
        u = Mth.clamp(u, 0, 16 - width);
        v = band + Math.floorMod(v, 8 - height + 1);
        var u0 = u / 16F;
        var u1 = (u + width) / 16F;
        var v0 = v / 16F;
        var v1 = (v + height) / 16F;
        quads.vertex(bottomLeft, u0, v1, normal, FRAME_COLOR);
        quads.vertex(bottomRight, u1, v1, normal, FRAME_COLOR);
        quads.vertex(topRight, u1, v0, normal, FRAME_COLOR);
        quads.vertex(topLeft, u0, v0, normal, FRAME_COLOR);
    }

    private static Vector3f v(float x, float y, float z)
    {
        return new Vector3f(x, y, z);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output)
    {
        for (var x = 0; x < 2; x++)
            for (var y = 0; y < 2; y++)
                for (var z = 0; z < 2; z++)
                    output.accept(new Vector3f(x, y, z));
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked<Float>
    {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<Float> bake(SpecialModelRenderer.BakingContext context)
        {
            return new TesseractItemRender();
        }

        @Override
        public MapCodec<Unbaked> type()
        {
            return MAP_CODEC;
        }
    }
}
