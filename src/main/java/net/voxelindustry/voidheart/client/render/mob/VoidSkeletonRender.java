package net.voxelindustry.voidheart.client.render.mob;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.client.model.mob.VoidSkeletonModel;
import net.voxelindustry.voidheart.common.content.mob.VoidSkeleton;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidSkeletonRender extends HumanoidMobRenderer<VoidSkeleton, ZombieRenderState, VoidSkeletonModel>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/void_skeleton.png");

    public VoidSkeletonRender(EntityRendererProvider.Context context)
    {
        super(context, new VoidSkeletonModel(context.bakeLayer(VoidSkeletonModel.LAYER)), 0.5F);
    }

    @Override
    public ZombieRenderState createRenderState()
    {
        return new ZombieRenderState();
    }

    @Override
    public void extractRenderState(VoidSkeleton skeleton, ZombieRenderState state, float partialTicks)
    {
        super.extractRenderState(skeleton, state, partialTicks);
        state.isAggressive = skeleton.isAggressive();
    }

    @Override
    public Identifier getTextureLocation(ZombieRenderState state)
    {
        return TEXTURE;
    }
}
