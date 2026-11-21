package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.hoard.HoardCofferTile;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A pool of rubrum in the socket of a coffer's lid, widening as its pocket's hoard fills, so a glance shows how much room is left. Over its room,
 * it fills the socket and burns brighter. One added quad, just above the lid.
 */
public class HoardCofferRender implements BlockEntityRenderer<HoardCofferTile, HoardCofferRender.State>
{
    private static final Identifier POOL_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/misc/ley_sight_halo.png");
    private static final RenderType POOL_TYPE    = VoidHeartRenderTypes.radiant(POOL_TEXTURE);

    private static final int   POOL_RGB   = 0xD0384E;
    private static final int   OVER_RGB   = 0xFF5A6E;
    private static final float POOL_ALPHA = 0.9F;
    /** The socket's half width, in blocks, which the pool reaches when the hoard is full. */
    private static final float SOCKET_HALF = 3 / 16F;
    /** The smallest pool shown for a hoard holding anything. */
    private static final float MIN_HALF    = 0.6F / 16F;
    private static final float LIFT        = 0.004F;

    public static class State extends BlockEntityRenderState
    {
        float fill;
        boolean socketed;
    }

    public HoardCofferRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(HoardCofferTile coffer, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(coffer, state, partialTicks, cameraPosition, breakProgress);
        state.fill = coffer.getFill();
        state.socketed = !coffer.getBundle().isEmpty();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (!state.socketed || state.fill <= 0)
            return;
        // By area, so a quarter full hoard covers a quarter of the socket
        float half = Math.max(MIN_HALF, SOCKET_HALF * Mth.sqrt(Math.min(1, state.fill)));
        int color = ARGB.color((int) (POOL_ALPHA * 255), state.fill > 1 ? OVER_RGB : POOL_RGB);
        float y = 1 + LIFT;
        collector.submitCustomGeometry(poseStack, POOL_TYPE, (pose, buffer) ->
        {
            float low = 0.5F - half;
            float high = 0.5F + half;
            float[][] corners = { { low, low, 0, 0 }, { low, high, 0, 1 }, { high, high, 1, 1 }, { high, low, 1, 0 } };
            for (var corner : corners)
                buffer.addVertex(pose, corner[0], y, corner[1]).setColor(color).setUv(corner[2], corner[3])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
        });
    }
}
