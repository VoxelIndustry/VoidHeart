package net.voxelindustry.voidheart.client.render.mob;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.client.model.mob.CenturionModel;
import net.voxelindustry.voidheart.client.render.state.CenturionRenderState;
import net.voxelindustry.voidheart.common.content.mob.Centurion;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class CenturionRender extends MobRenderer<Centurion, CenturionRenderState, CenturionModel>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/centurion.png");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath(MODID, "textures/entity/centurion_glow.png");

    public CenturionRender(EntityRendererProvider.Context context)
    {
        super(context, new CenturionModel(context.bakeLayer(CenturionModel.LAYER)), 0.55F);
        addLayer(new VoidGlowLayer<>(this, GLOW));
    }

    @Override
    public CenturionRenderState createRenderState()
    {
        return new CenturionRenderState();
    }

    @Override
    public void extractRenderState(Centurion centurion, CenturionRenderState state, float partialTicks)
    {
        super.extractRenderState(centurion, state, partialTicks);
        state.aggressive = centurion.isAggressive();
        int bash = centurion.bashTicks();
        int sweep = centurion.sweepTicks();
        state.bash = bash > 0 ? bash + partialTicks : 0;
        state.sweep = sweep > 0 ? sweep + partialTicks : 0;
    }

    @Override
    public Identifier getTextureLocation(CenturionRenderState state)
    {
        return TEXTURE;
    }
}
