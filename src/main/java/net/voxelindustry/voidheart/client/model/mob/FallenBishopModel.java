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
import net.voxelindustry.voidheart.client.render.state.FallenBishopRenderState;
import net.voxelindustry.voidheart.common.content.mob.FallenBishop;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The rank above the Elder, built on it: the same robe, long arms and skull sunk in a deep hood, the hood risen into a peaked mitre with a voidstone plate on
 * its front, and the Adept's voidstone plates over the shoulders and chest. Its crozier holds one of the Elder's orbs in its crook.
 */
public class FallenBishopModel extends EntityModel<FallenBishopRenderState>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "fallen_bishop"), "main");

    private static final int HEM_STRIPS = 8;
    private static final int FINGERS = 4;
    private static final float WALK_RATE = 0.6F;
    private static final float WALK_REACH = 0.45F;
    private static final float ARM_SWAY = 0.03F;
    private static final float HEM_SWAY = 0.09F;
    private static final float FINGER_REST = 0.1F;
    private static final float FINGER_GRIP = 0.45F;
    private static final float FINGER_SPREAD = 0.35F;

    private static final float RAISE_PITCH = -0.8F;
    private static final float CROZIER_REST = 0.05F;
    private static final float CAST_PITCH = -1.25F;
    private static final float CAST_SPREAD = 0.3F;

    private static final float WINDUP_SHARE = 0.25F;
    // The strike lands on screen a tick after the server hits, the delay of the swing reaching the client
    private static final float STRIKE_SHARE = (FallenBishop.BLOW_HIT_TICK + 1F) / FallenBishop.BLOW_TICKS;
    private static final float OVERHEAD_PITCH = -2.7F;
    private static final float STRIKE_PITCH = -1.1F;
    private static final float CROZIER_BACK = -0.55F;
    private static final float CROZIER_FORWARD = 1.45F;
    private static final float LEFT_FOLLOW = 0.6F;

    private static final float KNEEL_DROP = 7;
    private static final float KNEEL_SKIRT = 0.5F;
    private static final float KNEEL_BOW = 0.55F;
    private static final float KNEEL_LEFT_PITCH = -0.9F;
    private static final float KNEEL_LEFT_ROLL = 0.5F;

    private final ModelPart head;
    private final ModelPart skirt;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart crozier;
    private final ModelPart rightFoot;
    private final ModelPart leftFoot;
    private final ModelPart[] strips = new ModelPart[HEM_STRIPS];
    private final ModelPart[] rightFingers = new ModelPart[FINGERS];
    private final ModelPart[] leftFingers = new ModelPart[FINGERS];

    public FallenBishopModel(ModelPart root)
    {
        super(root);
        head = root.getChild("head");
        skirt = root.getChild("skirt");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        crozier = rightArm.getChild("crozier");
        rightFoot = root.getChild("right_foot");
        leftFoot = root.getChild("left_foot");
        for (int i = 0; i < HEM_STRIPS; i++)
            strips[i] = skirt.getChild("strip_" + i);
        for (int i = 0; i < FINGERS; i++)
        {
            rightFingers[i] = rightArm.getChild("finger_" + i);
            leftFingers[i] = leftArm.getChild("finger_" + i);
        }
    }

    public static LayerDefinition createBodyLayer()
    {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();

        // The Elder's hood and skull, with the hood drawn up into a peak and a plate set on its front
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.5F, -7, -3.5F, 7, 7, 7)
                        .texOffs(28, 0).addBox(-5, -10, -7, 10, 1, 11)
                        .texOffs(70, 0).addBox(-5, -9, -7, 1, 9, 11)
                        .texOffs(70, 0).mirror().addBox(4, -9, -7, 1, 9, 11).mirror(false)
                        .texOffs(94, 0).addBox(-5, -9, 3, 10, 9, 1)
                        .texOffs(94, 10).addBox(-4, -3, 4, 8, 8, 1)
                        .texOffs(0, 106).addBox(-4, -12, -6.5F, 8, 2, 9)
                        .texOffs(34, 106).addBox(-3, -14, -6, 6, 2, 7)
                        .texOffs(60, 106).addBox(-1.5F, -15.5F, -5, 3, 2, 5)
                        .texOffs(76, 106).addBox(-2.5F, -14.5F, -6.6F, 5, 5, 0),
                PartPose.offset(0, -10, 0));

        root.addOrReplaceChild("robe", CubeListBuilder.create()
                        .texOffs(0, 14).addBox(-7, -10, -4, 14, 4, 8)
                        .texOffs(44, 20).addBox(-5.5F, -6, -3.5F, 11, 13, 7)
                        .texOffs(86, 106).addBox(-5, -6, -4.5F, 10, 7, 1)
                        .texOffs(122, 60).addBox(-1.5F, -6, -4.6F, 3, 26, 0),
                PartPose.ZERO);

        var skirt = root.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(0, 26).addBox(-6.5F, 0, -4.5F, 13, 16, 9), PartPose.offset(0, 6, 0));
        for (int i = 0; i < HEM_STRIPS; i++)
        {
            boolean front = i < HEM_STRIPS / 2;
            float x = -4.5F + (i % (HEM_STRIPS / 2)) * 3;
            skirt.addOrReplaceChild("strip_" + i, CubeListBuilder.create().texOffs(80, 20).addBox(-2, 0, 0, 4, 6, 0),
                    PartPose.offset(x, 12, front ? -4.6F : 4.6F));
        }

        var rightArm = addArm(root, "right_arm", -8, false);
        addArm(root, "left_arm", 8, true);

        // A voidstone staff whose crook curls around a glowing orb
        rightArm.addOrReplaceChild("crozier", CubeListBuilder.create()
                        .texOffs(0, 70).addBox(-1, -22, -1, 2, 33, 2)
                        .texOffs(8, 70).addBox(-1.5F, -23, -1.5F, 3, 2, 3)
                        .texOffs(20, 70).addBox(-1, -27, -1, 2, 4, 2)
                        .texOffs(28, 70).addBox(-5, -28, -1, 5, 2, 2)
                        .texOffs(42, 70).addBox(-6, -27, -1, 2, 4, 2)
                        .texOffs(50, 70).addBox(-4, -26, -1.5F, 3, 3, 3),
                PartPose.offset(0, 21, 0));

        root.addOrReplaceChild("right_foot", CubeListBuilder.create().texOffs(44, 40).addBox(-1.5F, -2, -4, 3, 2, 5), PartPose.offset(-3, 24, 0));
        root.addOrReplaceChild("left_foot", CubeListBuilder.create().texOffs(44, 40).mirror().addBox(-1.5F, -2, -4, 3, 2, 5), PartPose.offset(3, 24, 0));
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static PartDefinition addArm(PartDefinition root, String name, float x, boolean mirror)
    {
        var builder = CubeListBuilder.create();
        if (mirror)
            builder.mirror();
        builder.texOffs(88, 20).addBox(-2.5F, -2, -2.5F, 5, 12, 5)
                .texOffs(108, 20).addBox(-1, 10, -1, 2, 10, 2)
                .texOffs(116, 20).addBox(-1.5F, 20, -1.5F, 3, 2, 3)
                .texOffs(64, 40).addBox(-3.5F, -3, -3.5F, 7, 4, 7);
        var arm = root.addOrReplaceChild(name, builder, PartPose.offset(x, -8, 0));
        float[][] spots = { { -1, -1 }, { 1, -1 }, { -1, 1 }, { 1, 1 } };
        for (int i = 0; i < FINGERS; i++)
        {
            arm.addOrReplaceChild("finger_" + i, CubeListBuilder.create().texOffs(116, 25).addBox(-0.5F, 0, -0.5F, 1, 3, 1),
                    PartPose.offset(spots[i][0], 21.5F, spots[i][1]));
        }
        return arm;
    }

    /**
     * Both hands take the crozier up over the head, then bring it down hard with its head forward, and ease back.
     *
     * @return the arm pitch and the crozier's angle in the world
     */
    private static float[] blow(float progress, float restArm, float restCrozier)
    {
        if (progress <= 0)
            return new float[] { restArm, restCrozier };
        if (progress < WINDUP_SHARE)
        {
            float share = progress / WINDUP_SHARE;
            return new float[] { Mth.lerp(share, restArm, OVERHEAD_PITCH), Mth.lerp(share, restCrozier, CROZIER_BACK) };
        }
        if (progress < STRIKE_SHARE)
        {
            float share = (progress - WINDUP_SHARE) / (STRIKE_SHARE - WINDUP_SHARE);
            return new float[] { Mth.lerp(share, OVERHEAD_PITCH, STRIKE_PITCH), Mth.lerp(share, CROZIER_BACK, CROZIER_FORWARD) };
        }
        float share = (progress - STRIKE_SHARE) / (1 - STRIKE_SHARE);
        return new float[] { Mth.lerp(share, STRIKE_PITCH, restArm), Mth.lerp(share, CROZIER_FORWARD, restCrozier) };
    }

    @Override
    public void setupAnim(FallenBishopRenderState state)
    {
        super.setupAnim(state);

        float kneel = state.kneel;
        float raise = state.raise;
        float age = state.ageInTicks;

        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = Mth.lerp(kneel, state.xRot * Mth.DEG_TO_RAD, KNEEL_BOW);

        float speed = state.walkAnimationSpeed;
        float stride = Mth.cos(state.walkAnimationPos * WALK_RATE) * speed;
        rightFoot.z = stride * 3;
        leftFoot.z = -stride * 3;
        rightFoot.y += Math.max(0, -stride) * -1;
        leftFoot.y += Math.max(0, stride) * -1;

        float sway = Mth.sin(age * 0.05F) * ARM_SWAY;
        float walkSwing = -stride * WALK_REACH * 0.4F * (1 - raise);
        float restArm = RAISE_PITCH * raise + walkSwing;
        // The crozier's own angle in the world, which its pitch on the arm is worked out from
        float restCrozier = CROZIER_REST + walkSwing;
        float[] blow = blow(state.attack, restArm, restCrozier);
        rightArm.xRot = blow[0];
        crozier.xRot = blow[1] - blow[0];
        rightArm.zRot = 0.05F;

        // The free hand opens toward the target while the lance charges, as the Elder's do to cast
        float leftWalk = stride * WALK_REACH + sway;
        float leftBlow = state.attack > 0 ? Mth.lerp(LEFT_FOLLOW, leftWalk, blow[0]) : leftWalk;
        float leftCast = Mth.lerp(raise, leftBlow, CAST_PITCH);
        leftArm.xRot = Mth.lerp(kneel, leftCast, KNEEL_LEFT_PITCH);
        leftArm.zRot = Mth.lerp(kneel, Mth.lerp(raise, -0.05F, CAST_SPREAD), KNEEL_LEFT_ROLL);

        setFingers(rightFingers, FINGER_GRIP);
        setFingers(leftFingers, Mth.lerp(raise, FINGER_REST, FINGER_SPREAD));

        root().y += KNEEL_DROP * kneel;
        skirt.yScale = Mth.lerp(kneel, 1, KNEEL_SKIRT);

        for (int i = 0; i < HEM_STRIPS; i++)
        {
            float wave = Mth.sin(age * 0.07F + i * 1.1F) * HEM_SWAY;
            strips[i].xRot = wave + speed * (i < HEM_STRIPS / 2 ? -0.2F : 0.2F);
        }
    }

    private static void setFingers(ModelPart[] fingers, float spread)
    {
        fingers[0].xRot = spread;
        fingers[0].zRot = spread;
        fingers[1].xRot = spread;
        fingers[1].zRot = -spread;
        fingers[2].xRot = -spread;
        fingers[2].zRot = spread;
        fingers[3].xRot = -spread;
        fingers[3].zRot = -spread;
    }
}
