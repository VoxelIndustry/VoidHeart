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
import net.voxelindustry.voidheart.client.render.state.FallenElderRenderState;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A tall robe with a skull set deep in its hood, walking on its own feet. Its long arms hang nearly to the ground and rise to cast.
 */
public class FallenElderModel extends EntityModel<FallenElderRenderState>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "fallen_elder"), "main");

    private static final int HEM_STRIPS = 8;
    private static final int FINGERS = 4;
    private static final float WALK_RATE = 0.6F;
    private static final float WALK_REACH = 0.45F;
    private static final float CAST_PITCH = -1.9F;
    private static final float CAST_SPREAD = 0.35F;
    private static final float ARM_SWAY = 0.03F;
    private static final float HEM_SWAY = 0.09F;
    private static final float FINGER_REST = 0.1F;
    private static final float FINGER_SPREAD = 0.35F;

    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightFoot;
    private final ModelPart leftFoot;
    private final ModelPart[] strips = new ModelPart[HEM_STRIPS];
    private final ModelPart[] rightFingers = new ModelPart[FINGERS];
    private final ModelPart[] leftFingers = new ModelPart[FINGERS];

    public FallenElderModel(ModelPart root)
    {
        super(root);
        head = root.getChild("head");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightFoot = root.getChild("right_foot");
        leftFoot = root.getChild("left_foot");
        var robe = root.getChild("robe");
        for (int i = 0; i < HEM_STRIPS; i++)
            strips[i] = robe.getChild("strip_" + i);
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

        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.5F, -7, -3.5F, 7, 7, 7)
                        .texOffs(28, 0).addBox(-5, -10, -7, 10, 1, 11)
                        .texOffs(70, 0).addBox(-5, -9, -7, 1, 9, 11)
                        .texOffs(70, 0).mirror().addBox(4, -9, -7, 1, 9, 11).mirror(false)
                        .texOffs(94, 0).addBox(-5, -9, 3, 10, 9, 1)
                        .texOffs(94, 10).addBox(-4, -3, 4, 8, 8, 1),
                PartPose.offset(0, -10, 0));

        var robe = root.addOrReplaceChild("robe", CubeListBuilder.create()
                        .texOffs(0, 14).addBox(-7, -10, -4, 14, 4, 8)
                        .texOffs(44, 20).addBox(-5.5F, -6, -3.5F, 11, 13, 7)
                        .texOffs(0, 26).addBox(-6.5F, 6, -4.5F, 13, 16, 9),
                PartPose.ZERO);
        for (int i = 0; i < HEM_STRIPS; i++)
        {
            boolean front = i < HEM_STRIPS / 2;
            float x = -4.5F + (i % (HEM_STRIPS / 2)) * 3;
            robe.addOrReplaceChild("strip_" + i, CubeListBuilder.create().texOffs(80, 20).addBox(-2, 0, 0, 4, 6, 0),
                    PartPose.offset(x, 18, front ? -4.6F : 4.6F));
        }

        addArm(root, "right_arm", -8, false);
        addArm(root, "left_arm", 8, true);

        root.addOrReplaceChild("right_foot", CubeListBuilder.create().texOffs(44, 40).addBox(-1.5F, -2, -4, 3, 2, 5), PartPose.offset(-3, 24, 0));
        root.addOrReplaceChild("left_foot", CubeListBuilder.create().texOffs(44, 40).mirror().addBox(-1.5F, -2, -4, 3, 2, 5), PartPose.offset(3, 24, 0));
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static void addArm(PartDefinition root, String name, float x, boolean mirror)
    {
        var builder = CubeListBuilder.create();
        if (mirror)
            builder.mirror();
        builder.texOffs(88, 20).addBox(-2.5F, -2, -2.5F, 5, 12, 5)
                .texOffs(108, 20).addBox(-1, 10, -1, 2, 10, 2)
                .texOffs(116, 20).addBox(-1.5F, 20, -1.5F, 3, 2, 3);
        var arm = root.addOrReplaceChild(name, builder, PartPose.offset(x, -8, 0));
        float[][] spots = { { -1, -1 }, { 1, -1 }, { -1, 1 }, { 1, 1 } };
        for (int i = 0; i < FINGERS; i++)
        {
            arm.addOrReplaceChild("finger_" + i, CubeListBuilder.create().texOffs(116, 25).addBox(-0.5F, 0, -0.5F, 1, 7, 1),
                    PartPose.offset(spots[i][0], 21.5F, spots[i][1]));
        }
    }

    @Override
    public void setupAnim(FallenElderRenderState state)
    {
        super.setupAnim(state);

        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;

        float age = state.ageInTicks;
        float speed = state.walkAnimationSpeed;
        float stride = Mth.cos(state.walkAnimationPos * WALK_RATE) * speed;
        rightFoot.z = stride * 3;
        leftFoot.z = -stride * 3;
        rightFoot.y += Math.max(0, -stride) * -1;
        leftFoot.y += Math.max(0, stride) * -1;

        float sway = Mth.sin(age * 0.05F) * ARM_SWAY;
        rightArm.xRot = Mth.lerp(state.cast, -stride * WALK_REACH + sway, CAST_PITCH);
        leftArm.xRot = Mth.lerp(state.cast, stride * WALK_REACH - sway, CAST_PITCH);
        rightArm.zRot = Mth.lerp(state.cast, 0.05F, -CAST_SPREAD);
        leftArm.zRot = Mth.lerp(state.cast, -0.05F, CAST_SPREAD);

        float spread = Mth.lerp(state.cast, FINGER_REST, FINGER_SPREAD);
        setFingers(rightFingers, spread);
        setFingers(leftFingers, spread);

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
