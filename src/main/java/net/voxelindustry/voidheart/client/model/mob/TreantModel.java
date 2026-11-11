package net.voxelindustry.voidheart.client.model.mob;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.render.state.TreantRenderState;
import net.voxelindustry.voidheart.common.content.mob.TreantMoves;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A hunched trunk on two stumps of legs, with a knotted head crowned by bare branches. Its arms hang long and bent, ending in three curved claws each that it
 * carries just off the ground. While it comes out of its trunk it starts pressed flat into the wood, head bowed and arms against its sides, and tears free.
 */
public class TreantModel extends EntityModel<TreantRenderState>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "treant"), "main");

    private static final int CLAWS = 3;
    private static final float LEAN = 0.14F;
    private static final float ARM_PITCH = -0.35F;
    private static final float ARM_SPREAD = 0.12F;
    private static final float ELBOW = -0.55F;
    private static final float CLAW_CURL = -0.35F;
    private static final float CLAW_TIP_CURL = -0.6F;
    private static final float CLAW_OPEN = 0.25F;
    // How far each claw fans out sideways when the hand opens, the back one staying put
    private static final float[] CLAW_SPREAD = { 0.3F, -0.3F, 0 };
    private static final float WALK_RATE = 0.45F;
    private static final float WALK_REACH = 0.5F;
    private static final float WALK_ARM_SWING = 0.25F;
    private static final float WALK_BOB = 1;
    private static final float IDLE_SWAY = 0.04F;
    private static final float IDLE_RATE = 0.05F;

    private static final float SWEEP_PITCH = -1.45F;
    private static final float SWEEP_COCKED_YAW = -1.3F;
    private static final float SWEEP_FINISHED_YAW = 1.4F;
    private static final float SWEEP_BODY_COCKED = -0.5F;
    private static final float SWEEP_BODY_FINISHED = 0.55F;
    private static final float SWEEP_STRIKE_TICKS = 4;
    private static final float SWEEP_HOLD_TICKS = 3;
    private static final float BRACE_PITCH = -0.9F;
    private static final float BRACE_YAW = 0.5F;

    private static final float BOWED_HEAD = 0.75F;
    private static final float SUNK_HEAD = 3;
    private static final float PRESSED_BACK = -0.1F;
    private static final float TEAR_ARMS = 0.65F;
    private static final float TEAR_LURCH = 0.3F;
    private static final float SHUDDER = 0.035F;
    private static final float SHUDDER_RATE = 2.3F;

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final Arm rightArm;
    private final Arm leftArm;

    private record Arm(ModelPart upper, ModelPart forearm, ModelPart[] claws, ModelPart[] tips, float side)
    {
        static Arm of(ModelPart upper, float side)
        {
            var forearm = upper.getChild("forearm");
            var hand = forearm.getChild("hand");
            var claws = new ModelPart[CLAWS];
            var tips = new ModelPart[CLAWS];
            for (int i = 0; i < CLAWS; i++)
            {
                claws[i] = hand.getChild("claw_" + i);
                tips[i] = claws[i].getChild("tip");
            }
            return new Arm(upper, forearm, claws, tips, side);
        }
    }

    public TreantModel(ModelPart root)
    {
        super(root);
        body = root.getChild("body");
        head = body.getChild("head");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
        rightArm = Arm.of(body.getChild("right_arm"), -1);
        leftArm = Arm.of(body.getChild("left_arm"), 1);
    }

    public static LayerDefinition createBodyLayer()
    {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();

        var body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(24, 0).addBox(-6, -4, -4, 12, 5, 8)
                        .texOffs(0, 32).addBox(-7.5F, -20, -5, 15, 17, 10),
                PartPose.offset(0, 9, 0));
        addBranch(body, "back_branch_0", PartPose.offsetAndRotation(-3, -17, 4, 0.7F, 0.3F, -0.3F));
        addBranch(body, "back_branch_1", PartPose.offsetAndRotation(4, -13, 4, 0.9F, -0.4F, 0.4F));

        var head = body.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(64, 0).addBox(-4.5F, -8, -5, 9, 8, 9)
                        .texOffs(100, 0).addBox(-3.5F, -2, -5.4F, 7, 5, 0, ModelBoxes.FRONT),
                PartPose.offset(0, -20, -1));
        addBranch(head, "crown_left", PartPose.offsetAndRotation(3, -7, 0, 0.1F, 0, 0.45F));
        addBranch(head, "crown_right", PartPose.offsetAndRotation(-3, -7, 0.5F, -0.15F, 0, -0.5F));

        addArm(body, "right_arm", -9, false);
        addArm(body, "left_arm", 9, true);
        addLeg(root, "right_leg", -4, false);
        addLeg(root, "left_leg", 4, true);
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static void addBranch(PartDefinition parent, String name, PartPose pose)
    {
        var branch = parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(100, 8).addBox(-1, -8, -1, 2, 8, 2), pose);
        branch.addOrReplaceChild("twig", CubeListBuilder.create().texOffs(110, 8).addBox(-0.5F, -5, -0.5F, 1, 5, 1),
                PartPose.offsetAndRotation(0, -7, 0, 0.2F, 0, name.endsWith("left") || name.endsWith("1") ? -0.7F : 0.7F));
    }

    private static void addArm(PartDefinition body, String name, float x, boolean mirror)
    {
        var upper = body.addOrReplaceChild(name, cubes(mirror)
                        .texOffs(52, 32).addBox(-3.5F, -3.5F, -3.5F, 7, 6, 7)
                        .texOffs(80, 32).addBox(-2.5F, -2, -2.5F, 5, 15, 5),
                PartPose.offset(x, -18, 0));
        var forearm = upper.addOrReplaceChild("forearm", cubes(mirror).texOffs(100, 32).addBox(-2, 0, -2, 4, 13, 4), PartPose.offset(0, 12, 0));
        var hand = forearm.addOrReplaceChild("hand", cubes(mirror).texOffs(52, 46).addBox(-2.5F, 0, -2.5F, 5, 2, 5), PartPose.offset(0, 12.5F, 0));

        float[][] spots = { { -1.6F, -1.5F }, { 1.6F, -1.5F }, { 0, 1.5F } };
        for (int i = 0; i < CLAWS; i++)
        {
            var claw = hand.addOrReplaceChild("claw_" + i, cubes(mirror).texOffs(72, 46).addBox(-0.5F, 0, -0.5F, 1, 11, 1),
                    PartPose.offset(spots[i][0], 1.5F, spots[i][1]));
            claw.addOrReplaceChild("tip", cubes(mirror).texOffs(76, 46).addBox(-0.5F, 0, -0.5F, 1, 4, 1, ModelBoxes.SUNK), PartPose.offset(0, 10.5F, 0));
        }
    }

    private static void addLeg(PartDefinition root, String name, float x, boolean mirror)
    {
        root.addOrReplaceChild(name, cubes(mirror)
                        .texOffs(0, 0).addBox(-3, 0, -3, 6, 14, 6)
                        .texOffs(0, 20).addBox(-4, 12, -4.5F, 8, 3, 9)
                        .texOffs(36, 20).addBox(-1.5F, 12.5F, -7.5F, 3, 2, 4),
                PartPose.offset(x, 9, 0));
    }

    private static CubeListBuilder cubes(boolean mirror)
    {
        var builder = CubeListBuilder.create();
        return mirror ? builder.mirror() : builder;
    }

    @Override
    public void setupAnim(TreantRenderState state)
    {
        super.setupAnim(state);

        head.yRot = state.yRot * Mth.DEG_TO_RAD * 0.6F;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;
        body.xRot = LEAN;

        float speed = state.walkAnimationSpeed;
        float stride = Mth.cos(state.walkAnimationPos * WALK_RATE) * speed;
        rightLeg.xRot = stride * WALK_REACH;
        leftLeg.xRot = -stride * WALK_REACH;
        body.y += Math.abs(stride) * WALK_BOB;

        float sway = Mth.sin(state.ageInTicks * IDLE_RATE) * IDLE_SWAY;
        rest(rightArm, -stride * WALK_ARM_SWING + sway);
        rest(leftArm, stride * WALK_ARM_SWING - sway);

        if (state.sweep > 0)
            sweep(state.sweep);
        if (state.emerge > 0)
            emerge(state.emerge, state.ageInTicks);
    }

    private static void rest(Arm arm, float swing)
    {
        arm.upper.xRot = ARM_PITCH + swing;
        arm.upper.yRot = 0;
        arm.upper.zRot = -arm.side * ARM_SPREAD;
        arm.forearm.xRot = ELBOW;
        curl(arm, 0);
    }

    private static void curl(Arm arm, float open)
    {
        for (int i = 0; i < CLAWS; i++)
        {
            arm.claws[i].xRot = Mth.lerp(open, CLAW_CURL, CLAW_OPEN);
            arm.claws[i].zRot = CLAW_SPREAD[i] * open;
            arm.tips[i].xRot = Mth.lerp(open, CLAW_TIP_CURL, 0);
        }
    }

    /**
     * Rears back with the right arm thrown out and claws spread through the wind-up, then lashes it across its whole front, the trunk twisting with it, and
     * settles back over the follow-through.
     */
    private void sweep(float ticks)
    {
        int windup = TreantMoves.SWEEP_WINDUP_TICKS;
        float strikeStart = windup - SWEEP_STRIKE_TICKS / 2;
        float cock = smooth(ticks / strikeStart);
        float strike = smooth((ticks - strikeStart) / SWEEP_STRIKE_TICKS);
        float settle = smooth((ticks - windup - SWEEP_HOLD_TICKS) / (TreantMoves.FOLLOW_THROUGH_TICKS - SWEEP_HOLD_TICKS));
        float held = 1 - settle;

        var arm = rightArm;
        arm.upper.xRot = Mth.lerp(cock * held, arm.upper.xRot, SWEEP_PITCH);
        arm.upper.yRot = (ticks < strikeStart ? SWEEP_COCKED_YAW * cock : Mth.lerp(strike, SWEEP_COCKED_YAW, SWEEP_FINISHED_YAW)) * held;
        arm.forearm.xRot = Mth.lerp(cock * held, ELBOW, -0.15F);
        curl(arm, cock * held);

        leftArm.upper.xRot = Mth.lerp(cock * held, leftArm.upper.xRot, BRACE_PITCH);
        leftArm.upper.yRot = BRACE_YAW * cock * held;

        body.yRot = (ticks < strikeStart ? SWEEP_BODY_COCKED * cock : Mth.lerp(strike, SWEEP_BODY_COCKED, SWEEP_BODY_FINISHED)) * held;
    }

    /**
     * Pressed into the wood at first, shuddering, then the head lifts, the arms tear away from its sides and it lurches forward out of the bark.
     */
    private void emerge(float ticks, float age)
    {
        float progress = TreantMoves.emergeProgress(ticks);
        float fused = 1 - smooth(progress * 1.4F);
        float tear = Mth.sin(Mth.PI * Mth.clamp((progress - 0.2F) / 0.55F, 0, 1));
        float lurch = Mth.sin(Mth.PI * Mth.clamp((progress - 0.45F) / 0.45F, 0, 1));

        head.xRot = Mth.lerp(fused, head.xRot, BOWED_HEAD);
        head.yRot *= 1 - fused;
        head.y += SUNK_HEAD * fused;
        body.xRot = Mth.lerp(fused, LEAN, PRESSED_BACK) + TEAR_LURCH * lurch;
        body.zRot = Mth.sin(age * SHUDDER_RATE) * SHUDDER * Math.max(fused, tear);

        for (var arm : new Arm[] { rightArm, leftArm })
        {
            arm.upper.xRot *= 1 - fused;
            arm.upper.zRot = Mth.lerp(fused, arm.upper.zRot, arm.side * 0.04F) - arm.side * TEAR_ARMS * tear;
            arm.forearm.xRot *= 1 - fused;
            curl(arm, tear * 0.6F);
            for (int i = 0; i < CLAWS; i++)
            {
                arm.claws[i].xRot *= 1 - fused;
                arm.tips[i].xRot *= 1 - fused;
            }
        }
        rightLeg.xRot *= 1 - fused;
        leftLeg.xRot *= 1 - fused;
    }

    private static float smooth(float t)
    {
        float x = Mth.clamp(t, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
