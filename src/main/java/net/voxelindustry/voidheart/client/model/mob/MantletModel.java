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
 * A squat machine behind a slab of riveted copper taller than itself. The shield carries everything it shows the room: its eye of amber glass near the top, and under it the shutter over the slit it fires through, which slides up while it shoots.
 */
public class MantletModel extends EntityModel<SecurityMobRenderState.Mantlet>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "mantlet"), "main");

    private static final float WALK_RATE = 0.7F;
    private static final float WALK_REACH = 0.5F;
    // Asleep its shield rests on the floor and its head hangs
    private static final float SHIELD_GROUNDED = 1.5F;
    private static final float BOWED = 0.4F;
    private static final float SHUTTER_TRAVEL = 3.5F;
    private static final float BASH_REACH = 5;
    private static final float ARM_FORWARD = -1.35F;

    private final ModelPart head;
    private final ModelPart shield;
    private final ModelPart shutter;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public MantletModel(ModelPart root)
    {
        super(root);
        head = root.getChild("head");
        shield = root.getChild("shield");
        shutter = shield.getChild("shutter");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer()
    {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();

        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3, -5, -3, 6, 5, 6), PartPose.offset(0, 4, 0));
        // The trunk stands a hair proud of the head, the legs and the arms it meets, and the tank a hair off its back
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 12).addBox(-5, 0, -3, 10, 10, 6, ModelBoxes.PROUD)
                        .texOffs(32, 12).addBox(-3, 1, 3.2F, 6, 8, 3),
                PartPose.offset(0, 4, 0));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(52, 12).addBox(-2, -2, -2, 4, 10, 4), PartPose.offset(-7.2F, 7, 0));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().mirror().texOffs(52, 12).addBox(-2, -2, -2, 4, 10, 4), PartPose.offset(7.2F, 7, 0));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 30).addBox(-2, 0, -2, 4, 10, 4), PartPose.offset(-2.6F, 14.1F, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().mirror().texOffs(0, 30).addBox(-2, 0, -2, 4, 10, 4), PartPose.offset(2.6F, 14.1F, 0));

        // The shield, with its eye standing off its face
        var shield = root.addOrReplaceChild("shield", CubeListBuilder.create()
                        .texOffs(24, 30).addBox(-11, -31, -1, 22, 30, 2)
                        .texOffs(76, 36).addBox(-2, -30.5F, -2.2F, 4, 4, 1),
                PartPose.offset(0, 24, -9.5F));
        shield.addOrReplaceChild("shutter", CubeListBuilder.create().texOffs(76, 30).addBox(-4, -1.5F, -0.5F, 8, 3, 1), PartPose.offset(0, -24, -1.7F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public void setupAnim(SecurityMobRenderState.Mantlet state)
    {
        super.setupAnim(state);
        float up = Mth.clamp(state.wake * 2 - 1, 0, 1);
        head.xRot = Mth.lerp(up, BOWED, state.xRot * Mth.DEG_TO_RAD);
        head.yRot = up * state.yRot * Mth.DEG_TO_RAD;
        shield.y += Mth.lerp(up, SHIELD_GROUNDED, 0);
        shutter.y -= state.slit * SHUTTER_TRAVEL;
        // Both arms reach forward to the back of the shield
        rightArm.xRot = ARM_FORWARD;
        leftArm.xRot = ARM_FORWARD;
        if (up <= 0)
            return;

        float stride = Mth.cos(state.walkAnimationPos * WALK_RATE) * state.walkAnimationSpeed;
        rightLeg.xRot = stride * WALK_REACH;
        leftLeg.xRot = -stride * WALK_REACH;
        // A bash throws the shield forward and brings it back
        shield.z -= Mth.sin(state.attack * Mth.PI) * BASH_REACH;
        shield.y += Mth.sin(state.walkAnimationPos * WALK_RATE * 2) * state.walkAnimationSpeed * 0.4F;
    }
}
