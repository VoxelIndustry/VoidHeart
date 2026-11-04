package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.model.mob.FallenElderModel;
import net.voxelindustry.voidheart.client.render.state.FallenElderRenderState;
import net.voxelindustry.voidheart.common.content.mob.FallenElder;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class FallenElderRender extends MobRenderer<FallenElder, FallenElderRenderState, FallenElderModel>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/fallen_elder.png");

    private static final float MODEL_SCALE = 0.95F;
    private static final float CAST_EASE_TICKS = 5;

    public FallenElderRender(EntityRendererProvider.Context context)
    {
        super(context, new FallenElderModel(context.bakeLayer(FallenElderModel.LAYER)), 0.55F);
    }

    @Override
    public FallenElderRenderState createRenderState()
    {
        return new FallenElderRenderState();
    }

    @Override
    public void extractRenderState(FallenElder elder, FallenElderRenderState state, float partialTicks)
    {
        super.extractRenderState(elder, state, partialTicks);
        state.cast = Mth.clamp((elder.castTicks() - partialTicks) / CAST_EASE_TICKS, 0, 1);
    }

    @Override
    protected void scale(FallenElderRenderState state, PoseStack poseStack)
    {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

    @Override
    public Identifier getTextureLocation(FallenElderRenderState state)
    {
        return TEXTURE;
    }
}
