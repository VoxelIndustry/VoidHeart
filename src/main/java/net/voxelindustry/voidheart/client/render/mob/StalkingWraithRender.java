package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.InsightSight;
import net.voxelindustry.voidheart.client.model.mob.StalkingWraithModel;
import net.voxelindustry.voidheart.client.model.mob.StalkingWraithMouthModel;
import net.voxelindustry.voidheart.client.render.state.StalkingWraithRenderState;
import net.voxelindustry.voidheart.common.content.mob.StalkingWraith;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Always a little see-through, and barely there at all until it closes in to stalk, when it fades in behind its prey. The teeth and eyes never fade: the mouth is
 * a separate opaque model and the eyes are in the glow layer, so a faint wraith shows only as a smile in the dark.
 */
public class StalkingWraithRender extends MobRenderer<StalkingWraith, StalkingWraithRenderState, StalkingWraithModel>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/stalking_wraith.png");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath(MODID, "textures/entity/stalking_wraith_glow.png");

    private static final float FAINT_ALPHA = 0.14F;
    private static final float PRESENT_ALPHA = 0.78F;
    private static final float CROSSING_ALPHA = 0.43F;
    private static final float SHADOW = 0.6F;

    public StalkingWraithRender(EntityRendererProvider.Context context)
    {
        super(context, new StalkingWraithModel(context.bakeLayer(StalkingWraithModel.LAYER)), SHADOW);
        addLayer(new VoidGlowLayer<>(this, GLOW));
        addLayer(new InsightSilhouetteLayer<>(this, state -> state.insight));
        addLayer(new MouthLayer(this, new StalkingWraithMouthModel(context.bakeLayer(StalkingWraithMouthModel.LAYER))));
    }

    @Override
    public StalkingWraithRenderState createRenderState()
    {
        return new StalkingWraithRenderState();
    }

    @Override
    public void extractRenderState(StalkingWraith wraith, StalkingWraithRenderState state, float partialTicks)
    {
        super.extractRenderState(wraith, state, partialTicks);
        state.crossing = wraith.isCrossing();
        state.rushing = wraith.isRushing();
        state.aggressive = wraith.isAggressive();
        state.smile = Mth.lerp(partialTicks, wraith.smileO, wraith.smile);
        state.presence = Mth.lerp(partialTicks, wraith.presenceO, wraith.presence);
        state.attack = wraith.getSwingAnimation(partialTicks);
        state.insight = InsightSight.silhouette(wraith, partialTicks, state.crossing);
    }

    @Override
    public Identifier getTextureLocation(StalkingWraithRenderState state)
    {
        return TEXTURE;
    }

    @Override
    protected @Nullable RenderType getRenderType(StalkingWraithRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing)
    {
        return isBodyVisible ? RenderTypes.entityTranslucent(TEXTURE) : super.getRenderType(state, isBodyVisible, forceTransparent, appearGlowing);
    }

    @Override
    protected int getModelTint(StalkingWraithRenderState state)
    {
        float alpha = Mth.lerp(state.presence, FAINT_ALPHA, PRESENT_ALPHA);
        if (state.crossing)
            alpha = Math.min(alpha, CROSSING_ALPHA);
        return ARGB.color(Math.round(alpha * 255), 0xFFFFFF);
    }

    @Override
    protected float getShadowRadius(StalkingWraithRenderState state)
    {
        return state.crossing ? 0 : SHADOW * state.presence;
    }

    private static final class MouthLayer extends RenderLayer<StalkingWraithRenderState, StalkingWraithModel>
    {
        private static final RenderType SOLID = RenderTypes.entityCutout(TEXTURE);
        private static final RenderType LIT = RenderTypes.eyes(GLOW);

        private final StalkingWraithMouthModel mouth;

        private MouthLayer(StalkingWraithRender renderer, StalkingWraithMouthModel mouth)
        {
            super(renderer);
            this.mouth = mouth;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, StalkingWraithRenderState state, float yRot, float xRot)
        {
            if (state.isInvisible)
                return;
            collector.submitModel(mouth, state, poseStack, SOLID, lightCoords, LivingEntityRenderer.getOverlayCoords(state, 0), state.outlineColor);
            collector.order(1).submitModel(mouth, state, poseStack, LIT, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        }
    }
}
