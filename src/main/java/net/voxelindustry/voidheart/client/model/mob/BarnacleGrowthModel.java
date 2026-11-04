package net.voxelindustry.voidheart.client.model.mob;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.Identifier;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

// One little shell, drawn wherever a barnacle has taken root on a player
public final class BarnacleGrowthModel
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "barnacle_growth"), "main");

    private BarnacleGrowthModel()
    {
    }

    public static LayerDefinition createBodyLayer()
    {
        var mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("growth", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2, -1, -2, 4, 1, 4)
                        .texOffs(16, 0).addBox(-1, -2, -1, 2, 1, 2),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 16);
    }
}
