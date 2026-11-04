package net.voxelindustry.voidheart.client.render.mob;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.model.mob.VoidGhoulModel;
import net.voxelindustry.voidheart.client.render.state.VoidGhoulRenderState;
import net.voxelindustry.voidheart.common.content.mob.VoidGhoul;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidGhoulRender extends HumanoidMobRenderer<VoidGhoul, VoidGhoulRenderState, VoidGhoulModel>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/void_ghoul.png");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath(MODID, "textures/entity/void_ghoul_glow.png");

    public VoidGhoulRender(EntityRendererProvider.Context context)
    {
        super(context, new VoidGhoulModel(context.bakeLayer(VoidGhoulModel.LAYER)), 0.5F);
        addLayer(new VoidGlowLayer<>(this, GLOW));
    }

    @Override
    public VoidGhoulRenderState createRenderState()
    {
        return new VoidGhoulRenderState();
    }

    @Override
    public void extractRenderState(VoidGhoul ghoul, VoidGhoulRenderState state, float partialTicks)
    {
        super.extractRenderState(ghoul, state, partialTicks);
        state.isAggressive = ghoul.isAggressive();
        state.flare = ghoul.isLunging() ? 1 : Mth.sin(ghoul.getSwingAnimation(partialTicks) * Mth.PI);
    }

    @Override
    public Identifier getTextureLocation(VoidGhoulRenderState state)
    {
        return TEXTURE;
    }
}
