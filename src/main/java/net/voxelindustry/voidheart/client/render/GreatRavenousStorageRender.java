package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.essence.GreatRavenousStorageBlock;
import net.voxelindustry.voidheart.common.content.essence.GreatRavenousStorageTile;
import org.jspecify.annotations.Nullable;

/**
 * The essence of a Great Ravenous Storage, drawn once from its holder: the column of a Ravenous Storage twice as wide,
 * standing in the middle of the cube between its four pillars.
 */
public class GreatRavenousStorageRender implements BlockEntityRenderer<GreatRavenousStorageTile, GreatRavenousStorageRender.State>
{
    private static final float BOTTOM    = 12 / 16F;
    private static final float EMPTY_TOP = 12.6F / 16;
    private static final float FULL_TOP  = 31 / 16F;
    // The cube's middle, from its lowest north-west block
    private static final float CENTER    = 1;
    private static final float WIDTH     = 2;

    public static class State extends BlockEntityRenderState
    {
        public boolean shown;
        public float   fill;
        public int     color;
        public float   time;
        public long    seed;
    }

    public GreatRavenousStorageRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(GreatRavenousStorageTile tile, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);

        state.shown = tile.getBlockState().getValue(GreatRavenousStorageBlock.PART).isHolder() && tile.cube() != null;
        if (!state.shown)
            return;
        var held = tile.held();
        state.shown = !held.isUnattuned();
        state.fill = Mth.clamp(held.amount() / (float) held.maximumAmount(), 0F, 1F);
        state.color = VoidBeamRender.aspectColor(held.type());
        state.time = tile.getLevel().getGameTime() + partialTicks;
        state.seed = tile.getBlockPos().asLong();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (!state.shown)
            return;

        collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(VoidBeamRender.VEIL_TEXTURE, true),
                (pose, buffer) -> RavenousStorageRender.essence(pose, buffer, BOTTOM, Mth.lerp(state.fill, EMPTY_TOP, FULL_TOP), FULL_TOP,
                        state.fill, state.color, state.time, state.seed, CENTER, WIDTH));
    }

    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }
}
