package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.works.OfficiantFolds;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The folds an Officiant opens over its station: a small flat diamond of void, rimmed in copper like the Fold's inlay, unfolds from a point on the
 * block, lets an item rise out of it or sink into it, and folds shut. A teal glint runs once around its rim while it is open. Laid items are the
 * station's own, raised by their renderers through {@link #rise}; taken and passed ones are drawn here.
 * <p>
 * The body is {@link VoidHeartRenderTypes#tint}, blended so it stays dark by day; the rim and glint are {@link VoidHeartRenderTypes#radiant}, added
 * without writing depth. All lie a hair above the block's top. Per frame: at most a rite's nine folds, three quads each and an item for some.
 */
public final class OfficiantFoldRender
{
    private static final Identifier BODY_TEXTURE  = Identifier.fromNamespaceAndPath(MODID, "textures/misc/reckoning_plate.png");
    private static final Identifier RIM_TEXTURE   = Identifier.fromNamespaceAndPath(MODID, "textures/misc/reckoning_rim.png");
    private static final Identifier GLINT_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/misc/ley_sight_mote.png");

    private static final RenderType BODY_TYPE  = VoidHeartRenderTypes.tint(BODY_TEXTURE);
    private static final RenderType RIM_TYPE   = VoidHeartRenderTypes.radiant(RIM_TEXTURE);
    private static final RenderType GLINT_TYPE = VoidHeartRenderTypes.radiant(GLINT_TEXTURE);

    private static final int   BODY_RGB    = 0x3A2C4C;
    private static final float BODY_ALPHA  = 0.95F;
    private static final int   RIM_RGB     = 0xA8603A;
    private static final float RIM_ALPHA   = 0.8F;
    private static final int   GLINT_RGB   = 0x7CE8D6;
    private static final float GLINT_ALPHA = 0.95F;
    private static final float GLINT_LENGTH = 0.11F;
    private static final float GLINT_HALF   = 0.028F;

    /** Corner to corner across the open fold, in blocks. */
    private static final float SIZE = 0.5F;
    /** How far above the block's top it lies, clear of its face. */
    private static final float LIFT = 0.012F;
    private static final float BODY_LIFT = LIFT;
    private static final float RIM_LIFT  = LIFT + 0.003F;
    private static final float GLINT_LIFT = LIFT + 0.006F;

    private static final long OPEN_MILLIS  = 250;
    private static final long MOVE_MILLIS  = 400;
    private static final long CLOSE_MILLIS = 250;
    private static final long PASS_OPEN_MILLIS  = 200;
    private static final long PASS_RISE_MILLIS  = 300;
    private static final long PASS_SINK_MILLIS  = 300;
    private static final long PASS_CLOSE_MILLIS = 200;
    private static final long JITTER_MILLIS = 50;
    private static final float TURN_JITTER  = 15 * Mth.DEG_TO_RAD;

    /** How far under its resting place an item starts its rise, or ends its sinking. */
    public static final float RISE_DROP   = 0.5F;
    public static final float RISE_SHRINK = 0.4F;

    /**
     * @param start when it begins opening, a little off the beat
     * @param turn  how far its diamond is turned from square to the block
     * @param glint the corner its glint starts from
     */
    private record Fold(BlockPos pos, float rest, ItemStack item, int kind, long start, float turn, int glint)
    {
        long duration()
        {
            return kind == OfficiantFolds.PASS ? PASS_OPEN_MILLIS + PASS_RISE_MILLIS + PASS_SINK_MILLIS + PASS_CLOSE_MILLIS
                    : OPEN_MILLIS + MOVE_MILLIS + CLOSE_MILLIS;
        }

        long open()
        {
            return kind == OfficiantFolds.PASS ? PASS_OPEN_MILLIS : OPEN_MILLIS;
        }

        long close()
        {
            return kind == OfficiantFolds.PASS ? PASS_CLOSE_MILLIS : CLOSE_MILLIS;
        }

        /**
         * @return how far the diamond has unfolded, 0 shut to 1 open
         */
        float openness(long now)
        {
            long elapsed = now - start;
            if (elapsed < 0)
                return 0;
            if (elapsed < open())
                return smooth(elapsed / (float) open());
            long closing = duration() - close();
            if (elapsed < closing)
                return 1;
            return 1 - smooth((elapsed - closing) / (float) close());
        }
    }

    private static final List<Fold> FOLDS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private OfficiantFoldRender()
    {
    }

    public static void receive(OfficiantFolds.Payload payload, ClientPlayNetworking.Context context)
    {
        long start = Util.getMillis() + RANDOM.nextInt((int) JITTER_MILLIS + 1);
        float turn = (RANDOM.nextFloat() * 2 - 1) * TURN_JITTER;
        FOLDS.add(new Fold(payload.pos(), payload.rest(), payload.item(), payload.kind(), start, turn, RANDOM.nextInt(4)));
    }

    public static void clear()
    {
        FOLDS.clear();
    }

    /**
     * @return how many folds of a kind are open or opening, for tests
     */
    public static int open(int kind)
    {
        long now = Util.getMillis();
        return (int) FOLDS.stream().filter(fold -> fold.kind() == kind && now < fold.start() + fold.duration()).count();
    }

    private static float smooth(float t)
    {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /**
     * @return how far the item a block shows has risen out of the fold laying it, 0 still hidden to 1 at rest; 1 when no fold lays it
     */
    public static float rise(BlockPos pos)
    {
        long now = Util.getMillis();
        for (int index = FOLDS.size() - 1; index >= 0; index--)
        {
            var fold = FOLDS.get(index);
            if (fold.kind() == OfficiantFolds.LAY && fold.pos().equals(pos) && now < fold.start() + fold.duration())
                return smooth((now - fold.start() - OPEN_MILLIS) / (float) MOVE_MILLIS);
        }
        return 1;
    }

    /**
     * Where a risen item stands under its resting place, and how small, for a rise between 0 and 1.
     */
    public static void applyRise(PoseStack poseStack, float rise)
    {
        poseStack.translate(0, -(1 - rise) * RISE_DROP, 0);
        float scale = 1 - RISE_SHRINK * (1 - rise);
        poseStack.scale(scale, scale, scale);
    }

    public static void collectSubmits(LevelRenderContext context)
    {
        long now = Util.getMillis();
        FOLDS.removeIf(fold -> now > fold.start() + fold.duration());
        if (FOLDS.isEmpty())
            return;
        var level = Minecraft.getInstance().level;
        if (level == null)
            return;

        var camera = context.levelState().cameraRenderState.pos;
        var body = new Layer();
        var rim = new Layer();
        var glint = new Layer();
        float worldTime = level.getGameTime() + context.levelState().cameraRenderState.cameraEntityPartialTicks;
        for (var fold : FOLDS)
        {
            float open = fold.openness(now);
            if (open <= 0)
                continue;
            var corners = corners(fold, open, camera);
            float y = (float) (fold.pos().getY() + 1 - camera.y);
            diamond(body, corners, y + BODY_LIFT, BODY_RGB, BODY_ALPHA * open);
            diamond(rim, corners, y + RIM_LIFT, RIM_RGB, RIM_ALPHA * open);

            // The glint runs once around the rim while it is open
            long elapsed = now - fold.start();
            long running = fold.duration() - fold.close();
            if (elapsed < running)
                glint(glint, corners, y + GLINT_LIFT, (fold.glint() + 4 * elapsed / (float) running) % 4, GLINT_ALPHA * open);

            if (!fold.item().isEmpty())
                item(context, level, fold, now, worldTime, camera);
        }
        var collector = context.submitNodeCollector();
        var poseStack = context.poseStack();
        body.submit(collector, poseStack, BODY_TYPE);
        rim.submit(collector, poseStack, RIM_TYPE);
        glint.submit(collector, poseStack, GLINT_TYPE);
    }

    /**
     * @return the diamond's four corners, x and z from the camera, turned by its own angle
     */
    private static float[][] corners(Fold fold, float open, Vec3 camera)
    {
        float half = SIZE / 2 * open;
        double centerX = fold.pos().getX() + 0.5;
        double centerZ = fold.pos().getZ() + 0.5;
        var corners = new float[4][];
        for (int corner = 0; corner < 4; corner++)
        {
            float angle = fold.turn() + corner * Mth.HALF_PI;
            corners[corner] = new float[] { (float) (centerX + Mth.cos(angle) * half - camera.x), (float) (centerZ + Mth.sin(angle) * half - camera.z) };
        }
        return corners;
    }

    private static void diamond(Layer layer, float[][] corners, float y, int rgb, float alpha)
    {
        int color = ARGB.color(Mth.clamp((int) (alpha * 255), 0, 255), rgb);
        layer.add(corners[0][0], y, corners[0][1], 0, 0, color);
        layer.add(corners[1][0], y, corners[1][1], 1, 0, color);
        layer.add(corners[2][0], y, corners[2][1], 1, 1, color);
        layer.add(corners[3][0], y, corners[3][1], 0, 1, color);
    }

    /**
     * @param along how far round the rim, in edges from its first corner
     */
    private static void glint(Layer layer, float[][] corners, float y, float along, float alpha)
    {
        int edge = (int) along;
        float t = along - edge;
        var from = corners[edge % 4];
        var to = corners[(edge + 1) % 4];
        float headX = Mth.lerp(t, from[0], to[0]);
        float headZ = Mth.lerp(t, from[1], to[1]);
        float dx = to[0] - from[0];
        float dz = to[1] - from[1];
        float length = Mth.sqrt(dx * dx + dz * dz);
        if (length < 1e-4F)
            return;
        dx /= length;
        dz /= length;
        float tailX = headX - dx * GLINT_LENGTH;
        float tailZ = headZ - dz * GLINT_LENGTH;
        float sideX = -dz * GLINT_HALF;
        float sideZ = dx * GLINT_HALF;
        int color = ARGB.color(Mth.clamp((int) (alpha * 255), 0, 255), GLINT_RGB);
        layer.add(tailX - sideX, y, tailZ - sideZ, 0, 0, color);
        layer.add(tailX + sideX, y, tailZ + sideZ, 0, 1, color);
        layer.add(headX + sideX, y, headZ + sideZ, 1, 1, color);
        layer.add(headX - sideX, y, headZ - sideZ, 1, 0, color);
    }

    /**
     * A taken item sinks into its fold; a passed one rises out of it, then sinks back.
     */
    private static void item(LevelRenderContext context, net.minecraft.client.multiplayer.ClientLevel level, Fold fold, long now, float worldTime, Vec3 camera)
    {
        long elapsed = now - fold.start();
        float risen;
        if (fold.kind() == OfficiantFolds.PASS)
        {
            long risingEnd = PASS_OPEN_MILLIS + PASS_RISE_MILLIS;
            risen = elapsed < risingEnd ? smooth((elapsed - PASS_OPEN_MILLIS) / (float) PASS_RISE_MILLIS)
                    : 1 - smooth((elapsed - risingEnd) / (float) PASS_SINK_MILLIS);
        }
        else
            risen = 1 - smooth((elapsed - OPEN_MILLIS) / (float) MOVE_MILLIS);
        if (risen <= 0)
            return;

        var state = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(state, fold.item(), ItemDisplayContext.GROUND, level, null, (int) fold.pos().asLong());
        if (state.isEmpty())
            return;
        var poseStack = context.poseStack();
        poseStack.pushPose();
        poseStack.translate(fold.pos().getX() + 0.5 - camera.x, fold.pos().getY() + fold.rest() - camera.y, fold.pos().getZ() + 0.5 - camera.z);
        applyRise(poseStack, risen);
        poseStack.rotate(new Quaternionf().rotationY(-worldTime * Mth.DEG_TO_RAD));
        state.submit(poseStack, context.submitNodeCollector(), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    private static final class Layer
    {
        private final FloatArrayList vertices = new FloatArrayList();
        private final IntArrayList   colors   = new IntArrayList();

        void add(float x, float y, float z, float u, float v, int color)
        {
            vertices.add(x);
            vertices.add(y);
            vertices.add(z);
            vertices.add(u);
            vertices.add(v);
            colors.add(color);
        }

        void submit(SubmitNodeCollector collector, PoseStack poseStack, RenderType type)
        {
            if (!colors.isEmpty())
                collector.submitCustomGeometry(poseStack, type, this::emit);
        }

        private void emit(PoseStack.Pose pose, VertexConsumer buffer)
        {
            for (int i = 0; i < colors.size(); i++)
            {
                int at = i * 5;
                buffer.addVertex(pose, vertices.getFloat(at), vertices.getFloat(at + 1), vertices.getFloat(at + 2))
                        .setColor(colors.getInt(i))
                        .setUv(vertices.getFloat(at + 3), vertices.getFloat(at + 4))
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightCoordsUtil.FULL_BRIGHT)
                        .setNormal(pose, 0, 1, 0);
            }
        }
    }
}
