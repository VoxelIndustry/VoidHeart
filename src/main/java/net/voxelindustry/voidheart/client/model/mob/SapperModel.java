package net.voxelindustry.voidheart.client.model.mob;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.render.state.SecurityMobRenderState;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A flat copper body on six thin legs, with the drum of its charge on its back and one eye in front. Folded, its legs lie tucked under it; they splay as it wakes, and scuttle in two tripods as it runs.
 */
public class SapperModel extends EntityModel<SecurityMobRenderState>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "sapper"), "main");

    private static final int LEGS_A_SIDE = 3;
    // A leg's lean on the right side, mirrored on the left: folded in under the body, and planted out and down
    private static final float TUCKED = -1.35F;
    private static final float SPLAYED = 0.45F;
    private static final float SCUTTLE_RATE = 1.6F;
    private static final float SCUTTLE_REACH = 0.7F;

    private final ModelPart[] legs = new ModelPart[LEGS_A_SIDE * 2];

    public SapperModel(ModelPart root)
    {
        super(root);
        for (int leg = 0; leg < legs.length; leg++)
            legs[leg] = root.getChild("leg" + leg);
    }

    public static LayerDefinition createBodyLayer()
    {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        // The drum stands a hair into the body it is bolted on, and the eye a hair off its face
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3, -2, -4, 6, 3, 8)
                        .texOffs(0, 12).addBox(-2, -4.9F, -2.5F, 4, 3, 5)
                        .texOffs(20, 12).addBox(-1, -1.5F, -5.1F, 2, 2, 1),
                PartPose.offset(0, 20, 0));
        for (int leg = 0; leg < LEGS_A_SIDE * 2; leg++)
        {
            boolean left = leg >= LEGS_A_SIDE;
            float z = (leg % LEGS_A_SIDE - 1) * 2.8F;
            var builder = CubeListBuilder.create().texOffs(28, 0);
            if (left)
                builder.mirror();
            root.addOrReplaceChild("leg" + leg, builder.addBox(-0.5F, 0, -0.5F, 1, 4, 1), PartPose.offset(left ? 3.6F : -3.6F, 20.2F, z));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(SecurityMobRenderState state)
    {
        super.setupAnim(state);
        float splay = Mth.lerp(state.wake, TUCKED, SPLAYED);
        for (int leg = 0; leg < legs.length; leg++)
        {
            boolean left = leg >= LEGS_A_SIDE;
            legs[leg].zRot = left ? -splay : splay;
            // Two tripods: the middle leg of a side steps with the outer legs of the other
            float phase = (leg % LEGS_A_SIDE == 1) == left ? 0 : Mth.PI;
            legs[leg].xRot = state.wake * Mth.cos(state.walkAnimationPos * SCUTTLE_RATE + phase) * state.walkAnimationSpeed * SCUTTLE_REACH;
        }
    }
}
