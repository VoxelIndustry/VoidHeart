package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.voxelindustry.voidheart.client.render.state.VoidAltarRenderState;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.List;

public class PillarPlacementRender
{
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
        for (var offset : state.missingPillarOffsets)
        {
            poseStack.pushPose();
            poseStack.translate(offset.getX(), offset.getY(), offset.getZ());
            state.pillarModel.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }
}
