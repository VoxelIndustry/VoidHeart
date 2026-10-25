package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.client.render.state.ShatterForgeRenderState;
import net.voxelindustry.voidheart.client.render.state.ShatterForgeRenderState.HintDraw;
import net.voxelindustry.voidheart.common.block.StateProperties;
import net.voxelindustry.voidheart.common.content.beam.LevelBeamWorldView;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeLayout;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeLayout.Part;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class ForgePreviewRender
{
    private static final int GHOST_COLOR    = ARGB.color(150, 255, 255, 255);
    private static final int CONFLICT_COLOR = ARGB.color(170, 255, 60, 60);
    private static final int FULL_BRIGHT    = 15728880;

    private static final Map<BlockState, List<BlockStateModelPart>> PARTS_CACHE = new HashMap<>();
    private static final RandomSource                              RANDOM      = RandomSource.create();

    static void collectHints(ShatterForgeTile forge, List<HintDraw> hints)
    {
        if (!forge.isPreviewActive())
            return;

        var level = forge.getLevel();
        var view = new LevelBeamWorldView(level);
        var forgePos = forge.getBlockPos();
        var expected = ShatterForgeLayout.expected(forgePos, forge.getPreviewDirection(), forge.getPreviewPairs());

        var conduits = new HashSet<BlockPos>();
        for (var block : expected)
        {
            if (block.part() == Part.CONDUIT)
                conduits.add(block.pos());
        }

        for (var block : expected)
        {
            if (view.kindAt(block.pos()) == block.part().kind())
                continue;
            if (block.part() == Part.PILLAR && ShatterForgeLayout.isEmitter(view, block.pos()))
                continue;

            var actual = level.getBlockState(block.pos());
            var free = actual.isAir() || !actual.getFluidState().isEmpty() && actual.canBeReplaced();

            var ghostState = ghostState(block, conduits);
            hints.add(new HintDraw(block.pos().subtract(forgePos), ghostState, !free, ghostState.isSolidRender()));
        }
    }

    private static BlockState ghostState(ShatterForgeLayout.Expected block, HashSet<BlockPos> conduits)
    {
        return switch (block.part())
        {
            case CONDUIT ->
            {
                var state = VoidHeartBlocks.VOID_CONDUIT.defaultBlockState();
                for (var direction : Direction.values())
                {
                    if (conduits.contains(block.pos().relative(direction)))
                        state = state.setValue(StateProperties.getSideFromDirection(direction), true);
                }
                yield state;
            }
            case MONOLITH_BOTTOM -> VoidHeartBlocks.VOID_MONOLITH.defaultBlockState().setValue(StateProperties.UP, true);
            case MONOLITH_MIDDLE -> VoidHeartBlocks.VOID_MONOLITH.defaultBlockState().setValue(StateProperties.UP, true).setValue(StateProperties.DOWN, true);
            case MONOLITH_TOP -> VoidHeartBlocks.VOID_MONOLITH.defaultBlockState().setValue(StateProperties.DOWN, true);
            case PILLAR -> VoidHeartBlocks.VOID_PILLAR.defaultBlockState();
        };
    }

    private static List<BlockStateModelPart> partsOf(BlockState state)
    {
        return PARTS_CACHE.computeIfAbsent(state, key ->
        {
            var parts = new java.util.ArrayList<BlockStateModelPart>();
            RANDOM.setSeed(42);
            Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(key).collectParts(RANDOM, parts);
            return parts;
        });
    }

    static void submitHints(ShatterForgeRenderState state, PoseStack poseStack, SubmitNodeCollector collector)
    {
        if (state.hints.isEmpty())
            return;

        var ghost = new QuadInstance();
        ghost.setColor(GHOST_COLOR);
        ghost.setLightCoords(FULL_BRIGHT);
        ghost.setOverlayCoords(OverlayTexture.NO_OVERLAY);

        var conflict = new QuadInstance();
        conflict.setColor(CONFLICT_COLOR);
        conflict.setLightCoords(FULL_BRIGHT);
        conflict.setOverlayCoords(OverlayTexture.NO_OVERLAY);

        for (var hint : state.hints)
        {
            var parts = partsOf(hint.ghostState());
            var instance = hint.conflict() ? conflict : ghost;

            poseStack.pushPose();
            poseStack.translate(hint.offset().getX(), hint.offset().getY(), hint.offset().getZ());
            // Full blocks shrink to a centered half-size cuboid so the hint does not hide the rest of the structure
            if (hint.shrink())
            {
                poseStack.translate(0.25F, 0.25F, 0.25F);
                poseStack.scale(0.5F, 0.5F, 0.5F);
            }
            collector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (pose, buffer) ->
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
    }
}
