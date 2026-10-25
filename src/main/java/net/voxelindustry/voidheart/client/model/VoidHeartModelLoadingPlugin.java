package net.voxelindustry.voidheart.client.model;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.voxelindustry.voidheart.client.model.conduit.ConduitUnbakedModel;
import net.voxelindustry.voidheart.client.model.monolith.VoidMonolithUnbakedModel;
import net.voxelindustry.voidheart.client.model.portalframe.PortalFrameCoreUnbakedModel;
import net.voxelindustry.voidheart.client.model.portalframe.PortalFrameUnbakedModel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartModelLoadingPlugin implements ModelLoadingPlugin
{
    public final ModelResourceLocation PORTAL_FRAME = new ModelResourceLocation(MODID, "portal_frame", "");
    public final ModelResourceLocation PORTAL_FRAME_CORE = new ModelResourceLocation(MODID, "portal_frame_core", "");
    public final ModelResourceLocation VOID_MONOLITH = new ModelResourceLocation(MODID, "void_monolith", "");
    public final ModelResourceLocation CONDUIT = new ModelResourceLocation(MODID, "void_conduit", "");

    private final Map<ModelResourceLocation, Function<UnbakedModel, UnbakedModel>> modelMap = new HashMap<>();

    @Override
    public void onInitializeModelLoader(Context pluginContext)
    {
        modelMap.put(PORTAL_FRAME, PortalFrameUnbakedModel::new);
        modelMap.put(PORTAL_FRAME_CORE, PortalFrameCoreUnbakedModel::new);
        modelMap.put(VOID_MONOLITH, VoidMonolithUnbakedModel::new);
        modelMap.put(CONDUIT, ConduitUnbakedModel::new);

        pluginContext.modifyModelOnLoad().register((original, context) ->
        {
            if (!context.id().getNamespace().equals(MODID))
                return original;

            if (context.id() instanceof ModelResourceLocation modelIdentifier)
            {
                if (modelIdentifier.getVariant().equals("inventory"))
                    return original;
            }

            if (context.id().getPath().equals(PORTAL_FRAME.getPath()))
                return modelMap.get(PORTAL_FRAME).apply(original);
            if (context.id().getPath().equals(PORTAL_FRAME_CORE.getPath()))
                return modelMap.get(PORTAL_FRAME_CORE).apply(original);
            if (context.id().getPath().equals(VOID_MONOLITH.getPath()))
                return modelMap.get(VOID_MONOLITH).apply(original);
            if (context.id().getPath().equals(CONDUIT.getPath()))
                return modelMap.get(CONDUIT).apply(original);
            return original;
        });
    }
}
