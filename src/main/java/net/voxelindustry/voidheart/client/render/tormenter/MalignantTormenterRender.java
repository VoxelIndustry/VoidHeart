package net.voxelindustry.voidheart.client.render.tormenter;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.tormenter.MalignantTormenterTile;
import net.voxelindustry.voidheart.common.content.tormenter.TormenterDropletShape;
import org.jspecify.annotations.Nullable;

import java.util.SortedSet;

public class MalignantTormenterRender implements BlockEntityRenderer<MalignantTormenterTile, MalignantTormenterRender.State>
{
    public static class State extends BlockEntityRenderState
    {
        final TormenterFigure figure = new TormenterFigure();
    }

    public MalignantTormenterRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(MalignantTormenterTile tormenter, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(tormenter, state, partialTicks, cameraPosition, breakProgress != null ? breakProgress : crumbling(tormenter, cameraPosition));

        var time = tormenter.getLevel().getGameTime() % TormenterFigure.TIME_WRAP + partialTicks;
        state.figure.set(tormenter.souls(), tormenter.getLevel(), tormenter.getBlockPos().asLong(), time);
    }

    /**
     * The game never hands cracks to a block entity drawn off screen, as this one must be, so it looks for its own. They can
     * be on any of its parts, since the droplet is mined wherever it is hit.
     */
    private static ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling(MalignantTormenterTile tormenter, Vec3 cameraPosition)
    {
        if (!(tormenter.getLevel() instanceof ClientLevel level))
            return null;

        var pos = tormenter.getBlockPos();
        var destruction = level.destructionProgress();
        if (destruction.isEmpty())
            return null;

        var furthest = progressAt(destruction.get(pos.asLong()));
        if (tormenter.isSated())
            for (var cell : TormenterDropletShape.CELLS)
                furthest = Math.max(furthest, progressAt(destruction.get(BlockPos.asLong(pos.getX() + cell.x(), pos.getY() + cell.y(), pos.getZ() + cell.z()))));
        if (furthest < 0)
            return null;

        var cameraPose = new PoseStack();
        cameraPose.translate(pos.getX() - cameraPosition.x, pos.getY() - cameraPosition.y, pos.getZ() - cameraPosition.z);
        return new ModelFeatureRenderer.CrumblingOverlay(furthest, cameraPose.last());
    }

    private static int progressAt(@Nullable SortedSet<BlockDestructionProgress> progresses)
    {
        return progresses == null || progresses.isEmpty() ? -1 : progresses.last().getProgress();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        state.figure.submit(poseStack, collector, state.lightCoords, state.breakProgress);
    }

    /**
     * The droplet spills over the blocks around its own, whose sections may be in view while this one is not.
     */
    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }
}
