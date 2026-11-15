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
import net.voxelindustry.voidheart.client.render.state.LesserShoggothRenderState;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A heap of protoplasm with lumps that heave, eyes and mouths that well up out of it and sink back, and limbs it puts out and draws in. Its
 * front, where the lashing limb grows, looks along -z.
 */
public class LesserShoggothModel extends EntityModel<LesserShoggothRenderState>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "lesser_shoggoth"), "main");

    private static final float HALF_PI = Mth.HALF_PI;

    // x, y, z of each lump's middle, then its size; texture offsets after
    private static final float[][] LUMPS = {
            { -4.3F, -9.2F, -2.7F, 11, 7, 11, 72, 0 },
            { 5.1F, -8.6F, 3.9F, 9, 6, 9, 72, 18 },
            { 2.7F, -11.3F, -4.4F, 7, 5, 7, 0, 28 },
            { -6.2F, -6.1F, 5.6F, 6, 5, 6, 28, 28 },
            { -1.1F, -1.9F, -8.7F, 8, 4, 8, 84, 33 } };
    // x, y, z on the surface, then the way it looks: 0 front, 1 right, 2 back, 3 left, 4 up
    private static final float[][] EYES = {
            { -3.2F, -6.1F, -9.31F, 0 }, { 4.7F, -4.3F, -9.33F, 0 }, { 9.32F, -5.2F, -2.1F, 1 }, { 9.34F, -3.1F, 4.6F, 1 },
            { -9.35F, -4.4F, 1.3F, 3 }, { 1.7F, -4.6F, 9.36F, 2 }, { -4.6F, -14.41F, -2.3F, 4 }, { 5.3F, -12.83F, 4.1F, 4 } };
    private static final float[][] MOUTHS = { { -1.1F, -1.6F, -12.76F, 0 }, { -9.44F, -6.9F, -4.2F, 3 }, { 9.43F, -7.7F, 1.0F, 1 } };
    // x, y, z where each limb leaves the body, and the way it points
    private static final float[][] LIMBS = { { 4.6F, -4.2F, -7.4F, -0.45F }, { -8.1F, -5.1F, 1.4F, HALF_PI }, { 2.2F, -6.3F, 8.1F, Mth.PI } };

    private static final float LASH_RAISED = -1.1F;
    private static final float LASH_STRUCK = 0.5F;

    private final ModelPart body;
    private final ModelPart[] lumps = new ModelPart[LUMPS.length];
    private final ModelPart[] eyes = new ModelPart[EYES.length];
    private final ModelPart[] mouths = new ModelPart[MOUTHS.length];
    private final ModelPart[] limbs = new ModelPart[LIMBS.length];
    private final ModelPart[] limbTips = new ModelPart[LIMBS.length];

    public LesserShoggothModel(ModelPart root)
    {
        super(root);
        body = root.getChild("body");
        for (int i = 0; i < lumps.length; i++)
            lumps[i] = body.getChild("lump" + i);
        for (int i = 0; i < eyes.length; i++)
            eyes[i] = body.getChild("eye" + i);
        for (int i = 0; i < mouths.length; i++)
            mouths[i] = body.getChild("mouth" + i);
        for (int i = 0; i < limbs.length; i++)
        {
            limbs[i] = body.getChild("limb" + i);
            limbTips[i] = limbs[i].getChild("tip");
        }
    }

    public static LayerDefinition createBodyLayer()
    {
        var mesh = new MeshDefinition();
        var body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-9, -10, -9, 18, 10, 18), PartPose.offset(0, 24, 0));
        for (int i = 0; i < LUMPS.length; i++)
        {
            var l = LUMPS[i];
            body.addOrReplaceChild("lump" + i, CubeListBuilder.create().texOffs((int) l[6], (int) l[7])
                    .addBox(-l[3] / 2, -l[4] + 2, -l[5] / 2, l[3], l[4], l[5]), PartPose.offset(l[0], l[1], l[2]));
        }
        for (int i = 0; i < EYES.length; i++)
            facing(body, "eye" + i, CubeListBuilder.create().texOffs(i * 8, 48).addBox(-1.5F, -1.5F, -0.5F, 3, 3, 1), EYES[i]);
        for (int i = 0; i < MOUTHS.length; i++)
            facing(body, "mouth" + i, CubeListBuilder.create().texOffs(64 + i * 12, 48).addBox(-2.5F, -1, -0.5F, 5, 2, 1), MOUTHS[i]);
        for (int i = 0; i < LIMBS.length; i++)
        {
            var l = LIMBS[i];
            var limb = body.addOrReplaceChild("limb" + i, CubeListBuilder.create().texOffs(i * 18, 52)
                    .addBox(-1.5F, -1.5F, -6, 3, 3, 6), PartPose.offsetAndRotation(l[0], l[1], l[2], 0, l[3], 0));
            limb.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(54 + i * 14, 52)
                    .addBox(-1, -1, -5, 2, 2, 5), PartPose.offset(0, 0, -5.5F));
        }
        return LayerDefinition.create(mesh, 128, 64);
    }

    private static void facing(PartDefinition body, String name, CubeListBuilder cube, float[] at)
    {
        int way = (int) at[3];
        float yRot = way == 4 ? 0 : switch (way)
        {
            case 1 -> -HALF_PI;
            case 2 -> Mth.PI;
            case 3 -> HALF_PI;
            default -> 0;
        };
        float xRot = way == 4 ? -HALF_PI : 0;
        body.addOrReplaceChild(name, cube, PartPose.offsetAndRotation(at[0], at[1], at[2], xRot, yRot, 0));
    }

    @Override
    public void setupAnim(LesserShoggothRenderState state)
    {
        super.setupAnim(state);
        float age = state.ageInTicks;
        int seed = state.seed;

        float breath = Mth.sin(age * 0.12F + seed);
        float squash = 1 + breath * 0.06F + state.moving * Mth.sin(age * 0.6F) * 0.08F;
        body.yScale = squash;
        body.xScale = body.zScale = 1 / Mth.sqrt(squash);
        body.yRot = 0;

        for (int i = 0; i < lumps.length; i++)
        {
            float phase = age * (0.07F + i * 0.013F) + i * 2.1F + seed * 0.37F;
            float swell = 0.85F + 0.25F * (0.5F + 0.5F * Mth.sin(phase));
            lumps[i].xScale = lumps[i].zScale = swell;
            lumps[i].yScale = 0.7F + 0.5F * (0.5F + 0.5F * Mth.sin(phase * 1.3F));
            lumps[i].y = LUMPS[i][1] + Mth.sin(phase * 0.8F) * 0.6F;
        }

        // Eyes and mouths well up out of it and sink back, each on its own slow cycle, more of them while it hunts
        for (int i = 0; i < eyes.length; i++)
            welling(eyes[i], age, seed, i, state.feeding ? 0.2F : 0.55F);
        for (int i = 0; i < mouths.length; i++)
            welling(mouths[i], age, seed + 97, i, 0.45F);

        for (int i = 0; i < limbs.length; i++)
        {
            float phase = age * 0.09F + i * 1.9F + seed * 0.21F;
            limbs[i].xRot = Mth.sin(phase) * 0.35F;
            limbs[i].yRot = LIMBS[i][3] + Mth.cos(phase * 0.7F) * 0.3F;
            limbs[i].zScale = 0.75F + 0.25F * Mth.sin(phase * 0.5F);
            limbTips[i].xRot = Mth.sin(phase - 1) * 0.5F;
            limbTips[i].yRot = Mth.cos(phase * 0.8F - 1) * 0.4F;
        }

        if (state.lash >= 0)
        {
            // Raised and drawn back, then flung out long and down at what it strikes
            float t = state.lash;
            float strike = t < 0.7F ? 0 : (t - 0.7F) / 0.3F;
            var limb = limbs[0];
            limb.yRot = LIMBS[0][3] * (1 - strike);
            limb.xRot = Mth.lerp(strike, LASH_RAISED * Math.min(1, t / 0.5F), LASH_STRUCK);
            limb.zScale = Mth.lerp(strike, 1, 2.4F);
            limbTips[0].xRot = Mth.lerp(strike, -0.6F, 0.3F);
            limbTips[0].zScale = Mth.lerp(strike, 1, 1.6F);
        }
    }

    /**
     * Sets how far an eye or a mouth has come up out of the flesh: hidden most of the time, opening and shutting along its depth.
     */
    private static void welling(ModelPart part, float age, int seed, int index, float share)
    {
        float period = 90 + Math.floorMod(seed * 31 + index * 57, 70);
        float offset = Math.floorMod(seed * 17 + index * 41, 100) / 100F;
        float cycle = (age / period + offset) % 1;
        float open = cycle < share ? Mth.sin(cycle / share * Mth.PI) : 0;
        part.visible = open > 0.05F;
        part.xScale = 0.6F + 0.4F * open;
        part.yScale = Math.max(0.05F, open);
    }
}
