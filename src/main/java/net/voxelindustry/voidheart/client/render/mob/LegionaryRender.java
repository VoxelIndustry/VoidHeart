package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.client.model.mob.LegionaryModel;
import net.voxelindustry.voidheart.client.render.state.LegionaryRenderState;
import net.voxelindustry.voidheart.common.content.mob.Legionary;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Its core and visor smoulder a dull red until its partner falls, then burn close to white.
 */
public class LegionaryRender extends MobRenderer<Legionary, LegionaryRenderState, LegionaryModel>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/legionary.png");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath(MODID, "textures/entity/legionary_glow.png");
    private static final Identifier FRENZY_GLOW = Identifier.fromNamespaceAndPath(MODID, "textures/entity/legionary_frenzy_glow.png");

    public LegionaryRender(EntityRendererProvider.Context context)
    {
        super(context, new LegionaryModel(context.bakeLayer(LegionaryModel.LAYER)), 0.45F);
        addLayer(new StateGlow(this, GLOW, false));
        addLayer(new StateGlow(this, FRENZY_GLOW, true));
    }

    @Override
    public LegionaryRenderState createRenderState()
    {
        return new LegionaryRenderState();
    }

    @Override
    public void extractRenderState(Legionary legionary, LegionaryRenderState state, float partialTicks)
    {
        super.extractRenderState(legionary, state, partialTicks);
        state.aggressive = legionary.isAggressive();
        state.frenzied = legionary.isFrenzied();
        state.attack = legionary.getSwingAnimation(partialTicks);
    }

    @Override
    public Identifier getTextureLocation(LegionaryRenderState state)
    {
        return TEXTURE;
    }

    private static final class StateGlow extends VoidGlowLayer<LegionaryRenderState, LegionaryModel>
    {
        private final boolean whenFrenzied;

        private StateGlow(LegionaryRender renderer, Identifier texture, boolean whenFrenzied)
        {
            super(renderer, texture);
            this.whenFrenzied = whenFrenzied;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, LegionaryRenderState state, float yRot, float xRot)
        {
            if (state.frenzied == whenFrenzied)
                super.submit(poseStack, collector, lightCoords, state, yRot, xRot);
        }
    }
}
