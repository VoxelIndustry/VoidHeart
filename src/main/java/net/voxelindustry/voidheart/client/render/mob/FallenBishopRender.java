package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.model.mob.FallenBishopModel;
import net.voxelindustry.voidheart.client.render.state.FallenBishopRenderState;
import net.voxelindustry.voidheart.common.content.mob.FallenBishop;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * While charging, a thin wavering line shows where the lance will go, turning steady and bright the moment its aim locks. It then flares into a thick beam that thins
 * out and is gone in a few ticks.
 */
public class FallenBishopRender extends MobRenderer<FallenBishop, FallenBishopRenderState, FallenBishopModel>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/fallen_bishop.png");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath(MODID, "textures/entity/fallen_bishop_glow.png");
    private static final Identifier BEAM = Identifier.withDefaultNamespace("textures/entity/beacon/beacon_beam.png");

    // The teal of the Fallen's magic, the same as the Elder's orbs
    private static final int LINE_COLOR = 0x36C5B0;
    private static final int LOCKED_COLOR = 0xA8F0E2;
    private static final int CORE_COLOR = 0xFFE8FFFA;
    private static final float MODEL_SCALE = 0.9F;
    private static final int LINE_ALPHA = 90;
    private static final int LOCKED_ALPHA = 200;
    private static final int FLARE_ALPHA = 190;
    private static final float LINE_RADIUS = 0.018F;
    private static final float LINE_WAVER = 0.008F;
    private static final float LOCKED_RADIUS = 0.03F;
    private static final float CORE_RADIUS = 0.1F;
    private static final float FLARE_RADIUS = 0.26F;

    public FallenBishopRender(EntityRendererProvider.Context context)
    {
        super(context, new FallenBishopModel(context.bakeLayer(FallenBishopModel.LAYER)), 0.55F);
        addLayer(new VoidGlowLayer<>(this, GLOW));
    }

    @Override
    public FallenBishopRenderState createRenderState()
    {
        return new FallenBishopRenderState();
    }

    @Override
    protected void scale(FallenBishopRenderState state, PoseStack poseStack)
    {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

    @Override
    public Identifier getTextureLocation(FallenBishopRenderState state)
    {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(FallenBishop bishop, Frustum culler, double camX, double camY, double camZ, float partialTicks)
    {
        if (super.shouldRender(bishop, culler, camX, camY, camZ, partialTicks))
            return true;
        return bishop.lanceTicks() > 0 && culler.isVisible(new AABB(bishop.lanceOrigin(partialTicks), bishop.lanceEnd()).inflate(0.5));
    }

    @Override
    public void extractRenderState(FallenBishop bishop, FallenBishopRenderState state, float partialTicks)
    {
        super.extractRenderState(bishop, state, partialTicks);
        state.raise = Mth.lerp(partialTicks, bishop.raiseO, bishop.raise);
        state.kneel = Mth.lerp(partialTicks, bishop.kneelO, bishop.kneel);
        state.attack = bishop.getSwingAnimation(partialTicks);

        int lance = bishop.lanceTicks();
        if (lance > 0)
        {
            state.lanceFrom = bishop.lanceOrigin(partialTicks).subtract(bishop.getPosition(partialTicks));
            state.lanceTo = bishop.lanceEnd().subtract(bishop.getPosition(partialTicks));
            state.lanceTicks = lance + partialTicks;
        }
        else
        {
            state.lanceFrom = null;
            state.lanceTo = null;
        }
    }

    @Override
    public void submit(FallenBishopRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        super.submit(state, poseStack, collector, camera);

        if (state.lanceFrom != null && state.lanceTo != null)
        {
            poseStack.pushPose();
            poseStack.translate(state.lanceFrom.x, state.lanceFrom.y, state.lanceFrom.z);
            submitLance(poseStack, collector, state.lanceTo.subtract(state.lanceFrom), state.lanceTicks);
            poseStack.popPose();
        }
    }

    private static void submitLance(PoseStack poseStack, SubmitNodeCollector collector, Vec3 beam, float ticks)
    {
        float length = (float) beam.length();
        if (length < 0.01F)
            return;
        var direction = beam.scale(1 / length);

        poseStack.rotate(Axis.YP.rotation(Mth.HALF_PI - (float) Mth.atan2(direction.z, direction.x)));
        poseStack.rotate(Axis.XP.rotation((float) Math.acos(Mth.clamp(direction.y, -1, 1))));

        float scroll = Mth.frac(-ticks * 0.2F);
        float spin = ticks * 0.3F;
        if (ticks <= FallenBishop.CHARGE_TICKS)
        {
            boolean locked = ticks > FallenBishop.CHARGE_TICKS - FallenBishop.LOCK_TICKS;
            float radius = locked ? LOCKED_RADIUS : LINE_RADIUS + Mth.sin(ticks * 1.7F) * LINE_WAVER;
            int color = locked ? ARGB.color(LOCKED_ALPHA, LOCKED_COLOR) : ARGB.color(LINE_ALPHA, LINE_COLOR);
            collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BEAM, true),
                    (pose, buffer) -> tube(pose, buffer, color, length, radius, spin, scroll, length));
            return;
        }

        float fade = 1 - Mth.clamp((ticks - FallenBishop.CHARGE_TICKS) / FallenBishop.LINGER_TICKS, 0, 1);
        float coreRadius = CORE_RADIUS * fade;
        float flareRadius = FLARE_RADIUS * fade;
        int flareColor = ARGB.color((int) (FLARE_ALPHA * fade), LINE_COLOR);
        collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BEAM, false),
                (pose, buffer) -> tube(pose, buffer, CORE_COLOR, length, coreRadius, spin, scroll, length * 2));
        collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BEAM, true),
                (pose, buffer) -> tube(pose, buffer, flareColor, length, flareRadius, -spin, scroll, length));
    }

    /**
     * A square tube along +y, turned around its axis by the given angle.
     */
    private static void tube(PoseStack.Pose pose, VertexConsumer buffer, int color, float length, float radius, float angle, float vStart, float vLength)
    {
        for (int side = 0; side < 4; side++)
        {
            float a0 = angle + side * Mth.HALF_PI;
            float a1 = a0 - Mth.HALF_PI;
            float x0 = Mth.cos(a0) * radius;
            float z0 = Mth.sin(a0) * radius;
            float x1 = Mth.cos(a1) * radius;
            float z1 = Mth.sin(a1) * radius;

            vertex(pose, buffer, color, x0, length, z0, 1, vStart + vLength);
            vertex(pose, buffer, color, x0, 0, z0, 1, vStart);
            vertex(pose, buffer, color, x1, 0, z1, 0, vStart);
            vertex(pose, buffer, color, x1, length, z1, 0, vStart + vLength);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int color, float x, float y, float z, float u, float v)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0, 1, 0);
    }
}
