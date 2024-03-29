package net.voxelindustry.voidheart.client.model;

import net.minecraft.util.Identifier;
import net.voxelindustry.voidheart.common.content.ravenouscollector.RavenousCollectorTile;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class RavenousCollectorModel extends DefaultedBlockGeoModel<RavenousCollectorTile>
{
    public RavenousCollectorModel()
    {
        super(new Identifier(MODID, "ravenous_collector"));
    }
}
