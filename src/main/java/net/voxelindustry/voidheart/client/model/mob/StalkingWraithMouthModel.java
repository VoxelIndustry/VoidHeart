package net.voxelindustry.voidheart.client.model.mob;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.client.render.state.StalkingWraithRenderState;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Only the Stalking Wraith's mouth, on a head posed the same way as the body's, so that it can be drawn opaque while the rest fades.
 */
public class StalkingWraithMouthModel extends EntityModel<StalkingWraithRenderState>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "stalking_wraith"), "mouth");

    private final ModelPart head;
    private final ModelPart[] mouth;

    public StalkingWraithMouthModel(ModelPart root)
    {
        super(root);
        head = root.getChild("head");
        mouth = StalkingWraithModel.mouthOf(head);
    }

    public static LayerDefinition createMouthLayer()
    {
        var mesh = new MeshDefinition();
        var head = mesh.getRoot().addOrReplaceChild("head", CubeListBuilder.create(), StalkingWraithModel.HEAD_POSE);
        StalkingWraithModel.addMouth(head);
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(StalkingWraithRenderState state)
    {
        super.setupAnim(state);
        StalkingWraithModel.poseHead(root(), head, state);
        StalkingWraithModel.setupMouth(mouth, state.smile);
    }
}
