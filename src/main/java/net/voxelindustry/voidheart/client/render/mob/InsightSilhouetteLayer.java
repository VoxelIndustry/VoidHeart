package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.voxelindustry.voidheart.client.InsightSight;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;

import java.util.function.ToDoubleFunction;

/**
 * Under Insight, the parts of a wall-crosser hidden behind blocks show as a faint violet glow.
 */
public class InsightSilhouetteLayer<S extends LivingEntityRenderState, M extends EntityModel<S>> extends RenderLayer<S, M>
{
    private final ToDoubleFunction<S> alpha;

    public InsightSilhouetteLayer(RenderLayerParent<S, M> renderer, ToDoubleFunction<S> alpha)
    {
        super(renderer);
        this.alpha = alpha;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot)
    {
        float value = (float) alpha.applyAsDouble(state);
        if (value <= 0)
            return;
        // The lightning blend scales by alpha, so it carries the strength
        int color = ARGB.color(Math.round(value * 255), InsightSight.SILHOUETTE_COLOR);
        collector.order(2).submitModel(getParentModel(), state, poseStack, VoidHeartRenderTypes.SILHOUETTE, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, null, 0);
    }
}
