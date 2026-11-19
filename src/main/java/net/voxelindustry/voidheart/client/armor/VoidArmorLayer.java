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

/**
 * The Void Armor on its wearer, in place of their body, which {@code MixinPlayerModelVoidArmor} hides along with what is worn over it.
 */
public class VoidArmorLayer extends RenderLayer<AvatarRenderState, PlayerModel>
{
    private final VoidArmorModel model;
    private final VoidArmorModel glowModel;

    public VoidArmorLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EntityModelSet models)
    {
        super(renderer);
        model = VoidArmorRender.model(models, false);
        glowModel = VoidArmorRender.model(models, true);
        VoidArmorRender.firstPersonModel(VoidArmorRender.model(models, false));
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot)
    {
        var look = ((FabricRenderState) state).getDataOrDefault(VoidArmorLook.KEY, VoidArmorLook.NONE);
        if (!look.shown() || state.isInvisible)
            return;
        VoidArmorRender.submit(model, glowModel, state, poseStack, collector, lightCoords, LivingEntityRenderer.getOverlayCoords(state, 0), state.outlineColor);
    }
}
