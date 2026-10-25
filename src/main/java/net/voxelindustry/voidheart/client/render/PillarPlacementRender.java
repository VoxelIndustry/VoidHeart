package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

public class PillarPlacementRender
{
    private static BlockState voidPillarState;

    static void renderPillarsPreview(VoidAltarRender voidAltarRender, VoidAltarTile altar, PoseStack matrices, VertexConsumer buffer)
    {
        MutableBlockPos pos = altar.getBlockPos().mutable();
        matrices.pushPose();

        matrices.translate(3, 0, -1);
        pos.move(3, 0, -1);
        renderPillarPreview(voidAltarRender, altar, pos, matrices, buffer);

        matrices.translate(0, 0, 2);
        pos.move(0, 0, 2);
        renderPillarPreview(voidAltarRender, altar, pos, matrices, buffer);

        matrices.translate(-2, 0, 2);
        pos.move(-2, 0, 2);
        renderPillarPreview(voidAltarRender, altar, pos, matrices, buffer);

        matrices.translate(-2, 0, 0);
        pos.move(-2, 0, 0);
        renderPillarPreview(voidAltarRender, altar, pos, matrices, buffer);

        matrices.translate(-2, 0, -2);
        pos.move(-2, 0, -2);
        renderPillarPreview(voidAltarRender, altar, pos, matrices, buffer);

        matrices.translate(0, 0, -2);
        pos.move(0, 0, -2);
        renderPillarPreview(voidAltarRender, altar, pos, matrices, buffer);

        matrices.translate(2, 0, -2);
        pos.move(2, 0, -2);
        renderPillarPreview(voidAltarRender, altar, pos, matrices, buffer);

        matrices.translate(2, 0, 0);
        pos.move(2, 0, 0);
        renderPillarPreview(voidAltarRender, altar, pos, matrices, buffer);

        matrices.popPose();
    }

    private static void renderPillarPreview(VoidAltarRender voidAltarRender, VoidAltarTile altar, MutableBlockPos pos, PoseStack matrices, VertexConsumer buffer)
    {
        if (!altar.getLevel().isEmptyBlock(pos) && altar.getLevel().getBlockState(pos).isSolidRender(altar.getLevel(), pos) || altar.getLevel().getBlockState(pos).getBlock() == VoidHeartBlocks.VOID_PILLAR)
            return;

        Minecraft.getInstance().getBlockRenderer().renderBatched(
                getVoidPillarState(voidAltarRender),
                altar.getBlockPos(),
                altar.getLevel(),
                matrices,
                buffer,
                true,
                altar.getLevel().getRandom());
    }

    private static BlockState getVoidPillarState(VoidAltarRender voidAltarRender)
    {
        if (voidPillarState == null)
            voidPillarState = VoidHeartBlocks.VOID_PILLAR.defaultBlockState();
        return voidPillarState;
    }
}
