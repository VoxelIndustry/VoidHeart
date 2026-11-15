package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.client.model.mob.VileFleshMassModel;
import net.voxelindustry.voidheart.client.render.state.VileFleshMassRenderState;
import net.voxelindustry.voidheart.common.content.sanctum.SanctumBalance;
import net.voxelindustry.voidheart.common.content.sanctum.VileFleshMass;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The mass in the flesh's muted wine, its eyes lit yellow. Dying, it does not fall on its side like other mobs but sags and sinks into the floor
 * while its pillar rises through it.
 */
public class VileFleshMassRender extends MobRenderer<VileFleshMass, VileFleshMassRenderState, VileFleshMassModel>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/vile_flesh_mass.png");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath(MODID, "textures/entity/vile_flesh_mass_glow.png");
    /** The model is drawn in sixteenths like any mob, then grown to the heap's size. */
    private static final float MODEL_SCALE = 1.35F;

    public VileFleshMassRender(EntityRendererProvider.Context context)
    {
        super(context, new VileFleshMassModel(context.bakeLayer(VileFleshMassModel.LAYER)), 2.8F);
        addLayer(new VoidGlowLayer<>(this, GLOW));
    }

    @Override
    public VileFleshMassRenderState createRenderState()
    {
        return new VileFleshMassRenderState();
    }

    @Override
    public void extractRenderState(VileFleshMass mass, VileFleshMassRenderState state, float partialTicks)
    {
        super.extractRenderState(mass, state, partialTicks);
        state.sweep = progress(mass.sweepAge, partialTicks, SanctumBalance.SWEEP_WINDUP + SanctumBalance.SWEEP_RECOVER);
        state.grab = progress(mass.grabAge, partialTicks, 30);
        state.spit = progress(mass.spitAge, partialTicks, SanctumBalance.SPIT_WINDUP + 10);
        state.bite = progress(mass.biteAge, partialTicks, 16);
        state.feed = progress(mass.feedAge, partialTicks, 20);
        state.awake = mass.isAwake();
        state.dying = mass.deathTime <= 0 ? 0 : Math.min(1, (mass.deathTime + partialTicks) / VileFleshMass.DEATH_TICKS);
        // It sinks rather than toppling, so the usual roll onto its side is left out
        state.deathTime = 0;
    }

    @Override
    protected void scale(VileFleshMassRenderState state, PoseStack poseStack)
    {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

    private static float progress(int age, float partialTicks, float lasts)
    {
        return age < 0 ? -1 : Math.min(1, (age + partialTicks) / lasts);
    }

    @Override
    public void submit(VileFleshMassRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        poseStack.pushPose();
        if (state.dying > 0)
        {
            float sag = state.dying * state.dying;
            poseStack.scale(1 + sag * 0.25F, Math.max(0.02F, 1 - sag), 1 + sag * 0.25F);
        }
        super.submit(state, poseStack, collector, camera);
        poseStack.popPose();
    }

    @Override
    public Identifier getTextureLocation(VileFleshMassRenderState state)
    {
        return TEXTURE;
    }
}
