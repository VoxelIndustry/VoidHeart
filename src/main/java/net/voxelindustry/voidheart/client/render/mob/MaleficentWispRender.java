package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.render.mausoleum.FuneraryFlames;
import net.voxelindustry.voidheart.common.content.mausoleum.MaleficentWisp;
import net.voxelindustry.voidheart.common.content.mausoleum.MausoleumBalance;
import org.joml.Vector3f;

/**
 * A Maleficent Wisp: its own animated sprite of green fire with a dark core and slanted eyes, always facing the viewer. It swells out of the crown
 * as it is born, flares while it gathers itself for a charge, and burns large through the charge, trailing the flames it sheds.
 * <p>
 * Per frame: one quad.
 */
public class MaleficentWispRender extends EntityRenderer<MaleficentWisp, MaleficentWispRender.State>
{
    private static final float HALF_SIZE = 0.48F;
    /** Born the size of a fireball of the crown. */
    private static final float BORN_FROM = 0.25F;
    private static final float FLARE = 1.35F;
    private static final float CHARGE = 1.2F;
    private static final float THROB_RATE = 0.5F;
    private static final float THROB = 0.06F;

    public static class State extends EntityRenderState
    {
        float scale = 1;
    }

    public MaleficentWispRender(EntityRendererProvider.Context context)
    {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(MaleficentWisp wisp, State state, float partialTicks)
    {
        super.extractRenderState(wisp, state, partialTicks);
        float age = wisp.tickCount - wisp.stateChangedTick + partialTicks;
        float scale = switch (wisp.state())
        {
            case BIRTH -> Mth.lerp(Mth.clamp(age / MausoleumBalance.WISP_BIRTH_TICKS, 0, 1), BORN_FROM, 1);
            case WINDUP -> Mth.lerp(Mth.clamp(age / MausoleumBalance.WISP_WINDUP, 0, 1), 1, FLARE);
            case CHARGE -> CHARGE;
            default -> 1;
        };
        state.scale = scale * (1 + Mth.sin((wisp.tickCount + partialTicks) * THROB_RATE) * THROB);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        var right = camera.orientation.transform(new Vector3f(1, 0, 0));
        var up = camera.orientation.transform(new Vector3f(0, 1, 0));
        float half = HALF_SIZE * state.scale;
        var sprite = FuneraryFlames.sprite(FuneraryFlames.WISP);
        collector.submitCustomGeometry(poseStack, FuneraryFlames.TYPE, (pose, buffer) ->
                FuneraryFlames.billboard(pose, buffer, sprite, 0, MaleficentWisp.SIZE / 2, 0, right, up, half, half));
        super.submit(state, poseStack, collector, camera);
    }
}
