package net.voxelindustry.voidheart.client.render.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.voxelindustry.voidheart.client.armor.VoidArmorLook;
import net.voxelindustry.voidheart.client.armor.VoidArmorRender;
import net.voxelindustry.voidheart.client.model.armor.VoidArmorModel;
import net.voxelindustry.voidheart.common.content.armor.ParkedArmor;

/**
 * A parked frame stands open where its wearer stepped out, waiting to be stepped into, its slit and lacing dark.
 * <p>
 * Render type: the frame's, see {@link VoidArmorRender}. Per frame: the frame's boxes once per parked frame.
 */
public class ParkedArmorRender extends EntityRenderer<ParkedArmor, ParkedArmorRender.State>
{
    private final VoidArmorModel model;
    private final VoidArmorModel glowModel;

    public static class State extends EntityRenderState
    {
        final AvatarRenderState frame = new AvatarRenderState();
        float yaw;
    }

    public ParkedArmorRender(EntityRendererProvider.Context context)
    {
        super(context);
        model = VoidArmorRender.model(context.getModelSet(), false);
        glowModel = VoidArmorRender.model(context.getModelSet(), true);
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(ParkedArmor parked, State state, float partialTicks)
    {
        super.extractRenderState(parked, state, partialTicks);
        state.yaw = parked.getYRot();
        state.frame.setData(VoidArmorLook.KEY, VoidArmorLook.standing(parked.frame(), 0));
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        super.submit(state, poseStack, collector, camera);
        VoidArmorRender.standing(model, glowModel, state.frame, state.yaw, poseStack, collector, state.lightCoords);
    }
}
