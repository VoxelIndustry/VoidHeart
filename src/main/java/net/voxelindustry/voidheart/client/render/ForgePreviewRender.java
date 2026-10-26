package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.client.render.state.ShatterForgeRenderState.HintKind;
import net.voxelindustry.voidheart.client.util.MathUtil;
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
    private static final int OPTIONAL_COLOR = ARGB.color(110, 140, 200, 255);
    private static final int LABEL_COLOR    = 0xFFB8DCFF;
    private static final int FULL_BRIGHT    = 15728880;

    private static final StaticGeoGhostRender COLLECTOR_GHOST = new StaticGeoGhostRender(
            Identifier.fromNamespaceAndPath(VoidHeart.MODID, "ravenous_collector"),
            Identifier.fromNamespaceAndPath(VoidHeart.MODID, "textures/block/ravenous_collector.png"));

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
            hints.add(new HintDraw(block.pos().subtract(forgePos), ghostState, free ? HintKind.MISSING : HintKind.CONFLICT, ghostState.isSolidRender(), null, 0));
        }

        var label = Component.translatable("voidheart.shatter_forge.preview.optional_collector").getVisualOrderText();
        var labelWidth = Minecraft.getInstance().font.width(label);
        for (var collectorPos : ShatterForgeLayout.optionalCollectors(forgePos, forge.getPreviewDirection(), forge.getPreviewPairs()))
        {
            if (!level.isEmptyBlock(collectorPos))
                continue;
            hints.add(new HintDraw(collectorPos.subtract(forgePos), null, HintKind.OPTIONAL, false, label, labelWidth));
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

    static void submitHints(ShatterForgeRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
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
            if (hint.kind() == HintKind.OPTIONAL)
            {
                // Only the collector is optional, it renders through GeckoLib so its ghost is drawn from the geo model
                poseStack.pushPose();
                poseStack.translate(hint.offset().getX(), hint.offset().getY(), hint.offset().getZ());
                COLLECTOR_GHOST.submit(poseStack, collector, OPTIONAL_COLOR);
                poseStack.popPose();

                if (hint.label() != null)
                    submitLabel(hint, poseStack, collector, camera);
                continue;
            }

            var parts = partsOf(hint.ghostState());
            var instance = hint.kind() == HintKind.CONFLICT ? conflict : ghost;

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

            if (hint.label() != null)
                submitLabel(hint, poseStack, collector, camera);
        }
    }

    private static void submitLabel(HintDraw hint, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        poseStack.pushPose();
        poseStack.translate(hint.offset().getX() + 0.5F, hint.offset().getY() + 1.05F, hint.offset().getZ() + 0.5F);
        poseStack.scale(1 / 48F, 1 / 48F, 1 / 48F);
        poseStack.rotate(camera.orientation);
        poseStack.rotate(MathUtil.quatFromAngleDegrees(180, MathUtil.POSITIVE_Y));
        collector.submitText(poseStack, -hint.labelWidth() / 2F, 0, hint.label(), true, DisplayMode.NORMAL, FULL_BRIGHT, LABEL_COLOR, 0, 0);
        poseStack.popPose();
    }
}
