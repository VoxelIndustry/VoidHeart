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
import net.voxelindustry.voidheart.client.render.state.FallenRenderState;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A small robed figure with its hood down on its shoulders and a bare skull. The armored rank adds plates and swaps the knife for a sword.
 * The plates and blades are only part of the look, never equipment.
 */
public class FallenModel extends EntityModel<FallenRenderState>
{
    public static final ModelLayerLocation NOVICE = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "fallen_novice"), "main");
    public static final ModelLayerLocation ADEPT = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "fallen_adept"), "main");

    private static final float WALK_RATE = 0.8F;
    private static final float WALK_REACH = 0.7F;
    private static final float RAISE_ARM = -1.2F;
    private static final float STRIKE = 1.5F;
    private static final float HOLD_ARM_FORWARD = -0.35F;
    private static final float HEM_SWAY = 0.08F;

    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart hemFront;
    private final ModelPart hemBack;

    public FallenModel(ModelPart root)
    {
        super(root);
        head = root.getChild("head");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
        var skirt = root.getChild("skirt");
        hemFront = skirt.getChild("hem_front");
        hemBack = skirt.getChild("hem_back");
    }

    public static LayerDefinition createNoviceLayer()
    {
        return createLayer(false);
    }

    public static LayerDefinition createAdeptLayer()
    {
        return createLayer(true);
    }

    private static LayerDefinition createLayer(boolean armored)
    {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();

        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8), PartPose.ZERO);

        var torso = CubeListBuilder.create()
                .texOffs(0, 16).addBox(-4, 0, -2, 8, 12, 4)
                .texOffs(32, 0).addBox(-4.5F, -1.5F, -3.5F, 9, 3, 7)
                .texOffs(32, 10).addBox(-4.5F, 0, 2.5F, 9, 6, 1);
        if (armored)
            torso.texOffs(0, 46).addBox(-5, 0, -3, 10, 8, 6);
        root.addOrReplaceChild("torso", torso, PartPose.ZERO);

        var skirt = root.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(24, 17).addBox(-4.5F, 0, -2.5F, 9, 8, 5), PartPose.offset(0, 12, 0));
        skirt.addOrReplaceChild("hem_front", CubeListBuilder.create().texOffs(0, 32).addBox(-4.5F, 0, 0, 9, 3, 0), PartPose.offset(0, 8, -2.5F));
        skirt.addOrReplaceChild("hem_back", CubeListBuilder.create().texOffs(0, 32).addBox(-4.5F, 0, 0, 9, 3, 0), PartPose.offset(0, 8, 2.5F));

        var rightArm = addArm(root, "right_arm", -5, false, armored);
        addArm(root, "left_arm", 5, true, armored);
        if (armored)
            addSword(rightArm);
        else
            addKnife(rightArm);

        addLeg(root, "right_leg", -2, false, armored);
        addLeg(root, "left_leg", 2, true, armored);
        return LayerDefinition.create(mesh, 128, 64);
    }

    private static PartDefinition addArm(PartDefinition root, String name, float x, boolean mirror, boolean armored)
    {
        var builder = CubeListBuilder.create();
        if (mirror)
            builder.mirror();
        builder.texOffs(52, 10).addBox(-1.5F, -2, -1.5F, 3, 8, 3)
                .texOffs(52, 21).addBox(-1, 6, -1, 2, 6, 2);
        if (armored)
            builder.texOffs(32, 46).addBox(-2.5F, -3, -3, 5, 3, 6);
        return root.addOrReplaceChild(name, builder, PartPose.offset(x, 2, 0));
    }

    private static void addLeg(PartDefinition root, String name, float x, boolean mirror, boolean armored)
    {
        var builder = CubeListBuilder.create();
        if (mirror)
            builder.mirror();
        builder.texOffs(18, 32).addBox(-1, 0, -1, 2, 12, 2)
                .texOffs(26, 32).addBox(-1.5F, 10, -2.5F, 3, 2, 4);
        if (armored)
            builder.texOffs(52, 46).addBox(-1.5F, 4, -1.5F, 3, 6, 3);
        root.addOrReplaceChild(name, builder, PartPose.offset(x, 12, 0));
    }

    private static void addKnife(PartDefinition arm)
    {
        arm.addOrReplaceChild("knife", CubeListBuilder.create()
                        .texOffs(54, 32).addBox(-0.5F, -0.5F, -2, 1, 1, 2)
                        .texOffs(40, 32).addBox(-0.5F, -0.5F, -8, 1, 1, 6),
                PartPose.offset(0, 11.5F, 0));
    }

    private static void addSword(PartDefinition arm)
    {
        arm.addOrReplaceChild("sword", CubeListBuilder.create()
                        .texOffs(76, 14).addBox(-0.5F, -1, -1, 1, 3, 1)
                        .texOffs(64, 14).addBox(-2.5F, -0.5F, -2, 5, 1, 1)
                        .texOffs(64, 0).addBox(-0.5F, -1, -14, 1, 2, 12)
                        .texOffs(90, 0).addBox(-0.5F, 1, -6, 1, 1, 2)
                        .texOffs(90, 0).addBox(-0.5F, -2, -9, 1, 1, 2)
                        .texOffs(90, 0).addBox(-0.5F, 1, -12, 1, 1, 2),
                PartPose.offset(0, 11.5F, 0));
    }

    @Override
    public void setupAnim(FallenRenderState state)
    {
        super.setupAnim(state);

        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;

        float pos = state.walkAnimationPos;
        float speed = state.walkAnimationSpeed;
        float stride = Mth.cos(pos * WALK_RATE) * WALK_REACH * speed;
        rightLeg.xRot = stride;
        leftLeg.xRot = -stride;

        float strike = Mth.sin(state.attack * Mth.PI);
        rightArm.xRot = (state.aggressive ? RAISE_ARM : HOLD_ARM_FORWARD - stride * 0.5F) + strike * STRIKE;
        leftArm.xRot = stride * 0.8F;
        rightArm.zRot = 0.06F;
        leftArm.zRot = -0.06F;

        hemFront.xRot = Mth.sin(state.ageInTicks * 0.08F) * HEM_SWAY - speed * 0.25F;
        hemBack.xRot = Mth.sin(state.ageInTicks * 0.08F + 1.5F) * HEM_SWAY + speed * 0.25F;
    }
}
