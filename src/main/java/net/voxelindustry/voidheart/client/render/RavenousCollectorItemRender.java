package net.voxelindustry.voidheart.client.render;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.model.DefaultedBlockGeoModel;
import com.geckolib.renderer.GeoItemRenderer;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageItemBlock;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Draws the collector item with its block GeckoLib model. The item has no animation controllers, so it stays in its rest pose.
 */
public class RavenousCollectorItemRender implements GeoRenderProvider
{
    private GeoItemRenderer<RavenousStorageItemBlock> renderer;

    @Override
    public GeoItemRenderer<?> getGeoItemRenderer()
    {
        if (renderer == null)
            renderer = new GeoItemRenderer<>(new DefaultedBlockGeoModel<>(Identifier.fromNamespaceAndPath(MODID, "ravenous_collector")));
        return renderer;
    }
}
