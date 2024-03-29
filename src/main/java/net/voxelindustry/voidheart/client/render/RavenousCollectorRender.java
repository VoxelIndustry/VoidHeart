package net.voxelindustry.voidheart.client.render;

import net.voxelindustry.voidheart.client.model.RavenousCollectorModel;
import net.voxelindustry.voidheart.common.content.ravenouscollector.RavenousCollectorTile;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class RavenousCollectorRender extends GeoBlockRenderer<RavenousCollectorTile>
{
    public RavenousCollectorRender()
    {
        super(new RavenousCollectorModel());
    }
}
