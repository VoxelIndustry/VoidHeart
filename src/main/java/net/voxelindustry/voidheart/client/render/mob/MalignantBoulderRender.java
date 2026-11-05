package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.model.mob.MalignantBoulderModel;
import net.voxelindustry.voidheart.client.render.state.MalignantBoulderRenderState;
import net.voxelindustry.voidheart.common.content.mob.MalignantBoulder;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Magic striking the Boulder lights a shell of its own runes, spreading out from the impact across a blocky hull around the body and dying away behind the front.
 * The hull is convex and drawn with back faces culled and no depth writes, so its tiles can never overlap each other or flicker, and it draws nothing between flashes.
 */
public class MalignantBoulderRender extends MobRenderer<MalignantBoulder, MalignantBoulderRenderState, MalignantBoulderModel>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/malignant_boulder.png");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath(MODID, "textures/entity/malignant_boulder_glow.png");
    private static final Identifier WARD = Identifier.fromNamespaceAndPath(MODID, "textures/entity/malignant_boulder_ward.png");
    private static final RenderType WARD_TYPE = RenderTypes.eyes(WARD);

    private static final float HALF_WIDTH = 1.5F;
    private static final float HALF_DEPTH = 0.85F;
    private static final float HEIGHT = 3.05F;
    private static final float TILE = 0.4F;
    private static final float TILE_GAP = 0.03F;
    private static final int ATLAS_CELLS = 4;
    private static final float SPREAD_TICKS = 8;
    private static final float SPREAD_JITTER = 2.5F;
    private static final float TILE_LIFE_TICKS = 7;
    private static final float FAR_CORNER = (float) Math.sqrt(2 * HALF_WIDTH * 2 * HALF_WIDTH + 2 * HALF_DEPTH * 2 * HALF_DEPTH + HEIGHT * HEIGHT);

    public MalignantBoulderRender(EntityRendererProvider.Context context)
    {
        super(context, new MalignantBoulderModel(context.bakeLayer(MalignantBoulderModel.LAYER)), 1.2F);
        addLayer(new VoidGlowLayer<>(this, GLOW));
    }

    @Override
    public MalignantBoulderRenderState createRenderState()
    {
        return new MalignantBoulderRenderState();
    }

    @Override
    public Identifier getTextureLocation(MalignantBoulderRenderState state)
    {
        return TEXTURE;
    }

    @Override
    public void extractRenderState(MalignantBoulder boulder, MalignantBoulderRenderState state, float partialTicks)
    {
        super.extractRenderState(boulder, state, partialTicks);

        int slam = boulder.slamTicks();
        state.slam = slam > 0 ? slam + partialTicks : 0;
        state.breaking = boulder.isBreaking();

        float age = boulder.level().getGameTime() - boulder.wardTick() + partialTicks;
        state.wardAge = age >= 0 && age < MalignantBoulder.WARD_TICKS ? age : -1;
        if (state.wardAge >= 0)
        {
            // Into the frame of the body, which the hull turns with
            var from = boulder.wardFrom();
            float yaw = state.bodyRot * Mth.DEG_TO_RAD;
            float sin = Mth.sin(yaw);
            float cos = Mth.cos(yaw);
            state.wardX = (float) (from.x * cos + from.z * sin);
            state.wardY = (float) from.y;
            state.wardZ = (float) (-from.x * sin + from.z * cos);
        }
    }

    @Override
    public void submit(MalignantBoulderRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        super.submit(state, poseStack, collector, camera);

        if (state.wardAge < 0 || state.isInvisible)
            return;

        float age = state.wardAge;
        float fromX = state.wardX;
        float fromY = state.wardY;
        float fromZ = state.wardZ;
        poseStack.pushPose();
        poseStack.rotate(Axis.YP.rotationDegrees(-state.bodyRot));
        collector.submitCustomGeometry(poseStack, WARD_TYPE, (pose, buffer) -> submitHull(pose, buffer, age, fromX, fromY, fromZ));
        poseStack.popPose();
    }

    private static void submitHull(PoseStack.Pose pose, VertexConsumer buffer, float age, float fromX, float fromY, float fromZ)
    {
        int columns = Mth.ceil(2 * HALF_WIDTH / TILE);
        int depths = Mth.ceil(2 * HALF_DEPTH / TILE);
        int rows = Mth.ceil(HEIGHT / TILE);
        float tileX = 2 * HALF_WIDTH / columns;
        float tileZ = 2 * HALF_DEPTH / depths;
        float tileY = HEIGHT / rows;

        for (int row = 0; row < rows; row++)
        {
            float y0 = row * tileY;
            float y1 = y0 + tileY;
            for (int column = 0; column < columns; column++)
            {
                float a = -HALF_WIDTH + column * tileX;
                float b = a + tileX;
                float mid = (a + b) / 2;
                float yMid = (y0 + y1) / 2;
                int front = alpha(age, row * 31 + column, mid, yMid, -HALF_DEPTH, fromX, fromY, fromZ);
                if (front > 0)
                    tile(pose, buffer, front, row * 7 + column, b, y0, -HALF_DEPTH, a, y0, -HALF_DEPTH, a, y1, -HALF_DEPTH, b, y1, -HALF_DEPTH, 0, 0, -1);
                int back = alpha(age, row * 37 + column * 3, mid, yMid, HALF_DEPTH, fromX, fromY, fromZ);
                if (back > 0)
                    tile(pose, buffer, back, row * 5 + column * 3, a, y0, HALF_DEPTH, b, y0, HALF_DEPTH, b, y1, HALF_DEPTH, a, y1, HALF_DEPTH, 0, 0, 1);
            }
            for (int depth = 0; depth < depths; depth++)
            {
                float a = -HALF_DEPTH + depth * tileZ;
                float b = a + tileZ;
                float mid = (a + b) / 2;
                float yMid = (y0 + y1) / 2;
                int right = alpha(age, row * 41 + depth * 5, HALF_WIDTH, yMid, mid, fromX, fromY, fromZ);
                if (right > 0)
                    tile(pose, buffer, right, row * 3 + depth, HALF_WIDTH, y0, b, HALF_WIDTH, y0, a, HALF_WIDTH, y1, a, HALF_WIDTH, y1, b, 1, 0, 0);
                int left = alpha(age, row * 43 + depth * 7, -HALF_WIDTH, yMid, mid, fromX, fromY, fromZ);
                if (left > 0)
                    tile(pose, buffer, left, row * 11 + depth, -HALF_WIDTH, y0, a, -HALF_WIDTH, y0, b, -HALF_WIDTH, y1, b, -HALF_WIDTH, y1, a, -1, 0, 0);
            }
        }

        for (int column = 0; column < columns; column++)
        {
            float a = -HALF_WIDTH + column * tileX;
            float b = a + tileX;
            for (int depth = 0; depth < depths; depth++)
            {
                float c = -HALF_DEPTH + depth * tileZ;
                float d = c + tileZ;
                int top = alpha(age, column * 47 + depth * 11, (a + b) / 2, HEIGHT, (c + d) / 2, fromX, fromY, fromZ);
                if (top > 0)
                    tile(pose, buffer, top, column * 13 + depth, a, HEIGHT, c, a, HEIGHT, d, b, HEIGHT, d, b, HEIGHT, c, 0, 1, 0);
            }
        }
    }

    /**
     * Lit once the front spreading from the impact has reached it, a little early or late so that the edge never looks ruled, and fading from there.
     */
    private static int alpha(float age, int seed, float x, float y, float z, float fromX, float fromY, float fromZ)
    {
        float distance = Mth.sqrt((x - fromX) * (x - fromX) + (y - fromY) * (y - fromY) + (z - fromZ) * (z - fromZ));
        float arrives = distance / FAR_CORNER * SPREAD_TICKS + (noise(seed) - 0.5F) * SPREAD_JITTER;
        float since = age - Math.max(0, arrives);
        if (since < 0 || since >= TILE_LIFE_TICKS)
            return 0;
        return (int) (255 * (1 - since / TILE_LIFE_TICKS));
    }

    private static float noise(int seed)
    {
        return Mth.frac(Mth.sin(seed * 12.9898F) * 43758.5453F);
    }

    private static void tile(PoseStack.Pose pose, VertexConsumer buffer, int alpha, int glyphSeed,
                             float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
                             float normalX, float normalY, float normalZ)
    {
        int glyph = (glyphSeed * 7 + 3) & (ATLAS_CELLS * ATLAS_CELLS - 1);
        float u0 = (glyph % ATLAS_CELLS) / (float) ATLAS_CELLS;
        float v0 = (glyph / ATLAS_CELLS) / (float) ATLAS_CELLS;
        float cell = 1F / ATLAS_CELLS;
        int color = ARGB.color(alpha, 0xFFFFFF);

        // Pulled in slightly at each corner so that each tile keeps an edge of its own
        float shrink = TILE_GAP / TILE;
        float cx = (x0 + x1 + x2 + x3) / 4;
        float cy = (y0 + y1 + y2 + y3) / 4;
        float cz = (z0 + z1 + z2 + z3) / 4;
        vertex(pose, buffer, color, Mth.lerp(shrink, x0, cx), Mth.lerp(shrink, y0, cy), Mth.lerp(shrink, z0, cz), u0, v0 + cell, normalX, normalY, normalZ);
        vertex(pose, buffer, color, Mth.lerp(shrink, x1, cx), Mth.lerp(shrink, y1, cy), Mth.lerp(shrink, z1, cz), u0 + cell, v0 + cell, normalX, normalY, normalZ);
        vertex(pose, buffer, color, Mth.lerp(shrink, x2, cx), Mth.lerp(shrink, y2, cy), Mth.lerp(shrink, z2, cz), u0 + cell, v0, normalX, normalY, normalZ);
        vertex(pose, buffer, color, Mth.lerp(shrink, x3, cx), Mth.lerp(shrink, y3, cy), Mth.lerp(shrink, z3, cz), u0, v0, normalX, normalY, normalZ);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int color, float x, float y, float z, float u, float v, float normalX, float normalY, float normalZ)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, normalX, normalY, normalZ);
    }
}
