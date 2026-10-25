package net.voxelindustry.voidheart.client.model;

import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import com.geckolib.model.DefaultedBlockGeoModel;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

import net.minecraft.resources.Identifier;

public class RavenousCollectorModel extends DefaultedBlockGeoModel<RavenousCollectorTile>
{
    public RavenousCollectorModel()
    {
        super(Identifier.fromNamespaceAndPath(MODID, "ravenous_collector"));
    }
}
