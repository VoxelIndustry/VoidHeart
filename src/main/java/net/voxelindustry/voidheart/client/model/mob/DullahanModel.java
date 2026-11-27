package net.voxelindustry.voidheart.client.model.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.render.state.DullahanRenderState;
import net.voxelindustry.voidheart.common.content.mausoleum.MausoleumBalance;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A headless rider in dark plate on a wraith beast, its scythe in the right hand and the reins in the left, a ragged cloak hanging down its back
 * over the beast. Its neck ends in a gorget, where the crown of flames burns. The rider is built onto the beast's body, so it rides every bob and pitch
 * of its mount; the beast's own parts are the wraith beast's, laid out on the same texture.
 * <p>
 * The scythe sweeps from one side across the front to the other: wound back, the torso twists away with the scythe raised behind; struck, it
 * swings through; stunned, the rider slumps and the scythe hangs.
 */
public class DullahanModel extends EntityModel<DullahanRenderState>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "dullahan"), "main");

    /** The top of the gorget, where the crown burns, in the torso's space. */
    public static final float CROWN_Y = -12.7F;
    public static final float CROWN_RADIUS = 4.3F;

    private static final float SEAT_Y = -7, SEAT_Z = -9;
    private static final float THIGH_TILT = 0.3F;
    private static final float SHIN_TILT = -0.35F;
    private static final float SCYTHE_ARM_REST = -0.55F, SCYTHE_REST = 0.55F, REINS_ARM_REST = -0.85F;
    private static final float BREATH_RATE = 0.06F, BREATH = 0.025F;
    private static final float CLOAK_SWAY_RATE = 0.08F, CLOAK_SWAY = 0.12F, CLOAK_TRAIL = 0.5F;

    // A sweep: winding back, then the swing through and the follow-through
    private static final float WIND_TWIST = 0.95F;
    private static final float WIND_ARM_RAISE = -1.6F;
    private static final float WIND_ARM_OUT = 0.9F;
    private static final float SWING_TICKS = 5;
    private static final float SWING_TWIST = -1.05F;
    private static final float SWING_ARM_OUT = -0.7F;
    private static final float SCYTHE_LEVEL = 1.45F;

    private static final float SLUMP = 0.65F;
    private static final float SLUMP_ARM = 0.35F;
    private static final float SLUMP_SCYTHE = 1.6F;
    private static final float SLUMP_NECK = 0.5F;
    private static final float SLUMP_TICKS = 10;

    private final WraithBeastModel beast;
    private final ModelPart neck;
    private final ModelPart rider;
    private final ModelPart torso;
    private final ModelPart scytheArm;
    private final ModelPart reinsArm;
    private final ModelPart scythe;
    private final ModelPart cloak;

    public DullahanModel(ModelPart root)
    {
        super(root);
        beast = new WraithBeastModel(root);
        var body = root.getChild("body");
        neck = body.getChild("neck");
        rider = body.getChild("rider");
        torso = rider.getChild("torso");
        scytheArm = torso.getChild("right_arm");
        reinsArm = torso.getChild("left_arm");
        scythe = scytheArm.getChild("scythe");
        cloak = torso.getChild("cloak");
    }

    public static LayerDefinition createBodyLayer()
    {
        var mesh = WraithBeastModel.createMesh();
        var body = mesh.getRoot().getChild("body");

        // Seated on the beast's back, its hips sunk a hair into the beast's
        var rider = body.addOrReplaceChild("rider", CubeListBuilder.create().texOffs(0, 64).addBox(-4.2F, -3.2F, -3, 8.4F, 3.3F, 6),
                PartPose.offset(0, SEAT_Y, SEAT_Z));
        for (int side = 0; side < 2; side++)
        {
            float sign = side == 0 ? -1 : 1;
            var thighBox = CubeListBuilder.create().texOffs(30, 64);
            var shinBox = CubeListBuilder.create().texOffs(50, 64);
            if (side == 1)
            {
                thighBox.mirror();
                shinBox.mirror();
            }
            var thigh = rider.addOrReplaceChild((side == 0 ? "right" : "left") + "_thigh", thighBox.addBox(-1.5F, -1.5F, -6, 3, 3, 6.5F),
                    PartPose.offsetAndRotation(sign * 4.6F, -1.5F, 0.5F, THIGH_TILT, 0, 0));
            thigh.addOrReplaceChild("shin", shinBox.addBox(-1.4F, -0.5F, -1.4F, 2.8F, 8, 2.8F),
                    PartPose.offsetAndRotation(0, 0, -5.2F, SHIN_TILT - THIGH_TILT, 0, 0));
        }

        var torso = rider.addOrReplaceChild("torso", CubeListBuilder.create()
                        .texOffs(0, 76).addBox(-4, -11, -2.5F, 8, 11.2F, 5)
                        // The gorget rises a little inside the breastplate, ending where the crown burns
                        .texOffs(28, 76).addBox(-2.5F, CROWN_Y, -2, 5, 1.9F, 4),
                PartPose.offset(0, -3, 0));
        torso.addOrReplaceChild("right_pauldron", CubeListBuilder.create().texOffs(48, 76).addBox(-6.3F, -11.4F, -3.2F, 3.7F, 3.5F, 6.4F), PartPose.ZERO);
        torso.addOrReplaceChild("left_pauldron", CubeListBuilder.create().texOffs(48, 76).mirror().addBox(2.6F, -11.4F, -3.2F, 3.7F, 3.5F, 6.4F), PartPose.ZERO);

        var scytheArm = torso.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(70, 64).addBox(-1.5F, -1, -1.5F, 3, 10, 3),
                PartPose.offsetAndRotation(-5.45F, -9.6F, 0, SCYTHE_ARM_REST, 0, 0));
        torso.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(70, 64).mirror().addBox(-1.5F, -1, -1.5F, 3, 10, 3),
                PartPose.offsetAndRotation(5.45F, -9.6F, 0, REINS_ARM_REST, 0, 0));

        // Held a little below its middle, the blade standing out from the top of the haft
        var scythe = scytheArm.addOrReplaceChild("scythe", CubeListBuilder.create()
                        .texOffs(84, 64).addBox(-0.6F, -18, -0.6F, 1.2F, 30, 1.2F)
                        .texOffs(90, 64).addBox(-0.9F, -19.2F, -0.9F, 1.8F, 1.4F, 1.8F),
                PartPose.offsetAndRotation(0, 8.5F, 0, SCYTHE_REST, 0, 0));
        scythe.addOrReplaceChild("blade", CubeListBuilder.create()
                        .texOffs(90, 70).addBox(-0.4F, -1.5F, -12.6F, 0.8F, 3, 12)
                        .texOffs(90, 86).addBox(-0.35F, 1.2F, -14.2F, 0.7F, 2.5F, 2.5F),
                PartPose.offset(0, -17.5F, 0));

        // Hung from the shoulders behind, clear of the breastplate's back
        torso.addOrReplaceChild("cloak", CubeListBuilder.create().texOffs(0, 94).addBox(-4.5F, 0, 0, 9, 15, 0, ModelBoxes.BACK),
                PartPose.offset(0, -10.6F, 3.4F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(DullahanRenderState state)
    {
        // Poses every part back to rest, then the beast's own walk
        beast.setupAnim(state);

        float breath = Mth.sin(state.ageInTicks * BREATH_RATE) * BREATH;
        torso.xRot = breath;
        float side = state.swingLeft ? -1 : 1;

        switch (state.phase)
        {
            case WINDUP ->
            {
                float wound = smooth(state.phaseAge / MausoleumBalance.SWEEP_WINDUP);
                torso.yRot = side * WIND_TWIST * wound;
                scytheArm.xRot = Mth.lerp(wound, SCYTHE_ARM_REST, WIND_ARM_RAISE);
                scytheArm.zRot = side * WIND_ARM_OUT * wound;
                scythe.xRot = Mth.lerp(wound, SCYTHE_REST, SCYTHE_LEVEL);
            }
            case RECOVER ->
            {
                float swing = smooth(state.phaseAge / SWING_TICKS);
                float settle = smooth((state.phaseAge - SWING_TICKS) / (MausoleumBalance.SWEEP_RECOVER - SWING_TICKS));
                torso.yRot = Mth.lerp(settle, Mth.lerp(swing, side * WIND_TWIST, side * SWING_TWIST), 0);
                scytheArm.xRot = Mth.lerp(settle, WIND_ARM_RAISE, SCYTHE_ARM_REST);
                scytheArm.zRot = Mth.lerp(settle, Mth.lerp(swing, side * WIND_ARM_OUT, side * SWING_ARM_OUT), 0);
                scythe.xRot = Mth.lerp(settle, SCYTHE_LEVEL, SCYTHE_REST);
            }
            case STUNNED ->
            {
                float slump = smooth(state.phaseAge / SLUMP_TICKS);
                torso.xRot += SLUMP * slump;
                scytheArm.xRot = Mth.lerp(slump, SCYTHE_ARM_REST, SLUMP_ARM);
                reinsArm.xRot = Mth.lerp(slump, REINS_ARM_REST, SLUMP_ARM);
                scythe.xRot = Mth.lerp(slump, SCYTHE_REST, SLUMP_SCYTHE);
                neck.xRot += SLUMP_NECK * slump;
            }
            default ->
            {
            }
        }

        cloak.xRot = state.walkAnimationSpeed * CLOAK_TRAIL + Mth.sin(state.ageInTicks * CLOAK_SWAY_RATE) * CLOAK_SWAY + 0.08F;
        cloak.zRot = Mth.cos(state.ageInTicks * CLOAK_SWAY_RATE * 0.7F) * CLOAK_SWAY * 0.4F;
    }

    private static float smooth(float t)
    {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** Moves a pose from the model's root into the rider's torso, where the crown burns. */
    public void toTorso(PoseStack poseStack)
    {
        root().getChild("body").translateAndRotate(poseStack);
        rider.translateAndRotate(poseStack);
        torso.translateAndRotate(poseStack);
    }
}
