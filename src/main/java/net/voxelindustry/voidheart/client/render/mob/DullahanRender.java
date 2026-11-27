package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.model.mob.DullahanModel;
import net.voxelindustry.voidheart.client.render.mausoleum.FuneraryFlames;
import net.voxelindustry.voidheart.client.render.state.DullahanRenderState;
import net.voxelindustry.voidheart.common.content.mausoleum.Dullahan;
import net.voxelindustry.voidheart.common.content.mausoleum.DullahanCrown;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The Dullahan on its mount, solid as a wraith beast taking shape, with the eyes of the beast and the edge of the scythe glowing, and its crown of
 * green fireballs floating where its head would be: one for each flame that has not yet left as a wisp. They burn smaller while it sleeps at its post
 * and while it is stunned.
 */
public class DullahanRender extends MobRenderer<Dullahan, DullahanRenderState, DullahanModel>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/dullahan.png");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath(MODID, "textures/entity/dullahan_glow.png");

    private static final float SHADOW = 0.9F;

    public DullahanRender(EntityRendererProvider.Context context)
    {
        super(context, new DullahanModel(context.bakeLayer(DullahanModel.LAYER)), SHADOW);
        addLayer(new VoidGlowLayer<>(this, GLOW));
        addLayer(new CrownLayer(this));
    }

    @Override
    public DullahanRenderState createRenderState()
    {
        return new DullahanRenderState();
    }

    @Override
    public void extractRenderState(Dullahan rider, DullahanRenderState state, float partialTicks)
    {
        super.extractRenderState(rider, state, partialTicks);
        state.solid = 1;
        state.rear = 0;
        state.slam = 0;
        state.dodge = 0;
        state.phase = rider.phase();
        state.phaseAge = rider.tickCount - rider.phaseChangedTick + partialTicks;
        state.swingLeft = rider.swingsLeft();
        state.released = rider.released();
    }

    @Override
    public void submit(DullahanRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        state.camera.set(camera.orientation);
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    public Identifier getTextureLocation(DullahanRenderState state)
    {
        return TEXTURE;
    }

    /**
     * The crown: a ring of small fireballs where the top of a head would be, one for each flame not yet gone as a wisp, the first at the front. Each one
     * bobs on its own beat so it reads apart from its neighbours. The ring is placed from the rider's torso, so it leans and twists with it, then each
     * fireball is drawn in the world facing the viewer.
     */
    private static final class CrownLayer extends RenderLayer<DullahanRenderState, DullahanModel>
    {
        private static final float PX = 1 / 16F;
        /** How far above the gorget the ring floats, and how wide it is, in model pixels: a head's height and a little more than its half width. */
        private static final float RAISE = 7;
        private static final float RADIUS = 5.5F;
        private static final float HALF_SIZE = 0.12F;
        private static final float LOW = 0.7F;
        private static final float BOB = 0.025F;
        private static final float BOB_RATE = 0.21F;
        private static final float GUTTER_TICKS = 10;

        private CrownLayer(RenderLayerParent<DullahanRenderState, DullahanModel> parent)
        {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, DullahanRenderState state, float yRot, float xRot)
        {
            int burning = DullahanCrown.FLAMES - state.released;
            if (burning <= 0 || state.deathTime > 0)
                return;
            float size = HALF_SIZE * switch (state.phase)
            {
                case IDLE -> LOW;
                case STUNNED -> Mth.lerp(Mth.clamp(state.phaseAge / GUTTER_TICKS, 0, 1), 1, LOW);
                default -> 1;
            };

            poseStack.pushPose();
            getParentModel().toTorso(poseStack);
            var torso = new Matrix4f(poseStack.last().pose());
            poseStack.popPose();

            // The model's space hangs upside down: up is toward negative y, and the front toward negative z
            var centers = new Vector3f[burning];
            for (int flame = state.released; flame < DullahanCrown.FLAMES; flame++)
            {
                float angle = flame * Mth.TWO_PI / DullahanCrown.FLAMES;
                float bob = Mth.sin(state.ageInTicks * BOB_RATE + flame * 2.3F) * BOB;
                float y = (DullahanModel.CROWN_Y - RAISE) * PX - bob;
                centers[flame - state.released] = torso.transformPosition(-Mth.sin(angle) * RADIUS * PX, y, -Mth.cos(angle) * RADIUS * PX, new Vector3f());
            }

            var right = state.camera.transform(new Vector3f(1, 0, 0));
            var up = state.camera.transform(new Vector3f(0, 1, 0));
            var sprite = FuneraryFlames.sprite(FuneraryFlames.FIREBALL);
            poseStack.pushPose();
            // Back to the world's axes about the camera, where the viewer's right and up are known
            poseStack.setIdentity();
            collector.submitCustomGeometry(poseStack, FuneraryFlames.TYPE, (pose, buffer) ->
            {
                for (var center : centers)
                    FuneraryFlames.billboard(pose, buffer, sprite, center.x(), center.y(), center.z(), right, up, size, size);
            });
            poseStack.popPose();
        }
    }
}
