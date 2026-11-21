package net.voxelindustry.voidheart.client.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;

/**
 * Under the Void Armor's spectral lens, the parts of a living thing hidden behind blocks show as a pale lilac glow. It draws through Insight's silhouette,
 * which only lights what something stands in front of, in the lens's own colour.
 */
public class SpectralSilhouetteLayer<S extends LivingEntityRenderState, M extends EntityModel<S>> extends RenderLayer<S, M>
{
    public SpectralSilhouetteLayer(RenderLayerParent<S, M> renderer)
    {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot)
    {
        int color = ((FabricRenderState) state).getDataOrDefault(ArmorSight.SPECTRAL, 0);
        if (color == 0)
            return;
        // The lightning blend scales by alpha, so it carries the strength
        collector.order(2).submitModel(getParentModel(), state, poseStack, VoidHeartRenderTypes.SILHOUETTE, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, null, 0);
    }
}
