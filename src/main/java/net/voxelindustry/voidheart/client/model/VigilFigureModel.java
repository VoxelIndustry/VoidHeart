package net.voxelindustry.voidheart.client.model;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.Set;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A hooded figure in a long tattered robe, facing -z with the hem of its robe at y=24 and nothing under it. The hood is a real hollow made of
 * separate plates, closing into a point on top and drooping into a tail behind. The same parts draw in several passes, see {@link Pass}.
 * The faces turned toward the hollow are painted black in curse_vigil.png and lit in curse_vigil_glow.png, which has the same layout at four times the
 * resolution. Those are found by texture offset and box size, so a box here can't change either without repainting them.
 */
public class VigilFigureModel extends Model<VigilFigureModel.State>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "curse_vigil"), "main");

    public static final int RUNES = 11;

    private static final float SHOULDER_X = 6;
    private static final float SHOULDER_Y = 3.4F;
    private static final float UPPER_LENGTH = 5.5F;
    private static final float FORE_LENGTH = 6;
    private static final float PALM_LENGTH = 3.9F;
    private static final float CUFF_DRAPE_ALONG = 0.6F;
    private static final float CUFF_DRAPE_SCALE = 0.7F;
    private static final float WING_DRAPE_ALONG = 0.55F;
    private static final float DRAPE_REST_SCALE = 0.25F;
    public static final float NECK_Y = 1.6F;

    // Pointing: the upper arm goes out and forward so the arm shows beside the body, then the sleeve opens toward the player with the pale hand coming out of its dark hole
    private static final float POINT_UPPER_X = 0.70F;
    private static final float POINT_UPPER_Y = 0.30F;
    private static final float POINT_UPPER_Z = -0.65F;
    private static final float POINT_FORE_BIAS_X = -0.10F;
    private static final float POINT_FORE_BIAS_Y = -0.05F;
    private static final float POINT_HAND_BIAS_X = -0.42F;
    private static final float POINT_HAND_BIAS_Y = 0.02F;
    private static final float FINGER_BASE_RISE = 0.7F;
    // Exactly at the eye, a finger is a dot. A slight bend over the dark of the sleeve lets it show as a finger
    private static final float POINT_FINGER_BEND_X = 0.26F;
    private static final float POINT_FINGER_BEND_Y = 0.12F;

    private static final float REST_UPPER_X = 0.09F;
    private static final float REST_UPPER_Z = 0.03F;
    private static final float REST_FORE_X = 0.03F;
    private static final float REST_FORE_Z = -0.1F;
    private static final float REST_HAND_Z = -0.05F;
    private static final float REST_FINGER_Y = 0.45F;
    private static final float REST_FINGER_Z = 0.9F;

    // The right arm of the leader, holding the baton out in front of it
    private static final float GRIP_UPPER_X = -0.19F;
    private static final float GRIP_UPPER_Y = 0.96F;
    private static final float GRIP_UPPER_Z = 0.22F;
    private static final float GRIP_FORE_X = -0.04F;
    private static final float GRIP_FORE_Y = 0.1F;
    private static final float GRIP_FORE_Z = -0.99F;
    private static final float BATON_X = -7.35F;
    private static final float BATON_Z = -6.75F;
    private static final float GRIP_Y = 8.9F;
    private static final float GRIP_BAND = 1.05F;
    private static final int   GRIP_BANDS = 3;

    private static final float STRIP_SWAY_SPEED = 0.05F;
    private static final float STRIP_SWAY_ANGLE = 0.035F;
    private static final float STRIP_PHASE_SPREAD = 1.37F;

    private static final float STRIP_FLARE = 0.13F;
    /**
     * x, pivot y, flare side (0 front, 1 back, 2 right, 3 left), z or x along the side, kind of strip
     */
    private static final float[][] STRIPS = {
            {-5, 13, 0, 0, 0},
            {-2.6F, 11, 0, 0, 1},
            {0.2F, 14, 0, 0, 2},
            {2.7F, 12, 0, 0, 1},
            {5.1F, 14, 0, 0, 3},
            {-4.6F, 12, 1, 0, 0},
            {-1.5F, 14, 1, 0, 3},
            {1.6F, 11, 1, 0, 1},
            {4.8F, 13, 1, 0, 0},
            {0, 14, 2, -2, 2},
            {0, 12, 2, 1.6F, 1},
            {0, 13, 3, -1.8F, 0},
            {0, 11, 3, 1.4F, 1},
    };
    private static final int[][] STRIP_KINDS = {
            // u, v, width, length
            {90, 24, 3, 10},
            {98, 24, 2, 12},
            {104, 24, 3, 8},
            {112, 24, 2, 9},
    };

    private final ModelPart   clothing;
    private final ModelPart   head;
    private final ModelPart   hoodCore;
    private final ModelPart   hoodHalo;
    private final ModelPart   baton;
    private final ModelPart   runes;
    private final ModelPart[] runeParts = new ModelPart[RUNES];
    private final ModelPart[] strips    = new ModelPart[STRIPS.length];
    private final Arm         leftArm;
    private final Arm         rightArm;
    private final ModelPart   rightFist;

    private final Vector3f upper = new Vector3f();
    private final Vector3f fore  = new Vector3f();
    private final Vector3f hand  = new Vector3f();
    private final Vector3f goal  = new Vector3f();

    public VigilFigureModel(ModelPart root)
    {
        super(root, RenderTypes::entityCutout);
        clothing = root.getChild("clothing");
        head = root.getChild("head");
        var cowl = head.getChild("cowl");
        hoodCore = cowl.getChild("hood_core");
        hoodHalo = cowl.getChild("hood_halo");
        baton = root.getChild("baton");
        runes = root.getChild("runes");
        for (int i = 0; i < RUNES; i++)
            runeParts[i] = runes.getChild("rune" + i);
        for (int i = 0; i < STRIPS.length; i++)
            strips[i] = clothing.getChild("strip" + i);
        leftArm = new Arm(clothing, "left", 1);
        rightArm = new Arm(clothing, "right", -1);
        rightFist = clothing.getChild("right_fist");
    }

    public static LayerDefinition createBodyLayer()
    {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();

        var clothing = root.addOrReplaceChild("clothing",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.5F, 2, -2.5F, 9, 7, 5)
                        .texOffs(28, 0).addBox(-5, 9, -3, 10, 5, 6)
                        .texOffs(60, 0).addBox(-5.5F, 14, -3.5F, 11, 4, 7)
                        .texOffs(0, 12).addBox(-6.5F, 18, -4, 13, 3, 8)
                        .texOffs(42, 12).addBox(-5, 2, -3.5F, 10, 2, 7)
                        .texOffs(80, 12).addBox(-4.5F, 0.4F, -3, 9, 2, 6),
                PartPose.ZERO);
        clothing.addOrReplaceChild("shoulder_right",
                CubeListBuilder.create().texOffs(0, 24).addBox(-5, -0.5F, -3.5F, 5, 1, 7, new CubeDeformation(0, 0.3F, 0)),
                PartPose.offsetAndRotation(-3, 1.7F, 0, 0, 0, -0.42F));
        clothing.addOrReplaceChild("shoulder_left",
                CubeListBuilder.create().texOffs(0, 24).addBox(0, -0.5F, -3.5F, 5, 1, 7, new CubeDeformation(0, 0.3F, 0)),
                PartPose.offsetAndRotation(3, 1.7F, 0, 0, 0, 0.42F));

        for (int i = 0; i < STRIPS.length; i++)
        {
            var strip = STRIPS[i];
            var kind = STRIP_KINDS[(int) strip[4]];
            int side = (int) strip[2];
            float x = side == 2 ? -5.8F : side == 3 ? 5.8F : strip[0];
            float z = side == 0 ? -3.4F : side == 1 ? 3.4F : strip[3];
            float flareX = side == 0 ? -STRIP_FLARE : side == 1 ? STRIP_FLARE : 0;
            float flareZ = side == 2 ? STRIP_FLARE : side == 3 ? -STRIP_FLARE : 0;
            // The strips on the sides hang edge-on to the front
            float turn = side >= 2 ? Mth.HALF_PI : 0;
            clothing.addOrReplaceChild("strip" + i,
                    CubeListBuilder.create().texOffs(kind[0], kind[1]).addBox(-kind[2] / 2F, 0, -0.5F, kind[2], kind[3], 1),
                    PartPose.offsetAndRotation(x, strip[1], z, flareX, turn, flareZ));
        }

        Arm.create(clothing, "left");
        Arm.create(clothing, "right");
        var fist = CubeListBuilder.create().texOffs(64, 70);
        for (int i = 0; i < GRIP_BANDS; i++)
            fist.addBox(-1.5F, i * GRIP_BAND, -1.5F, 3, 1, 3, new CubeDeformation(-0.12F, -0.1F, -0.12F));
        clothing.addOrReplaceChild("right_fist", fist, PartPose.offset(BATON_X, GRIP_Y, BATON_Z));

        var head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0, NECK_Y, 0));
        var cowl = head.addOrReplaceChild("cowl",
                CubeListBuilder.create()
                        // Fold under the chin, closing the opening from below
                        .texOffs(104, 36).addBox(-4.5F, -1.2F, -6.2F, 9, 2, 2)
                        // Floor, its top in the black
                        .texOffs(0, 52).addBox(-4.5F, 0.2F, -5, 9, 1, 7)
                        // Back of the hollow, cut to the arch of the roof in the texture
                        .texOffs(36, 52).addBox(-4, -7.5F, 1.4F, 8, 8, 1, Set.of(Direction.NORTH, Direction.SOUTH)),
                PartPose.rotation(0.14F, 0, 0));

        // The shell slopes down toward the back: walls and roof plates leaning in to close over the head, their front edges thickening into the rim,
        // a ridge on top and a fold of cloth drooping over the opening
        var shell = cowl.addOrReplaceChild("shell",
                CubeListBuilder.create()
                        .texOffs(78, 52).addBox(-1, -5.3F, 1.4F, 2, 1, 6, new CubeDeformation(-0.2F, 0, 0))
                        .texOffs(100, 52).addBox(-1.2F, -4.7F, -1.3F, 2.4F, 1.9F, 1),
                PartPose.offsetAndRotation(0, -4.7F, -5.4F, -0.22F, 0, 0));
        shell.addOrReplaceChild("brow",
                CubeListBuilder.create().texOffs(110, 52).addBox(-1, -0.1F, -2.8F, 2, 1, 3, new CubeDeformation(-0.2F, 0, 0)),
                PartPose.offsetAndRotation(0, -5.2F, 1.5F, 0.46F, 0, 0));
        shell.addOrReplaceChild("wall_right",
                CubeListBuilder.create()
                        .texOffs(0, 36).addBox(-0.5F, -5, -5, 1, 5, 7, new CubeDeformation(0, 0, 0.5F))
                        .texOffs(80, 36).addBox(-0.8F, -5, -6, 2, 5, 2),
                PartPose.offsetAndRotation(-4.3F, 4.9F, 5.4F, 0, 0, 0.09F));
        shell.addOrReplaceChild("wall_left",
                CubeListBuilder.create()
                        .texOffs(20, 36).addBox(-0.5F, -5, -5, 1, 5, 7, new CubeDeformation(0, 0, 0.5F))
                        .texOffs(80, 36).addBox(-1.2F, -5, -6, 2, 5, 2),
                PartPose.offsetAndRotation(4.3F, 4.9F, 5.4F, 0, 0, -0.09F));
        shell.addOrReplaceChild("roof_right",
                CubeListBuilder.create()
                        .texOffs(40, 36).addBox(-0.5F, -6, -5, 1, 6.4F, 7)
                        .texOffs(92, 36).addBox(-0.8F, -5.4F, -6, 2, 5.8F, 2),
                PartPose.offsetAndRotation(-3.85F, 0, 5.4F, 0, 0, 0.68F));
        shell.addOrReplaceChild("roof_left",
                CubeListBuilder.create()
                        .texOffs(60, 36).addBox(-0.5F, -6, -5, 1, 6.4F, 7)
                        .texOffs(92, 36).addBox(-1.2F, -5.4F, -6, 2, 5.8F, 2),
                PartPose.offsetAndRotation(3.85F, 0, 5.4F, 0, 0, -0.68F));

        // The point of the hood falling behind it
        var tail = cowl.addOrReplaceChild("tail0",
                CubeListBuilder.create().texOffs(0, 62).addBox(-3, -2.5F, -0.5F, 6, 5, 4),
                PartPose.offsetAndRotation(0, -3.6F, 3.6F, -0.72F, 0, 0));
        tail = tail.addOrReplaceChild("tail1",
                CubeListBuilder.create().texOffs(20, 62).addBox(-1.5F, -1.5F, -0.3F, 3, 3, 4),
                PartPose.offsetAndRotation(0, 0.2F, 3.2F, -0.42F, 0.08F, 0));
        tail.addOrReplaceChild("tail2",
                CubeListBuilder.create().texOffs(34, 62).addBox(-1, -1, -0.2F, 2, 2, 4),
                PartPose.offsetAndRotation(0, 0, 3.4F, -0.38F, 0.1F, 0));

        // Only ever drawn glowing: a light deep in the hollow, and the haze it casts out of the opening
        cowl.addOrReplaceChild("hood_core",
                CubeListBuilder.create().texOffs(84, 72).addBox(-3.5F, -8.5F, 0, 7, 9, 0, Set.of(Direction.NORTH)),
                PartPose.offset(0, 0, 1.2F));
        cowl.addOrReplaceChild("hood_halo",
                CubeListBuilder.create().texOffs(64, 72).addBox(-10, -10, 0, 20, 20, 0, Set.of(Direction.NORTH)),
                PartPose.offset(0, -4, -7));

        var baton = root.addOrReplaceChild("baton",
                CubeListBuilder.create()
                        .texOffs(96, 64).addBox(-1, 5, -1, 2, 20, 2, new CubeDeformation(-0.2F))
                        .texOffs(108, 64).addBox(-1.5F, 1.5F, -1.5F, 3, 4, 3, new CubeDeformation(-0.1F))
                        .texOffs(108, 72).addBox(-1.5F, 6, -1.5F, 3, 1, 3, new CubeDeformation(-0.25F))
                        .texOffs(108, 78).addBox(-1, -0.3F, -1, 2, 2, 2, new CubeDeformation(-0.35F))
                        .texOffs(118, 78).addBox(-1, 23.5F, -1, 2, 2, 2, new CubeDeformation(-0.05F)),
                PartPose.offset(BATON_X, 0, BATON_Z));
        // Rings where the hand doesn't reach
        baton.addOrReplaceChild("ring",
                CubeListBuilder.create().texOffs(108, 72).addBox(-1.5F, 16, -1.5F, 3, 1, 3, new CubeDeformation(-0.3F)),
                PartPose.ZERO);

        var runes = root.addOrReplaceChild("runes", CubeListBuilder.create(), PartPose.offset(0, 7.2F, -3.7F));
        for (int i = 0; i < RUNES; i++)
        {
            runes.addOrReplaceChild("rune" + i,
                    CubeListBuilder.create().texOffs(i % 6 * 16, 96 + i / 6 * 16).addBox(-8, -8, 0, 16, 16, 0, Set.of(Direction.NORTH)),
                    PartPose.ZERO);
        }

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(State state)
    {
        super.setupAnim(state);

        var pose = state.pose;
        var pass = state.pass;

        clothing.visible = pass == Pass.BODY;
        head.visible = pass == Pass.BODY || pass == Pass.HOOD;
        hoodCore.visible = pass == Pass.HOOD;
        hoodHalo.visible = pass == Pass.HOOD;
        baton.visible = pose.leader && (pass == Pass.BODY || pass == Pass.BATON);
        runes.visible = pass == Pass.RUNE;

        head.yRot = pose.headYaw;
        head.xRot = pose.headPitch;
        head.zRot = pose.headTilt;

        if (pass == Pass.RUNE)
        {
            for (int i = 0; i < RUNES; i++)
                runeParts[i].visible = i == pose.rune;
        }
        if (pass != Pass.BODY)
            return;

        for (int i = 0; i < strips.length; i++)
        {
            float phase = pose.time * STRIP_SWAY_SPEED + pose.phase + i * STRIP_PHASE_SPREAD;
            strips[i].xRot += Mth.sin(phase) * STRIP_SWAY_ANGLE;
            strips[i].zRot += Mth.cos(phase * 0.8F + i) * STRIP_SWAY_ANGLE;
        }

        poseLeft(pose);
        rightFist.visible = pose.leader;
        if (pose.leader)
            poseGrip();
        else
            poseRest(rightArm, pose);
    }

    /**
     * The arm chain is a flat list of parts placed in model space, since each segment aims at a point of its own.
     */
    private void poseLeft(Pose pose)
    {
        float raise = pose.raise;
        if (raise <= 0)
        {
            poseRest(leftArm, pose);
            return;
        }

        float side = leftArm.side;
        float x = side * SHOULDER_X;
        float y = SHOULDER_Y;
        float z = 0;

        upper.set(side * (REST_UPPER_X + pose.splay), 1, REST_UPPER_Z).normalize();
        goal.set(side * POINT_UPPER_X, POINT_UPPER_Y, POINT_UPPER_Z).normalize();
        upper.lerp(goal, raise).normalize();
        place(leftArm.upper, x, y, z, upper);
        x += upper.x * UPPER_LENGTH;
        y += upper.y * UPPER_LENGTH;
        z += upper.z * UPPER_LENGTH;

        fore.set(side * REST_FORE_X, 1, REST_FORE_Z).normalize();
        toward(goal, pose, x, y, z).add(side * POINT_FORE_BIAS_X, POINT_FORE_BIAS_Y, 0).normalize();
        fore.lerp(goal, raise).normalize();
        place(leftArm.fore, x, y, z, fore);
        placeDrapes(leftArm, x, y, z, raise);
        x += fore.x * FORE_LENGTH;
        y += fore.y * FORE_LENGTH;
        z += fore.z * FORE_LENGTH;

        hand.set(0, 1, REST_HAND_Z).normalize();
        toward(goal, pose, x, y, z).add(side * POINT_HAND_BIAS_X, POINT_HAND_BIAS_Y, 0).normalize();
        hand.lerp(goal, raise).normalize();
        place(leftArm.hand, x, y, z, hand);
        x += hand.x * PALM_LENGTH;
        y += hand.y * PALM_LENGTH - FINGER_BASE_RISE * raise;
        z += hand.z * PALM_LENGTH;

        upper.set(0, REST_FINGER_Y, REST_FINGER_Z).normalize();
        toward(goal, pose, x, y, z).add(side * POINT_FINGER_BEND_X, POINT_FINGER_BEND_Y, 0).normalize();
        upper.lerp(goal, raise).normalize();
        place(leftArm.finger, x, y, z, upper);
    }

    private void poseRest(Arm arm, Pose pose)
    {
        float side = arm.side;
        float x = side * SHOULDER_X;
        float y = SHOULDER_Y;
        float z = 0;

        upper.set(side * (REST_UPPER_X + pose.splay), 1, REST_UPPER_Z).normalize();
        place(arm.upper, x, y, z, upper);
        x += upper.x * UPPER_LENGTH;
        y += upper.y * UPPER_LENGTH;
        z += upper.z * UPPER_LENGTH;

        fore.set(side * REST_FORE_X, 1, REST_FORE_Z).normalize();
        place(arm.fore, x, y, z, fore);
        placeDrapes(arm, x, y, z, 0);
        x += fore.x * FORE_LENGTH;
        y += fore.y * FORE_LENGTH;
        z += fore.z * FORE_LENGTH;

        hand.set(0, 1, REST_HAND_Z).normalize();
        place(arm.hand, x, y, z, hand);
        x += hand.x * PALM_LENGTH;
        y += hand.y * PALM_LENGTH;
        z += hand.z * PALM_LENGTH;

        goal.set(0, REST_FINGER_Y, REST_FINGER_Z).normalize();
        place(arm.finger, x, y, z, goal);
        arm.hand.visible = true;
        arm.finger.visible = true;
    }

    private void poseGrip()
    {
        float x = -SHOULDER_X;
        float y = SHOULDER_Y;
        float z = 0;

        upper.set(GRIP_UPPER_X, GRIP_UPPER_Y, GRIP_UPPER_Z).normalize();
        place(rightArm.upper, x, y, z, upper);
        x += upper.x * UPPER_LENGTH;
        y += upper.y * UPPER_LENGTH;
        z += upper.z * UPPER_LENGTH;

        fore.set(GRIP_FORE_X, GRIP_FORE_Y, GRIP_FORE_Z).normalize();
        place(rightArm.fore, x, y, z, fore);
        placeDrapes(rightArm, x, y, z, 1);

        rightArm.hand.visible = false;
        rightArm.finger.visible = false;
    }

    /**
     * The wide sleeve keeps hanging straight down from the raised arm: one curtain under the upper arm and a shorter one under the cuff.
     */
    private void placeDrapes(Arm arm, float elbowX, float elbowY, float elbowZ, float raise)
    {
        arm.wing.x = elbowX - upper.x * UPPER_LENGTH * (1 - WING_DRAPE_ALONG);
        arm.wing.y = elbowY - upper.y * UPPER_LENGTH * (1 - WING_DRAPE_ALONG);
        arm.wing.z = elbowZ - upper.z * UPPER_LENGTH * (1 - WING_DRAPE_ALONG);
        arm.wing.yRot = (float) Mth.atan2(-upper.z, upper.x);
        arm.wing.yScale = Mth.lerp(raise, DRAPE_REST_SCALE, 1);

        arm.drape.x = elbowX + fore.x * FORE_LENGTH * CUFF_DRAPE_ALONG;
        arm.drape.y = elbowY + fore.y * FORE_LENGTH * CUFF_DRAPE_ALONG;
        arm.drape.z = elbowZ + fore.z * FORE_LENGTH * CUFF_DRAPE_ALONG;
        arm.drape.yRot = (float) Mth.atan2(-fore.z, fore.x);
        arm.drape.yScale = Mth.lerp(raise, DRAPE_REST_SCALE, CUFF_DRAPE_SCALE);
    }

    private Vector3f toward(Vector3f out, Pose pose, float x, float y, float z)
    {
        out.set(pose.targetX - x, pose.targetY - y, pose.targetZ - z);
        return out.lengthSquared() < 1.0E-4F ? out.set(0, 0, -1) : out.normalize();
    }

    /**
     * Stands the part at that point with its +y axis along the unit direction.
     */
    private static void place(ModelPart part, float x, float y, float z, Vector3f direction)
    {
        part.x = x;
        part.y = y;
        part.z = z;
        part.xRot = -(float) Math.acos(Mth.clamp(direction.y, -1, 1));
        part.yRot = (float) Mth.atan2(-direction.x, -direction.z);
        part.zRot = 0;
    }

    /**
     * What a pass of the model draws. Sleeves and robe never glow, and the glowing parts each follow their own timing and intensity.
     */
    public enum Pass
    {
        BODY, RUNE, HOOD, BATON
    }

    /**
     * Everything about the pose of one figure this frame, shared by the states of its passes.
     */
    public static final class Pose
    {
        public float time;
        public float phase;
        public float splay;
        public float headYaw;
        public float headPitch;
        public float headTilt;
        public float targetX;
        public float targetY;
        public float targetZ;
        public float raise;
        public boolean leader;
        public int rune;
    }

    public record State(Pose pose, Pass pass)
    {
    }

    private static final class Arm
    {
        private final ModelPart upper;
        private final ModelPart fore;
        private final ModelPart hand;
        private final ModelPart finger;
        private final ModelPart wing;
        private final ModelPart drape;
        private final float     side;

        private Arm(ModelPart clothing, String name, float side)
        {
            upper = clothing.getChild(name + "_upper");
            fore = clothing.getChild(name + "_fore");
            hand = clothing.getChild(name + "_hand");
            finger = clothing.getChild(name + "_finger");
            wing = clothing.getChild(name + "_wing");
            drape = clothing.getChild(name + "_drape");
            this.side = side;
        }

        private static void create(PartDefinition clothing, String name)
        {
            clothing.addOrReplaceChild(name + "_upper",
                    CubeListBuilder.create().texOffs(26, 24).addBox(-2, -0.6F, -2, 4, 7, 4, new CubeDeformation(-0.4F, -0.2F, -0.4F)),
                    PartPose.ZERO);
            clothing.addOrReplaceChild(name + "_fore",
                    CubeListBuilder.create()
                            .texOffs(42, 24).addBox(-2, 0, -2, 4, 6, 4, new CubeDeformation(-0.25F))
                            .texOffs(58, 24).addBox(-2.5F, 3.8F, -2.5F, 5, 3, 5, new CubeDeformation(-0.2F)),
                    PartPose.ZERO);
            var hand = clothing.addOrReplaceChild(name + "_hand",
                    CubeListBuilder.create().texOffs(64, 64).addBox(-0.5F, -0.5F, -0.5F, 1, 3, 1, new CubeDeformation(-0.05F)),
                    PartPose.ZERO);
            // A long narrow palm standing on its edge, thumb up when pointing and the other fingers folded under it
            hand.addOrReplaceChild("palm",
                    CubeListBuilder.create()
                            .texOffs(70, 64).addBox(-1, 1.7F, -0.5F, 2, 3, 1, new CubeDeformation(0.35F, 0, 0.15F))
                            .texOffs(84, 64).addBox(-1.3F, 4.6F, -0.4F, 2, 1, 1, new CubeDeformation(-0.7F, 0.3F, 0.15F))
                            .texOffs(84, 64).addBox(-2.1F, 4.5F, -0.4F, 2, 1, 1, new CubeDeformation(-0.7F, 0.1F, 0.15F))
                            .texOffs(90, 64).addBox(1.15F, 1.9F, -0.4F, 1, 2, 1, new CubeDeformation(-0.12F, 0.1F, -0.12F)),
                    PartPose.rotation(0, Mth.HALF_PI, 0));
            clothing.addOrReplaceChild(name + "_finger",
                    CubeListBuilder.create().texOffs(78, 64).addBox(-0.5F, 0.5F, -0.5F, 1, 4, 1, new CubeDeformation(-0.02F, 1, -0.02F)),
                    PartPose.ZERO);
            clothing.addOrReplaceChild(name + "_wing",
                    CubeListBuilder.create().texOffs(78, 24).addBox(-2.5F, 0, -0.5F, 5, 7, 1, new CubeDeformation(0.5F, 0, -0.1F)),
                    PartPose.ZERO);
            clothing.addOrReplaceChild(name + "_drape",
                    CubeListBuilder.create().texOffs(78, 24).addBox(-2.5F, 0, -0.5F, 5, 7, 1, new CubeDeformation(-0.5F, 0, 0.8F)),
                    PartPose.ZERO);
        }
    }
}
