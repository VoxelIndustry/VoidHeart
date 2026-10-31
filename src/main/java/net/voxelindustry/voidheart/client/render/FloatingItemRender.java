package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.state.FloatingItemRenderState;
import net.voxelindustry.voidheart.client.util.MathUtil;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Render the stack of a tile floating and spinning above it.
 */
public class FloatingItemRender<T extends BlockEntity> implements BlockEntityRenderer<T, FloatingItemRenderState>
{
    private final ItemModelResolver         itemModelResolver;
    private final Function<T, ItemStack>    stackGetter;
    private final ToDoubleFunction<T>       heightGetter;

    public FloatingItemRender(BlockEntityRendererProvider.Context context, Function<T, ItemStack> stackGetter, double height)
    {
        this(context, stackGetter, tile -> height);
    }

    public FloatingItemRender(BlockEntityRendererProvider.Context context, Function<T, ItemStack> stackGetter, ToDoubleFunction<T> heightGetter)
    {
        this.itemModelResolver = context.itemModelResolver();
        this.stackGetter = stackGetter;
        this.heightGetter = heightGetter;
    }

    @Override
    public FloatingItemRenderState createRenderState()
    {
        return new FloatingItemRenderState();
    }

    @Override
    public void extractRenderState(T tile, FloatingItemRenderState state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);

        state.worldTime = tile.getLevel().getGameTime() + partialTicks;
        state.height = heightGetter.applyAsDouble(tile);
        itemModelResolver.updateForTopItem(state.item, stackGetter.apply(tile), ItemDisplayContext.GROUND, tile.getLevel(), null, (int) tile.getBlockPos().asLong());
    }

    @Override
    public void submit(FloatingItemRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.item.isEmpty())
            return;

        poseStack.pushPose();

        double offset = Math.abs(Math.sin(state.worldTime / 8.0) / 24.0);

        poseStack.translate(0.5, state.height + offset, 0.5);
        poseStack.rotate(MathUtil.quatFromAngleDegrees(state.worldTime, MathUtil.NEGATIVE_Y));

        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);

        poseStack.popPose();
    }
}
