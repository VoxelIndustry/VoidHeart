package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * The parts of a mob that stay lit in the dark, which must also vanish while the mob lies dormant.
 */
public class VoidGlowLayer<S extends LivingEntityRenderState, M extends EntityModel<S>> extends EyesLayer<S, M>
{
    private final RenderType renderType;

    public VoidGlowLayer(RenderLayerParent<S, M> renderer, Identifier texture)
    {
        super(renderer);
        renderType = RenderTypes.eyes(texture);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot)
    {
        if (!state.isInvisible)
            super.submit(poseStack, collector, lightCoords, state, yRot, xRot);
    }

    @Override
    public RenderType renderType()
    {
        return renderType;
    }
}
