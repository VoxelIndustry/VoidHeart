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
import net.voxelindustry.voidheart.client.render.state.StalkingWraithRenderState;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A broad, hunched shade with a pale face set in a heavy hood. Its mouth is a row of narrow strips: at rest a short dark line, and as it smiles the strips grow out
 * from the middle, past the edges of the face and over the hood, with the corners climbing toward its eyes. The mouth lives in {@link StalkingWraithMouthModel},
 * since it stays opaque while the body fades.
 */
public class StalkingWraithModel extends EntityModel<StalkingWraithRenderState>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "stalking_wraith"), "main");

    private static final int MOUTH_REACH = 6;
    private static final int MOUTH_STRIPS = MOUTH_REACH * 2 + 1;
    private static final int MOUTH_AT_REST = 2;
    private static final int MOUTH_ON_FACE = 4;
    private static final float MOUTH_Y = -2.5F;
    private static final float MOUTH_FACE_Z = -5.05F;
    private static final float MOUTH_HOOD_Z = -6.05F;
    private static final float MOUTH_REST_HEIGHT = 0.22F;
    private static final float CORNER_RISE = 3.5F;
    private static final float STRIP_GROW_SHARE = 0.15F;
    private static final float SMILE_TILT = 0.2F;
    private static final float SMILE_TREMBLE = 0.012F;
    private static final float SMILE_TREMBLE_RATE = 1.3F;

    private static final int WISPS = 8;
    private static final float HEAD_DROOP = 0.1F;
    private static final float WISP_SWAY_RATE = 0.09F;
    private static final float WISP_SWAY = 0.16F;
    private static final float WALK_RATE = 0.65F;
    private static final float WALK_REACH = 0.55F;
    private static final float RUSH_LEAN = 0.45F;
    private static final float RUSH_REACH = -1.5F;
    private static final float CLAW_REST = 0.12F;
    private static final float CLAW_OPEN = 0.55F;

    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart[] wisps = new ModelPart[WISPS];
    private final ModelPart[] claws = new ModelPart[6];

    public StalkingWraithModel(ModelPart root)
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
                        .texOffs(0, 0).addBox(-4, -9, -4, 8, 9, 8)
                        .texOffs(32, 0).addBox(-6, -11, -6, 12, 2, 11)
                        .texOffs(78, 0).addBox(-6, -9, -6, 1, 10, 11)
                        .texOffs(78, 0).mirror().addBox(5, -9, -6, 1, 10, 11).mirror(false)
                        .texOffs(102, 0).addBox(-6, -9, 4, 12, 10, 1)
                        .texOffs(102, 12).addBox(-4, 1, 4, 8, 8, 1)
                        .texOffs(0, 18).addBox(-5, -9, -5, 10, 10, 1),
                HEAD_POSE);

        root.addOrReplaceChild("torso", CubeListBuilder.create()
                        .texOffs(0, 30).addBox(-8, -6, -4, 16, 14, 8)
                        .texOffs(48, 22).addBox(-11, -8, -5.5F, 22, 6, 11),
                PartPose.ZERO);

        var skirt = root.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(0, 54).addBox(-7, 0, -4.5F, 14, 10, 9), PartPose.offset(0, 8, 0));
        float[][] wispSpots = { { -4.5F, -4.5F, 0 }, { 0, -4.5F, 0 }, { 4.5F, -4.5F, 0 }, { -4.5F, 4.5F, 0 }, { 0, 4.5F, 0 }, { 4.5F, 4.5F, 0 }, { -7, 0, Mth.HALF_PI }, { 7, 0, Mth.HALF_PI } };
        for (int i = 0; i < WISPS; i++)
        {
            skirt.addOrReplaceChild("wisp_" + i, CubeListBuilder.create().texOffs(46, 54).addBox(-2, 0, 0, 4, 7, 0),
                    PartPose.offsetAndRotation(wispSpots[i][0], 9, wispSpots[i][1], 0, wispSpots[i][2], 0));
        }

        addArm(root, "right_arm", -11, false);
        addArm(root, "left_arm", 11, true);

        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(24, 76).addBox(-2.5F, 0, -2.5F, 5, 16, 5), PartPose.offset(-3.5F, 8, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(24, 76).mirror().addBox(-2.5F, 0, -2.5F, 5, 16, 5), PartPose.offset(3.5F, 8, 0));
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static void addArm(PartDefinition root, String name, float x, boolean mirror)
    {
        var builder = CubeListBuilder.create().texOffs(0, 76);
        if (mirror)
            builder.mirror();
        var arm = root.addOrReplaceChild(name, builder.addBox(-2.5F, -1, -2.5F, 5, 22, 5), PartPose.offset(x, -5, 0));
        float[][] clawSpots = { { -1.6F, -1.2F }, { 1.6F, -1.2F }, { 0, 1.5F } };
        for (int i = 0; i < 3; i++)
        {
            arm.addOrReplaceChild("claw_" + i, CubeListBuilder.create().texOffs(20, 76).addBox(-0.5F, 0, -0.5F, 1, 7, 1),
                    PartPose.offset(clawSpots[i][0], 20.5F, clawSpots[i][1]));
        }
    }

    static final PartPose HEAD_POSE = PartPose.offset(0, -5, -3);

    static void addMouth(PartDefinition head)
    {
        for (int i = 0; i < MOUTH_STRIPS; i++)
        {
            int offset = i - MOUTH_REACH;
            head.addOrReplaceChild("mouth_" + i, CubeListBuilder.create().texOffs(i % 2 == 0 ? 54 : 56, 54).addBox(-0.5F, -1.5F, 0, 1, 3, 0),
                    PartPose.offset(offset, MOUTH_Y, Math.abs(offset) > MOUTH_ON_FACE ? MOUTH_HOOD_Z : MOUTH_FACE_Z));
        }
    }

    static ModelPart[] mouthOf(ModelPart head)
    {
        var mouth = new ModelPart[MOUTH_STRIPS];
        for (int i = 0; i < MOUTH_STRIPS; i++)
            mouth[i] = head.getChild("mouth_" + i);
        return mouth;
    }

    /**
     * Shared with the mouth model, so that both move exactly together.
     */
    static void poseHead(ModelPart root, ModelPart head, StalkingWraithRenderState state)
    {
        float smile = state.smile;
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD + HEAD_DROOP * (1 - smile);
        head.zRot = smile * SMILE_TILT + Mth.sin(state.ageInTicks * SMILE_TREMBLE_RATE) * SMILE_TREMBLE * smile;
        if (state.rushing)
            root.xRot = RUSH_LEAN;
    }

    @Override
    public void setupAnim(StalkingWraithRenderState state)
    {
        super.setupAnim(state);

        float smile = state.smile;
        float age = state.ageInTicks;
        poseHead(root(), head, state);

        float pos = state.walkAnimationPos;
        float speed = state.walkAnimationSpeed;
        rightLeg.xRot = Mth.cos(pos * WALK_RATE) * WALK_REACH * speed;
        leftLeg.xRot = -rightLeg.xRot;

        float swing = Mth.sin(state.attack * Mth.PI);
        float reach = state.rushing ? RUSH_REACH : 0;
        float walkSwing = state.rushing ? 0 : rightLeg.xRot * 0.6F;
        rightArm.xRot = reach - swing * 1.3F - walkSwing;
        leftArm.xRot = reach - swing * 0.6F + walkSwing;
        rightArm.zRot = 0.1F;
        leftArm.zRot = -0.1F;

        if (state.rushing)
        {
            rightLeg.xRot *= 0.3F;
            leftLeg.xRot *= 0.3F;
        }

        for (int i = 0; i < WISPS; i++)
        {
            float sway = Mth.sin(age * WISP_SWAY_RATE + i * 1.3F) * WISP_SWAY;
            wisps[i].xRot = sway + speed * (i < 3 ? 0.25F : -0.25F);
            wisps[i].zRot = Mth.cos(age * WISP_SWAY_RATE * 0.8F + i) * WISP_SWAY * 0.6F;
        }

        float clawOpen = CLAW_REST + (state.rushing || state.aggressive ? CLAW_OPEN : 0) + smile * CLAW_OPEN * 0.5F;
        for (int side = 0; side < 2; side++)
        {
            claws[side * 3].zRot = clawOpen;
            claws[side * 3 + 1].zRot = -clawOpen;
            claws[side * 3 + 2].xRot = clawOpen;
        }
    }

    static void setupMouth(ModelPart[] mouth, float smile)
    {
        for (int i = 0; i < MOUTH_STRIPS; i++)
        {
            int reach = Math.abs(i - MOUTH_REACH);
            float share = reach / (float) MOUTH_REACH;
            float grown = reach <= MOUTH_AT_REST ? 1 : Mth.clamp((smile - share * (1 - STRIP_GROW_SHARE)) / STRIP_GROW_SHARE, 0, 1);

            var strip = mouth[i];
            strip.visible = grown > 0;
            strip.xScale = grown;
            strip.yScale = Mth.lerp(smile, MOUTH_REST_HEIGHT, 1);
            strip.y = MOUTH_Y - CORNER_RISE * share * share * smile;
        }
    }
}
