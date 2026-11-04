package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.Monster;
import net.voxelindustry.voidheart.client.model.mob.FallenModel;
import net.voxelindustry.voidheart.client.render.state.FallenRenderState;
import org.jspecify.annotations.Nullable;

/**
 * The same small figure for both lesser ranks, drawn at a scale fitting their 1.4 block height.
 */
public class FallenRender<T extends Monster> extends MobRenderer<T, FallenRenderState, FallenModel>
{
    private static final float MODEL_SCALE = 0.7F;

    private final Identifier texture;

    public FallenRender(EntityRendererProvider.Context context, ModelLayerLocation layer, Identifier texture, @Nullable Identifier glow)
    {
        super(context, new FallenModel(context.bakeLayer(layer)), 0.35F);
        this.texture = texture;
        if (glow != null)
            addLayer(new VoidGlowLayer<>(this, glow));
    }

    @Override
    public FallenRenderState createRenderState()
    {
        return new FallenRenderState();
    }

    @Override
    public void extractRenderState(T mob, FallenRenderState state, float partialTicks)
    {
        super.extractRenderState(mob, state, partialTicks);
        state.aggressive = mob.isAggressive();
        state.attack = mob.getSwingAnimation(partialTicks);
    }

    @Override
    protected void scale(FallenRenderState state, PoseStack poseStack)
    {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

    @Override
    public Identifier getTextureLocation(FallenRenderState state)
    {
        return texture;
    }
}
