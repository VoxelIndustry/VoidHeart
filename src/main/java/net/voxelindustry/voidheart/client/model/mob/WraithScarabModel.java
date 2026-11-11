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
import net.voxelindustry.voidheart.client.render.state.WraithScarabRenderState;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A dung beetle gone to shade: split wing cases over a low body, a toothed rake of a head with short mandibles under it, clubbed feelers and torn wisps
 * trailing from the wing cases. The wing cases flare as it lands from a hop and fold back.
 */
public class WraithScarabModel extends EntityModel<WraithScarabRenderState>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "wraith_scarab"), "main");

    private static final float[] LEG_Z = { -3.8F, 0.2F, 3.8F };
    private static final float LEG_LIFT = 0.35F;
    private static final float LEG_TWITCH = 0.06F;
    private static final float LEG_SPLAY = 0.35F;

    private static final int FLARE_TICKS = 6;
    private static final float FLARE_ANGLE = 0.9F;
    private static final float FLARE_SPREAD = 0.25F;
    private static final float CASE_BREATH = 0.03F;

    private static final float MANDIBLE_REST = 0.12F;
    private static final float MANDIBLE_AGGRESSIVE = 0.3F;
    private static final float MANDIBLE_TWITCH = 0.08F;
    private static final float LUNGE = 1.6F;
    private static final float LUNGE_DIP = 0.2F;

    private static final float FEELER_LIFT = -0.5F;
    private static final float FEELER_SPREAD = 0.45F;
    private static final float FEELER_SWAY = 0.12F;
    private static final float FEELER_FORWARD = 0.2F;

    private static final float WISP_DROOP = 0.25F;
    private static final float WISP_SWAY = 0.15F;

    private final ModelPart head;
    private final ModelPart rightCase;
    private final ModelPart leftCase;
    private final ModelPart rightMandible;
    private final ModelPart leftMandible;
    private final ModelPart rightFeeler;
    private final ModelPart leftFeeler;
    private final ModelPart rightWisp;
    private final ModelPart leftWisp;
    private final ModelPart[] rightLegs = new ModelPart[3];
    private final ModelPart[] leftLegs = new ModelPart[3];

    public WraithScarabModel(ModelPart root)
    {
        super(root);
        var body = root.getChild("body");
        head = body.getChild("head");
        rightCase = body.getChild("right_case");
        leftCase = body.getChild("left_case");
        rightMandible = head.getChild("right_mandible");
        leftMandible = head.getChild("left_mandible");
        rightFeeler = head.getChild("right_feeler");
        leftFeeler = head.getChild("left_feeler");
        rightWisp = body.getChild("right_wisp");
        leftWisp = body.getChild("left_wisp");
        for (int i = 0; i < 3; i++)
        {
            rightLegs[i] = root.getChild("right_leg_" + i);
            leftLegs[i] = root.getChild("left_leg_" + i);
        }
    }

    public static LayerDefinition createBodyLayer()
    {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();

        var body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.5F, 19.25F, -2, 7, 2, 9)
                        .texOffs(32, 0).addBox(-3, 18.5F, -5.5F, 6, 3, 4),
                PartPose.ZERO);

        // Each a hair narrower than its half, so the two don't meet in one plane down the middle
        body.addOrReplaceChild("right_case", CubeListBuilder.create().texOffs(0, 11).addBox(-4, 0, 0, 4, 3, 9, ModelBoxes.proud(-1, 0, 0)),
                PartPose.offset(0, 18, -2.5F));
        body.addOrReplaceChild("left_case", CubeListBuilder.create().texOffs(0, 11).mirror().addBox(0, 0, 0, 4, 3, 9, ModelBoxes.proud(-1, 0, 0)),
                PartPose.offset(0, 18, -2.5F));

        var head = body.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(32, 7).addBox(-2, -1.25F, -2.5F, 4, 2, 3)
                        .texOffs(46, 7).addBox(-3, 0, -4, 6, 1, 2),
                PartPose.offset(0, 20, -5.5F));
        head.addOrReplaceChild("right_mandible", CubeListBuilder.create().texOffs(54, 0).addBox(-0.5F, -0.5F, -3, 1, 1, 3), PartPose.offset(-1.2F, 0.3F, -2.3F));
        head.addOrReplaceChild("left_mandible", CubeListBuilder.create().texOffs(54, 0).mirror().addBox(-0.5F, -0.5F, -3, 1, 1, 3), PartPose.offset(1.2F, 0.3F, -2.3F));
        head.addOrReplaceChild("right_feeler", CubeListBuilder.create().texOffs(52, 10).addBox(-0.5F, -0.5F, -3, 1, 1, 3),
                PartPose.offsetAndRotation(-1.4F, -1, -2.2F, FEELER_LIFT, FEELER_SPREAD, 0));
        head.addOrReplaceChild("left_feeler", CubeListBuilder.create().texOffs(52, 10).mirror().addBox(-0.5F, -0.5F, -3, 1, 1, 3),
                PartPose.offsetAndRotation(1.4F, -1, -2.2F, FEELER_LIFT, -FEELER_SPREAD, 0));

        addWisp(body, "right_wisp", -1.8F);
        addWisp(body, "left_wisp", 1.8F);

        for (int i = 0; i < 3; i++)
        {
            addLeg(root, "right_leg_" + i, -3.3F, LEG_Z[i], -1);
            addLeg(root, "left_leg_" + i, 3.3F, LEG_Z[i], 1);
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static void addWisp(PartDefinition body, String name, float x)
    {
        body.addOrReplaceChild(name, CubeListBuilder.create().texOffs(38, 13).addBox(-1, 0, 0, 2, 0, 3, ModelBoxes.TOP),
                PartPose.offsetAndRotation(x, 18.3F, 6.4F, WISP_DROOP, 0, 0));
    }

    private static void addLeg(PartDefinition root, String name, float x, float z, int side)
    {
        var upper = CubeListBuilder.create().texOffs(26, 13);
        var lower = CubeListBuilder.create().texOffs(34, 13);
        if (side > 0)
        {
            upper.mirror();
            lower.mirror();
        }
        var leg = root.addOrReplaceChild(name, upper.addBox(side > 0 ? 0 : -3, -0.5F, -0.5F, 3, 1, 1), PartPose.offset(x, 20.8F, z));
        // A hair thinner than the upper leg it hangs from, so their sides don't meet in one plane
        leg.addOrReplaceChild("lower", lower.addBox(-0.5F, 0, -0.5F, 1, 4, 1, ModelBoxes.proud(0, 0, -1)), PartPose.offset(side * 3, 0, 0));
    }

    @Override
    public void setupAnim(WraithScarabRenderState state)
    {
        super.setupAnim(state);

        float age = state.ageInTicks;
        float flare = state.sinceHop < FLARE_TICKS ? 1 - state.sinceHop / FLARE_TICKS : 0;
        flare *= flare;
        float breath = Mth.sin(age * 0.08F) * CASE_BREATH;
        rightCase.zRot = breath + flare * FLARE_ANGLE;
        leftCase.zRot = -breath - flare * FLARE_ANGLE;
        rightCase.yRot = flare * FLARE_SPREAD;
        leftCase.yRot = -flare * FLARE_SPREAD;

        float strike = Mth.sin(state.attack * Mth.PI);
        head.z -= strike * LUNGE;
        head.xRot = strike * LUNGE_DIP;

        float open = (state.aggressive ? MANDIBLE_AGGRESSIVE : MANDIBLE_REST) + Math.max(0, Mth.sin(age * 0.23F)) * MANDIBLE_TWITCH;
        // Wide as it lunges, shut as it lands
        open = state.attack < 0.5F ? open + strike * MANDIBLE_AGGRESSIVE : open * (1 - strike);
        rightMandible.yRot = open;
        leftMandible.yRot = -open;

        float forward = state.aggressive ? FEELER_FORWARD : 0;
        rightFeeler.xRot = FEELER_LIFT + forward + Mth.sin(age * 0.11F) * FEELER_SWAY;
        leftFeeler.xRot = FEELER_LIFT + forward + Mth.sin(age * 0.11F + 1.7F) * FEELER_SWAY;
        rightFeeler.yRot = FEELER_SPREAD + Mth.cos(age * 0.07F) * FEELER_SWAY;
        leftFeeler.yRot = -FEELER_SPREAD - Mth.cos(age * 0.07F + 0.9F) * FEELER_SWAY;

        rightWisp.xRot = WISP_DROOP + Mth.sin(age * 0.09F) * WISP_SWAY - flare * WISP_DROOP;
        leftWisp.xRot = WISP_DROOP + Mth.sin(age * 0.09F + 1.3F) * WISP_SWAY - flare * WISP_DROOP;

        for (int i = 0; i < 3; i++)
        {
            setLeg(rightLegs[i], -1, i, age);
            setLeg(leftLegs[i], 1, i, age);
        }
    }

    private static void setLeg(ModelPart leg, int side, int index, float age)
    {
        float twitch = Mth.sin(age * 0.13F + index * 2.1F + side) * LEG_TWITCH;
        leg.yRot = (index - 1) * LEG_SPLAY * -side + twitch;
        leg.zRot = -side * (LEG_LIFT + twitch);
        leg.getChild("lower").zRot = -leg.zRot;
    }
}
