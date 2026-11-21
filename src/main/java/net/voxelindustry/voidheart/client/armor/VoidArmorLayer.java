package net.voxelindustry.voidheart.client.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.voxelindustry.voidheart.client.model.armor.VoidArmorModel;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;

/**
 * The Void Armor on its wearer, in place of their body, which {@code MixinPlayerModelVoidArmor} hides along with what is worn over it.
 */
public class VoidArmorLayer extends RenderLayer<AvatarRenderState, PlayerModel>
{
    private final VoidArmorModel model;
    private final VoidArmorModel[] glow;

    public VoidArmorLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EntityModelSet models)
    {
        super(renderer);
        model = VoidArmorRender.model(models, false);
        glow = VoidArmorRender.glow(models);
        VoidArmorRender.firstPersonModel(VoidArmorRender.model(models, false));
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot)
    {
        var look = ((FabricRenderState) state).getDataOrDefault(VoidArmorLook.KEY, VoidArmorLook.NONE);
        if (!look.shown() || state.isInvisible)
            return;
        // The wearer's own body is hidden, so a spectral lens finds them through walls by the frame instead
        int spectral = ((FabricRenderState) state).getDataOrDefault(ArmorSight.SPECTRAL, 0);
        if (spectral != 0)
            collector.order(2).submitModel(model, state, poseStack, VoidHeartRenderTypes.SILHOUETTE, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    spectral, null, 0);
        VoidArmorRender.submit(model, glow, state, poseStack, collector, lightCoords, LivingEntityRenderer.getOverlayCoords(state, 0), state.outlineColor);
    }
}
