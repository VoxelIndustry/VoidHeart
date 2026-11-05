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
import net.voxelindustry.voidheart.client.render.state.MalignantBoulderRenderState;
import net.voxelindustry.voidheart.common.content.mob.MalignantBoulder;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A huge square block of a torso on stubs of legs, with a head far too small for it set low on its front edge and fists that nearly reach the floor.
 */
public class MalignantBoulderModel extends EntityModel<MalignantBoulderRenderState>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "malignant_boulder"), "main");

    private static final float ARM_X = 17.5F;
    private static final float WALK_RATE = 0.5F;
    private static final float WALK_REACH = 0.3F;
    private static final float ARM_SWAY = 0.25F;
    private static final float BODY_ROCK = 0.04F;
    private static final float RAISED_PITCH = -2.9F;
    private static final float RAISE_EASE_TICKS = 16;
    private static final float DROP_TICKS = 3;
    private static final float PUNCH_RATE = 0.55F;
    private static final float PUNCH_PITCH = -1.5F;

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public MalignantBoulderModel(ModelPart root)
    {
        super(root);
        body = root.getChild("body");
        head = body.getChild("head");
        rightArm = body.getChild("right_arm");
        leftArm = body.getChild("left_arm");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer()
    {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();

        var body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-12, -16, -9, 24, 30, 18)
                        .texOffs(84, 14).addBox(-8, -10, -11, 16, 10, 2),
                PartPose.ZERO);
        body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(84, 0).addBox(-4, -6, -3, 8, 6, 6), PartPose.offset(0, -16, -6));

        addArm(body, "right_arm", -ARM_X, false);
        addArm(body, "left_arm", ARM_X, true);

        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 68).addBox(-5, 0, -6, 10, 10, 12), PartPose.offset(-6, 14, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 68).mirror().addBox(-5, 0, -6, 10, 10, 12), PartPose.offset(6, 14, 0));
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static void addArm(PartDefinition body, String name, float x, boolean mirror)
    {
        var builder = CubeListBuilder.create();
        if (mirror)
            builder.mirror();
        builder.texOffs(0, 48).addBox(-5, -4, -6, 10, 8, 12)
                .texOffs(44, 48).addBox(-4, 4, -4, 8, 18, 8)
                .texOffs(76, 48).addBox(-6, 22, -6, 12, 12, 12);
        body.addOrReplaceChild(name, builder, PartPose.offset(x, -12, 0));
    }

    @Override
    public void setupAnim(MalignantBoulderRenderState state)
    {
        super.setupAnim(state);

        head.yRot = state.yRot * Mth.DEG_TO_RAD * 0.6F;
        head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.6F;

        float speed = state.walkAnimationSpeed;
        float stride = Mth.cos(state.walkAnimationPos * WALK_RATE) * speed;
        rightLeg.xRot = stride * WALK_REACH;
        leftLeg.xRot = -stride * WALK_REACH;
        body.zRot = stride * BODY_ROCK;

        float rightPitch = -stride * ARM_SWAY;
        float leftPitch = stride * ARM_SWAY;

        if (state.breaking)
        {
            float punch = state.ageInTicks * PUNCH_RATE;
            rightPitch = PUNCH_PITCH * Math.max(0, Mth.sin(punch));
            leftPitch = PUNCH_PITCH * Math.max(0, -Mth.sin(punch));
        }

        float slam = slamPitch(state.slam);
        if (slam != 0)
        {
            rightPitch = slam;
            leftPitch = slam;
            body.xRot = slam * 0.08F;
        }
        rightArm.xRot = rightPitch;
        leftArm.xRot = leftPitch;
    }

    /**
     * Both arms rise slowly over its head, stay there until the last moment, and come down all at once at the tick the slam lands.
     */
    private static float slamPitch(float ticks)
    {
        if (ticks <= 0)
            return 0;
        int windup = MalignantBoulder.WINDUP_TICKS;
        if (ticks < windup - DROP_TICKS)
            return RAISED_PITCH * Mth.clamp(ticks / RAISE_EASE_TICKS, 0, 1);
        if (ticks < windup)
            return RAISED_PITCH * (windup - ticks) / DROP_TICKS;
        return -0.25F * Mth.clamp((windup + MalignantBoulder.RECOVER_TICKS - ticks) / MalignantBoulder.RECOVER_TICKS, 0, 1);
    }
}
