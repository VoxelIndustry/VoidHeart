package net.voxelindustry.voidheart.client.render;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.model.DefaultedBlockGeoModel;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
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
        {
            renderer = new GeoItemRenderer<>(new DefaultedBlockGeoModel<RavenousStorageItemBlock>(Identifier.fromNamespaceAndPath(MODID, "ravenous_collector")))
            {
                // Item models are centered by GeckoLib, this one is a block model standing on its bottom like GeoBlockRenderer draws it
                @Override
                public void adjustRenderPose(RenderPassInfo<GeoRenderState> renderPassInfo)
                {
                    renderPassInfo.poseStack().translate(0.5F, 0, 0.5F);
                }
            };
        }
        return renderer;
    }
}
