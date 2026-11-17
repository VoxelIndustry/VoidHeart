package net.voxelindustry.voidheart.client.render.blight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The pale shimmer running along the edge of a void blight, drawn over its flat model in the hand and the world. The blade itself stays the black of its
 * sprite, so only this light shows where it ends. Each edge pixel is a glowing square on both faces of the sprite, lit by a slow wave from hilt to tip.
 */
public class BlightEdgeRender implements SpecialModelRenderer<Void>
{
    private static final RenderType TYPE  = VoidHeartRenderTypes.radiant(Identifier.fromNamespaceAndPath(MODID, "textures/entity/blight/edge.png"));
    private static final int        COLOR = 0xD9C8FF;

    /** How lit the edge is between waves, and at a wave's crest. */
    private static final float BASE        = 0.22F;
    private static final float PEAK        = 0.78F;
    /** Waves a second, and how many fit along the blade at once. */
    private static final float WAVES_PER_S = 0.45F;
    private static final float ALONG       = 0.8F;
    /** How sharp a crest is: higher keeps the light to a narrow band. */
    private static final float SHARPNESS   = 5;
    /** The faces of a sprite's extruded model, and how far off them the light sits. */
    private static final float FRONT       = 8.5F / 16;
    private static final float BACK        = 7.5F / 16;
    private static final float LIFT        = 0.0015F;

    /**
     * Edge pixels of a blade's sprite, from the hilt to the tip.
     *
     * @param x     pixel column
     * @param y     pixel row, from the top
     * @param along from 0 at the hilt to 1 at the tip
     */
    public record Pixel(int x, int y, float along)
    {
    }

    private static final Map<String, List<Pixel>> BLADES = Map.of(
            "rapier", List.of(
                    new Pixel(8, 8, 0), new Pixel(9, 7, 0.12F), new Pixel(10, 6, 0.25F), new Pixel(11, 5, 0.37F), new Pixel(12, 4, 0.5F),
                    new Pixel(13, 3, 0.62F), new Pixel(14, 2, 0.75F), new Pixel(15, 1, 0.87F), new Pixel(15, 0, 1), new Pixel(14, 0, 0.95F)),
            // The inner curve of the blade, from the haft to its point
            "scythe", List.of(
                    new Pixel(10, 3, 0), new Pixel(9, 3, 0.09F), new Pixel(8, 3, 0.18F), new Pixel(7, 3, 0.27F), new Pixel(6, 3, 0.36F), new Pixel(5, 3, 0.45F),
                    new Pixel(4, 4, 0.55F), new Pixel(3, 4, 0.62F), new Pixel(2, 5, 0.72F), new Pixel(1, 6, 0.82F), new Pixel(1, 7, 0.9F), new Pixel(0, 8, 1)));

    private final List<Pixel> pixels;

    public BlightEdgeRender(List<Pixel> pixels)
    {
        this.pixels = pixels;
    }

    @Override
    public void submit(@Nullable Void argument, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor)
    {
        float seconds = Util.getMillis() / 1000F;
        collector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) ->
        {
            for (var pixel : pixels)
            {
                float phase = seconds * WAVES_PER_S - pixel.along() * ALONG;
                float crest = (float) Math.pow(Math.max(0, Mth.cos((phase - Mth.floor(phase)) * Mth.TWO_PI)), SHARPNESS);
                int color = ARGB.color((int) (255 * Mth.clamp(BASE + PEAK * crest, 0, 1)), COLOR);
                float x0 = pixel.x() / 16F, x1 = (pixel.x() + 1) / 16F;
                float y0 = 1 - (pixel.y() + 1) / 16F, y1 = 1 - pixel.y() / 16F;
                square(pose, buffer, x0, x1, y0, y1, FRONT + LIFT, color);
                square(pose, buffer, x0, x1, y0, y1, BACK - LIFT, color);
            }
        });
    }

    private static void square(PoseStack.Pose pose, VertexConsumer buffer, float x0, float x1, float y0, float y1, float z, int color)
    {
        vertex(pose, buffer, x0, y0, z, 0, 1, color);
        vertex(pose, buffer, x1, y0, z, 1, 1, color);
        vertex(pose, buffer, x1, y1, z, 1, 0, color);
        vertex(pose, buffer, x0, y1, z, 0, 0, color);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int color)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0, 0, 1);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output)
    {
        for (var x = 0; x < 2; x++)
            for (var y = 0; y < 2; y++)
                for (var z = 0; z < 2; z++)
                    output.accept(new Vector3f(x, y, z == 0 ? BACK : FRONT));
    }

    @Override
    public @Nullable Void extractArgument(ItemStack stack)
    {
        return null;
    }

    /** @param blade which blade's edge pixels to light */
    public record Unbaked(String blade) implements SpecialModelRenderer.Unbaked<Void>
    {
        public static final MapCodec<Unbaked> MAP_CODEC = Codec.STRING.fieldOf("blade").xmap(Unbaked::new, Unbaked::blade);

        @Override
        public SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext context)
        {
            return new BlightEdgeRender(BLADES.getOrDefault(blade, List.of()));
        }

        @Override
        public MapCodec<Unbaked> type()
        {
            return MAP_CODEC;
        }
    }
}
