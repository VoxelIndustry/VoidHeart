package net.voxelindustry.voidheart.client.render.blight;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.blight.BlightBalance;
import net.voxelindustry.voidheart.common.content.blight.ThrownBlightSpear;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import org.jspecify.annotations.Nullable;

/**
 * The thrown spear blight: its own item model laid along its flight, as a trident is, and the hole it opens, see {@link HoleLook}.
 */
public class ThrownBlightSpearRender extends EntityRenderer<ThrownBlightSpear, ThrownBlightSpearRender.State>
{
    /** The model's size against the block it fills, so it is about as long as it is in the hand. */
    private static final float SCALE = 1.6F;
    /** Turns the in-hand sprite, drawn head up and to the left, so its head lies along +X, the way an arrow's model points as it flies. */
    private static final float SPRITE_TURN = -135;

    private final ItemModelResolver itemModelResolver;
    /** Made once it is first drawn: renderers are built before items have their components. */
    private @Nullable ItemStack     stack;

    public static class State extends EntityRenderState
    {
        final ItemStackRenderState item = new ItemStackRenderState();
        float                      yRot;
        float                      xRot;
        /** Ticks since its hole opened, or below zero before it has. */
        float                      holeAge = -1;
        /** From the spear to its hole's middle. */
        Vec3                       toHole  = Vec3.ZERO;
        int                        seed;
    }

    public ThrownBlightSpearRender(EntityRendererProvider.Context context)
    {
        super(context);
        itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(ThrownBlightSpear spear, State state, float partialTicks)
    {
        super.extractRenderState(spear, state, partialTicks);
        state.yRot = spear.getYRot(partialTicks);
        state.xRot = spear.getXRot(partialTicks);
        if (stack == null)
            stack = new ItemStack(VoidHeartItems.VOID_BLIGHT_SPEAR);
        itemModelResolver.updateForNonLiving(state.item, stack, ItemDisplayContext.NONE, spear);
        state.holeAge = spear.isHoleOpen() ? spear.holeAge(partialTicks) : -1;
        state.toHole = spear.holeCenter().subtract(spear.getPosition(partialTicks));
        state.seed = spear.getId();
    }

    @Override
    protected AABB getBoundingBoxForCulling(ThrownBlightSpear spear, float partialTicks)
    {
        var box = super.getBoundingBoxForCulling(spear, partialTicks).inflate(1.5);
        return spear.isHoleOpen() ? box.minmax(new AABB(spear.holeCenter(), spear.holeCenter()).inflate(BlightBalance.HOLE_PULL_RANGE)) : box;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, state.yRot - 90);
        poseStack.rotateDegrees(Axis.ZP, state.xRot + SPRITE_TURN);
        poseStack.scale(SCALE, SCALE, SCALE);
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();

        if (state.holeAge >= 0)
            HoleLook.submit(poseStack, collector, camera.orientation, state.toHole, camera.pos.subtract(state.x, state.y, state.z), state.holeAge, state.seed);
        super.submit(state, poseStack, collector, camera);
    }
}
