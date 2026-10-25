package net.voxelindustry.voidheart.client.render;

import com.geckolib.renderer.GeoBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.voxelindustry.voidheart.client.model.RavenousCollectorModel;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;

public class RavenousCollectorRender extends GeoBlockRenderer<RavenousCollectorTile, BlockEntityRenderState>
{
    public RavenousCollectorRender(BlockEntityRendererProvider.Context context)
    {
        super(context, new RavenousCollectorModel());
    }
}
