package net.voxelindustry.voidheart.client.model.mob;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A skeleton with empty hands, raising its arms only once it has something to reach for.
 */
public class VoidSkeletonModel extends HumanoidModel<ZombieRenderState>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "void_skeleton"), "main");

    public VoidSkeletonModel(ModelPart root)
    {
        super(root);
    }

    public static LayerDefinition createBodyLayer()
    {
        var mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0);
        var root = mesh.getRoot();
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-1, -2, -1, 2, 12, 2), PartPose.offset(-5, 2, 0));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1, -2, -1, 2, 12, 2), PartPose.offset(5, 2, 0));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-1, 0, -1, 2, 12, 2), PartPose.offset(-2, 12, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-1, 0, -1, 2, 12, 2), PartPose.offset(2, 12, 0));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    protected void setupAttackAnimation(ZombieRenderState state)
    {
        super.setupAttackAnimation(state);
        if (state.isAggressive)
            AnimationUtils.animateZombieArms(leftArm, rightArm, true, state);
    }
}
