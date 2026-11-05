package net.voxelindustry.voidheart.client.model.mob;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.render.state.VoidGhoulRenderState;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A zombie bent forward at the hips, with jaws growing out of its back, shoulder and belly that keep opening and closing on their own.
 */
public class VoidGhoulModel extends HumanoidModel<VoidGhoulRenderState>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "void_ghoul"), "main");

    private static final float SLOUCH = 0.5F;
    private static final float TORSO_LENGTH = 12;
    private static final float ARM_DROP_ON_TORSO = 2;
    private static final float HEAD_PITCH_OFFSET = -0.35F;
    private static final float WHEEZE_RATE = 0.045F;
    private static final float WHEEZE_RANGE = 0.16F;
    private static final float WHEEZE_REST = 0.2F;
    private static final float FLARE_OPEN = 0.75F;
    private static final String[] MAWS = { "maw_back", "maw_belly", "maw_shoulder" };
    private static final float[] MAW_PHASES = { 0, 2.1F, 4.3F };

    private final ModelPart[] uppers = new ModelPart[MAWS.length];
    private final ModelPart[] lowers = new ModelPart[MAWS.length];

    public VoidGhoulModel(ModelPart root)
    {
        super(root);
        for (int i = 0; i < MAWS.length; i++)
        {
            var maw = i == 2 ? rightArm.getChild(MAWS[i]) : body.getChild(MAWS[i]);
            uppers[i] = maw.getChild("upper");
            lowers[i] = maw.getChild("lower");
        }
    }

    public static LayerDefinition createBodyLayer()
    {
        var mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0);
        var root = mesh.getRoot();
        addMaw(root.getChild("body"), MAWS[0], PartPose.offsetAndRotation(0.5F, 5, 2, 0, Mth.PI, 0).withScale(1.1F));
        addMaw(root.getChild("body"), MAWS[1], PartPose.offsetAndRotation(1.2F, 9, -2, 0, 0, 0).withScale(0.8F));
        addMaw(root.getChild("right_arm"), MAWS[2], PartPose.offsetAndRotation(-3, 0.5F, 0, 0, Mth.HALF_PI, 0).withScale(0.7F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void addMaw(PartDefinition parent, String name, PartPose pose)
    {
        var maw = parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(0, 32).addBox(-3, -3, -0.5F, 6, 6, 1), pose);
        maw.addOrReplaceChild("upper", CubeListBuilder.create()
                        .texOffs(16, 32).addBox(-3, -2, -4, 6, 2, 4)
                        .texOffs(0, 40).addBox(-2.5F, 0, -3.8F, 1, 1, 1)
                        .texOffs(0, 40).addBox(-0.5F, 0, -3.8F, 1, 1, 1)
                        .texOffs(0, 40).addBox(1.5F, 0, -3.8F, 1, 1, 1),
                PartPose.offset(0, -0.5F, -0.5F));
        maw.addOrReplaceChild("lower", CubeListBuilder.create()
                        .texOffs(36, 32).addBox(-3, 0, -4, 6, 2, 4)
                        .texOffs(0, 40).addBox(-1.5F, -1, -3.8F, 1, 1, 1)
                        .texOffs(0, 40).addBox(0.5F, -1, -3.8F, 1, 1, 1),
                PartPose.offset(0, 0.5F, -0.5F));
    }

    @Override
    protected void setupAttackAnimation(VoidGhoulRenderState state)
    {
        super.setupAttackAnimation(state);
        if (state.isAggressive)
            AnimationUtils.animateZombieArms(leftArm, rightArm, true, state);
    }

    @Override
    public void setupAnim(VoidGhoulRenderState state)
    {
        super.setupAnim(state);
        slouch();
        moveMaws(state);
    }

    /**
     * Turns the torso around the hips rather than the neck, so that the legs stay under it and the head and arms follow.
     */
    private void slouch()
    {
        float forward = -TORSO_LENGTH * Mth.sin(SLOUCH);
        float sag = TORSO_LENGTH * (1 - Mth.cos(SLOUCH));

        body.xRot += SLOUCH;
        body.z += forward;
        body.y += sag;

        head.z += forward;
        head.y += sag;
        head.xRot += HEAD_PITCH_OFFSET;

        float armForward = forward + ARM_DROP_ON_TORSO * Mth.sin(SLOUCH);
        float armSag = sag - ARM_DROP_ON_TORSO * (1 - Mth.cos(SLOUCH));
        rightArm.z += armForward;
        rightArm.y += armSag;
        leftArm.z += armForward;
        leftArm.y += armSag;
    }

    private void moveMaws(VoidGhoulRenderState state)
    {
        for (int i = 0; i < MAWS.length; i++)
        {
            float wheeze = WHEEZE_REST + WHEEZE_RANGE * Mth.sin(state.ageInTicks * WHEEZE_RATE + MAW_PHASES[i]);
            float open = wheeze + state.flare * FLARE_OPEN;
            uppers[i].xRot = -open;
            lowers[i].xRot = open;
        }
    }
}
