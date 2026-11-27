package net.voxelindustry.voidheart.client.render.shroud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
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
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.client.util.MathUtil;
import net.voxelindustry.voidheart.common.content.shroud.ShroudAltarTile;
import net.voxelindustry.voidheart.common.content.shroud.ShroudVeil;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.ShroudGate;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A shroud altar's offered ink, lying in the outline cut in its top, and the void its gate opens onto. The void is drawn a hair in front of the seal's
 * face, from the threshold up to the seal's ragged edge, one quad per pixel column and block, opaque and writing depth, so the seal shows only above
 * it. A faint violet line glows along the edge while it moves. Drawn by the altar, which faces its gate, so the renderer is not culled with it.
 * <p>
 * Per frame: at most 3 x 16 columns times 5 blocks of void, and as many edge strips, all rebuilt from the progress alone.
 */
public class ShroudAltarRender implements BlockEntityRenderer<ShroudAltarTile, ShroudAltarRender.State>
{
    private static final Identifier VOID_SPRITE = Identifier.fromNamespaceAndPath(MODID, "block/shroud_void");
    /** From the block atlas, so the void's sprite animates as a block's would. */
    private static final RenderType VOID_TYPE = RenderTypes.entitySolid(TextureAtlas.LOCATION_BLOCKS);
    /** How far in front of the seal's face the void lies, clear of its texture. */
    private static final float VOID_GAP = 0.02F;
    private static final float EDGE_GAP = 0.025F;
    private static final float PIXEL = 1F / ShroudVeil.PIXELS;
    /** The ink lies flat on the altar's top, half its thickness above it. */
    private static final float INK_LIFT = 1 / 32F + 0.002F;
    private static final int EDGE_RED = 140;
    private static final int EDGE_GREEN = 72;
    private static final int EDGE_BLUE = 200;
    private static final int EDGE_ALPHA = 100;
    private static final int VIEW_DISTANCE = 96;

    private final ItemModelResolver itemModelResolver;

    public static class State extends BlockEntityRenderState
    {
        final ItemStackRenderState ink = new ItemStackRenderState();
        int inkLight;
        int altarFacing;
        /** The middle of the seal's face at the threshold, from the altar's corner. */
        float faceX;
        float faceY;
        float faceZ;
        int gateFacing;
        int lateral;
        final int[] tops = new int[ShroudVeil.COLUMNS];
        /** Which of the seal's blocks stand, by column then row. */
        final boolean[] seal = new boolean[(2 * ShroudGate.SEAL_HALF_WIDTH + 1) * ShroudGate.SEAL_HEIGHT];
        boolean anySeal;
        float glow;
    }

    public ShroudAltarRender(BlockEntityRendererProvider.Context context)
    {
        itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(ShroudAltarTile altar, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(altar, state, partialTicks, cameraPosition, breakProgress);
        var level = altar.getLevel();
        var pos = altar.getBlockPos();
        itemModelResolver.updateForTopItem(state.ink, altar.ink(), ItemDisplayContext.FIXED, level, null, (int) pos.asLong());
        state.inkLight = LightCoordsUtil.getLightCoords(level, pos.above());

        var gate = altar.gate();
        state.altarFacing = gate.altarFacing();
        state.gateFacing = gate.facing();
        state.lateral = gate.lateral();
        float forward = 0.5F + VOID_GAP;
        state.faceX = gate.x() - pos.getX() + 0.5F + Compass.dx(gate.facing()) * forward;
        state.faceY = gate.y() - pos.getY() + ShroudGate.SEAL_BOTTOM;
        state.faceZ = gate.z() - pos.getZ() + 0.5F + Compass.dz(gate.facing()) * forward;

        state.anySeal = false;
        gate.forEachSealBlock((x, y, z, column, row) ->
        {
            boolean stands = level.getBlockState(new BlockPos(x, y, z)).is(VoidHeartBlocks.SHROUD_SEAL);
            state.seal[column * ShroudGate.SEAL_HEIGHT + row] = stands;
            state.anySeal |= stands;
        });

        float progress = altar.progress(level.getGameTime() + (double) partialTicks);
        float risen = ShroudVeil.risen(progress);
        long salt = BlockPos.asLong(gate.x(), gate.y(), gate.z());
        for (int column = 0; column < ShroudVeil.COLUMNS; column++)
            state.tops[column] = ShroudVeil.columnTop(risen, column, salt);
        // Brightest halfway, gone once the seal is wholly lifted or settled
        state.glow = Mth.sin(Mth.clamp(progress, 0, 1) * Mth.PI);
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
        submitInk(state, poseStack, collector);
        if (state.anySeal)
            submitVoid(state, poseStack, collector);
    }

    private static void submitInk(State state, PoseStack poseStack, SubmitNodeCollector collector)
    {
        if (state.ink.isEmpty())
            return;
        poseStack.pushPose();
        poseStack.translate(0.5, 1 + INK_LIFT, 0.5);
        // Its tip points at the gate, as the outline does
        float yaw = (float) Math.toDegrees(Math.atan2(Compass.dx(state.altarFacing), Compass.dz(state.altarFacing)));
        poseStack.rotate(MathUtil.quatFromAngleDegrees(yaw, MathUtil.POSITIVE_Y));
        poseStack.rotate(MathUtil.quatFromAngleDegrees(90, MathUtil.POSITIVE_X));
        state.ink.submit(poseStack, collector, state.inkLight, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    private static void submitVoid(State state, PoseStack poseStack, SubmitNodeCollector collector)
    {
        var sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(VOID_SPRITE);
        collector.submitCustomGeometry(poseStack, VOID_TYPE, (pose, buffer) ->
        {
            for (int column = 0; column < ShroudVeil.COLUMNS; column++)
            {
                int top = state.tops[column];
                int blockColumn = column / ShroudVeil.PIXELS;
                for (int row = 0; row < ShroudGate.SEAL_HEIGHT && top > row * ShroudVeil.PIXELS; row++)
                {
                    if (!state.seal[blockColumn * ShroudGate.SEAL_HEIGHT + row])
                        continue;
                    int from = row * ShroudVeil.PIXELS;
                    int to = Math.min(top, from + ShroudVeil.PIXELS);
                    voidQuad(state, pose, buffer, sprite, column, from, to);
                }
            }
        });
        if (state.glow <= 0)
            return;
        int color = ARGB.color(Math.round(EDGE_ALPHA * state.glow), EDGE_RED, EDGE_GREEN, EDGE_BLUE);
        collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) ->
        {
            for (int column = 0; column < ShroudVeil.COLUMNS; column++)
            {
                int top = state.tops[column];
                int row = Math.min(top / ShroudVeil.PIXELS, ShroudGate.SEAL_HEIGHT - 1);
                if (top <= 0 || top >= ShroudVeil.OPENING_PIXELS || !state.seal[column / ShroudVeil.PIXELS * ShroudGate.SEAL_HEIGHT + row])
                    continue;
                edgeQuad(state, pose, buffer, column, top, color);
            }
        });
    }

    /** The left edge of a pixel column as seen from the room, along the gate's lateral direction, which points to the viewer's left. */
    private static float leftOf(int column)
    {
        return ShroudGate.SEAL_HALF_WIDTH + 0.5F - column * PIXEL;
    }

    private static void voidQuad(State state, PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int column, int from, int to)
    {
        float left = leftOf(column);
        float right = left - PIXEL;
        int inBlock = column % ShroudVeil.PIXELS;
        int fromInBlock = from % ShroudVeil.PIXELS;
        int toInBlock = to - from + fromInBlock;
        float u0 = sprite.getU(inBlock * PIXEL);
        float u1 = sprite.getU((inBlock + 1) * PIXEL);
        float vTop = sprite.getV(1 - toInBlock * PIXEL);
        float vBottom = sprite.getV(1 - fromInBlock * PIXEL);
        float bottom = from * PIXEL;
        float top = to * PIXEL;
        // Counter-clockwise as seen from the room
        vertex(state, pose, buffer, left, bottom, 0, u0, vBottom);
        vertex(state, pose, buffer, right, bottom, 0, u1, vBottom);
        vertex(state, pose, buffer, right, top, 0, u1, vTop);
        vertex(state, pose, buffer, left, top, 0, u0, vTop);
    }

    private static void vertex(State state, PoseStack.Pose pose, VertexConsumer buffer, float across, float up, float out, float u, float v)
    {
        float x = state.faceX + Compass.dx(state.lateral) * across + Compass.dx(state.gateFacing) * out;
        float z = state.faceZ + Compass.dz(state.lateral) * across + Compass.dz(state.gateFacing) * out;
        buffer.addVertex(pose, x, state.faceY + up, z)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, Compass.dx(state.gateFacing), 0, Compass.dz(state.gateFacing));
    }

    private static void edgeQuad(State state, PoseStack.Pose pose, VertexConsumer buffer, int column, int top, int color)
    {
        float left = leftOf(column);
        float right = left - PIXEL;
        float bottom = top * PIXEL;
        float upper = bottom + PIXEL;
        float out = EDGE_GAP - VOID_GAP;
        edgeVertex(state, pose, buffer, left, bottom, out, color);
        edgeVertex(state, pose, buffer, right, bottom, out, color);
        edgeVertex(state, pose, buffer, right, upper, out, color);
        edgeVertex(state, pose, buffer, left, upper, out, color);
    }

    private static void edgeVertex(State state, PoseStack.Pose pose, VertexConsumer buffer, float across, float up, float out, int color)
    {
        float x = state.faceX + Compass.dx(state.lateral) * across + Compass.dx(state.gateFacing) * out;
        float z = state.faceZ + Compass.dz(state.lateral) * across + Compass.dz(state.gateFacing) * out;
        buffer.addVertex(pose, x, state.faceY + up, z).setColor(color);
    }
}
