package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.client.render.SecurityBeams;
import net.voxelindustry.voidheart.client.render.state.SecurityMobRenderState;
import net.voxelindustry.voidheart.common.content.underway.SecurityBalance;
import net.voxelindustry.voidheart.common.content.underway.security.SecurityMob;

import java.util.function.Predicate;
import java.util.function.Supplier;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Draws a walking machine of the Underway. Three lights tell what it is doing. The rubrum behind the glass of its boiler burns red while its section has power. The glyphs banding its plates light teal as it wakes. Its eye opens amber last, once it is under pressure, which is the moment it starts to move.
 * <p>
 * Each light is a texture next to the machine's own: {@code name_rubrum}, {@code name_glyphs} and {@code name_eye}. A machine that shoots draws the same aiming line and streak as the fort's guns, see {@link SecurityBeams}.
 */
public class SecurityMobRender<T extends SecurityMob, S extends SecurityMobRenderState, M extends EntityModel<S>> extends MobRenderer<T, S, M>
{
    /** What a machine shows beyond what they all share. */
    public interface Extra<T, S>
    {
        void extract(T mob, S state, float partialTicks);
    }

    /** How far into waking its glyphs light, and how far its eye opens. */
    private static final float GLYPHS_AT = 0.3F;
    private static final float EYE_AT = 0.95F;
    private static final float SWELL = 0.25F;

    private final Identifier texture;
    private final Supplier<S> states;
    private final Extra<T, S> extra;

    public SecurityMobRender(EntityRendererProvider.Context context, M model, float shadow, String name, Supplier<S> states, Extra<T, S> extra)
    {
        super(context, model, shadow);
        texture = texture(name);
        this.states = states;
        this.extra = extra;
        addLayer(new Glow(texture(name + "_rubrum"), state -> state.live));
        addLayer(new Glow(texture(name + "_glyphs"), state -> state.wake >= GLYPHS_AT));
        addLayer(new Glow(texture(name + "_eye"), state -> state.wake >= EYE_AT));
    }

    private static Identifier texture(String name)
    {
        return Identifier.fromNamespaceAndPath(MODID, "textures/entity/" + name + ".png");
    }

    @Override
    public S createRenderState()
    {
        return states.get();
    }

    @Override
    public void extractRenderState(T mob, S state, float partialTicks)
    {
        super.extractRenderState(mob, state, partialTicks);
        var origin = mob.position();
        state.wake = Mth.lerp(partialTicks, mob.wakeO, mob.wake);
        state.live = mob.isLive();
        state.attack = mob.getSwingAnimation(partialTicks);
        state.muzzle = mob.muzzle().subtract(origin);
        state.aimTo = mob.aimTicks() > 0 ? mob.aim().subtract(origin) : null;
        state.locked = false;
        state.swell = 0;
        state.tracerFade = mob.tracer > 0 ? Mth.clamp((mob.tracer - partialTicks) / SecurityMob.TRACER_TICKS, 0, 1) : 0;
        state.tracerTo = state.tracerFade > 0 ? mob.lastShot().subtract(origin) : null;
        extra.extract(mob, state, partialTicks);
    }

    /** Its lines reach as far as it shoots. */
    @Override
    protected AABB getBoundingBoxForCulling(T mob, float partialTicks)
    {
        return mob.getBoundingBox().inflate(SecurityBalance.AUTOMATON_SHOT_RANGE + 4);
    }

    @Override
    public Identifier getTextureLocation(S state)
    {
        return texture;
    }

    @Override
    protected void scale(S state, PoseStack poseStack)
    {
        float swollen = 1 + state.swell * SWELL;
        poseStack.scale(swollen, swollen, swollen);
    }

    @Override
    protected float getWhiteOverlayProgress(S state)
    {
        // A burning fuse flashes, faster as it runs out
        return state.swell > 0 && Mth.sin(state.swell * state.swell * 60) > 0 ? 0.6F : 0;
    }

    @Override
    public void submit(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        super.submit(state, poseStack, collector, camera);
        if (state.aimTo != null)
            SecurityBeams.submitAim(poseStack, collector, state.muzzle, state.aimTo, state.locked, state.ageInTicks);
        if (state.tracerTo != null)
            SecurityBeams.submitTracer(poseStack, collector, state.muzzle, state.tracerTo, state.tracerFade);
    }

    /** A lit part of the model, shown only while its condition holds. */
    private final class Glow extends VoidGlowLayer<S, M>
    {
        private final Predicate<S> lit;

        private Glow(Identifier texture, Predicate<S> lit)
        {
            super(SecurityMobRender.this, texture);
            this.lit = lit;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot)
        {
            if (lit.test(state))
                super.submit(poseStack, collector, lightCoords, state, yRot, xRot);
        }
    }
}
