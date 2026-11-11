package net.voxelindustry.voidheart.client.render.mob;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.client.model.mob.TreantModel;
import net.voxelindustry.voidheart.client.render.state.TreantRenderState;
import net.voxelindustry.voidheart.common.content.mob.Treant;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class TreantRender extends MobRenderer<Treant, TreantRenderState, TreantModel>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/treant.png");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath(MODID, "textures/entity/treant_glow.png");
    private static final float SHADOW = 0.8F;

    public TreantRender(EntityRendererProvider.Context context)
    {
        super(context, new TreantModel(context.bakeLayer(TreantModel.LAYER)), SHADOW);
        addLayer(new VoidGlowLayer<>(this, GLOW));
    }

    @Override
    public TreantRenderState createRenderState()
    {
        return new TreantRenderState();
    }

    @Override
    public void extractRenderState(Treant treant, TreantRenderState state, float partialTicks)
    {
        super.extractRenderState(treant, state, partialTicks);
        int emerge = treant.emergeTicks();
        int sweep = treant.sweepTicks();
        state.emerge = emerge > 0 ? Math.min(emerge + partialTicks, Treant.EMERGE_TICKS) : 0;
        state.sweep = sweep > 0 ? sweep + partialTicks : 0;
    }

    @Override
    public Identifier getTextureLocation(TreantRenderState state)
    {
        return TEXTURE;
    }
}
