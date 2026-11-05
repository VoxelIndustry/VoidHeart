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
import net.voxelindustry.voidheart.client.render.state.LegionaryRenderState;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Thin metal limbs inside what remains of a legion's armor: a crested helmet with nothing behind its slit, a plated chest open onto a burning core and a skirt of torn
 * leather strips. Both hands close on a long battle axe with a bearded blade.
 */
public class LegionaryModel extends EntityModel<LegionaryRenderState>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "legionary"), "main");

    private static final int SKIRT_STRIPS = 8;
    private static final float WALK_RATE = 0.8F;
    private static final float WALK_REACH = 0.7F;
    private static final float READY_PITCH = -0.75F;
    // Leans the haft's top a little forward of upright, against the arm's own pitch
    private static final float READY_AXE_PITCH = 1.05F;
    private static final float LEFT_READY_PITCH = -1.15F;
    private static final float LEFT_READY_YAW = 0.55F;
    private static final float CHOP_LIFT = -1.9F;
    private static final float STRIP_SWAY = 0.1F;

    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart axe;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart[] strips = new ModelPart[SKIRT_STRIPS];

    public LegionaryModel(ModelPart root)
    {
        super(root);
        head = root.getChild("head");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        axe = rightArm.getChild("axe");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
        var body = root.getChild("body");
        for (int i = 0; i < SKIRT_STRIPS; i++)
            strips[i] = body.getChild("strip_" + i);
    }

    public static LayerDefinition createBodyLayer()
    {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();

        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8)
                        .texOffs(32, 0).addBox(-5, -4, -3, 1, 5, 4)
                        .texOffs(32, 0).mirror().addBox(4, -4, -3, 1, 5, 4).mirror(false)
                        .texOffs(42, 0).addBox(-4.5F, -2, 3, 9, 2, 2)
                        .texOffs(42, 5).addBox(-4.5F, -6, -5, 9, 1, 1)
                        .texOffs(64, 0).addBox(-0.5F, -12, -5, 1, 5, 10),
                PartPose.offset(0, -2, 0));

        var body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 18).addBox(-5, -2, -3, 10, 11, 6)
                        .texOffs(32, 18).addBox(-4.5F, 9, -2.5F, 9, 3, 5),
                PartPose.ZERO);
        for (int i = 0; i < SKIRT_STRIPS; i++)
        {
            boolean front = i < SKIRT_STRIPS / 2;
            float x = -3.6F + (i % (SKIRT_STRIPS / 2)) * 2.4F;
            body.addOrReplaceChild("strip_" + i, CubeListBuilder.create().texOffs(60, 18).addBox(-1, 0, 0, 2, 7, 0),
                    PartPose.offset(x, 11, front ? -2.7F : 2.7F));
        }

        var rightArm = addArm(root, "right_arm", -7, false);
        addArm(root, "left_arm", 7, true);
        rightArm.addOrReplaceChild("axe", CubeListBuilder.create()
                        .texOffs(72, 28).addBox(-0.5F, -20, -0.5F, 1, 26, 1)
                        .texOffs(78, 28).addBox(-1, -21, -1.5F, 2, 3, 3)
                        .texOffs(12, 56).addBox(-0.5F, -21, -3.5F, 1, 3, 2)
                        .texOffs(0, 56).addBox(-0.5F, -23, -6.5F, 1, 7, 3)
                        .texOffs(8, 56).addBox(-0.5F, -26, -7.5F, 1, 13, 1)
                        .texOffs(18, 56).addBox(-0.5F, -20.5F, 1.5F, 1, 2, 3)
                        .texOffs(26, 56).addBox(-0.5F, -24, -0.5F, 1, 3, 1)
                        .texOffs(30, 56).addBox(-1, 6, -1, 2, 2, 2),
                PartPose.offset(0, 13, 0));

        addLeg(root, "right_leg", -2.5F, false);
        addLeg(root, "left_leg", 2.5F, true);
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static PartDefinition addArm(PartDefinition root, String name, float x, boolean mirror)
    {
        var builder = CubeListBuilder.create();
        if (mirror)
            builder.mirror();
        builder.texOffs(32, 28).addBox(-3, -3, -3, 6, 5, 6)
                .texOffs(56, 28).addBox(-1.5F, -1, -1.5F, 3, 15, 3);
        return root.addOrReplaceChild(name, builder, PartPose.offset(x, 0, 0));
    }

    private static void addLeg(PartDefinition root, String name, float x, boolean mirror)
    {
        var builder = CubeListBuilder.create();
        if (mirror)
            builder.mirror();
        builder.texOffs(0, 38).addBox(-1.5F, 0, -1.5F, 3, 13, 3)
                .texOffs(12, 38).addBox(-2, 6, -2.5F, 4, 7, 5);
        root.addOrReplaceChild(name, builder, PartPose.offset(x, 11, 0));
    }

    @Override
    public void setupAnim(LegionaryRenderState state)
    {
        super.setupAnim(state);

        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;

        float speed = state.walkAnimationSpeed;
        float stride = Mth.cos(state.walkAnimationPos * WALK_RATE) * speed;
        rightLeg.xRot = stride * WALK_REACH;
        leftLeg.xRot = -stride * WALK_REACH;

        float chop = Mth.sin(state.attack * Mth.PI);
        rightArm.xRot = READY_PITCH + chop * CHOP_LIFT + (state.aggressive ? 0 : stride * 0.15F);
        leftArm.xRot = LEFT_READY_PITCH + chop * CHOP_LIFT;
        leftArm.yRot = LEFT_READY_YAW;
        axe.xRot = READY_AXE_PITCH;

        float age = state.ageInTicks;
        for (int i = 0; i < SKIRT_STRIPS; i++)
        {
            float wave = Mth.sin(age * 0.08F + i * 1.4F) * STRIP_SWAY;
            strips[i].xRot = wave + speed * (i < SKIRT_STRIPS / 2 ? -0.3F : 0.3F);
        }
    }
}
