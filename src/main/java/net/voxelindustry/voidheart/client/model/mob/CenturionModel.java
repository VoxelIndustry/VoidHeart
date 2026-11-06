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
import net.voxelindustry.voidheart.client.render.state.CenturionRenderState;
import net.voxelindustry.voidheart.common.content.mob.Centurion;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The same make as a Legionary, half a block taller and better kept, with a crest running across its helmet and a torn cape. A tall shield hangs off one arm, a curved
 * blade in the other hand.
 */
public class CenturionModel extends EntityModel<CenturionRenderState>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "centurion"), "main");

    private static final int SKIRT_STRIPS = 8;
    private static final float WALK_RATE = 0.7F;
    private static final float WALK_REACH = 0.65F;
    private static final float GUARD_PITCH = -0.7F;
    private static final float GUARD_YAW = 0.35F;
    private static final float BASH_PULL_PITCH = -0.2F;
    private static final float BASH_THRUST_PITCH = -1.45F;
    private static final float BASH_THRUST_FORWARD = 5;
    private static final float READY_PITCH = -0.6F;
    private static final float SWEEP_PITCH = -1.45F;
    private static final float SWEEP_COCKED_YAW = -1.2F;
    private static final float SWEEP_FINISHED_YAW = 1.3F;
    private static final float SWEEP_STRIKE_TICKS = 4;
    private static final float CAPE_SWAY = 0.05F;
    private static final float STRIP_SWAY = 0.1F;

    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart shield;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart cape;
    private final ModelPart[] strips = new ModelPart[SKIRT_STRIPS];

    public CenturionModel(ModelPart root)
    {
        super(root);
        head = root.getChild("head");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        shield = leftArm.getChild("shield");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
        cape = root.getChild("cape");
        var body = root.getChild("body");
        for (int i = 0; i < SKIRT_STRIPS; i++)
            strips[i] = body.getChild("strip_" + i);
    }

    public static LayerDefinition createBodyLayer()
    {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();

        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.5F, -9, -4.5F, 9, 9, 9)
                        .texOffs(36, 0).addBox(-5.5F, -4, -3, 1, 5, 4)
                        .texOffs(36, 0).mirror().addBox(4.5F, -4, -3, 1, 5, 4).mirror(false)
                        .texOffs(46, 0).addBox(-5, -2, 3.5F, 10, 2, 2)
                        .texOffs(46, 5).addBox(-5, -7, -5.5F, 10, 1, 1)
                        .texOffs(72, 0).addBox(-7, -12, -0.5F, 14, 5, 1),
                PartPose.offset(0, -7, 0));

        var body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 20).addBox(-6, -7, -3.5F, 12, 13, 7)
                        .texOffs(38, 20).addBox(-5, 6, -3, 10, 4, 6),
                PartPose.ZERO);
        for (int i = 0; i < SKIRT_STRIPS; i++)
        {
            boolean front = i < SKIRT_STRIPS / 2;
            float x = -4 + (i % (SKIRT_STRIPS / 2)) * 2.7F;
            body.addOrReplaceChild("strip_" + i, CubeListBuilder.create().texOffs(70, 20).addBox(-1, 0, 0, 2, 8, 0),
                    PartPose.offset(x, 9, front ? -3.2F : 3.2F));
        }

        root.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(0, 64).addBox(-6, 0, 0, 12, 26, 0), PartPose.offset(0, -6, 3.6F));

        var rightArm = addArm(root, "right_arm", -8, false);
        var leftArm = addArm(root, "left_arm", 8, true);

        leftArm.addOrReplaceChild("shield", CubeListBuilder.create()
                        .texOffs(24, 64).addBox(-7, -11, -1, 14, 22, 2)
                        .texOffs(56, 64).addBox(-2, -2, -2, 4, 4, 1),
                PartPose.offset(1, 10, -5));

        var blade0 = rightArm.addOrReplaceChild("scimitar", CubeListBuilder.create()
                        .texOffs(84, 32).addBox(-0.5F, -0.5F, -2, 1, 1, 5)
                        .texOffs(84, 40).addBox(-1, -2, -3, 2, 4, 1)
                        .texOffs(96, 32).addBox(-0.5F, -1.5F, -9, 1, 3, 6),
                PartPose.offset(0, 15, 0));
        var blade1 = blade0.addOrReplaceChild("blade_1", CubeListBuilder.create().texOffs(96, 42).addBox(-0.5F, -1.5F, -6, 1, 3, 6),
                PartPose.offsetAndRotation(0, 0, -9, 0.25F, 0, 0));
        blade1.addOrReplaceChild("blade_2", CubeListBuilder.create().texOffs(96, 52).addBox(-0.5F, -1, -5, 1, 2, 5),
                PartPose.offsetAndRotation(0, -0.2F, -6, 0.32F, 0, 0));

        addLeg(root, "right_leg", -3, false);
        addLeg(root, "left_leg", 3, true);
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static PartDefinition addArm(PartDefinition root, String name, float x, boolean mirror)
    {
        var builder = CubeListBuilder.create();
        if (mirror)
            builder.mirror();
        builder.texOffs(38, 32).addBox(-4, -3.5F, -3.5F, 7, 6, 7)
                .texOffs(66, 32).addBox(-2, -1, -2, 4, 17, 4);
        return root.addOrReplaceChild(name, builder, PartPose.offset(x, -5, 0));
    }

    private static void addLeg(PartDefinition root, String name, float x, boolean mirror)
    {
        var builder = CubeListBuilder.create();
        if (mirror)
            builder.mirror();
        builder.texOffs(0, 42).addBox(-2, 0, -2, 4, 16, 4)
                .texOffs(16, 42).addBox(-2.5F, 8, -3, 5, 8, 6);
        root.addOrReplaceChild(name, builder, PartPose.offset(x, 8, 0));
    }

    @Override
    public void setupAnim(CenturionRenderState state)
    {
        super.setupAnim(state);

        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;

        float speed = state.walkAnimationSpeed;
        float stride = Mth.cos(state.walkAnimationPos * WALK_RATE) * speed;
        rightLeg.xRot = stride * WALK_REACH;
        leftLeg.xRot = -stride * WALK_REACH;

        setupShieldArm(state.bash);
        setupSwordArm(state.sweep);

        float age = state.ageInTicks;
        cape.xRot = 0.08F + speed * 0.3F + Mth.sin(age * 0.06F) * CAPE_SWAY;
        for (int i = 0; i < SKIRT_STRIPS; i++)
        {
            float wave = Mth.sin(age * 0.08F + i * 1.4F) * STRIP_SWAY;
            strips[i].xRot = wave + speed * (i < SKIRT_STRIPS / 2 ? -0.3F : 0.3F);
        }
    }

    /**
     * Drawn back against the chest for the wind-up, then driven out at the tick the bash lands, the shield kept upright all the way.
     */
    private void setupShieldArm(float ticks)
    {
        float pitch = GUARD_PITCH;
        if (ticks > 0)
        {
            int windup = Centurion.BASH_WINDUP_TICKS;
            if (ticks < windup - 2)
                pitch = Mth.lerp(ticks / (windup - 2), GUARD_PITCH, BASH_PULL_PITCH);
            else if (ticks < windup)
                pitch = Mth.lerp((ticks - (windup - 2)) / 2, BASH_PULL_PITCH, BASH_THRUST_PITCH);
            else
                pitch = Mth.lerp(Mth.clamp((ticks - windup) / Centurion.FOLLOW_THROUGH_TICKS, 0, 1), BASH_THRUST_PITCH, GUARD_PITCH);

            float thrust = Mth.clamp((pitch - BASH_PULL_PITCH) / (BASH_THRUST_PITCH - BASH_PULL_PITCH), 0, 1);
            leftArm.z -= BASH_THRUST_FORWARD * thrust;
        }
        leftArm.xRot = pitch;
        leftArm.yRot = GUARD_YAW;
        shield.xRot = -pitch;
    }

    /**
     * Cocked out to the side during the wind-up, then across its whole front around the tick the sweep lands.
     */
    private void setupSwordArm(float ticks)
    {
        if (ticks <= 0)
        {
            rightArm.xRot = READY_PITCH;
            return;
        }

        int windup = Centurion.SWEEP_WINDUP_TICKS;
        float strikeStart = windup - SWEEP_STRIKE_TICKS / 2;
        rightArm.xRot = SWEEP_PITCH * Mth.clamp(ticks / 4, 0, 1);
        if (ticks < strikeStart)
            rightArm.yRot = SWEEP_COCKED_YAW * Mth.clamp(ticks / 6, 0, 1);
        else
            rightArm.yRot = Mth.lerp(Mth.clamp((ticks - strikeStart) / SWEEP_STRIKE_TICKS, 0, 1), SWEEP_COCKED_YAW, SWEEP_FINISHED_YAW);
    }
}
