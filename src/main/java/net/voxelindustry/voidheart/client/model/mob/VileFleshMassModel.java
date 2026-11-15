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
import net.voxelindustry.voidheart.client.render.state.VileFleshMassRenderState;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A heap of flesh in three tiers with lumps grown over them, a vertical gash down the front of its middle tier held between two toothed lips, eyes
 * all over, and six tentacles lying out round its foot. Its front looks along -z.
 */
public class VileFleshMassModel extends EntityModel<VileFleshMassRenderState>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "vile_flesh_mass"), "main");

    private static final float HALF_PI = Mth.HALF_PI;

    // x, y, z of each lump's middle, its size, then its texture offset
    private static final float[][] LUMPS = {
            { -23.4F, -14.1F, -14.2F, 12, 11, 12, 208, 0 },
            { 22.6F, -12.3F, 9.8F, 10, 9, 10, 208, 23 },
            { 8.3F, -22.1F, 17.7F, 14, 9, 12, 152, 97 },
            { -12.6F, -24.4F, 12.9F, 10, 8, 10, 204, 97 },
            { -16.5F, -29.5F, -4.2F, 9, 7, 9, 0, 138 },
            { 15.8F, -28.7F, 2.6F, 8, 6, 10, 36, 138 },
            { -2.4F, -40.4F, 5.6F, 10, 5, 8, 72, 138 } };
    // The tiers sit a little askew of each other, so the heap does not read as stacked
    private static final float UPPER_TURN = 0.06F;
    private static final float CROWN_TURN = -0.18F;
    // x, y, z on the surface, the way it looks (0 front, 1 right, 2 back, 3 left, 4 up), and whether it is a large one
    private static final float[][] EYES = {
            { -15.3F, -6.4F, -26.31F, 0, 1 }, { 12.6F, -8.1F, -26.33F, 0, 0 }, { 19.1F, -4.2F, -26.32F, 0, 0 },
            { -12.4F, -22.6F, -18.71F, 0, 1 }, { 11.9F, -19.3F, -18.72F, 0, 0 }, { 14.6F, -24.4F, -18.73F, 0, 0 },
            { -4.6F, -34.1F, -10.01F, 0, 0 }, { 5.2F, -33.2F, -10.02F, 0, 1 },
            { 26.31F, -7.3F, 4.6F, 1, 0 }, { -26.32F, -6.9F, 7.7F, 3, 1 },
            { 19.81F, -21.6F, -6.1F, 1, 0 }, { -19.82F, -19.9F, 3.4F, 3, 0 },
            { 2.1F, -38.91F, -1.3F, 4, 1 }, { -22.4F, -13.31F, -22.6F, 4, 0 } };
    private static final float[] TEETH_Y = { -14, -11.5F, -9, -6.5F, -4, -1.5F };
    private static final float[] TENTACLE_ANGLES = { 25, 85, 145, 205, 265, 325 };
    private static final float TENTACLE_REACH = 24.3F;
    // Width and length of each segment of a tentacle, from its root
    private static final float[][] SEGMENTS = { { 6, 10, 0 }, { 5, 9, 32 }, { 4, 8, 60 }, { 3, 7, 84 } };
    private static final float LIP_OPEN = 0.75F;

    private final ModelPart body;
    private final ModelPart upper;
    private final ModelPart crown;
    private final ModelPart lipLeft;
    private final ModelPart lipRight;
    private final ModelPart gullet;
    private final ModelPart[] lumps = new ModelPart[LUMPS.length];
    private final ModelPart[] eyes = new ModelPart[EYES.length];
    private final ModelPart[][] tentacles = new ModelPart[TENTACLE_ANGLES.length][SEGMENTS.length];

    public VileFleshMassModel(ModelPart root)
    {
        super(root);
        body = root.getChild("body");
        upper = body.getChild("upper");
        crown = body.getChild("crown");
        lipLeft = body.getChild("lip_left");
        lipRight = body.getChild("lip_right");
        gullet = body.getChild("gullet");
        for (int i = 0; i < lumps.length; i++)
            lumps[i] = body.getChild("lump" + i);
        for (int i = 0; i < eyes.length; i++)
            eyes[i] = body.getChild("eye" + i);
        for (int t = 0; t < tentacles.length; t++)
        {
            var part = body.getChild("tentacle" + t);
            for (int s = 0; s < SEGMENTS.length; s++)
            {
                tentacles[t][s] = part;
                if (s < SEGMENTS.length - 1)
                    part = part.getChild("segment");
            }
        }
    }

    public static LayerDefinition createBodyLayer()
    {
        var mesh = new MeshDefinition();
        var body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-26, -13, -26, 52, 13, 52), PartPose.offset(0, 24, 0));
        body.addOrReplaceChild("upper", CubeListBuilder.create()
                .texOffs(0, 65).addBox(-19.5F, -27.3F, -18.4F, 39, 15, 37), PartPose.rotation(0, UPPER_TURN, 0));
        body.addOrReplaceChild("crown", CubeListBuilder.create()
                .texOffs(152, 65).addBox(-11.2F, -38.6F, -9.7F, 22, 12, 20), PartPose.rotation(0, CROWN_TURN, 0));
        for (int i = 0; i < LUMPS.length; i++)
        {
            var l = LUMPS[i];
            body.addOrReplaceChild("lump" + i, CubeListBuilder.create().texOffs((int) l[6], (int) l[7])
                    .addBox(-l[3] / 2, -l[4] / 2 - 0.5F, -l[5] / 2, l[3], l[4], l[5]), PartPose.offset(l[0], l[1], l[2]));
        }

        // The gash: dark flesh between two lips hinged at their outer edges, set with teeth along the inner ones
        body.addOrReplaceChild("gullet", CubeListBuilder.create()
                .texOffs(152, 120).addBox(-3.5F, -15, -1, 7, 15, 2), PartPose.offset(0, -12.6F, -19.2F));
        var left = body.addOrReplaceChild("lip_left", CubeListBuilder.create()
                .texOffs(120, 120).addBox(0, -15, -2.5F, 4, 15, 4), PartPose.offset(-7.6F, -12.8F, -19.4F));
        var right = body.addOrReplaceChild("lip_right", CubeListBuilder.create()
                .texOffs(136, 120).addBox(-4, -15, -2.5F, 4, 15, 4), PartPose.offset(7.7F, -12.9F, -19.3F));
        var leftTeeth = CubeListBuilder.create().texOffs(170, 120);
        var rightTeeth = CubeListBuilder.create().texOffs(170, 120);
        for (float y : TEETH_Y)
        {
            leftTeeth.addBox(3.7F, y, -2.1F, 1.8F, 1.2F, 1.4F);
            rightTeeth.addBox(-5.5F, y + 0.7F, -2.2F, 1.8F, 1.2F, 1.4F);
        }
        left.addOrReplaceChild("teeth", leftTeeth, PartPose.ZERO);
        right.addOrReplaceChild("teeth", rightTeeth, PartPose.ZERO);

        for (int i = 0; i < EYES.length; i++)
        {
            var e = EYES[i];
            var cube = e[4] > 0 ? CubeListBuilder.create().texOffs(104, 124).addBox(-2.5F, -2.5F, -0.5F, 5, 5, 1)
                                : CubeListBuilder.create().texOffs(104, 120).addBox(-1.5F, -1.5F, -0.5F, 3, 3, 1);
            facing(body, "eye" + i, cube, e);
        }

        for (int t = 0; t < TENTACLE_ANGLES.length; t++)
        {
            float angle = TENTACLE_ANGLES[t] * Mth.DEG_TO_RAD;
            PartDefinition parent = body;
            String name = "tentacle" + t;
            var pose = PartPose.offsetAndRotation(Mth.sin(angle) * TENTACLE_REACH, -3.7F - t * 0.13F, -Mth.cos(angle) * TENTACLE_REACH, 0, -angle, 0);
            for (int s = 0; s < SEGMENTS.length; s++)
            {
                float w = SEGMENTS[s][0], length = SEGMENTS[s][1];
                parent = parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs((int) SEGMENTS[s][2], 120)
                        .addBox(-w / 2, -w / 2, -length, w, w, length), pose);
                name = "segment";
                pose = PartPose.offset(0, 0, -length + 0.4F);
            }
        }
        return LayerDefinition.create(mesh, 256, 256);
    }

    private static void facing(PartDefinition body, String name, CubeListBuilder cube, float[] at)
    {
        int way = (int) at[3];
        float yRot = switch (way)
        {
            case 1 -> -HALF_PI;
            case 2 -> Mth.PI;
            case 3 -> HALF_PI;
            default -> 0;
        };
        float xRot = way == 4 ? -HALF_PI : 0;
        body.addOrReplaceChild(name, cube, PartPose.offsetAndRotation(at[0], at[1], at[2], xRot, way == 4 ? 0 : yRot, 0));
    }

    @Override
    public void setupAnim(VileFleshMassRenderState state)
    {
        super.setupAnim(state);
        float age = state.ageInTicks;
        // It does not turn round on its foot: the whole heap faces where its body does
        body.yRot = 0;

        float breath = Mth.sin(age * (state.awake ? 0.11F : 0.05F));
        float heave = 1 + breath * 0.035F;
        if (state.spit >= 0)
            heave += Mth.sin(Math.min(1, state.spit) * Mth.PI) * 0.09F;
        if (state.feed >= 0)
            heave += Mth.sin(state.feed * Mth.PI) * 0.07F;
        upper.yScale = heave;
        crown.yScale = 1 + (heave - 1) * 1.6F;
        upper.xScale = upper.zScale = 1 / Mth.sqrt(heave);

        for (int i = 0; i < lumps.length; i++)
        {
            float phase = age * (0.05F + i * 0.011F) + i * 1.7F;
            float swell = 1 + Mth.sin(phase) * 0.08F;
            lumps[i].xScale = lumps[i].zScale = swell;
            lumps[i].yScale = 2 - swell;
        }

        float open = (state.awake ? 0.12F : 0.03F) * (0.5F + 0.5F * Mth.sin(age * 0.07F));
        if (state.spit >= 0)
            open = Math.max(open, state.spit < 0.7F ? state.spit / 0.7F : 1 - (state.spit - 0.7F) / 0.3F * 0.6F);
        if (state.grab >= 0)
            open = Math.max(open, Mth.sin(state.grab * Mth.PI) * 0.6F);
        if (state.feed >= 0)
            open = Math.max(open, Mth.sin(state.feed * Mth.PI) * 0.9F);
        if (state.bite >= 0)
            open = Math.max(open, state.bite < 0.4F ? state.bite / 0.4F : Math.max(0, 1 - (state.bite - 0.4F) / 0.15F));
        lipLeft.yRot = -open * LIP_OPEN;
        lipRight.yRot = open * LIP_OPEN;
        gullet.xScale = 0.6F + open * 0.6F;

        for (int i = 0; i < eyes.length; i++)
        {
            // Asleep, its eyes hang half shut; awake they stare, and each blinks on its own
            float period = 60 + Math.floorMod(i * 37, 50);
            float cycle = (age / period + i * 0.31F) % 1;
            float blink = cycle > 0.94F ? Mth.sin((cycle - 0.94F) / 0.06F * Mth.PI) : 0;
            float lid = state.awake ? 1 - blink : 0.25F + 0.1F * Mth.sin(age * 0.03F + i);
            eyes[i].yScale = Math.max(0.08F, lid);
        }

        for (int t = 0; t < tentacles.length; t++)
        {
            for (int s = 0; s < SEGMENTS.length; s++)
            {
                float phase = age * (state.awake ? 0.08F : 0.035F) + t * 1.3F - s * 0.8F;
                var segment = tentacles[t][s];
                // Lying out on the floor, curling and uncurling at the tips
                segment.xRot = (s == 0 ? 0.12F : 0.05F) + Mth.sin(phase) * 0.12F * (s + 1) * 0.5F;
                segment.yRot = (s == 0 ? -TENTACLE_ANGLES[t] * Mth.DEG_TO_RAD : 0) + Mth.cos(phase * 0.8F) * 0.15F * s;
            }
            if (state.sweep >= 0)
            {
                // Drawn up and back as it winds up, then flung round all together
                float windup = Math.min(1, state.sweep / 0.55F);
                float lash = state.sweep < 0.55F ? 0 : Math.min(1, (state.sweep - 0.55F) / 0.15F);
                float settle = state.sweep < 0.7F ? 0 : (state.sweep - 0.7F) / 0.3F;
                var root = tentacles[t][0];
                root.xRot = Mth.lerp(settle, -0.9F * windup + 1.2F * lash, root.xRot);
                root.yRot += Mth.lerp(settle, -0.8F * windup + 2.0F * lash, 0);
                for (int s = 1; s < SEGMENTS.length; s++)
                    tentacles[t][s].xRot = Mth.lerp(settle, -0.35F * windup + 0.25F * lash, tentacles[t][s].xRot);
            }
        }
        if (state.grab >= 0)
        {
            // The front tentacle rears as another shoots from the gash
            var front = tentacles[0][0];
            front.xRot = -Mth.sin(state.grab * Mth.PI) * 1.1F;
        }
    }
}
