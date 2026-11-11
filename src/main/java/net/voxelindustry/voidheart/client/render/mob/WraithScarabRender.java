package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.model.mob.WraithScarabModel;
import net.voxelindustry.voidheart.client.render.state.WraithScarabRenderState;
import net.voxelindustry.voidheart.common.content.mob.WraithScarab;

import org.joml.Vector3f;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Solid itself. Each hop leaves a trail of fading images of it along the way it went, wing cases flared, an even see-through pale violet that ignores the
 * light, faint where it left and stronger toward where it landed, turning from the one face to the other. Trails last a little longer than the half second
 * between hops, so each frame draws at most two of them per scarab, {@link #TRAIL_IMAGES} images each.
 */
public class WraithScarabRender extends CrawlerRender<WraithScarab, WraithScarabRenderState, WraithScarabModel>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/wraith_scarab.png");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath(MODID, "textures/entity/wraith_scarab_glow.png");
    // Its own skin is near black, so the afterimage takes a pale even one that the tint colours
    private static final Identifier AFTERIMAGE_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/insight_silhouette.png");
    private static final RenderType AFTERIMAGE = RenderTypes.entityTranslucentEmissive(AFTERIMAGE_TEXTURE);

    private static final float SHADOW = 0.3F;
    private static final int AFTERIMAGE_COLOR = 0xB49CFF;
    private static final float AFTERIMAGE_ALPHA = 0.45F;
    // Images along each hop, the spot it left included and the one it landed on, where it stands, left out
    private static final int TRAIL_IMAGES = 5;
    // How strong the image at the spot it left is, against the one nearest the landing
    private static final float TRAIL_START_ALPHA = 0.3F;
    // In model units from the feet, as LivingEntityRenderer places every model
    private static final float MODEL_DROP = -1.501F;
    // How far the model reaches past its collision box, feelers and legs included
    private static final double MODEL_REACH = 0.5;

    public WraithScarabRender(EntityRendererProvider.Context context)
    {
        super(context, new WraithScarabModel(context.bakeLayer(WraithScarabModel.LAYER)), SHADOW);
        addLayer(new VoidGlowLayer<>(this, GLOW));
    }

    @Override
    public WraithScarabRenderState createRenderState()
    {
        return new WraithScarabRenderState();
    }

    @Override
    public void extractRenderState(WraithScarab scarab, WraithScarabRenderState state, float partialTicks)
    {
        super.extractRenderState(scarab, state, partialTicks);
        state.sinceHop = scarab.tickCount - scarab.landedTick + partialTicks;

        state.afterimages.clear();
        var here = new Vec3(state.x, state.y, state.z);
        var images = scarab.afterimages;
        for (int i = 0; i < images.size(); i++)
        {
            var image = images.get(i);
            float fade = 1 - (scarab.tickCount - image.born() + partialTicks) / WraithScarab.AFTERIMAGE_TICKS;
            if (fade <= 0)
                continue;

            // Each hop lands where the next one leaves from, and the last where the scarab now is
            var next = i + 1 < images.size() ? images.get(i + 1) : null;
            var landing = next != null ? next.position() : scarab.position();
            var landingSurface = next != null ? next.surface() : scarab.surface();
            float landingRot = next != null ? next.bodyRot() : state.bodyRot;
            for (int step = 0; step < TRAIL_IMAGES; step++)
            {
                float along = step / (float) TRAIL_IMAGES;
                state.afterimages.add(ghost(scarab, state, image, landing, landingSurface, landingRot, along, fade * Mth.lerp(along, TRAIL_START_ALPHA, 1), here));
            }
        }
    }

    /**
     * A fresh state for each image and frame, since the model is posed from it later in the frame.
     *
     * @param along how far from the spot it left toward the one it landed on
     */
    private static WraithScarabRenderState ghost(WraithScarab scarab, WraithScarabRenderState state, WraithScarab.Afterimage image, Vec3 landing,
            Direction landingSurface, float landingRot, float along, float fade, Vec3 here)
    {
        var ghost = new WraithScarabRenderState();
        ghost.fade = fade;
        ghost.offset = image.position().lerp(landing, along).subtract(here);
        ghost.ageInTicks = image.born();
        ghost.bodyRot = Mth.rotLerp(along, image.bodyRot(), landingRot);
        ghost.aggressive = state.aggressive;
        ghost.clinging = image.surface() != Direction.UP || landingSurface != Direction.UP;
        ghost.surfaceRotation.set(image.surface().getRotation()).slerp(landingSurface.getRotation(), along);
        float width = scarab.getBbWidth();
        float height = scarab.getBbHeight();
        feetOffset(image.surface(), width, height, ghost.surfaceOffset).lerp(feetOffset(landingSurface, width, height, new Vector3f()), along);
        return ghost;
    }

    @Override
    public void submit(WraithScarabRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        super.submit(state, poseStack, collector, camera);
        if (state.isInvisible)
            return;

        for (var ghost : state.afterimages)
        {
            poseStack.pushPose();
            poseStack.translate(ghost.offset.x, ghost.offset.y, ghost.offset.z);
            setupRotations(ghost, poseStack, ghost.bodyRot, 1);
            poseStack.scale(-1, -1, 1);
            poseStack.translate(0, MODEL_DROP, 0);
            int color = ARGB.color(Math.round(AFTERIMAGE_ALPHA * ghost.fade * 255), AFTERIMAGE_COLOR);
            collector.submitModel(getModel(), ghost, poseStack, AFTERIMAGE, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, null, 0);
            poseStack.popPose();
        }
    }

    @Override
    protected AABB getBoundingBoxForCulling(WraithScarab scarab, float partialTicks)
    {
        var box = super.getBoundingBoxForCulling(scarab, partialTicks);
        // Every trail runs between spots it left and the one it stands on, so holding each of those spots holds every trail
        var size = scarab.getBoundingBox().inflate(MODEL_REACH);
        for (var image : scarab.afterimages)
            box = box.minmax(size.move(image.position().subtract(scarab.position())));
        return box;
    }

    @Override
    public Identifier getTextureLocation(WraithScarabRenderState state)
    {
        return TEXTURE;
    }
}
