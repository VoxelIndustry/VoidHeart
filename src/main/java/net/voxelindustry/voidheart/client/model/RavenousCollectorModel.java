package net.voxelindustry.voidheart.client.model;

import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

import net.minecraft.resources.ResourceLocation;

public class RavenousCollectorModel extends DefaultedBlockGeoModel<RavenousCollectorTile>
{
    public RavenousCollectorModel()
    {
        super(new ResourceLocation(MODID, "ravenous_collector"));
    }
}
