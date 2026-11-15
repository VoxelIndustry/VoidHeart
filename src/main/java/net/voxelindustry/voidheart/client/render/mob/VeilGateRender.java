package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.SanctumWindow;
import net.voxelindustry.voidheart.client.render.WraithInk;
import net.voxelindustry.voidheart.common.content.sanctum.VeilGate;

import java.util.List;

/**
 * A door of wraith ink drawn as a Wraith Veil's mark: it spreads, its hole opens, and a temple's door seals and recedes as a veil's tunnel would.
 * A temple's door opens onto the hall of its sanctum, see {@link SanctumWindow}; a sanctum's gate stands open onto the shadow realm, its rim
 * smouldering.
 */
public class VeilGateRender extends EntityRenderer<VeilGate, VeilGateRender.State>
{
    private static final float SPREAD_GLOW = 1;
    private static final float HOLE_GLOW   = 0.7F;
    private static final float STAY_PULSE  = 0.15F;
    private static final float STAY_RATE   = 0.08F;
    private static final int   GROW_TICKS  = 20;
    /** How far the hole must open before the hall beyond begins to come out of the dark, and fades back into it as the hole closes. */
    private static final float HALL_FROM   = 0.35F;

    public static class State extends EntityRenderState
    {
        Direction face = Direction.NORTH;
        float halfU;
        float halfV;
        boolean stays;
        List<float[]> parts = List.of();
    }

    public VeilGateRender(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(VeilGate gate, State state, float partialTicks)
    {
        super.extractRenderState(gate, state, partialTicks);
        state.face = gate.face();
        state.halfU = gate.halfU();
        state.halfV = gate.halfV();
        state.stays = gate.stays();
        state.parts = WraithInk.holding(gate.level(), state.face, gate.position(), state.halfU, state.halfV);
    }

    @Override
    protected AABB getBoundingBoxForCulling(VeilGate gate, float partialTicks)
    {
        return super.getBoundingBoxForCulling(gate, partialTicks).inflate(Math.max(gate.halfU(), gate.halfV()));
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        super.submit(state, poseStack, collector, camera);
        // A mark is on the face of the wall, and shows nothing from behind it
        var face = state.face;
        if ((camera.pos.x - state.x) * face.getStepX() + (camera.pos.y - state.y) * face.getStepY() + (camera.pos.z - state.z) * face.getStepZ() < 0)
            return;

        float age = state.ageInTicks;
        float spread, open, glow;
        if (state.stays)
        {
            spread = WraithInk.SPREAD_FULL;
            open = 1;
            glow = HOLE_GLOW + STAY_PULSE * Mth.sin(age * STAY_RATE);
        }
        else
        {
            float growth = Mth.clamp(age / GROW_TICKS, 0, 1);
            float recede = Mth.clamp((age - VeilGate.closesAt()) / VeilGate.FADE_TICKS, 0, 1);
            spread = WraithInk.SPREAD_FULL * growth * (1 - recede * recede);
            open = VeilGate.holeOpen(false, age);
            glow = recede > 0 ? 0 : growth < 1 ? SPREAD_GLOW : HOLE_GLOW;
        }
        var variant = WraithInk.variant(face, new Vec3(state.x, state.y, state.z), state.halfU, state.halfV);
        // A temple's door opens onto its sanctum, not the shadow realm: its ink leaves the hole empty, and the hall shows through it
        var mode = state.stays ? WraithInk.Mode.MARK : WraithInk.Mode.WINDOW;
        WraithInk.submit(poseStack, collector, face, state.halfU, state.halfV, variant, WraithInk.color(spread, open, glow, mode), state.lightCoords, state.parts);
        if (!state.stays)
        {
            float shown = Mth.clamp((open - HALL_FROM) / (1 - HALL_FROM), 0, 1);
            SanctumWindow.submit(poseStack, collector, face, state.halfU, state.halfV, variant, open, shown * shown * (3 - 2 * shown), glow);
        }
    }
}
