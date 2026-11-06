package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.util.MathUtil;
import net.voxelindustry.voidheart.common.content.research.KnowledgeRepositoryBlock;
import net.voxelindustry.voidheart.common.content.research.KnowledgeRepositoryStatus;
import net.voxelindustry.voidheart.common.content.research.KnowledgeRepositoryTile;
import org.jspecify.annotations.Nullable;

/**
 * A look is enough to tell what a repository is doing: the tablet lights up, a bar shows the pool and a ring around an icon shows the running research.
 * Upright like a sign and turned only around Y, so that the ring stays where the glyphs rising from the tablet are heading.
 * MARKER keeps the hues readable against the sky, and its quads do not overlap: each arc and bar stops where the next begins, and the plate behind sits a little further back.
 * At most about 50 quads and one item per repository in range.
 */
public class KnowledgeRepositoryRender implements BlockEntityRenderer<KnowledgeRepositoryTile, KnowledgeRepositoryRender.State>
{
    private static final int KNOWLEDGE_RGB = 0x36C5B0;
    private static final int BRIMMING_RGB  = 0x7CE8D6;
    private static final int RESEARCH_RGB  = 0xF4D670;
    private static final int LACK_RGB      = 0xEF8A98;
    private static final int TRACK_RGB     = 0x4A3860;
    private static final int PLATE_RGB     = 0x08050C;

    private static final float PLATE_ALPHA   = 0.6F;
    private static final float TRACK_ALPHA   = 0.8F;
    private static final float STARVED_RING  = 0.5F;
    private static final float LACK_PULSE    = 0.12F;
    private static final float PLATE_DEPTH   = 0.012F;
    private static final int   RING_SEGMENTS = 24;
    private static final int   PLATE_SIDES   = 12;

    private static final float BAR_HEIGHT     = 1.38F;
    private static final float BAR_HALF_WIDTH = 0.26F;
    private static final float BAR_HALF_THICK = 0.022F;
    private static final float BAR_BORDER     = 0.01F;

    private static final float RING_OUTER  = 0.175F;
    private static final float RING_INNER  = 0.15F;
    private static final float PLATE_RIM   = 0.015F;
    private static final float ICON_SCALE  = 0.2F;
    private static final float FLARE_REACH = 0.22F;
    private static final float FLARE_WIDTH = 0.05F;
    private static final float FLARE_ALPHA = 0.7F;

    private static final float FADE_NEAR  = 18;
    private static final float FADE_FAR   = 24;
    private static final int   VIEW_RANGE = 32;

    // Where the tablet lies in the model, in sixteenths: a slab tilted around the center of the block
    private static final float TABLET_TILT    = -22.5F;
    private static final float TABLET_X0      = 1.5F / 16;
    private static final float TABLET_X1      = 14.5F / 16;
    private static final float TABLET_Z0      = 5 / 16F;
    private static final float TABLET_Z1      = 14 / 16F;
    private static final float TABLET_SURFACE = 17.04F / 16;

    private static final float TABLET_GATHERING   = 0.28F;
    private static final float TABLET_RESEARCHING = 0.22F;
    private static final float TABLET_STARVED     = 0.22F;
    private static final float TABLET_FULL_LOW    = 0.2F;
    private static final float TABLET_FULL_HIGH   = 0.5F;
    private static final float TABLET_FULL_PULSE  = 0.07F;
    private static final float TABLET_EDGE        = 0.4F;
    private static final float TABLET_CORNER      = 0.12F;

    public static class State extends BlockEntityRenderState
    {
        public final ItemStackRenderState icon = new ItemStackRenderState();

        public KnowledgeRepositoryStatus status;
        public Direction facing;
        public float yaw;
        public float time;
        public float hud;
        public float ring;
        public float progress;
        public float fill;
        public float flare;
        public float tablet;
        public int tabletColor;
    }

    private final ItemModelResolver itemModelResolver;

    public KnowledgeRepositoryRender(BlockEntityRendererProvider.Context context)
    {
        itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(KnowledgeRepositoryTile tile, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);

        var display = tile.getDisplay();
        var center = Vec3.atBottomCenterOf(tile.getBlockPos());
        double dx = cameraPosition.x - center.x;
        double dz = cameraPosition.z - center.z;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);

        state.status = tile.getStatus();
        state.facing = tile.getBlockState().getValue(KnowledgeRepositoryBlock.FACING);
        state.yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        state.time = tile.getLevel().getGameTime() + partialTicks;
        state.hud = display.getHud().at(partialTicks) * Mth.clamp((FADE_FAR - distance) / (FADE_FAR - FADE_NEAR), 0, 1);
        state.ring = display.getRing().at(partialTicks);
        state.progress = Mth.clamp(display.getProgress().at(partialTicks), 0, 1);
        state.fill = Mth.clamp(display.getFill().at(partialTicks), 0, 1);
        state.flare = display.getFlare().at(partialTicks);
        state.tablet = display.getHud().at(partialTicks) * tabletGlow(state.status, state.time);
        state.tabletColor = ARGB.srgbLerp(state.ring, KNOWLEDGE_RGB, RESEARCH_RGB);

        var stack = display.getIcon().map(id -> new ItemStack(BuiltInRegistries.ITEM.getValue(id))).orElse(ItemStack.EMPTY);
        itemModelResolver.updateForTopItem(state.icon, stack, ItemDisplayContext.GUI, tile.getLevel(), null, 0);
    }

    private static float tabletGlow(KnowledgeRepositoryStatus status, float time)
    {
        return switch (status)
        {
            case DORMANT -> 0;
            case GATHERING -> TABLET_GATHERING;
            case FULL -> Mth.lerp(0.5F + 0.5F * Mth.sin(time * TABLET_FULL_PULSE), TABLET_FULL_LOW, TABLET_FULL_HIGH);
            case RESEARCHING -> TABLET_RESEARCHING;
            case STARVED, STRAINED -> TABLET_STARVED;
        };
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.tablet > 0)
            submitTablet(state, poseStack, collector);
        if (state.hud <= 0)
            return;

        poseStack.pushPose();
        poseStack.translate(0.5F, 0, 0.5F);
        poseStack.rotate(MathUtil.quatFromAngleDegrees(state.yaw, MathUtil.POSITIVE_Y));

        collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.MARKER, (pose, buffer) ->
        {
            bar(state, pose, buffer);
            if (state.ring > 0)
                ring(state, pose, buffer);
        });

        if (state.flare > 0)
            collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) -> flare(state, pose, buffer));

        if (state.ring > 0 && !state.icon.isEmpty())
        {
            float scale = ICON_SCALE * state.ring * state.hud;
            poseStack.translate(0, KnowledgeRepositoryTile.RING_HEIGHT, 0);
            poseStack.scale(scale, scale, scale);
            state.icon.submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        }
        poseStack.popPose();
    }

    private static void submitTablet(State state, PoseStack poseStack, SubmitNodeCollector collector)
    {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        // The block model turns clockwise from north as the facing goes around
        poseStack.rotate(MathUtil.quatFromAngleDegrees(180 - state.facing.toYRot(), MathUtil.POSITIVE_Y));
        poseStack.rotate(MathUtil.quatFromAngleDegrees(TABLET_TILT, MathUtil.POSITIVE_X));
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) -> tablet(state, pose, buffer));
        poseStack.popPose();
    }

    /** Four cells meeting at the center, bright there and fading out, so that it glows from within rather than lighting a rectangle. */
    private static void tablet(State state, PoseStack.Pose pose, VertexConsumer buffer)
    {
        float midX = (TABLET_X0 + TABLET_X1) / 2;
        float midZ = (TABLET_Z0 + TABLET_Z1) / 2;
        for (int cell = 0; cell < 4; cell++)
        {
            float x = (cell & 1) == 0 ? TABLET_X0 : TABLET_X1;
            float z = (cell & 2) == 0 ? TABLET_Z0 : TABLET_Z1;
            tabletVertex(state, pose, buffer, midX, midZ, 1);
            tabletVertex(state, pose, buffer, x, midZ, TABLET_EDGE);
            tabletVertex(state, pose, buffer, x, z, TABLET_CORNER);
            tabletVertex(state, pose, buffer, midX, z, TABLET_EDGE);
        }
    }

    private static void tabletVertex(State state, PoseStack.Pose pose, VertexConsumer buffer, float x, float z, float strength)
    {
        buffer.addVertex(pose, x, TABLET_SURFACE, z).setColor(color(state.tabletColor, state.tablet * strength));
    }

    private static void bar(State state, PoseStack.Pose pose, VertexConsumer buffer)
    {
        float left = -BAR_HALF_WIDTH;
        float right = BAR_HALF_WIDTH;
        float bottom = BAR_HEIGHT - BAR_HALF_THICK;
        float top = BAR_HEIGHT + BAR_HALF_THICK;
        float split = Mth.lerp(state.fill, left, right);

        quad(pose, buffer, left - BAR_BORDER, bottom - BAR_BORDER, right + BAR_BORDER, top + BAR_BORDER, -PLATE_DEPTH, color(PLATE_RGB, PLATE_ALPHA * state.hud));

        int filled = state.status == KnowledgeRepositoryStatus.FULL ? BRIMMING_RGB : KNOWLEDGE_RGB;
        quad(pose, buffer, left, bottom, split, top, 0, color(filled, state.hud));

        int track = TRACK_RGB;
        if (state.status == KnowledgeRepositoryStatus.STARVED || state.status == KnowledgeRepositoryStatus.STRAINED)
            track = ARGB.srgbLerp(0.5F + 0.5F * Mth.sin(state.time * LACK_PULSE), TRACK_RGB, LACK_RGB);
        quad(pose, buffer, split, bottom, right, top, 0, color(track, TRACK_ALPHA * state.hud));
    }

    private static void ring(State state, PoseStack.Pose pose, VertexConsumer buffer)
    {
        float alpha = state.hud * state.ring;
        float brightness = state.status == KnowledgeRepositoryStatus.STARVED || state.status == KnowledgeRepositoryStatus.STRAINED ? STARVED_RING : 1;
        float center = KnowledgeRepositoryTile.RING_HEIGHT;

        for (int side = 0; side < PLATE_SIDES; side++)
        {
            float from = Mth.TWO_PI * side / PLATE_SIDES;
            float to = Mth.TWO_PI * (side + 1) / PLATE_SIDES;
            int plate = color(PLATE_RGB, PLATE_ALPHA * alpha);
            buffer.addVertex(pose, 0, center, -PLATE_DEPTH).setColor(plate);
            buffer.addVertex(pose, Mth.sin(to) * (RING_OUTER + PLATE_RIM), center + Mth.cos(to) * (RING_OUTER + PLATE_RIM), -PLATE_DEPTH).setColor(plate);
            buffer.addVertex(pose, Mth.sin(from) * (RING_OUTER + PLATE_RIM), center + Mth.cos(from) * (RING_OUTER + PLATE_RIM), -PLATE_DEPTH).setColor(plate);
            buffer.addVertex(pose, 0, center, -PLATE_DEPTH).setColor(plate);
        }

        for (int segment = 0; segment < RING_SEGMENTS; segment++)
        {
            float start = segment / (float) RING_SEGMENTS;
            float end = (segment + 1) / (float) RING_SEGMENTS;
            if (state.progress >= end)
                arc(pose, buffer, center, start, end, RING_INNER, RING_OUTER, color(RESEARCH_RGB, alpha * brightness));
            else if (state.progress <= start)
                arc(pose, buffer, center, start, end, RING_INNER, RING_OUTER, color(TRACK_RGB, TRACK_ALPHA * alpha));
            else
            {
                arc(pose, buffer, center, start, state.progress, RING_INNER, RING_OUTER, color(RESEARCH_RGB, alpha * brightness));
                arc(pose, buffer, center, state.progress, end, RING_INNER, RING_OUTER, color(TRACK_RGB, TRACK_ALPHA * alpha));
            }
        }
    }

    private static void flare(State state, PoseStack.Pose pose, VertexConsumer buffer)
    {
        float reach = FLARE_REACH * (1 - state.flare);
        int color = color(RESEARCH_RGB, FLARE_ALPHA * state.flare * state.hud);
        for (int segment = 0; segment < RING_SEGMENTS; segment++)
            arc(pose, buffer, KnowledgeRepositoryTile.RING_HEIGHT, segment / (float) RING_SEGMENTS, (segment + 1) / (float) RING_SEGMENTS,
                    RING_INNER + reach, RING_OUTER + reach + FLARE_WIDTH, color);
    }

    /** Clockwise from the top, the angles as fractions of a turn. */
    private static void arc(PoseStack.Pose pose, VertexConsumer buffer, float center, float from, float to, float inner, float outer, int color)
    {
        float sinFrom = Mth.sin(from * Mth.TWO_PI);
        float cosFrom = Mth.cos(from * Mth.TWO_PI);
        float sinTo = Mth.sin(to * Mth.TWO_PI);
        float cosTo = Mth.cos(to * Mth.TWO_PI);
        buffer.addVertex(pose, sinFrom * outer, center + cosFrom * outer, 0).setColor(color);
        buffer.addVertex(pose, sinFrom * inner, center + cosFrom * inner, 0).setColor(color);
        buffer.addVertex(pose, sinTo * inner, center + cosTo * inner, 0).setColor(color);
        buffer.addVertex(pose, sinTo * outer, center + cosTo * outer, 0).setColor(color);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float x1, float y1, float z, int color)
    {
        buffer.addVertex(pose, x0, y1, z).setColor(color);
        buffer.addVertex(pose, x0, y0, z).setColor(color);
        buffer.addVertex(pose, x1, y0, z).setColor(color);
        buffer.addVertex(pose, x1, y1, z).setColor(color);
    }

    private static int color(int rgb, float alpha)
    {
        return ARGB.color(Math.round(Mth.clamp(alpha, 0, 1) * 255), rgb);
    }

    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }

    @Override
    public int getViewDistance()
    {
        return VIEW_RANGE;
    }
}
