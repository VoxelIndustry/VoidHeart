package net.voxelindustry.voidheart.client.render;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos.Mutable;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;

public class PillarPlacementRender
{
    private static BlockState voidPillarState;

    static void renderPillarsPreview(VoidAltarRender voidAltarRender, VoidAltarTile altar, MatrixStack matrices, VertexConsumer buffer)
    {
        Mutable pos = altar.getPos().mutableCopy();
        matrices.push();

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

        matrices.pop();
    }

    private static void renderPillarPreview(VoidAltarRender voidAltarRender, VoidAltarTile altar, Mutable pos, MatrixStack matrices, VertexConsumer buffer)
    {
        if (!altar.getWorld().isAir(pos))
            return;

        MinecraftClient.getInstance().getBlockRenderManager().renderBlock(
                getVoidPillarState(voidAltarRender),
                altar.getPos(),
                altar.getWorld(),
                matrices,
                buffer,
                true,
                altar.getWorld().getRandom());
    }

    private static BlockState getVoidPillarState(VoidAltarRender voidAltarRender)
    {
        if (voidPillarState == null)
            voidPillarState = VoidHeartBlocks.VOID_PILLAR.getDefaultState();
        return voidPillarState;
    }
}
