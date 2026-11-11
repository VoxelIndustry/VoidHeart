package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.WraithInk;
import net.voxelindustry.voidheart.client.render.state.WraithStainRenderState;
import net.voxelindustry.voidheart.common.content.mob.WraithStain;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassagePlan;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassages;

/**
 * Wraith ink spreading over a surface from several seeds, its front glowing as it goes, then receding to nothing. A Wraith Veil's mark also opens
 * a hole onto its tunnel's realm while the tunnel is open. See {@link WraithInk} for how it is drawn.
 */
public class WraithStainRender extends EntityRenderer<WraithStain, WraithStainRenderState>
{
    // How brightly the front glows while the ink spreads, and the hole's edge smoulders while open
    private static final float SPREAD_GLOW = 1;
    private static final float HOLE_GLOW   = 0.7F;
    // How far a stain may sit from a mark of the local player's tunnel to be that mark
    private static final double OWN_MARK_REACH = 0.05;

    public WraithStainRender(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public WraithStainRenderState createRenderState()
    {
        return new WraithStainRenderState();
    }

    @Override
    public void extractRenderState(WraithStain stain, WraithStainRenderState state, float partialTicks)
    {
        super.extractRenderState(stain, state, partialTicks);
        state.face = stain.face();
        state.growTicks = stain.growTicks();
        state.lingerTicks = stain.lingerTicks();
        state.halfU = stain.halfU();
        state.halfV = stain.halfV();
        state.opens = stain.opens();
        state.parts = WraithInk.holding(stain.level(), state.face, stain.position(), state.halfU, state.halfV);
        state.window = state.opens && isOwnTunnel(stain);
    }

    /**
     * Only the bearer's client has opened the tunnel behind a mark, so only there can its hole be looked through.
     */
    private static boolean isOwnTunnel(WraithStain stain)
    {
        var player = Minecraft.getInstance().player;
        var plan = player == null ? null : WallPassages.passageOf(player);
        if (plan == null)
            return false;
        for (var mark : new WallPassagePlan.Mark[]{plan.entry(), plan.exit()})
            if (mark.face() == stain.face() && mark.center().distanceToSqr(stain.position()) < OWN_MARK_REACH * OWN_MARK_REACH)
                return true;
        return false;
    }

    @Override
    protected AABB getBoundingBoxForCulling(WraithStain stain, float partialTicks)
    {
        return super.getBoundingBoxForCulling(stain, partialTicks).inflate(Math.max(stain.halfU(), stain.halfV()));
    }

    @Override
    public void submit(WraithStainRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        // A mark is on the surface, not in the wall: from behind, as when crossing with the Wraith Veil, it would only hide the room beyond
        var face = state.face;
        if ((camera.pos.x - state.x) * face.getStepX() + (camera.pos.y - state.y) * face.getStepY() + (camera.pos.z - state.z) * face.getStepZ() < 0)
        {
            super.submit(state, poseStack, collector, camera);
            return;
        }
        float age = state.ageInTicks;
        float growth = Mth.clamp(age / state.growTicks, 0, 1);
        float recede = Mth.clamp((age - state.growTicks - state.lingerTicks) / WraithStain.FADE_TICKS, 0, 1);
        // Linear, as the mask already slows where the fronts must cross more ground
        float spread = WraithInk.SPREAD_FULL * growth * (1 - recede * recede);
        float open = state.opens ? WallPassagePlan.Plan.holeOpen(age) : 0;
        // Spreading, the front glows; receding, the ink just dries away
        float glow = recede > 0 ? 0 : growth < 1 ? SPREAD_GLOW : HOLE_GLOW;
        var variant = WraithInk.variant(face, new Vec3(state.x, state.y, state.z), state.halfU, state.halfV);
        var mode = state.window ? WraithInk.Mode.WINDOW : WraithInk.Mode.MARK;
        WraithInk.submit(poseStack, collector, face, state.halfU, state.halfV, variant, WraithInk.color(spread, open, glow, mode), state.lightCoords, state.parts);
        super.submit(state, poseStack, collector, camera);
    }
}
