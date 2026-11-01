package net.voxelindustry.voidheart.client.model;

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
import net.voxelindustry.voidheart.client.render.state.ShoulderEyeRenderState;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * An eyeball looking along -z, with an iris bulging out of its front, a lid sliding down when it blinks and veiny tendrils trailing behind.
 */
public class ShoulderEyeModel extends EntityModel<ShoulderEyeRenderState>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "shoulder_eye"), "main");

    private static final int     TENDRILS      = 4;
    private static final float[] TENDRIL_X     = {-1.5F, 1.5F, -1.5F, 1.5F};
    private static final float[] TENDRIL_Y     = {-1.5F, -1.5F, 1.5F, 1.5F};
    private static final float   TENDRIL_SPLAY = 0.3F;

    private final ModelPart   eye;
    private final ModelPart   iris;
    private final ModelPart   lid;
    private final ModelPart[] tendrils = new ModelPart[TENDRILS];
    private final ModelPart[] tips     = new ModelPart[TENDRILS];

    public ShoulderEyeModel(ModelPart root)
    {
        super(root);
        eye = root.getChild("eye");
        iris = eye.getChild("iris");
        lid = eye.getChild("lid");
        for (int i = 0; i < TENDRILS; i++)
        {
            tendrils[i] = eye.getChild("tendril" + i);
            tips[i] = tendrils[i].getChild("tip");
        }
    }

    public static LayerDefinition createBodyLayer()
    {
        var mesh = new MeshDefinition();
        var eye = mesh.getRoot().addOrReplaceChild("eye",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3, -3, -3, 6, 6, 6),
                PartPose.offset(0, 21, 0));

        eye.addOrReplaceChild("iris",
                CubeListBuilder.create().texOffs(0, 14).addBox(-2, -2, -0.5F, 4, 4, 1),
                PartPose.offset(0, 0, -3));
        eye.addOrReplaceChild("lid",
                CubeListBuilder.create().texOffs(24, 0).addBox(-3, 0, -0.5F, 6, 6, 1, new CubeDeformation(0.15F)),
                PartPose.offset(0, -3, -4.1F));

        for (int i = 0; i < TENDRILS; i++)
        {
            var tendril = eye.addOrReplaceChild("tendril" + i,
                    CubeListBuilder.create().texOffs(40, 0).addBox(-0.5F, -0.5F, 0, 1, 1, 3),
                    PartPose.offset(TENDRIL_X[i], TENDRIL_Y[i], 2.5F));
            tendril.addOrReplaceChild("tip",
                    CubeListBuilder.create().texOffs(40, 6).addBox(-0.5F, -0.5F, 0, 1, 1, 3, new CubeDeformation(-0.15F)),
                    PartPose.offset(0, 0, 2.7F));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(ShoulderEyeRenderState state)
    {
        super.setupAnim(state);

        eye.yRot = state.yRot * Mth.DEG_TO_RAD;
        eye.xRot = state.xRot * Mth.DEG_TO_RAD;

        float age = state.ageInTicks;
        float pulse = 1 + Mth.sin(age * 0.11F) * 0.025F;
        eye.xScale = pulse;
        eye.yScale = pulse;
        eye.zScale = pulse;

        iris.x = state.pupilX;
        iris.y = state.pupilY;

        lid.visible = state.blink > 0;
        lid.yScale = Math.max(state.blink, 0.01F);

        boolean firing = state.beamTo != null;
        float speed = firing ? 0.45F : 0.16F;
        float sway = firing ? 0.12F : 0.28F;
        for (int i = 0; i < TENDRILS; i++)
        {
            float phase = age * speed + i * 1.7F;
            tendrils[i].xRot = -TENDRIL_Y[i] * TENDRIL_SPLAY / 1.5F + Mth.sin(phase) * sway;
            tendrils[i].yRot = TENDRIL_X[i] * TENDRIL_SPLAY / 1.5F + Mth.cos(phase * 0.8F) * sway;
            tips[i].xRot = Mth.sin(phase - 0.9F) * sway * 1.4F;
            tips[i].yRot = Mth.cos(phase * 0.8F - 0.9F) * sway * 1.4F;
        }
    }
}
