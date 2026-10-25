package net.voxelindustry.voidheart.client.model;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.world.level.block.Block;
import net.voxelindustry.voidheart.client.model.conduit.ConduitBakedModel;
import net.voxelindustry.voidheart.client.model.conduit.ConduitSpriteManager;
import net.voxelindustry.voidheart.client.model.monolith.VoidMonolithBakedModel;
import net.voxelindustry.voidheart.client.model.monolith.VoidMonolithSpriteManager;
import net.voxelindustry.voidheart.client.model.portalframe.PortalFrameBakedModel;
import net.voxelindustry.voidheart.client.model.portalframe.PortalFrameCoreBakedModel;
import net.voxelindustry.voidheart.client.model.portalframe.PortalFrameVeinSpriteManager;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.Map;
import java.util.function.Function;

public class VoidHeartModelLoadingPlugin implements ModelLoadingPlugin
{
    private MaterialBaker spritesLoadedFrom;

    @Override
    public void initialize(Context pluginContext)
    {
        Map<Block, Function<BlockStateModel, BlockStateModel>> wrappers = Map.of(
                VoidHeartBlocks.PORTAL_FRAME, PortalFrameBakedModel::new,
                VoidHeartBlocks.PORTAL_FRAME_CORE, PortalFrameCoreBakedModel::new,
                VoidHeartBlocks.VOID_MONOLITH, VoidMonolithBakedModel::new,
                VoidHeartBlocks.VOID_CONDUIT, ConduitBakedModel::new
        );

        pluginContext.modifyBlockModelAfterBake().register((model, context) ->
        {
            var wrapper = wrappers.get(context.state().getBlock());
            if (wrapper == null)
                return model;

            loadSprites(context.baker().materials());
            return wrapper.apply(model);
        });
    }

    private synchronized void loadSprites(MaterialBaker materials)
    {
        if (spritesLoadedFrom == materials)
            return;

        VoidMonolithSpriteManager.loadSprites(materials);
        PortalFrameVeinSpriteManager.loadSprites(materials);
        ConduitSpriteManager.loadSprites(materials);
        spritesLoadedFrom = materials;
    }
}
