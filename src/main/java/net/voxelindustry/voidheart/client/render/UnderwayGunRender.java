package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.client.render.state.UnderwayGunRenderState;
import net.voxelindustry.voidheart.common.content.underway.SecurityBalance;
import net.voxelindustry.voidheart.common.content.underway.security.TurretCupola;
import net.voxelindustry.voidheart.common.content.underway.security.UnderwayGun;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The steam repeaters of the Underway, built from boxes in the same copper and brass as their housings: twin brass barrels under an eye of amber glass. A cupola's gun hangs from the lowest box of its housing block and is not drawn while folded, when that block's own stub stands for it. An embrasure's gun stands on a pedestal behind its slit, its barrels through the gap and its eye on a stalk above them.
 * <p>
 * The boxes are opaque cutout geometry lit by the room, with corners counter-clockwise seen from outside. The cupola's neck starts a hair under its housing so the two never share a plane. The eye is full bright while the gun is awake, and dark glass otherwise. About forty quads a gun, plus the aiming line and a bolt's streak, see {@link SecurityBeams}.
 */
public class UnderwayGunRender<T extends UnderwayGun> extends EntityRenderer<T, UnderwayGunRenderState>
{
    private static final RenderType COPPER_TYPE = RenderTypes.entityCutout(Identifier.fromNamespaceAndPath(MODID, "textures/block/underway/copper.png"));
    private static final RenderType COPPER_DARK_TYPE = RenderTypes.entityCutout(Identifier.fromNamespaceAndPath(MODID, "textures/block/underway/copper_dark.png"));
    private static final RenderType BRASS_TYPE = RenderTypes.entityCutout(Identifier.fromNamespaceAndPath(MODID, "textures/block/underway/brass.png"));
    private static final RenderType EYE_LIT_TYPE = RenderTypes.entityCutout(Identifier.fromNamespaceAndPath(MODID, "textures/block/underway/eye_small.png"));
    private static final RenderType EYE_DARK_TYPE = RenderTypes.entityCutout(Identifier.fromNamespaceAndPath(MODID, "textures/block/underway/glass_dark.png"));

    private static final float PIXEL = 1 / 16F;
    /** Keeps boxes that meet from sharing a plane. */
    private static final float GAP = 0.004F;
    // The cupola: the foot of its housing's lowest box over the entity's feet, and the head's middle once folded up against it and once down
    private static final float HOUSING_FOOT = (float) TurretCupola.HANG + 5 * PIXEL;
    private static final float HEAD_HALF = 3 * PIXEL;
    private static final float HEAD_HALF_HEIGHT = 2.5F * PIXEL;
    private static final float HEAD_FOLDED = HOUSING_FOOT - HEAD_HALF_HEIGHT - GAP;
    private static final float HEAD_DOWN = (float) TurretCupola.HANG - 0.3F;
    private static final float NECK_HALF = PIXEL;
    private static final float CUPOLA_BARREL = 6 * PIXEL;
    // Twin barrels a pixel thick, a half pixel apart, and the eye three pixels square
    private static final float BARREL_INNER = 0.25F * PIXEL;
    private static final float BARREL_OUTER = 1.25F * PIXEL;
    private static final float BARREL_HALF = 0.5F * PIXEL;
    private static final float EYE_HALF = 1.5F * PIXEL;
    // The embrasure's gun: its pivot is level with the middle of the slit
    private static final float PIVOT_HEIGHT = 0.48F;
    private static final float RECEIVER_HALF = 1.5F * PIXEL;
    private static final float EMBRASURE_BARREL = 0.85F;
    // As far as the gun itself can be brought to bear, so the barrels always lie along the line they fire on
    private static final float MAX_SWING = (float) Math.toRadians(SecurityBalance.EMBRASURE_ARC);
    private static final float MAX_TILT = Mth.PI / 4;
    // A cupola with nobody in sight turns slowly, looking down into its room
    private static final float SCAN_RATE = 0.04F;
    private static final float SCAN_ELEVATION = -0.35F;

    private final boolean cupola;

    public UnderwayGunRender(EntityRendererProvider.Context context, boolean cupola)
    {
        super(context);
        this.cupola = cupola;
    }

    @Override
    public UnderwayGunRenderState createRenderState()
    {
        return new UnderwayGunRenderState();
    }

    @Override
    public void extractRenderState(T gun, UnderwayGunRenderState state, float partialTicks)
    {
        super.extractRenderState(gun, state, partialTicks);
        var origin = gun.position();
        var facing = gun.facing();
        state.awake = gun.isAwake();
        state.out = Mth.lerp(partialTicks, gun.outO, gun.out);
        state.facingYaw = (float) Mth.atan2(facing.x, facing.z);
        state.muzzle = gun.muzzle().subtract(origin);

        var phase = gun.phase();
        boolean aiming = phase == UnderwayGun.Phase.AIMING || phase == UnderwayGun.Phase.FIRING;
        if (aiming)
        {
            var way = gun.aim().subtract(gun.muzzle());
            state.yaw = (float) Mth.atan2(way.x, way.z);
            state.elevation = (float) Mth.atan2(way.y, Math.hypot(way.x, way.z));
            state.aimTo = phase == UnderwayGun.Phase.AIMING ? gun.aim().subtract(origin) : null;
            state.locked = gun.isLocked();
        }
        else
        {
            state.yaw = cupola ? state.ageInTicks * SCAN_RATE : state.facingYaw;
            state.elevation = cupola ? SCAN_ELEVATION : 0;
            state.aimTo = null;
        }
        state.tracerFade = gun.tracer > 0 ? Mth.clamp((gun.tracer - partialTicks) / UnderwayGun.TRACER_TICKS, 0, 1) : 0;
        state.tracerTo = state.tracerFade > 0 ? gun.lastShot().subtract(origin) : null;
    }

    /** Its lines reach as far as it shoots. */
    @Override
    protected AABB getBoundingBoxForCulling(T gun, float partialTicks)
    {
        return gun.getBoundingBox().inflate(SecurityBalance.GUN_RANGE + 4);
    }

    @Override
    public void submit(UnderwayGunRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (cupola)
            submitCupola(state, poseStack, collector);
        else
            submitEmbrasure(state, poseStack, collector);

        if (state.aimTo != null)
            SecurityBeams.submitAim(poseStack, collector, state.muzzle, state.aimTo, state.locked, state.ageInTicks);
        if (state.tracerTo != null)
            SecurityBeams.submitTracer(poseStack, collector, state.muzzle, state.tracerTo, state.tracerFade);
        super.submit(state, poseStack, collector, camera);
    }

    private void submitCupola(UnderwayGunRenderState state, PoseStack poseStack, SubmitNodeCollector collector)
    {
        if (state.out <= 0)
            return;
        int light = state.lightCoords;
        float head = Mth.lerp(state.out, HEAD_FOLDED, HEAD_DOWN);
        collector.submitCustomGeometry(poseStack, BRASS_TYPE, (pose, buffer) ->
                UnderwayKeyRender.box(pose, buffer, -NECK_HALF, head + HEAD_HALF_HEIGHT, -NECK_HALF, NECK_HALF, HOUSING_FOOT - GAP, NECK_HALF, light));

        poseStack.pushPose();
        poseStack.translate(0, head, 0);
        poseStack.rotate(Axis.YP.rotation(state.yaw));
        poseStack.rotate(Axis.XP.rotation(-state.elevation));
        collector.submitCustomGeometry(poseStack, COPPER_TYPE, (pose, buffer) ->
                UnderwayKeyRender.box(pose, buffer, -HEAD_HALF, -HEAD_HALF_HEIGHT, -HEAD_HALF, HEAD_HALF, HEAD_HALF_HEIGHT, HEAD_HALF, light));
        collector.submitCustomGeometry(poseStack, BRASS_TYPE, (pose, buffer) ->
        {
            float foot = -HEAD_HALF_HEIGHT + PIXEL / 4;
            UnderwayKeyRender.box(pose, buffer, -BARREL_OUTER, foot, HEAD_HALF, -BARREL_INNER, foot + 2 * BARREL_HALF, HEAD_HALF + CUPOLA_BARREL, light);
            UnderwayKeyRender.box(pose, buffer, BARREL_INNER, foot, HEAD_HALF, BARREL_OUTER, foot + 2 * BARREL_HALF, HEAD_HALF + CUPOLA_BARREL, light);
        });
        collector.submitCustomGeometry(poseStack, state.awake ? EYE_LIT_TYPE : EYE_DARK_TYPE, (pose, buffer) ->
                UnderwayKeyRender.box(pose, buffer, -EYE_HALF, -PIXEL, HEAD_HALF, EYE_HALF, 2 * PIXEL, HEAD_HALF + PIXEL / 2, state.awake ? LightCoordsUtil.FULL_BRIGHT : light));
        poseStack.popPose();
    }

    private void submitEmbrasure(UnderwayGunRenderState state, PoseStack poseStack, SubmitNodeCollector collector)
    {
        int light = state.lightCoords;
        poseStack.pushPose();
        poseStack.rotate(Axis.YP.rotation(state.facingYaw));
        collector.submitCustomGeometry(poseStack, COPPER_TYPE, (pose, buffer) ->
                UnderwayKeyRender.box(pose, buffer, -3 * PIXEL, 0, -3 * PIXEL, 3 * PIXEL, PIXEL, 3 * PIXEL, light));
        collector.submitCustomGeometry(poseStack, BRASS_TYPE, (pose, buffer) ->
                UnderwayKeyRender.box(pose, buffer, -PIXEL, PIXEL, -PIXEL, PIXEL, PIVOT_HEIGHT - RECEIVER_HALF - GAP, PIXEL, light));

        // The slit only lets the barrels swing so far
        float swing = Mth.clamp(Mth.wrapDegrees((float) Math.toDegrees(state.yaw - state.facingYaw)) * Mth.DEG_TO_RAD, -MAX_SWING, MAX_SWING);
        float tilt = Mth.clamp(state.elevation, -MAX_TILT, MAX_TILT);
        poseStack.translate(0, PIVOT_HEIGHT, 0);
        poseStack.rotate(Axis.YP.rotation(swing));
        poseStack.rotate(Axis.XP.rotation(-tilt));
        collector.submitCustomGeometry(poseStack, COPPER_TYPE, (pose, buffer) ->
                UnderwayKeyRender.box(pose, buffer, -RECEIVER_HALF, -RECEIVER_HALF, -5 * PIXEL, RECEIVER_HALF, RECEIVER_HALF, 2.5F * PIXEL, light));
        collector.submitCustomGeometry(poseStack, BRASS_TYPE, (pose, buffer) ->
        {
            UnderwayKeyRender.box(pose, buffer, -BARREL_OUTER, -BARREL_HALF, 2.5F * PIXEL, -BARREL_INNER, BARREL_HALF, EMBRASURE_BARREL, light);
            UnderwayKeyRender.box(pose, buffer, BARREL_INNER, -BARREL_HALF, 2.5F * PIXEL, BARREL_OUTER, BARREL_HALF, EMBRASURE_BARREL, light);
            // The stalk its eye stands on
            UnderwayKeyRender.box(pose, buffer, -PIXEL / 2, RECEIVER_HALF + GAP, -2.5F * PIXEL, PIXEL / 2, RECEIVER_HALF + PIXEL, -1.5F * PIXEL, light);
        });
        // The pressure tank that feeds it, slung on its side
        collector.submitCustomGeometry(poseStack, COPPER_DARK_TYPE, (pose, buffer) ->
                UnderwayKeyRender.box(pose, buffer, RECEIVER_HALF + GAP, -3 * PIXEL, -4 * PIXEL, RECEIVER_HALF + 3 * PIXEL, PIXEL, PIXEL, light));
        // Its eye shows from in front and from behind, where whoever comes to wreck it stands
        collector.submitCustomGeometry(poseStack, state.awake ? EYE_LIT_TYPE : EYE_DARK_TYPE, (pose, buffer) ->
                UnderwayKeyRender.box(pose, buffer, -EYE_HALF, RECEIVER_HALF + PIXEL + GAP, -2.5F * PIXEL, EYE_HALF, RECEIVER_HALF + 4 * PIXEL, -1.5F * PIXEL, state.awake ? LightCoordsUtil.FULL_BRIGHT : light));
        poseStack.popPose();
    }
}
