package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.client.model.mob.CorruptedCrabModel;
import net.voxelindustry.voidheart.client.render.state.CrabRenderState;
import net.voxelindustry.voidheart.common.content.mob.CorruptedCrab;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class CorruptedCrabRender extends CrawlerRender<CorruptedCrab, CrabRenderState, CorruptedCrabModel>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/corrupted_crab.png");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath(MODID, "textures/entity/corrupted_crab_glow.png");

    public CorruptedCrabRender(EntityRendererProvider.Context context, CorruptedCrab.Size size)
    {
        super(context, new CorruptedCrabModel(context.bakeLayer(CorruptedCrabModel.LAYER)), size.width * 0.55F);
        addLayer(new VoidGlowLayer<>(this, GLOW));
    }

    @Override
    public CrabRenderState createRenderState()
    {
        return new CrabRenderState();
    }

    @Override
    public void extractRenderState(CorruptedCrab crab, CrabRenderState state, float partialTicks)
    {
        super.extractRenderState(crab, state, partialTicks);
        state.modelScale = crab.size().modelScale;
    }

    @Override
    protected void scale(CrabRenderState state, PoseStack poseStack)
    {
        poseStack.scale(state.modelScale, state.modelScale, state.modelScale);
    }

    @Override
    public Identifier getTextureLocation(CrabRenderState state)
    {
        return TEXTURE;
    }
}
