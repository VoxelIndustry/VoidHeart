package net.voxelindustry.voidheart.client.render.mausoleum;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.common.content.mausoleum.SarcophagusBlock;
import net.voxelindustry.voidheart.common.content.mausoleum.SarcophagusTile;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The lid of a sarcophagus, drawn by its middle block over the whole length of the chest: two halves of polished stone meeting down the middle, a
 * raised bronze mask at the head and bronze arms crossed over the breast. Once the rider is dead a green light runs down the crack between them,
 * then the halves slide apart and tip off either side onto the dais, baring the chest and what lies in it.
 * <p>
 * Local space runs along the chest: {@code z} from the foot to the head, {@code x} across, in blocks from the middle block's corner.
 * Per frame: two halves of a few boxes each, the crack's glow, and the item inside.
 */
public class SarcophagusRender implements BlockEntityRenderer<SarcophagusTile, SarcophagusRender.State>
{
    private static final Identifier STONE = Identifier.fromNamespaceAndPath(MODID, "block/voidstone_polished");
    private static final Identifier BRONZE = Identifier.fromNamespaceAndPath(MODID, "block/bronze_inlaid_voidstone");
    private static final RenderType TYPE = RenderTypes.entitySolid(TextureAtlas.LOCATION_BLOCKS);

    private static final float PX = 1 / 16F;
    private static final float LID_BOTTOM = 11 * PX, LID_TOP = 14 * PX;
    private static final float LID_FOOT = -1 + PX, LID_HEAD = 2 - PX;
    private static final float LID_SIDE = PX, LID_MIDDLE = 0.5F;
    private static final float MASK_HALF = 4 * PX, MASK_LENGTH = 6 * PX, MASK_HEIGHT = 2 * PX;
    private static final float ARMS_HALF = 5 * PX, ARMS_AT = 0.9F, ARMS_LENGTH = 2 * PX, ARMS_HEIGHT = PX;

    /** How far each half slides out, how far it tips and how far it falls once the lid has split. */
    private static final float SLIDE = 0.62F;
    private static final float TIP_DEGREES = 72;
    private static final float DROP = 0.42F;
    private static final float SHAKE = 0.012F;

    private static final int CRACK_RED = 140, CRACK_GREEN = 255, CRACK_BLUE = 70, CRACK_ALPHA = 230;
    private static final float CRACK_HALF_WIDTH = 0.6F * PX;
    /** It lies in the chest, on the dark of its hollow. */
    private static final float ITEM_Y = 11.4F * PX;
    private static final float ITEM_SCALE = 0.75F;
    private static final int VIEW_DISTANCE = 96;

    private final ItemModelResolver itemModelResolver;

    public static class State extends BlockEntityRenderState
    {
        final ItemStackRenderState contents = new ItemStackRenderState();
        float headYaw;
        /** 0 whole, then how far the crack has spread, 0 to 1. */
        float crack;
        /** How far the halves have parted, 0 to 1. */
        float split;
        float shake;
    }

    public SarcophagusRender(BlockEntityRendererProvider.Context context)
    {
        itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(SarcophagusTile sarcophagus, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(sarcophagus, state, partialTicks, cameraPosition, breakProgress);
        var level = sarcophagus.getLevel();
        var head = sarcophagus.getBlockState().getValue(SarcophagusBlock.FACING);
        state.headYaw = (float) Math.toDegrees(Math.atan2(head.getStepX(), head.getStepZ()));
        double since = sarcophagus.sinceOpened(level.getGameTime() + (double) partialTicks);
        if (since < 0)
        {
            state.crack = 0;
            state.split = 0;
        }
        else
        {
            state.crack = Mth.clamp((float) since / SarcophagusTile.CRACK_TICKS, 0, 1);
            float split = Mth.clamp((float) (since - SarcophagusTile.CRACK_TICKS) / SarcophagusTile.SPLIT_TICKS, 0, 1);
            // Slow to give, then falling
            state.split = split * split;
        }
        state.shake = state.crack > 0 && state.split == 0 ? Mth.sin((float) since * 2.7F) * SHAKE * state.crack : 0;
        itemModelResolver.updateForTopItem(state.contents, sarcophagus.getContents(), ItemDisplayContext.FIXED, level, null, (int) sarcophagus.getBlockPos().asLong());
    }

    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }

    @Override
    public int getViewDistance()
    {
        return VIEW_DISTANCE;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        poseStack.rotateDegrees(Axis.YP, state.headYaw);
        poseStack.translate(-0.5, 0, -0.5);

        var stone = FuneraryFlames.sprite(STONE);
        var bronze = FuneraryFlames.sprite(BRONZE);
        for (int side = -1; side <= 1; side += 2)
        {
            int half = side;
            poseStack.pushPose();
            if (state.split > 0)
            {
                // Each half slides out over its own side and tips about its outer edge as it goes
                float outer = half < 0 ? LID_SIDE : 1 - LID_SIDE;
                poseStack.translate(half * SLIDE * state.split, -DROP * state.split, 0);
                poseStack.translate(outer, LID_BOTTOM, 0);
                poseStack.rotateDegrees(Axis.ZP, -half * TIP_DEGREES * state.split);
                poseStack.translate(-outer, -LID_BOTTOM, 0);
            }
            else
                poseStack.translate(half * state.shake, 0, 0);
            int light = state.lightCoords;
            collector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) -> half(pose, buffer, stone, bronze, half, light));
            poseStack.popPose();
        }

        if (state.crack > 0 && state.split < 1)
        {
            float alpha = CRACK_ALPHA * state.crack * (1 - state.split);
            int color = ARGB.color(Math.round(alpha), CRACK_RED, CRACK_GREEN, CRACK_BLUE);
            float reach = Mth.lerp(state.crack, 0.5F, LID_HEAD) - LID_FOOT;
            collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) ->
            {
                float start = Math.max(LID_FOOT, 0.5F - reach / 2);
                float end = Math.min(LID_HEAD, 0.5F + reach / 2);
                glow(pose, buffer, LID_MIDDLE, LID_TOP + 0.003F, start, end, color);
            });
        }

        if (!state.contents.isEmpty() && state.split > 0.5F)
        {
            poseStack.pushPose();
            poseStack.translate(0.5, ITEM_Y, 0.5);
            poseStack.rotateDegrees(Axis.XP, 90);
            poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            state.contents.submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    /** One half of the lid, with its share of the mask and the arms. */
    private static void half(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite stone, TextureAtlasSprite bronze, int side, int light)
    {
        float x0 = side < 0 ? LID_SIDE : LID_MIDDLE;
        float x1 = side < 0 ? LID_MIDDLE : 1 - LID_SIDE;
        box(pose, buffer, stone, x0, LID_BOTTOM, LID_FOOT, x1, LID_TOP, LID_HEAD, light);
        float maskX0 = side < 0 ? 0.5F - MASK_HALF : LID_MIDDLE;
        float maskX1 = side < 0 ? LID_MIDDLE : 0.5F + MASK_HALF;
        box(pose, buffer, bronze, maskX0, LID_TOP, LID_HEAD - MASK_LENGTH - PX, maskX1, LID_TOP + MASK_HEIGHT, LID_HEAD - PX, light);
        float armsX0 = side < 0 ? 0.5F - ARMS_HALF : LID_MIDDLE;
        float armsX1 = side < 0 ? LID_MIDDLE : 0.5F + ARMS_HALF;
        box(pose, buffer, bronze, armsX0, LID_TOP, ARMS_AT, armsX1, LID_TOP + ARMS_HEIGHT, ARMS_AT + ARMS_LENGTH, light);
    }

    /**
     * A box whose faces take the sprite as a block's would, by where they lie: each face is cut at block edges so the texture repeats along it.
     */
    private static void box(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, float x0, float y0, float z0, float x1, float y1, float z1, int light)
    {
        // Up and down, cut along z
        for (float z = z0; z < z1; z = Math.min(z1, (float) Math.floor(z) + 1))
        {
            float zEnd = Math.min(z1, (float) Math.floor(z) + 1);
            quad(pose, buffer, sprite, light, 0, 1, 0,
                    x0, y1, zEnd, x1, y1, zEnd, x1, y1, z, x0, y1, z, x0, z, x1, zEnd);
            quad(pose, buffer, sprite, light, 0, -1, 0,
                    x0, y0, z, x1, y0, z, x1, y0, zEnd, x0, y0, zEnd, x0, z, x1, zEnd);
            quad(pose, buffer, sprite, light, -1, 0, 0,
                    x0, y0, z, x0, y0, zEnd, x0, y1, zEnd, x0, y1, z, z, y0, zEnd, y1);
            quad(pose, buffer, sprite, light, 1, 0, 0,
                    x1, y0, zEnd, x1, y0, z, x1, y1, z, x1, y1, zEnd, z, y0, zEnd, y1);
        }
        quad(pose, buffer, sprite, light, 0, 0, -1,
                x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, x0, y0, x1, y1);
        quad(pose, buffer, sprite, light, 0, 0, 1,
                x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, x1, y1);
    }

    /**
     * Four corners counter-clockwise from outside, and the span of local coordinates the sprite is read across, by their place within a block.
     */
    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int light, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                             float s0, float t0, float s1, float t1)
    {
        float u0 = sprite.getU(frac(s0)), u1 = sprite.getU(frac(s0) + (s1 - s0));
        float v0 = sprite.getV(1 - frac(t0) - (t1 - t0)), v1 = sprite.getV(1 - frac(t0));
        vertex(pose, buffer, ax, ay, az, u0, v1, nx, ny, nz, light);
        vertex(pose, buffer, bx, by, bz, u1, v1, nx, ny, nz, light);
        vertex(pose, buffer, cx, cy, cz, u1, v0, nx, ny, nz, light);
        vertex(pose, buffer, dx, dy, dz, u0, v0, nx, ny, nz, light);
    }

    private static float frac(float value)
    {
        return value - (float) Math.floor(value);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, float nx, float ny, float nz, int light)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    /** A thin strip of light along the crack, lying on the lid. */
    private static void glow(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z0, float z1, int color)
    {
        buffer.addVertex(pose, x - CRACK_HALF_WIDTH, y, z1).setColor(color);
        buffer.addVertex(pose, x + CRACK_HALF_WIDTH, y, z1).setColor(color);
        buffer.addVertex(pose, x + CRACK_HALF_WIDTH, y, z0).setColor(color);
        buffer.addVertex(pose, x - CRACK_HALF_WIDTH, y, z0).setColor(color);
    }
}
