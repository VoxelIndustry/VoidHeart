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
import net.voxelindustry.voidheart.client.render.state.WraithBeastRenderState;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A gaunt horse of shadow, ridge of spine showing along its back, a torn shroud hanging off its flanks and a ragged mane and tail. The body pivots on its hind
 * hips, so rearing lifts the front legs, neck and head together while the hind legs stay planted.
 */
public class WraithBeastModel extends EntityModel<WraithBeastRenderState>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "wraith_beast"), "main");

    private static final int SHROUDS = 3;
    private static final float[] SHROUD_Z = { -14, -8.5F, -3 };
    private static final float SIDE = 4 + ModelBoxes.GAP;

    private static final float NECK_TILT = 0.55F;
    // From level, the head hangs low like a horse's at rest
    private static final float HEAD_TILT = 0.9F;
    private static final float TAIL_TILT = 0.45F;
    private static final float GALLOP_RATE = 0.55F;
    private static final float GALLOP_REACH = 0.7F;
    private static final float GALLOP_BOB = 0.6F;
    private static final float KNEE_BEND = 0.6F;
    private static final float REAR_PITCH = -0.75F;
    private static final float REAR_LEG_LIFT = -1.1F;
    private static final float REAR_KNEE = 1.3F;
    private static final float REAR_PAW = 0.35F;
    private static final float REAR_PAW_RATE = 0.9F;
    private static final float REAR_NECK = -0.45F;
    private static final float SLAM_DIP = 0.12F;
    private static final float SLAM_HEAD = 0.45F;
    private static final float JAW_REST = 0.05F;
    private static final float JAW_OPEN = 0.55F;
    private static final float BREATH_RATE = 0.07F;
    private static final float BREATH = 0.03F;
    private static final float CLOTH_SWAY_RATE = 0.09F;
    private static final float CLOTH_SWAY = 0.14F;
    private static final float CLOTH_TRAIL = 0.6F;

    private final ModelPart body;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart tail;
    private final ModelPart mane;
    private final ModelPart[] frontLegs = new ModelPart[2];
    private final ModelPart[] hindLegs = new ModelPart[2];
    private final ModelPart[] frontShins = new ModelPart[2];
    private final ModelPart[] hindShins = new ModelPart[2];
    private final ModelPart[] shrouds = new ModelPart[SHROUDS * 2];

    public WraithBeastModel(ModelPart root)
    {
        super(root);
        body = root.getChild("body");
        neck = body.getChild("neck");
        mane = neck.getChild("mane");
        head = neck.getChild("head");
        jaw = head.getChild("jaw");
        tail = body.getChild("tail");
        for (int side = 0; side < 2; side++)
        {
            String name = side == 0 ? "right" : "left";
            frontLegs[side] = body.getChild(name + "_front_leg");
            frontShins[side] = frontLegs[side].getChild("shin");
            hindLegs[side] = root.getChild(name + "_hind_leg");
            hindShins[side] = hindLegs[side].getChild("shin");
            for (int i = 0; i < SHROUDS; i++)
                shrouds[side * SHROUDS + i] = body.getChild(name + "_shroud_" + i);
        }
    }

    public static LayerDefinition createBodyLayer()
    {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();

        // Pivots on the hind hips, so everything on it is placed from there
        var body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4, -7, -18, 8, 8, 20)
                        // The spine, sunk into the back so their tops never share a plane
                        .texOffs(56, 0).addBox(-1, -8.5F, -16, 2, 2, 16),
                PartPose.offset(0, 11, 9));

        var neck = body.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(92, 0).addBox(-2, -10, -3, 4, 11, 6),
                PartPose.offsetAndRotation(0, -5, -16, NECK_TILT, 0, 0));
        neck.addOrReplaceChild("mane", CubeListBuilder.create().texOffs(76, 28).addBox(0, 0, 0, 0, 9, 3, ModelBoxes.LEFT),
                PartPose.offset(0, -10, 3 + ModelBoxes.GAP));

        var head = neck.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 28).addBox(-2.5F, -4, -3.5F, 5, 5, 6)
                        // The muzzle sinks a hair into the skull behind it
                        .texOffs(22, 28).addBox(-1.75F, -3.25F, -9.45F, 3.5F, 3.5F, 6),
                PartPose.offsetAndRotation(0, -9, 0, -NECK_TILT + HEAD_TILT, 0, 0));
        // Its top a hair inside the muzzle, its back well inside the skull
        head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(42, 28).addBox(-1.5F, 0, -9, 3, 1.5F, 6.5F),
                PartPose.offset(0, 0.25F - ModelBoxes.GAP, -0.5F));
        head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(70, 28).addBox(-0.5F, -2.5F, -0.5F, 1, 3, 1),
                PartPose.offsetAndRotation(-1.5F, -4, 1.5F, -0.3F, 0, -0.25F));
        head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(70, 28).mirror().addBox(-0.5F, -2.5F, -0.5F, 1, 3, 1),
                PartPose.offsetAndRotation(1.5F, -4, 1.5F, -0.3F, 0, 0.25F));

        body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(84, 28).addBox(0, 0, 0, 0, 12, 3, ModelBoxes.LEFT),
                PartPose.offsetAndRotation(0, -6, 2 + ModelBoxes.GAP, TAIL_TILT, 0, 0));

        for (int side = 0; side < 2; side++)
        {
            String name = side == 0 ? "right" : "left";
            float sign = side == 0 ? -1 : 1;
            boolean mirror = side == 1;
            // Their outer faces stay inside the flanks' planes
            addLeg(body, name + "_front_leg", sign * 2.4F, 0, -15, 3, 3, -1, 7, 6, mirror, 0, 42);
            addLeg(root, name + "_hind_leg", sign * 2.5F, 12, 8, 3.5F, 4, -2, 7, 5, mirror, 50, 40);

            float shroudX = side == 0 ? -SIDE : SIDE;
            for (int i = 0; i < SHROUDS; i++)
            {
                body.addOrReplaceChild(name + "_shroud_" + i, CubeListBuilder.create().texOffs(100 + i * 8, 28).addBox(0, 0, -2, 0, 7, 4, ModelBoxes.LEFT),
                        PartPose.offset(shroudX, -1, SHROUD_Z[i]));
            }
        }
        return LayerDefinition.create(mesh, 128, 64);
    }

    /**
     * An upper leg holding a thinner shin, which holds the hoof. Each sinks a little into the part above so no two of their faces share a plane.
     */
    private static void addLeg(PartDefinition parent, String name, float x, float y, float z, float width, float depth, float top, float upper, float shinAt,
                               boolean mirror, int u, int v)
    {
        var builder = CubeListBuilder.create().texOffs(u, v);
        if (mirror)
            builder.mirror();
        var leg = parent.addOrReplaceChild(name, builder.addBox(-width / 2, top, -depth / 2, width, upper, depth), PartPose.offset(x, y, z));

        int shinV = v + (int) Math.ceil(depth + upper);
        var shin = CubeListBuilder.create().texOffs(u, shinV);
        if (mirror)
            shin.mirror();
        shin.addBox(-1, -0.5F, -1, 2, 6.5F, 2)
                .texOffs(u + 8, shinV).addBox(-1.4F, 5.5F, -1.4F, 2.8F, 1.5F, 2.8F);
        leg.addOrReplaceChild("shin", shin, PartPose.offset(0, shinAt, 0));
    }

    @Override
    public void setupAnim(WraithBeastRenderState state)
    {
        super.setupAnim(state);

        float pos = state.walkAnimationPos * GALLOP_RATE;
        float speed = state.walkAnimationSpeed * (1 - state.rear);
        float breath = Mth.sin(state.ageInTicks * BREATH_RATE) * BREATH;

        body.xRot = state.rear * REAR_PITCH + state.slam * SLAM_DIP + breath;
        body.y += Mth.abs(Mth.sin(pos)) * -GALLOP_BOB * speed;

        neck.xRot += state.rear * REAR_NECK + state.slam * SLAM_HEAD - breath * 2;
        neck.yRot = Mth.clamp(state.yRot, -40, 40) * Mth.DEG_TO_RAD * 0.5F;
        head.xRot += state.xRot * Mth.DEG_TO_RAD * 0.5F;
        jaw.xRot = JAW_REST + state.rear * JAW_OPEN;

        // Diagonal pairs move together, the gait of a trot pushed hard
        for (int side = 0; side < 2; side++)
        {
            float phase = side == 0 ? 0 : Mth.PI;
            float front = Mth.cos(pos + phase) * GALLOP_REACH * speed;
            float hind = Mth.cos(pos + phase + Mth.PI) * GALLOP_REACH * speed;

            float paw = Mth.sin(state.ageInTicks * REAR_PAW_RATE + phase) * REAR_PAW * state.rear;
            frontLegs[side].xRot = front + state.rear * REAR_LEG_LIFT + paw - body.xRot;
            frontShins[side].xRot = Math.max(0, -Mth.sin(pos + phase)) * KNEE_BEND * speed + state.rear * REAR_KNEE;
            hindLegs[side].xRot = hind;
            hindShins[side].xRot = Math.max(0, Mth.sin(pos + phase)) * KNEE_BEND * speed;
        }

        tail.xRot += speed * CLOTH_TRAIL - state.rear * 0.3F;
        tail.zRot = Mth.sin(state.ageInTicks * CLOTH_SWAY_RATE) * CLOTH_SWAY;
        mane.xRot = speed * CLOTH_TRAIL * 0.5F + Mth.sin(state.ageInTicks * CLOTH_SWAY_RATE + 1) * CLOTH_SWAY * 0.5F;
        for (int i = 0; i < shrouds.length; i++)
        {
            float sway = Mth.sin(state.ageInTicks * CLOTH_SWAY_RATE + i * 1.3F) * CLOTH_SWAY;
            shrouds[i].xRot = sway + speed * CLOTH_TRAIL;
            shrouds[i].zRot = (i < SHROUDS ? 1 : -1) * (Mth.cos(state.ageInTicks * CLOTH_SWAY_RATE * 0.8F + i) * CLOTH_SWAY * 0.5F + 0.08F);
        }
    }
}
