package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.model.mob.EyeMawModel;
import net.voxelindustry.voidheart.client.render.state.EyeMawRenderState;
import net.voxelindustry.voidheart.common.content.mob.FloatingEye;

/**
 * Both sizes of Eye Maw, drawn from the same shape. Opaque like any mob, with only the iris lit by the glow layer.
 */
public class EyeMawRender<T extends FloatingEye> extends MobRenderer<T, EyeMawRenderState, EyeMawModel>
{
    private static final float BOB_SLOW = 0.09F;
    private static final float BOB_FAST = 0.23F;
    private static final float BOB_RANGE = 0.05F;
    private static final float BOB_DETAIL = 0.012F;
    private static final float DART_PERIOD = 17;
    private static final float DART_X = 0.45F;
    private static final float DART_Y = 0.35F;
    private static final float BLINK_TICKS = 4;
    private static final float FLYING_FULL_SPEED = 0.3F;

    private final Identifier texture;
    private final float modelScale;

    public EyeMawRender(EntityRendererProvider.Context context, ModelLayerLocation layer, int detail, Identifier texture, Identifier glow, float modelScale, float shadow)
    {
        super(context, new EyeMawModel(context.bakeLayer(layer), detail), shadow);
        this.texture = texture;
        this.modelScale = modelScale;
        addLayer(new VoidGlowLayer<>(this, glow));
    }

    @Override
    public EyeMawRenderState createRenderState()
    {
        return new EyeMawRenderState();
    }

    @Override
    public void extractRenderState(T eye, EyeMawRenderState state, float partialTicks)
    {
        super.extractRenderState(eye, state, partialTicks);

        state.bodyRot = Mth.rotLerp(partialTicks, eye.yHeadRotO, eye.yHeadRot);
        state.yRot = 0;

        float open = Mth.lerp(partialTicks, eye.jawsO, eye.jaws);
        state.jaws = open * open * (3 - 2 * open);

        float age = state.ageInTicks;
        state.bob = Mth.sin(age * BOB_SLOW) * BOB_RANGE + Mth.sin(age * BOB_FAST) * BOB_DETAIL;
        state.blink = eye.blinkTicks > 0 ? Mth.sin(Mth.PI * Math.max(eye.blinkTicks - partialTicks, 0) / BLINK_TICKS) : 0;

        // Small darting of the iris, as if it kept scanning around what it looks at
        float dart = Mth.floor(age / DART_PERIOD) * 12.9898F + eye.getId();
        state.pupilX = Mth.sin(dart) * DART_X * (1 - state.jaws);
        state.pupilY = Mth.cos(dart * 1.7F) * DART_Y * (1 - state.jaws);
        state.flying = Mth.clamp((float) eye.getDeltaMovement().length() / FLYING_FULL_SPEED, 0, 1);
    }

    @Override
    protected void scale(EyeMawRenderState state, PoseStack poseStack)
    {
        poseStack.scale(modelScale, modelScale, modelScale);
    }

    @Override
    public void submit(EyeMawRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        poseStack.pushPose();
        poseStack.translate(0, state.bob, 0);
        super.submit(state, poseStack, collector, camera);
        poseStack.popPose();
    }

    @Override
    public Identifier getTextureLocation(EyeMawRenderState state)
    {
        return texture;
    }
}
