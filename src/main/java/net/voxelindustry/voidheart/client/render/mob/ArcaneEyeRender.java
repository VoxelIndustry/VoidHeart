package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.model.mob.ArcaneEyeModel;
import net.voxelindustry.voidheart.client.render.state.ArcaneEyeRenderState;
import net.voxelindustry.voidheart.common.content.mob.ArcaneEye;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Opaque like any mob, with the iris, veins and tendril tips lit by the glow layer.
 */
public class ArcaneEyeRender extends MobRenderer<ArcaneEye, ArcaneEyeRenderState, ArcaneEyeModel>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/arcane_eye.png");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath(MODID, "textures/entity/arcane_eye_glow.png");

    private static final float BOB_SLOW = 0.06F;
    private static final float BOB_RANGE = 0.06F;
    private static final float DART_PERIOD = 29;
    private static final float DART_RANGE = 0.5F;

    public ArcaneEyeRender(EntityRendererProvider.Context context)
    {
        super(context, new ArcaneEyeModel(context.bakeLayer(ArcaneEyeModel.LAYER)), 0.3F);
        addLayer(new VoidGlowLayer<>(this, GLOW));
    }

    @Override
    public ArcaneEyeRenderState createRenderState()
    {
        return new ArcaneEyeRenderState();
    }

    @Override
    public void extractRenderState(ArcaneEye eye, ArcaneEyeRenderState state, float partialTicks)
    {
        super.extractRenderState(eye, state, partialTicks);

        state.bodyRot = Mth.rotLerp(partialTicks, eye.yHeadRotO, eye.yHeadRot);
        state.yRot = 0;
        state.lid = Mth.lerp(partialTicks, eye.lidO, eye.lid);
        state.charging = eye.charging();
        state.bob = Mth.sin(state.ageInTicks * BOB_SLOW) * BOB_RANGE;

        float dart = Mth.floor(state.ageInTicks / DART_PERIOD) * 12.9898F + eye.getId();
        state.pupilX = state.charging ? 0 : Mth.sin(dart) * DART_RANGE;
        state.pupilY = state.charging ? 0 : Mth.cos(dart * 1.7F) * DART_RANGE;
    }

    @Override
    public void submit(ArcaneEyeRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        poseStack.pushPose();
        poseStack.translate(0, state.bob, 0);
        super.submit(state, poseStack, collector, camera);
        poseStack.popPose();
    }

    @Override
    public Identifier getTextureLocation(ArcaneEyeRenderState state)
    {
        return TEXTURE;
    }
}
