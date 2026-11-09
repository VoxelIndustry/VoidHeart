package net.voxelindustry.voidheart.client.render.energy.ley_taint;

import com.mojang.blaze3d.vertex.QuadInstance;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.ley.anchor.OldAnchorBlock;
import net.voxelindustry.voidheart.common.ley.taint.LeyTaintPayload;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A tainted Old Anchor gutters: its dormant model, mauve crystal and stripes, is blended over the awake one by
 * {@link AnchorFlicker}. The block itself never changes.
 */
public final class OldAnchorFlickerRender
{
    private static final double DRAW_DISTANCE = 96;
    private static final float MIN_MAUVE = 0.01F;

    private static final Map<BlockState, List<BlockStateModelPart>> DORMANT_PARTS = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();
    /**
     * Models are baked again when resources reload, so the parts are kept only while the model set is the same.
     */
    private static Object modelSet;

    private OldAnchorFlickerRender()
    {
    }

    public static void collectSubmits(LevelRenderContext context)
    {
        var level = Minecraft.getInstance().level;
        if (level == null || level.dimension() != Level.OVERWORLD || LeyTaintView.isEmpty())
            return;

        var levelState = context.levelState();
        var camera = levelState.cameraRenderState.pos;
        double time = LeyTaintView.ticks() + levelState.worldPartialTicks;
        for (var node : LeyTaintView.nodes())
        {
            if (node.anchorY() == LeyTaintPayload.NO_ANCHOR)
                continue;
            var lower = new BlockPos(node.x(), node.anchorY(), node.z());
            if (lower.distToCenterSqr(camera) > DRAW_DISTANCE * DRAW_DISTANCE)
                continue;
            float mauve = node.flicker().mauve(time);
            if (mauve < MIN_MAUVE)
                continue;

            for (var pos : List.of(lower, lower.above()))
                submitDormant(context, level, pos, mauve, camera);
        }
    }

    private static void submitDormant(LevelRenderContext context, ClientLevel level, BlockPos pos, float mauve, Vec3 camera)
    {
        var state = level.getBlockState(pos);
        if (!state.is(VoidHeartBlocks.OLD_ANCHOR) || !state.getValue(OldAnchorBlock.ACTIVE))
            return;

        var currentSet = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        if (currentSet != modelSet)
        {
            DORMANT_PARTS.clear();
            modelSet = currentSet;
        }
        var parts = DORMANT_PARTS.computeIfAbsent(state.setValue(OldAnchorBlock.ACTIVE, false), OldAnchorFlickerRender::collectParts);
        var instance = new QuadInstance();
        instance.setColor(ARGB.white(mauve));
        instance.setLightCoords(LightCoordsUtil.getLightCoords(level, pos));

        var poseStack = context.poseStack();
        poseStack.pushPose();
        poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        context.submitNodeCollector().submitCustomGeometry(poseStack, LeyTaintRenderTypes.OVERLAY_BLOCK, (pose, buffer) ->
        {
            for (var part : parts)
            {
                for (var quad : part.getQuads(null))
                    buffer.putBakedQuad(pose, quad, instance);
                for (var direction : Direction.values())
                {
                    for (var quad : part.getQuads(direction))
                        buffer.putBakedQuad(pose, quad, instance);
                }
            }
        });
        poseStack.popPose();
    }

    private static List<BlockStateModelPart> collectParts(BlockState state)
    {
        var parts = new ArrayList<BlockStateModelPart>();
        RANDOM.setSeed(42);
        Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state).collectParts(RANDOM, parts);
        return parts;
    }

    public static void clear()
    {
        DORMANT_PARTS.clear();
        modelSet = null;
    }
}
