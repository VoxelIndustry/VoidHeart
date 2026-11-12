package net.voxelindustry.voidheart.client.model.mob;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.render.state.SecurityMobRenderState;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A low copper hull on two iron treads, a tank at its rear and one eye in front. A jointed brass arm ending in a torch lies folded back along its top, and reaches up and forward to whatever it works on.
 */
public class FitterModel extends EntityModel<SecurityMobRenderState.Fitter>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "fitter"), "main");

    // The arm folded along the hull, and reaching to its work
    private static final float ARM_FOLDED = 1.3F;
    private static final float ARM_WORKING = -0.35F;
    private static final float FOREARM_FOLDED = -2.5F;
    private static final float FOREARM_WORKING = -0.5F;
    private static final float WORK_SHAKE = 0.12F;
    private static final float RATTLE = 0.3F;

    private final ModelPart body;
    private final ModelPart arm;
    private final ModelPart forearm;

    public FitterModel(ModelPart root)
    {
        super(root);
        body = root.getChild("body");
        arm = body.getChild("arm");
        forearm = arm.getChild("forearm");
    }

    public static LayerDefinition createBodyLayer()
    {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        // The tank stands a hair into the hull, the eye a hair off its face, and the treads a hair proud of where they meet it
        var body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4, -2, -5, 8, 4, 10)
                        .texOffs(36, 0).addBox(-3, -4.9F, 1, 6, 3, 4)
                        .texOffs(36, 8).addBox(-1, -1, -6.1F, 2, 2, 1),
                PartPose.offset(0, 19, 0));
        root.addOrReplaceChild("right_tread", CubeListBuilder.create().texOffs(0, 14).addBox(-1, -1.5F, -6, 2, 3, 12, ModelBoxes.PROUD), PartPose.offset(-4.2F, 22.4F, 0));
        root.addOrReplaceChild("left_tread", CubeListBuilder.create().mirror().texOffs(0, 14).addBox(-1, -1.5F, -6, 2, 3, 12, ModelBoxes.PROUD), PartPose.offset(4.2F, 22.4F, 0));

        var arm = body.addOrReplaceChild("arm", CubeListBuilder.create().texOffs(44, 8).addBox(-1, -6, -1, 2, 6, 2), PartPose.offset(0, -2.1F, -2.5F));
        arm.addOrReplaceChild("forearm", CubeListBuilder.create()
                        .texOffs(52, 8).addBox(-0.5F, -6, -0.5F, 1, 6, 1)
                        .texOffs(56, 8).addBox(-1, -7.1F, -1, 2, 1, 2),
                PartPose.offset(0, -6.1F, 0));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(SecurityMobRenderState.Fitter state)
    {
        super.setupAnim(state);
        float shake = state.reach >= 1 ? Mth.sin(state.ageInTicks * 1.7F) * WORK_SHAKE : 0;
        arm.xRot = Mth.lerp(state.reach, ARM_FOLDED, ARM_WORKING) + shake;
        forearm.xRot = Mth.lerp(state.reach, FOREARM_FOLDED, FOREARM_WORKING) - shake;
        // Its treads rattle the hull as it rolls
        body.y += Mth.sin(state.walkAnimationPos * 2.5F) * state.walkAnimationSpeed * RATTLE;
    }
}
