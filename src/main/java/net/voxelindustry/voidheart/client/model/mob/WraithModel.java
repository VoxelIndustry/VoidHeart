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
import net.voxelindustry.voidheart.client.render.state.WraithRenderState;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A gaunt figure under a torn shroud, its long arms ending in claws and its wrappings trailing off in wisps below the knees.
 */
public class WraithModel extends EntityModel<WraithRenderState>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "wraith"), "main");

    private static final int WISPS = 6;
    private static final float HEAD_DROOP = 0.12F;
    private static final float HEAD_FORWARD = -1;
    private static final float HEAD_SINK = 0.6F;
    private static final float WISP_SWAY_RATE = 0.09F;
    private static final float WISP_SWAY = 0.16F;
    private static final float WALK_RATE = 0.7F;
    private static final float WALK_REACH = 0.6F;
    private static final float REACH_FORWARD = -1.35F;
    private static final float ARM_BREATH = 0.05F;
    private static final float CLAW_REST = 0.15F;
    private static final float CLAW_OPEN = 0.5F;

    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart[] wisps = new ModelPart[WISPS];
    private final ModelPart[] claws = new ModelPart[6];

    public WraithModel(ModelPart root)
    {
        super(root);
        head = root.getChild("head");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
        var skirt = root.getChild("skirt");
        for (int i = 0; i < WISPS; i++)
            wisps[i] = skirt.getChild("wisp_" + i);
        for (int i = 0; i < 3; i++)
        {
            claws[i] = rightArm.getChild("claw_" + i);
            claws[i + 3] = leftArm.getChild("claw_" + i);
        }
    }

    public static LayerDefinition createBodyLayer()
    {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();

        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.5F, -8, -3.5F, 7, 8, 7)
                        // The hood reaches a hair behind the skull, whose back would otherwise lie on its own
                        .texOffs(28, 0).addBox(-4, -8.5F, -4.5F, 8, 10, 8, ModelBoxes.proud(0, 0, 1))
                        .texOffs(40, 36).addBox(-2.5F, -10.5F, -3, 5, 2, 6)
                        .texOffs(40, 44).addBox(-3, -7, 3.5F, 6, 9, 1),
                PartPose.ZERO);
        root.addOrReplaceChild("torso", CubeListBuilder.create()
                        .texOffs(0, 15).addBox(-3.5F, 0, -2, 7, 12, 4)
                        .texOffs(18, 34).addBox(-5, -0.5F, -3, 10, 4, 6),
                PartPose.ZERO);

        var skirt = root.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(22, 19).addBox(-4, 0, -2.5F, 8, 9, 5), PartPose.offset(0, 10, 0));
        // A hair out from the skirt they hang off, each showing one face, the one that is outward before it turns
        float front = 2.5F + ModelBoxes.GAP;
        float side = 4 + ModelBoxes.GAP;
        float[][] wispSpots = { { -2.5F, -front, 0 }, { 2.5F, -front, 0 }, { -2.5F, front, 0 }, { 2.5F, front, 0 }, { -side, 0, Mth.HALF_PI }, { side, 0, Mth.HALF_PI } };
        for (int i = 0; i < WISPS; i++)
        {
            boolean outwardNorth = wispSpots[i][1] < 0 || wispSpots[i][0] < 0 && wispSpots[i][1] == 0;
            skirt.addOrReplaceChild("wisp_" + i, CubeListBuilder.create().texOffs(12, 34).addBox(-1.5F, 0, 0, 3, 6, 0, outwardNorth ? ModelBoxes.FRONT : ModelBoxes.BACK),
                    PartPose.offsetAndRotation(wispSpots[i][0], 8, wispSpots[i][1], 0, wispSpots[i][2], 0));
        }

        addArm(root, "right_arm", -5, false);
        addArm(root, "left_arm", 5, true);

        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 31).addBox(-1, 0, -1, 2, 12, 2), PartPose.offset(-2, 12, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 31).mirror().addBox(-1, 0, -1, 2, 12, 2), PartPose.offset(2, 12, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void addArm(PartDefinition root, String name, float x, boolean mirror)
    {
        var builder = CubeListBuilder.create().texOffs(52, 19);
        if (mirror)
            builder.mirror();
        var arm = root.addOrReplaceChild(name, builder.addBox(-1, -1, -1, 2, 15, 2), PartPose.offset(x, 1, 0));
        float[][] clawSpots = { { -0.9F, -0.6F }, { 0.9F, -0.6F }, { 0, 0.8F } };
        for (int i = 0; i < 3; i++)
        {
            arm.addOrReplaceChild("claw_" + i, CubeListBuilder.create().texOffs(8, 31).addBox(-0.5F, 0, -0.5F, 1, 5, 1),
                    PartPose.offset(clawSpots[i][0], 13.5F, clawSpots[i][1]));
        }
    }

    @Override
    public void setupAnim(WraithRenderState state)
    {
        super.setupAnim(state);

        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD + HEAD_DROOP;
        head.z += HEAD_FORWARD;
        head.y += HEAD_SINK;

        float pos = state.walkAnimationPos;
        float speed = state.walkAnimationSpeed;
        rightLeg.xRot = Mth.cos(pos * WALK_RATE) * WALK_REACH * speed;
        leftLeg.xRot = -rightLeg.xRot;

        float breath = Mth.sin(state.ageInTicks * 0.05F) * ARM_BREATH;
        float reach = state.aggressive ? REACH_FORWARD : 0;
        float swing = Mth.sin(state.attack * Mth.PI);
        rightArm.xRot = reach + breath - swing * 1.1F + (state.aggressive ? 0 : -rightLeg.xRot * 0.7F);
        leftArm.xRot = reach - breath - swing * 0.5F + (state.aggressive ? 0 : rightLeg.xRot * 0.7F);
        rightArm.zRot = 0.08F;
        leftArm.zRot = -0.08F;

        for (int i = 0; i < WISPS; i++)
        {
            float sway = Mth.sin(state.ageInTicks * WISP_SWAY_RATE + i * 1.3F) * WISP_SWAY;
            wisps[i].xRot = sway + speed * (i < 2 ? 0.25F : -0.25F);
            wisps[i].zRot = Mth.cos(state.ageInTicks * WISP_SWAY_RATE * 0.8F + i) * WISP_SWAY * 0.6F;
        }

        float clawOpen = CLAW_REST + (state.aggressive ? CLAW_OPEN : 0);
        claws[0].zRot = clawOpen;
        claws[1].zRot = -clawOpen;
        claws[2].xRot = clawOpen;
        claws[3].zRot = clawOpen;
        claws[4].zRot = -clawOpen;
        claws[5].xRot = clawOpen;
    }
}
