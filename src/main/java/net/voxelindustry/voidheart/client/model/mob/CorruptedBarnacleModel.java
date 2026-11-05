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
import net.voxelindustry.voidheart.client.render.state.BarnacleRenderState;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A stepped shell on tiny legs. Once grown the legs are gone and a long jointed limb lashes out from under its lips.
 */
public class CorruptedBarnacleModel extends EntityModel<BarnacleRenderState>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "corrupted_barnacle"), "main");

    private static final int LEGS = 6;
    private static final int SEGMENTS = 5;
    private static final float SEGMENT_LENGTH = 8;
    private static final float LIP_REST = 0.2F;
    private static final float LIP_OPEN = 0.7F;
    private static final float LEG_RATE = 1;
    private static final float LEG_SWING = 0.5F;
    private static final float COILED_EXTENSION = 0.12F;
    private static final float COIL_PITCH = -0.5F;
    private static final float SWAY = 0.09F;

    private final ModelPart frontLip;
    private final ModelPart backLip;
    private final ModelPart[] legs = new ModelPart[LEGS];
    private final ModelPart[] segments = new ModelPart[SEGMENTS];
    private final ModelPart limb;

    public CorruptedBarnacleModel(ModelPart root)
    {
        super(root);
        frontLip = root.getChild("front_lip");
        backLip = root.getChild("back_lip");
        for (int i = 0; i < LEGS; i++)
            legs[i] = root.getChild("leg_" + i);

        limb = root.getChild("limb");
        var parent = limb;
        for (int i = 0; i < SEGMENTS; i++)
        {
            segments[i] = parent.getChild("segment_" + i);
            parent = segments[i];
        }
    }

    public static LayerDefinition createBodyLayer()
    {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();

        root.addOrReplaceChild("shell", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-6, 21, -6, 12, 3, 12)
                        .texOffs(0, 15).addBox(-4.5F, 18, -4.5F, 9, 3, 9)
                        .texOffs(36, 15).addBox(-3, 16, -3, 6, 2, 6),
                PartPose.ZERO);
        root.addOrReplaceChild("front_lip", CubeListBuilder.create().texOffs(36, 23).addBox(-2, -1, -3, 4, 1, 3), PartPose.offset(0, 16, 0));
        root.addOrReplaceChild("back_lip", CubeListBuilder.create().texOffs(36, 23).addBox(-2, -1, -3, 4, 1, 3), PartPose.offsetAndRotation(0, 16, 0, 0, Mth.PI, 0));

        for (int i = 0; i < LEGS; i++)
        {
            float angle = i * Mth.TWO_PI / LEGS + 0.5F;
            root.addOrReplaceChild("leg_" + i, CubeListBuilder.create().texOffs(48, 0).addBox(0, -0.5F, -0.5F, 3, 1, 1),
                    PartPose.offsetAndRotation(Mth.cos(angle) * 5.5F, 23.5F, Mth.sin(angle) * 5.5F, 0, -angle, 0));
        }

        var limb = root.addOrReplaceChild("limb", CubeListBuilder.create(), PartPose.offset(0, 16.5F, 0));
        PartDefinition parent = limb;
        for (int i = 0; i < SEGMENTS; i++)
        {
            float offset = i == 0 ? 0 : -SEGMENT_LENGTH;
            var builder = CubeListBuilder.create().texOffs(0, 27).addBox(-1, -1, -SEGMENT_LENGTH, 2, 2, 8);
            if (i == SEGMENTS - 1)
            {
                builder.texOffs(20, 27).addBox(-2, -1.5F, -SEGMENT_LENGTH - 4, 4, 1, 5)
                        .texOffs(20, 27).addBox(-2, 0.5F, -SEGMENT_LENGTH - 4, 4, 1, 5);
            }
            parent = parent.addOrReplaceChild("segment_" + i, builder, PartPose.offset(0, 0, offset));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(BarnacleRenderState state)
    {
        super.setupAnim(state);

        float strike = Mth.sin(state.attack * Mth.PI);
        float breathe = Mth.sin(state.ageInTicks * 0.05F) * 0.1F;
        float open = LIP_REST + breathe + (state.aggressive ? 0.2F : 0) + strike * LIP_OPEN;
        frontLip.xRot = -open;
        backLip.xRot = -open;

        boolean walks = !state.rooted;
        for (int i = 0; i < LEGS; i++)
        {
            legs[i].visible = walks;
            legs[i].zRot = Mth.sin(state.stride * LEG_RATE + i * 1.7F) * LEG_SWING * state.strideAmount;
        }

        limb.visible = state.rooted;
        if (state.rooted)
            setLimb(state, strike);
    }

    /**
     * Mostly coiled inside the shell, then thrown straight at whatever it faces.
     */
    private void setLimb(BarnacleRenderState state, float strike)
    {
        float extension = Mth.lerp(strike, COILED_EXTENSION, 1);
        limb.xRot = Mth.lerp(strike, COIL_PITCH, state.xRot * Mth.DEG_TO_RAD);
        for (int i = 0; i < SEGMENTS; i++)
        {
            segments[i].zScale = extension;
            segments[i].z = i == 0 ? 0 : -SEGMENT_LENGTH * extension;
            float wave = Mth.sin(state.ageInTicks * 0.12F + i * 0.9F) * SWAY * (1 - strike);
            segments[i].yRot = wave;
            segments[i].xRot = (1 - strike) * COIL_PITCH * 0.5F;
        }
    }
}
