package net.voxelindustry.voidheart.client.render.mob;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.voxelindustry.voidheart.client.model.mob.WraithModel;
import net.voxelindustry.voidheart.client.render.state.WraithRenderState;
import net.voxelindustry.voidheart.common.content.mob.Wraith;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Solid in the open. While crossing a wall it switches to a blended render type, faded through the model tint.
 */
public class WraithRender extends MobRenderer<Wraith, WraithRenderState, WraithModel>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/wraith.png");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath(MODID, "textures/entity/wraith_glow.png");

    private static final int CROSSING_ALPHA = 110;
    private static final int CROSSING_TINT = ARGB.color(CROSSING_ALPHA, 0xFFFFFF);

    public WraithRender(EntityRendererProvider.Context context)
    {
        super(context, new WraithModel(context.bakeLayer(WraithModel.LAYER)), 0.45F);
        addLayer(new VoidGlowLayer<>(this, GLOW));
    }

    @Override
    public WraithRenderState createRenderState()
    {
        return new WraithRenderState();
    }

    @Override
    public void extractRenderState(Wraith wraith, WraithRenderState state, float partialTicks)
    {
        super.extractRenderState(wraith, state, partialTicks);
        state.crossing = wraith.isCrossing();
        state.aggressive = wraith.isAggressive();
        state.attack = wraith.getSwingAnimation(partialTicks);
    }

    @Override
    public Identifier getTextureLocation(WraithRenderState state)
    {
        return TEXTURE;
    }

    @Override
    protected @Nullable RenderType getRenderType(WraithRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing)
    {
        return state.crossing && isBodyVisible
               ? RenderTypes.entityTranslucent(TEXTURE)
               : super.getRenderType(state, isBodyVisible, forceTransparent, appearGlowing);
    }

    @Override
    protected int getModelTint(WraithRenderState state)
    {
        return state.crossing ? CROSSING_TINT : super.getModelTint(state);
    }

    @Override
    protected float getShadowRadius(WraithRenderState state)
    {
        return state.crossing ? 0 : super.getShadowRadius(state);
    }
}
