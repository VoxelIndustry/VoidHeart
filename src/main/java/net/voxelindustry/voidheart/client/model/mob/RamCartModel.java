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
 * A copper wagon that runs either way: the same ram plate and the same lamp at each end, a boiler and its stack in the middle, four iron wheels. Its wheels turn with the track it covers, and it shakes while it builds steam.
 */
public class RamCartModel extends EntityModel<SecurityMobRenderState.RamCart>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "ram_cart"), "main");

    private static final float SHAKE = 0.35F;
    private static final float SHAKE_RATE = 2.6F;

    private final ModelPart body;
    private final ModelPart[] wheels = new ModelPart[4];

    public RamCartModel(ModelPart root)
    {
        super(root);
        body = root.getChild("body");
        for (int wheel = 0; wheel < wheels.length; wheel++)
            wheels[wheel] = root.getChild("wheel" + wheel);
    }

    public static LayerDefinition createBodyLayer()
    {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        // The plates stand a hair off the ends of the chassis, the boiler a hair over it, and each lamp a hair off its plate
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-7, -4, -9, 14, 8, 18)
                        .texOffs(0, 26).addBox(-8, -7, -11.1F, 16, 12, 2)
                        .texOffs(0, 26).addBox(-8, -7, 9.1F, 16, 12, 2)
                        .texOffs(36, 26).addBox(-4, -9.1F, -5, 8, 5, 10)
                        .texOffs(72, 26).addBox(-1, -13.2F, -1, 2, 4, 2)
                        .texOffs(16, 40).addBox(-1.5F, -3, -12.2F, 3, 3, 1)
                        .texOffs(16, 40).addBox(-1.5F, -3, 11.2F, 3, 3, 1),
                PartPose.offset(0, 15, 0));
        for (int wheel = 0; wheel < 4; wheel++)
        {
            boolean left = wheel % 2 == 1;
            var builder = CubeListBuilder.create().texOffs(0, 40);
            if (left)
                builder.mirror();
            root.addOrReplaceChild("wheel" + wheel, builder.addBox(-1, -3, -3, 2, 6, 6), PartPose.offset(left ? 8.1F : -8.1F, 21, wheel < 2 ? -5.5F : 5.5F));
        }
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public void setupAnim(SecurityMobRenderState.RamCart state)
    {
        super.setupAnim(state);
        for (var wheel : wheels)
            wheel.xRot = state.roll;
        if (state.windingUp)
            body.x += Mth.sin(state.ageInTicks * SHAKE_RATE) * SHAKE;
    }
}
