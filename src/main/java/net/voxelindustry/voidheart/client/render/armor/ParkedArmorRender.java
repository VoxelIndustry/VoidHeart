package net.voxelindustry.voidheart.client.render.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.voxelindustry.voidheart.common.content.armor.ParkedArmor;

/**
 * A parked frame, drawn for now as its item standing at a wearer's height and turned the way it was left, until the frame has its own model.
 * <p>
 * Render type: the item's own. Per frame: one item model per parked frame.
 */
public class ParkedArmorRender extends EntityRenderer<ParkedArmor, ParkedArmorRender.State>
{
    private static final float ITEM_SCALE = 2.2F;
    private static final float ITEM_LIFT  = 1.6F;

    private final ItemModelResolver itemModelResolver;

    public static class State extends EntityRenderState
    {
        final ItemStackRenderState item = new ItemStackRenderState();
        float yaw;
    }

    public ParkedArmorRender(EntityRendererProvider.Context context)
    {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
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
        itemModelResolver.updateForNonLiving(state.item, parked.frame(), ItemDisplayContext.FIXED, parked);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        super.submit(state, poseStack, collector, camera);
        poseStack.pushPose();
        poseStack.translate(0, ITEM_LIFT, 0);
        poseStack.rotate(Axis.YP.rotationDegrees(180 - state.yaw));
        poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
