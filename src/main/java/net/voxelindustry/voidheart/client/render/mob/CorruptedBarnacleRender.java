package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.client.model.mob.CorruptedBarnacleModel;
import net.voxelindustry.voidheart.client.render.state.BarnacleRenderState;
import net.voxelindustry.voidheart.common.content.mob.CorruptedBarnacle;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class CorruptedBarnacleRender extends CrawlerRender<CorruptedBarnacle, BarnacleRenderState, CorruptedBarnacleModel>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/corrupted_barnacle.png");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath(MODID, "textures/entity/corrupted_barnacle_glow.png");

    private static final float[] MODEL_SCALES = { 0.67F, 1, 1.33F };

    public CorruptedBarnacleRender(EntityRendererProvider.Context context)
    {
        super(context, new CorruptedBarnacleModel(context.bakeLayer(CorruptedBarnacleModel.LAYER)), 0.35F);
        addLayer(new VoidGlowLayer<>(this, GLOW));
    }

    @Override
    public BarnacleRenderState createRenderState()
    {
        return new BarnacleRenderState();
    }

    @Override
    public void extractRenderState(CorruptedBarnacle barnacle, BarnacleRenderState state, float partialTicks)
    {
        super.extractRenderState(barnacle, state, partialTicks);
        state.rooted = barnacle.isRooted();
        state.modelScale = MODEL_SCALES[barnacle.stage()];
    }

    @Override
    protected void scale(BarnacleRenderState state, PoseStack poseStack)
    {
        poseStack.scale(state.modelScale, state.modelScale, state.modelScale);
    }

    @Override
    public Identifier getTextureLocation(BarnacleRenderState state)
    {
        return TEXTURE;
    }
}
