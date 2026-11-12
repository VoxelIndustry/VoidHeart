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
import net.voxelindustry.voidheart.client.render.state.AutomatonRenderState;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A machine of banded copper plates built around a boiler: a squat head that is mostly one brass-ringed eye, a glass port and a pressure gauge on its chest, a tank with two vents on its back, heavy shoulders. Its piston lance lies along its right arm, point at the far end, so that the arm's pitch alone carries it from hanging at rest to levelled at the shoulder.
 */
public class AutomatonModel extends EntityModel<AutomatonRenderState>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "automaton"), "main");

    private static final float WALK_RATE = 0.8F;
    private static final float WALK_REACH = 0.7F;
    // Asleep its head hangs, and the needle of its gauge lies at rest
    private static final float BOWED = 0.45F;
    private static final float NEEDLE_REST = -1.3F;
    private static final float NEEDLE_PRESSURE = 0.9F;
    private static final float NEEDLE_TREMBLE = 0.08F;
    // Awake it carries its lance low and forward, the left hand across to it
    private static final float CARRY_PITCH = -0.6F;
    private static final float LEFT_CARRY_PITCH = -0.95F;
    private static final float LEFT_CARRY_YAW = 0.55F;
    private static final float LEFT_AIM_YAW = 0.7F;
    private static final float THRUST_REACH = 0.7F;
    private static final float THRUST_LEAN = 0.25F;

    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart needle;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public AutomatonModel(ModelPart root)
    {
        super(root);
        head = root.getChild("head");
        body = root.getChild("body");
        needle = body.getChild("needle");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer()
    {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();

        // The eye's ring stands a hair off the face it is set in
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4, -6, -4, 8, 6, 8)
                        .texOffs(32, 0).addBox(-2.5F, -5.5F, -5, 5, 5, 1, ModelBoxes.proud(0, 0, -1)),
                PartPose.offset(0, -2, 0));

        // The trunk stops a hair short of the head above and the waist below, and the tank stands a hair off its back
        var body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 16).addBox(-5, -2, -3, 10, 11, 6, ModelBoxes.proud(0, -1, 0))
                        .texOffs(32, 36).addBox(-4, 9, -2.5F, 8, 3, 5)
                        .texOffs(32, 16).addBox(-4, -1, 3, 8, 9, 3, ModelBoxes.proud(0, 0, -1))
                        .texOffs(54, 16).addBox(-3, -5, 4.5F, 2, 5, 2)
                        .texOffs(54, 16).addBox(1, -5, 4.5F, 2, 5, 2),
                PartPose.ZERO);
        body.addOrReplaceChild("needle", CubeListBuilder.create().texOffs(62, 16).addBox(-0.5F, -2, 0, 1, 2, 0, ModelBoxes.FRONT), PartPose.offset(2.5F, 3.5F, -3.1F));

        var rightArm = addArm(root, "right_arm", -7, false);
        addArm(root, "left_arm", 7, true);
        rightArm.addOrReplaceChild("lance", CubeListBuilder.create()
                        .texOffs(16, 36).addBox(-2, 2, -5.5F, 3, 12, 3)
                        .texOffs(28, 36).addBox(-1, 13, -4.5F, 1, 9, 1),
                PartPose.ZERO);

        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 36).addBox(-2, 0, -2, 4, 12, 4, ModelBoxes.proud(0, -1, 0)), PartPose.offset(-2.5F, 12, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().mirror().texOffs(0, 36).addBox(-2, 0, -2, 4, 12, 4, ModelBoxes.proud(0, -1, 0)), PartPose.offset(2.5F, 12, 0));
        return LayerDefinition.create(mesh, 128, 64);
    }

    /** An arm under a heavy shoulder plate, which stands a hair proud of the trunk it meets. */
    private static net.minecraft.client.model.geom.builders.PartDefinition addArm(net.minecraft.client.model.geom.builders.PartDefinition root, String name, float x, boolean mirror)
    {
        var builder = CubeListBuilder.create();
        if (mirror)
            builder.mirror();
        builder.texOffs(64, 10).addBox(-2, -2, -2, 4, 12, 4, ModelBoxes.PROUD)
                .texOffs(64, 0).addBox(-2.5F, -3, -2.5F, 5, 4, 5, ModelBoxes.proud(2, 0, 0));
        return root.addOrReplaceChild(name, builder, PartPose.offset(x, 0, 0));
    }

    @Override
    public void setupAnim(AutomatonRenderState state)
    {
        super.setupAnim(state);

        // The needle climbs as the boiler comes up, and trembles once it is there
        needle.zRot = Mth.lerp(state.wake, NEEDLE_REST, NEEDLE_PRESSURE) + (state.wake >= 1 ? Mth.sin(state.ageInTicks * 0.9F) * NEEDLE_TREMBLE : 0);
        float up = Mth.clamp(state.wake * 2 - 1, 0, 1);
        head.xRot = Mth.lerp(up, BOWED, state.xRot * Mth.DEG_TO_RAD);
        head.yRot = up * state.yRot * Mth.DEG_TO_RAD;
        if (up <= 0)
            return;

        float stride = Mth.cos(state.walkAnimationPos * WALK_RATE) * state.walkAnimationSpeed;
        rightLeg.xRot = stride * WALK_REACH;
        leftLeg.xRot = -stride * WALK_REACH;

        float thrust = Mth.sin(state.attack * Mth.PI);
        float level = -Mth.HALF_PI + head.xRot;
        rightArm.xRot = up * (Mth.lerp(state.raise, CARRY_PITCH, level) - thrust * THRUST_REACH);
        leftArm.xRot = up * (Mth.lerp(state.raise, LEFT_CARRY_PITCH, level) - thrust * THRUST_REACH);
        leftArm.yRot = up * Mth.lerp(state.raise, LEFT_CARRY_YAW, LEFT_AIM_YAW);
        body.xRot = thrust * THRUST_LEAN;
    }
}
