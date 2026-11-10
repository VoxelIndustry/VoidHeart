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
import net.voxelindustry.voidheart.client.render.state.CrabRenderState;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A wide shell on six jointed legs, eyes on stalks and one claw far too big for it. Drawn at the medium size, one block across.
 */
public class CorruptedCrabModel extends EntityModel<CrabRenderState>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "corrupted_crab"), "main");

    private static final float[] LEG_Z = { -3.5F, 0, 3.5F };
    private static final float LEG_LIFT = 0.35F;
    private static final float LEG_SWING = 0.45F;
    private static final float LEG_RAISE = 0.25F;
    private static final float BIG_CLAW_REST_YAW = -0.55F;
    private static final float SMALL_CLAW_REST_YAW = 0.5F;
    private static final float PINCER_REST = 0.08F;
    private static final float PINCER_OPEN = 0.45F;
    private static final float STRIKE_PITCH = 0.9F;
    private static final float STALK_SWAY = 0.08F;

    private final ModelPart body;
    private final ModelPart[] rightLegs = new ModelPart[3];
    private final ModelPart[] leftLegs = new ModelPart[3];
    private final ModelPart bigClaw;
    private final ModelPart bigUpper;
    private final ModelPart bigLower;
    private final ModelPart smallClaw;
    private final ModelPart smallUpper;
    private final ModelPart smallLower;
    private final ModelPart rightStalk;
    private final ModelPart leftStalk;

    public CorruptedCrabModel(ModelPart root)
    {
        super(root);
        body = root.getChild("body");
        for (int i = 0; i < 3; i++)
        {
            rightLegs[i] = root.getChild("right_leg_" + i);
            leftLegs[i] = root.getChild("left_leg_" + i);
        }
        bigClaw = body.getChild("big_claw");
        bigUpper = bigClaw.getChild("upper");
        bigLower = bigClaw.getChild("lower");
        smallClaw = body.getChild("small_claw");
        smallUpper = smallClaw.getChild("upper");
        smallLower = smallClaw.getChild("lower");
        rightStalk = body.getChild("right_stalk");
        leftStalk = body.getChild("left_stalk");
    }

    public static LayerDefinition createBodyLayer()
    {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();

        var body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-7, 15, -6, 14, 5, 12)
                        .texOffs(0, 17).addBox(-5, 13, -4, 10, 2, 8),
                PartPose.ZERO);

        addStalk(body, "right_stalk", -2.5F);
        addStalk(body, "left_stalk", 2.5F);

        var big = body.addOrReplaceChild("big_claw", CubeListBuilder.create().texOffs(0, 27).addBox(-1.5F, -1.5F, -6, 3, 3, 6), PartPose.offset(-6, 17, -5));
        big.addOrReplaceChild("upper", CubeListBuilder.create().texOffs(18, 27).addBox(-2.5F, -3, -8, 5, 3, 8), PartPose.offset(0, 0, -6));
        big.addOrReplaceChild("lower", CubeListBuilder.create().texOffs(0, 38).addBox(-2.5F, 0, -6, 5, 2, 6), PartPose.offset(0, 0, -6));

        // A hair narrower than its spot, whose outer side would otherwise lie on the shell's
        var small = body.addOrReplaceChild("small_claw", CubeListBuilder.create().texOffs(22, 38).addBox(-1, -1, -4, 2, 2, 4, ModelBoxes.proud(-1, 0, 0)), PartPose.offset(6, 18, -5));
        small.addOrReplaceChild("upper", CubeListBuilder.create().texOffs(34, 38).addBox(-1.5F, -1, -4, 3, 1, 4), PartPose.offset(0, 0, -4));
        small.addOrReplaceChild("lower", CubeListBuilder.create().texOffs(34, 38).addBox(-1.5F, 0, -4, 3, 1, 4), PartPose.offset(0, 0, -4));

        for (int i = 0; i < 3; i++)
        {
            addLeg(root, "right_leg_" + i, -7, LEG_Z[i], -1);
            addLeg(root, "left_leg_" + i, 7, LEG_Z[i], 1);
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void addStalk(PartDefinition body, String name, float x)
    {
        body.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(52, 0).addBox(-0.5F, -4, -0.5F, 1, 4, 1)
                        .texOffs(56, 0).addBox(-1, -6, -1, 2, 2, 2),
                PartPose.offset(x, 14, -4));
    }

    private static void addLeg(PartDefinition root, String name, float x, float z, int side)
    {
        var upper = CubeListBuilder.create().texOffs(36, 17);
        var lower = CubeListBuilder.create().texOffs(50, 17);
        if (side > 0)
        {
            upper.mirror();
            lower.mirror();
        }
        var leg = root.addOrReplaceChild(name, upper.addBox(side > 0 ? 0 : -5, -1, -1, 5, 2, 2), PartPose.offset(x, 18, z));
        // A hair thinner than the upper leg it hangs from, so their sides don't meet in one plane
        leg.addOrReplaceChild("lower", lower.addBox(-1, 0, -1, 2, 7, 2, ModelBoxes.proud(0, 0, -1)), PartPose.offset(side * 5, 0, 0));
    }

    @Override
    public void setupAnim(CrabRenderState state)
    {
        super.setupAnim(state);

        float age = state.ageInTicks;
        for (int i = 0; i < 3; i++)
        {
            float phase = state.stride + i * 2.1F;
            setLeg(rightLegs[i], phase, state.strideAmount, -1);
            setLeg(leftLegs[i], phase + Mth.PI, state.strideAmount, 1);
        }

        rightStalk.zRot = Mth.sin(age * 0.07F) * STALK_SWAY;
        leftStalk.zRot = Mth.cos(age * 0.06F) * STALK_SWAY;

        float strike = Mth.sin(state.attack * Mth.PI);
        float open = state.aggressive ? PINCER_OPEN : PINCER_REST;
        bigClaw.yRot = BIG_CLAW_REST_YAW + strike * 0.5F;
        bigClaw.xRot = -0.2F + strike * STRIKE_PITCH;
        bigUpper.xRot = -open - strike * 0.3F;
        bigLower.xRot = open + strike * 0.3F;

        smallClaw.yRot = SMALL_CLAW_REST_YAW;
        smallUpper.xRot = -open * 0.7F;
        smallLower.xRot = open * 0.7F;

        body.y = Mth.sin(state.stride * 2) * 0.25F * state.strideAmount;
    }

    private static void setLeg(ModelPart leg, float phase, float amount, int side)
    {
        leg.yRot = Mth.sin(phase) * LEG_SWING * amount;
        leg.zRot = -side * (LEG_LIFT + Math.max(0, Mth.cos(phase)) * LEG_RAISE * amount);
        leg.getChild("lower").zRot = -leg.zRot;
    }
}
