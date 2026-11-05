package net.voxelindustry.voidheart.client.model.mob;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.render.state.EyeMawRenderState;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * An eyeball looking along -z that hinges open: the top jaw carries the iris and the bottom jaw a tongue, with teeth sunk into the other jaw while shut.
 * The back of the skull stays in place and holds the tendrils. Both sizes share this shape, the large one with every part twice as fine.
 */
public class EyeMawModel extends EntityModel<EyeMawRenderState>
{
    public static final ModelLayerLocation SMALL = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "eye_maw"), "main");
    public static final ModelLayerLocation LARGE = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "large_eye_maw"), "main");

    private static final float UPPER_OPEN = -58 * Mth.DEG_TO_RAD;
    private static final float LOWER_OPEN = 32 * Mth.DEG_TO_RAD;
    private static final float SMALL_CENTER_Y = 19.2F;
    private static final float LARGE_CENTER_Y = 16;
    private static final float[][] UPPER_TEETH = { {-3.5F, -5.5F}, {-1.5F, -5.5F}, {0.5F, -5.5F}, {2.5F, -5.5F}, {-3.5F, -3.5F}, {2.5F, -3.5F}, {-3.5F, -1.5F}, {2.5F, -1.5F} };
    private static final float[][] LOWER_TEETH = { {-2.5F, -5.0F}, {-0.5F, -5.0F}, {1.5F, -5.0F}, {-3.0F, -3.0F}, {2.0F, -3.0F}, {-3.0F, -1.0F}, {2.0F, -1.0F} };
    private static final float[] TENDRIL_X = {-2, 2, -2, 2, 0, 0, -3, 3};
    private static final float[] TENDRIL_Y = {-2, -2, 2, 2, -3, 3, 0, 0};
    private static final float TENDRIL_SPLAY = 0.2F;
    private static final int TENDRILS_PER_SCALE = 4;

    private final ModelPart eye;
    private final ModelPart upper;
    private final ModelPart lower;
    private final ModelPart iris;
    private final ModelPart lid;
    private final float irisScale;
    private final ModelPart[] tendrils;
    private final ModelPart[] tips;

    public EyeMawModel(ModelPart root, int scale)
    {
        super(root);
        eye = root.getChild("eye");
        upper = eye.getChild("upper");
        lower = eye.getChild("lower");
        iris = upper.getChild("iris");
        lid = upper.getChild("lid");
        irisScale = scale;
        int count = TENDRILS_PER_SCALE * scale;
        tendrils = new ModelPart[count];
        tips = new ModelPart[count];
        for (int i = 0; i < count; i++)
        {
            tendrils[i] = eye.getChild("tendril" + i);
            tips[i] = tendrils[i].getChild("tip");
        }
    }

    public static LayerDefinition createSmallLayer()
    {
        return createLayer(1, SMALL_CENTER_Y, 64, 32);
    }

    public static LayerDefinition createLargeLayer()
    {
        return createLayer(2, LARGE_CENTER_Y, 128, 64);
    }

    private static LayerDefinition createLayer(int s, float centerY, int textureWidth, int textureHeight)
    {
        var mesh = new MeshDefinition();
        var eye = mesh.getRoot().addOrReplaceChild("eye", CubeListBuilder.create()
                .texOffs(28 * s, 0).addBox(-4 * s, -4 * s, 2 * s, 8 * s, 8 * s, 2 * s), PartPose.offset(0, centerY, 0));

        var hinge = PartPose.offset(0, s, 2 * s);
        var upper = eye.addOrReplaceChild("upper", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4 * s, -5 * s, -6 * s, 8 * s, 5 * s, 6 * s), hinge);
        var lower = eye.addOrReplaceChild("lower", CubeListBuilder.create()
                .texOffs(0, 11 * s).addBox(-4 * s, 0, -6 * s, 8 * s, 3 * s, 6 * s), hinge);

        upper.addOrReplaceChild("iris", CubeListBuilder.create()
                .texOffs(28 * s, 10 * s).addBox(-2 * s, -4 * s, -0.5F, 4 * s, 4 * s, 1), PartPose.offset(0, 0, -6 * s));
        upper.addOrReplaceChild("lid", CubeListBuilder.create()
                .texOffs(28 * s, 16 * s).addBox(-4 * s, 0, -0.5F, 8 * s, 5 * s, 1, new CubeDeformation(0.15F)), PartPose.offset(0, -5 * s, -6 * s - 1.1F));

        upper.addOrReplaceChild("teeth", teeth(UPPER_TEETH, s, 0), PartPose.ZERO);
        lower.addOrReplaceChild("teeth", teeth(LOWER_TEETH, s, -2 * s), PartPose.ZERO);
        lower.addOrReplaceChild("tongue", CubeListBuilder.create()
                .texOffs(48 * s, 4 * s).addBox(-1.5F * s, -0.5F, -5 * s, 3 * s, s, 5 * s), PartPose.ZERO);

        for (int i = 0; i < TENDRILS_PER_SCALE * s; i++)
        {
            var tendril = eye.addOrReplaceChild("tendril" + i, CubeListBuilder.create()
                    .texOffs(48 * s, 10 * s).addBox(-0.5F * s, -0.5F * s, 0, s, s, 3 * s), PartPose.offset(TENDRIL_X[i] * s, TENDRIL_Y[i] * s, 3.5F * s));
            tendril.addOrReplaceChild("tip", CubeListBuilder.create()
                    .texOffs(48 * s, 14 * s).addBox(-0.5F * s, -0.5F * s, 0, s, s, 3 * s, new CubeDeformation(-0.15F)), PartPose.offset(0, 0, 2.7F * s));
        }
        return LayerDefinition.create(mesh, textureWidth, textureHeight);
    }

    private static CubeListBuilder teeth(float[][] spots, int s, float y)
    {
        var builder = CubeListBuilder.create().texOffs(48 * s, 0);
        for (var spot : spots)
            builder.addBox(spot[0] * s, y, spot[1] * s, s, 2 * s, s);
        return builder;
    }

    @Override
    public void setupAnim(EyeMawRenderState state)
    {
        super.setupAnim(state);

        eye.yRot = state.yRot * Mth.DEG_TO_RAD;
        eye.xRot = state.xRot * Mth.DEG_TO_RAD;

        upper.xRot = UPPER_OPEN * state.jaws;
        lower.xRot = LOWER_OPEN * state.jaws;

        iris.x = state.pupilX * irisScale;
        iris.y = state.pupilY * irisScale;

        lid.visible = state.blink > 0 && state.jaws < 0.05F;
        lid.yScale = Math.max(state.blink, 0.01F);

        float age = state.ageInTicks;
        float speed = 0.16F + state.flying * 0.3F;
        float sway = 0.3F - state.flying * 0.14F;
        for (int i = 0; i < tendrils.length; i++)
        {
            float phase = age * speed + i * 1.7F;
            tendrils[i].xRot = -TENDRIL_Y[i] * TENDRIL_SPLAY / 2 + Mth.sin(phase) * sway;
            tendrils[i].yRot = TENDRIL_X[i] * TENDRIL_SPLAY / 2 + Mth.cos(phase * 0.8F) * sway;
            tips[i].xRot = Mth.sin(phase - 0.9F) * sway * 1.4F;
            tips[i].yRot = Mth.cos(phase * 0.8F - 0.9F) * sway * 1.4F;
        }
    }
}
