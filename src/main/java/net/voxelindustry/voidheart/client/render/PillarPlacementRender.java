package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.voxelindustry.voidheart.client.render.state.VoidAltarRenderState;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.List;

public class PillarPlacementRender
{
    private static final int GHOST_ALPHA = 160;

    private static final List<BlockPos> PILLAR_OFFSETS = List.of(
            new BlockPos(3, 0, -1),
            new BlockPos(3, 0, 1),
            new BlockPos(1, 0, 3),
            new BlockPos(-1, 0, 3),
            new BlockPos(-3, 0, 1),
            new BlockPos(-3, 0, -1),
            new BlockPos(-1, 0, -3),
            new BlockPos(1, 0, -3)
    );

    static void collectMissingPillars(VoidAltarTile altar, List<BlockPos> missingOffsets)
    {
        var level = altar.getLevel();

        for (var offset : PILLAR_OFFSETS)
        {
            var pos = altar.getBlockPos().offset(offset);
            var state = level.getBlockState(pos);

            if (!level.isEmptyBlock(pos) && state.isSolidRender() || state.getBlock() == VoidHeartBlocks.VOID_PILLAR)
                continue;
            missingOffsets.add(offset);
        }
    }

    static void submitPillarsPreview(VoidAltarRenderState state, PoseStack poseStack, SubmitNodeCollector collector)
    {
        if (state.missingPillarOffsets.isEmpty())
            return;

        // The pillar texture is opaque, the ghost transparency is applied here
        var ghost = new QuadInstance();
        ghost.setColor(ARGB.color(GHOST_ALPHA, 255, 255, 255));
        ghost.setLightCoords(state.lightCoords);
        ghost.setOverlayCoords(OverlayTexture.NO_OVERLAY);

        for (var offset : state.missingPillarOffsets)
        {
            poseStack.pushPose();
            poseStack.translate(offset.getX(), offset.getY(), offset.getZ());
            collector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (pose, buffer) ->
            {
                for (var part : state.pillarParts)
                {
                    for (var quad : part.getQuads(null))
                        buffer.putBakedQuad(pose, quad, ghost);
                    for (var direction : Direction.values())
                    {
                        for (var quad : part.getQuads(direction))
                            buffer.putBakedQuad(pose, quad, ghost);
                    }
                }
            });
            poseStack.popPose();
        }
    }
}
