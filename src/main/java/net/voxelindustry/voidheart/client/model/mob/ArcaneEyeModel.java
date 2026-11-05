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
import net.voxelindustry.voidheart.client.render.state.ArcaneEyeRenderState;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A heavy-browed eyeball looking along -z, its lid half down while it stalks and thrown open when it fires, with long tendrils lit at the tips.
 */
public class ArcaneEyeModel extends EntityModel<ArcaneEyeRenderState>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "arcane_eye"), "main");

    private static final float CENTER_Y = 18;
    private static final float[] TENDRIL_X = {-3, 3, -3, 3, 0, 0};
    private static final float[] TENDRIL_Y = {-3, -3, 3, 3, -4, 4};
    private static final float TENDRIL_SPLAY = 0.1F;

    private final ModelPart eye;
    private final ModelPart iris;
    private final ModelPart lid;
    private final ModelPart[] tendrils = new ModelPart[TENDRIL_X.length];
    private final ModelPart[] tips = new ModelPart[TENDRIL_X.length];

    public ArcaneEyeModel(ModelPart root)
    {
        super(root);
        eye = root.getChild("eye");
        iris = eye.getChild("iris");
        lid = eye.getChild("lid");
        for (int i = 0; i < tendrils.length; i++)
        {
            tendrils[i] = eye.getChild("tendril" + i);
            tips[i] = tendrils[i].getChild("tip");
        }
    }

    public static LayerDefinition createBodyLayer()
    {
        var mesh = new MeshDefinition();
        var eye = mesh.getRoot().addOrReplaceChild("eye", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5, -5, -5, 10, 10, 10), PartPose.offset(0, CENTER_Y, 0));

        eye.addOrReplaceChild("iris", CubeListBuilder.create()
                .texOffs(0, 20).addBox(-3, -3, -0.5F, 6, 6, 1), PartPose.offset(0, 0, -5));
        eye.addOrReplaceChild("lid", CubeListBuilder.create()
                .texOffs(0, 27).addBox(-5, 0, -0.5F, 10, 10, 1, new CubeDeformation(0.15F)), PartPose.offset(0, -5, -6.1F));
        eye.addOrReplaceChild("brow", CubeListBuilder.create()
                .texOffs(22, 27).addBox(-5.5F, -1, -2, 11, 2, 4), PartPose.offset(0, -5, -4.5F));

        for (int i = 0; i < TENDRIL_X.length; i++)
        {
            var tendril = eye.addOrReplaceChild("tendril" + i, CubeListBuilder.create()
                    .texOffs(40, 0).addBox(-0.5F, -0.5F, 0, 1, 1, 4), PartPose.offset(TENDRIL_X[i], TENDRIL_Y[i], 4.5F));
            tendril.addOrReplaceChild("tip", CubeListBuilder.create()
                    .texOffs(40, 6).addBox(-0.5F, -0.5F, 0, 1, 1, 4, new CubeDeformation(-0.15F)), PartPose.offset(0, 0, 3.7F));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(ArcaneEyeRenderState state)
    {
        super.setupAnim(state);

        eye.yRot = state.yRot * Mth.DEG_TO_RAD;
        eye.xRot = state.xRot * Mth.DEG_TO_RAD;

        iris.x = state.pupilX;
        iris.y = state.pupilY;

        lid.visible = state.lid > 0.01F;
        lid.yScale = Math.max(state.lid, 0.01F);

        float age = state.ageInTicks;
        float speed = state.charging ? 0.5F : 0.09F;
        float sway = state.charging ? 0.1F : 0.26F;
        for (int i = 0; i < tendrils.length; i++)
        {
            float phase = age * speed + i * 1.9F;
            tendrils[i].xRot = -TENDRIL_Y[i] * TENDRIL_SPLAY + Mth.sin(phase) * sway;
            tendrils[i].yRot = TENDRIL_X[i] * TENDRIL_SPLAY + Mth.cos(phase * 0.8F) * sway;
            tips[i].xRot = Mth.sin(phase - 0.9F) * sway * 1.4F;
            tips[i].yRot = Mth.cos(phase * 0.8F - 0.9F) * sway * 1.4F;
        }
    }
}
