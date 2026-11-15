package net.voxelindustry.voidheart.client.render.mob;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.model.mob.LesserShoggothModel;
import net.voxelindustry.voidheart.client.render.state.LesserShoggothRenderState;
import net.voxelindustry.voidheart.common.content.sanctum.LesserShoggoth;
import net.voxelindustry.voidheart.common.content.sanctum.SanctumBalance;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Black protoplasm with an oily sheen, its eyes lit a sickly green. The small ones a shoggoth falls into are the same shape, scaled down by their
 * size.
 */
public class LesserShoggothRender extends MobRenderer<LesserShoggoth, LesserShoggothRenderState, LesserShoggothModel>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/lesser_shoggoth.png");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath(MODID, "textures/entity/lesser_shoggoth_glow.png");
    private static final float LASH_TICKS = SanctumBalance.LASH_WINDUP + 4;
    private static final float FULL_SPEED = 0.2F;

    public LesserShoggothRender(EntityRendererProvider.Context context)
    {
        super(context, new LesserShoggothModel(context.bakeLayer(LesserShoggothModel.LAYER)), 0.7F);
        addLayer(new VoidGlowLayer<>(this, GLOW));
    }

    @Override
    public LesserShoggothRenderState createRenderState()
    {
        return new LesserShoggothRenderState();
    }

    @Override
    public void extractRenderState(LesserShoggoth shoggoth, LesserShoggothRenderState state, float partialTicks)
    {
        super.extractRenderState(shoggoth, state, partialTicks);
        state.seed = shoggoth.getId();
        state.lash = shoggoth.lashAge < 0 ? -1 : Math.min(1, (shoggoth.lashAge + partialTicks) / LASH_TICKS);
        state.moving = Mth.clamp((float) shoggoth.getDeltaMovement().horizontalDistance() / FULL_SPEED, 0, 1);
        state.feeding = shoggoth.isFeeding();
    }

    @Override
    public Identifier getTextureLocation(LesserShoggothRenderState state)
    {
        return TEXTURE;
    }
}
