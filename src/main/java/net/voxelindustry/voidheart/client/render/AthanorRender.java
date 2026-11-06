package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.athanor.AthanorTile;
import org.jspecify.annotations.Nullable;

/**
 * The base of the brew, standing still in the furnace mouth and facing out of it. The mouth is a recess, so the item never meets a face of the model.
 */
public class AthanorRender implements BlockEntityRenderer<AthanorTile, AthanorRender.State>
{
    /**
     * Height of the item's middle, the middle of the mouth.
     */
    private static final double ITEM_HEIGHT = 7 / 16D;
    /**
     * How far behind the front of the block the item stands, halfway into the 4 pixel deep mouth.
     */
    private static final double ITEM_DEPTH  = 4 / 16D;
    private static final float  ITEM_SCALE  = 0.35F;

    private final ItemModelResolver itemModelResolver;

    public AthanorRender(BlockEntityRendererProvider.Context context)
    {
        this.itemModelResolver = context.itemModelResolver();
    }

    public static class State extends BlockEntityRenderState
    {
        public final ItemStackRenderState item = new ItemStackRenderState();
        public Direction facing = Direction.NORTH;
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(AthanorTile tile, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);

        var blockState = tile.getBlockState();
        state.facing = blockState.hasProperty(HorizontalDirectionalBlock.FACING) ? blockState.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        itemModelResolver.updateForTopItem(state.item, tile.getStack(), ItemDisplayContext.FIXED, tile.getLevel(), null, (int) tile.getBlockPos().asLong());
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.item.isEmpty())
            return;

        poseStack.pushPose();
        poseStack.translate(0.5, ITEM_HEIGHT, 0.5);
        // Turns south, the unrotated front, to the facing
        poseStack.rotate(Axis.YP.rotationDegrees(-state.facing.toYRot()));
        poseStack.translate(0, 0, 0.5 - ITEM_DEPTH);
        poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);

        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
